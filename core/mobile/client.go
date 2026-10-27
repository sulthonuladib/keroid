// Package mobile is an Android-bindable client for Kero's existing SSH bridge.
// It contains no CLI, local daemon, terminal emulator, or process execution.
package mobile

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/hashicorp/yamux"
	"golang.org/x/crypto/ssh"
)

const protocolVersion = 3
const maxLine = 16 << 20

// Observer receives JSON events on a Go worker thread, never the UI thread.
type Observer interface {
	Event(message string)
	Closed(message string)
}

type config struct {
	Host        string `json:"host"`
	Port        int    `json:"port"`
	User        string `json:"user"`
	Fingerprint string `json:"fingerprint"`
	Executable  string `json:"executable"`
	// Transport is "ssh" (the bridge, the default) or "tailscale" (kerod's own
	// tailnet listener or Tailscale Serve). Desktop takes the same two ways.
	Transport string `json:"transport"`
	// Endpoint is a tailscale machine's address: http://host:7373 or
	// https://host.tailnet.ts.net. SSH machines ignore it.
	Endpoint string `json:"endpoint"`
}

type reply struct {
	result json.RawMessage
	err    error
}

// Client owns one machine's connection. Create a fresh Client for each attempt.
type Client struct {
	observer Observer
	mu       sync.Mutex
	wmu      sync.Mutex
	closed   bool
	nextID   int64
	pending  map[int64]chan reply
	tcp      net.Conn
	ssh      *ssh.Client
	ws       io.Closer
	mux      *yamux.Session
	control  net.Conn
}

func NewClient(observer Observer) *Client {
	return &Client{observer: observer, pending: make(map[int64]chan reply)}
}

func (c *Client) addResource(set func()) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.closed {
		return errors.New("connection cancelled")
	}
	set()
	return nil
}

// Connect reaches one machine over the transport its configuration names — SSH
// through `kero __bridge`, or kerod's own tailnet listener / Tailscale Serve —
// then negotiates Kero's mux and control protocols. It returns host metadata
// JSON. For SSH an unknown/mismatched host key fails BEFORE authenticating with
// the user key; a Tailscale machine sends no key at all, as kerod identifies the
// caller itself.
func (c *Client) Connect(configuration, privateKey, passphrase string) (string, error) {
	var cfg config
	if err := json.Unmarshal([]byte(configuration), &cfg); err != nil {
		return "", errors.New("invalid connection configuration")
	}
	switch cfg.Transport {
	case "", "ssh":
		return c.connectSSH(cfg, privateKey, passphrase)
	case "tailscale":
		return c.connectTailscale(cfg)
	default:
		return "", errors.New("unknown connection transport")
	}
}

// connectTailscale opens kerod's tailnet listener or Tailscale Serve. kerod's
// owner/listen-allow rules decide who gets in; this app adds no identity of its
// own and never falls back to SSH when refused.
func (c *Client) connectTailscale(cfg config) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), dialTimeout)
	defer cancel()
	conn, err := dialTailnet(ctx, cfg.Endpoint, clientName)
	if err != nil {
		return "", err
	}
	if err = c.addResource(func() { c.ws = conn }); err != nil {
		conn.Close()
		return "", err
	}
	return c.handshake(conn, "kerod")
}

// connectSSH authenticates, runs `kero __bridge` without a PTY, and hands the
// bridge's stdio to the shared handshake.
func (c *Client) connectSSH(cfg config, privateKey, passphrase string) (string, error) {
	if cfg.Host == "" || cfg.User == "" || cfg.Port < 1 || cfg.Port > 65535 {
		return "", errors.New("host, user and valid SSH port are required")
	}
	var signer ssh.Signer
	var err error
	if passphrase == "" {
		signer, err = ssh.ParsePrivateKey([]byte(privateKey))
	} else {
		signer, err = ssh.ParsePrivateKeyWithPassphrase([]byte(privateKey), []byte(passphrase))
	}
	if err != nil {
		return "", errors.New("cannot unlock private key: check its format and passphrase")
	}
	defer func() {
		if err != nil {
			c.Close()
		}
	}()
	address := net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
	tcp, err := net.DialTimeout("tcp", address, 15*time.Second)
	if err != nil {
		return "", fmt.Errorf("cannot reach SSH server: %w", err)
	}
	if err = c.addResource(func() { c.tcp = tcp }); err != nil {
		tcp.Close()
		return "", err
	}
	tcp.SetDeadline(time.Now().Add(20 * time.Second))
	var hostKeyError error
	conn, channels, requests, err := ssh.NewClientConn(tcp, address, &ssh.ClientConfig{
		User: cfg.User,
		Auth: []ssh.AuthMethod{ssh.PublicKeys(signer)},
		HostKeyCallback: func(_ string, _ net.Addr, key ssh.PublicKey) error {
			hostKeyError = checkHostKey(cfg.Fingerprint, ssh.FingerprintSHA256(key), key.Type())
			return hostKeyError
		},
	})
	if err != nil {
		if hostKeyError != nil {
			return "", hostKeyError
		}
		return "", fmt.Errorf("SSH authentication failed: %w", err)
	}
	tcp.SetDeadline(time.Time{})
	sc := ssh.NewClient(conn, channels, requests)
	if err = c.addResource(func() { c.ssh = sc }); err != nil {
		sc.Close()
		return "", err
	}
	// Bound bridge discovery and protocol startup as well as TCP/SSH setup.
	tcp.SetDeadline(time.Now().Add(20 * time.Second))
	sess, err := sc.NewSession()
	if err != nil {
		return "", err
	}
	stdout, err := sess.StdoutPipe()
	if err != nil {
		sess.Close()
		return "", err
	}
	stdin, err := sess.StdinPipe()
	if err != nil {
		sess.Close()
		return "", err
	}
	// Do not collect remote stderr indefinitely or put it into Android logs.
	sess.Stderr = io.Discard
	if err = sess.Start(bridgeCommand(cfg.Executable)); err != nil {
		sess.Close()
		return "", fmt.Errorf("cannot start Kero bridge: %w", err)
	}
	transport := &bridge{Reader: stdout, Writer: stdin, close: sess.Close}
	return c.handshake(transport, "Kero bridge")
}

// handshake negotiates the mux over an established transport and opens the
// control stream. It is the same for every transport: only the bytes' way in
// differs, so the terminal and sizing behaviour are unchanged.
func (c *Client) handshake(transport io.ReadWriteCloser, absent string) (string, error) {
	var err error
	defer func() {
		if err != nil {
			c.Close()
		}
	}()
	br := bufio.NewReaderSize(transport, 32<<10)
	hello := map[string]any{"kind": "mux", "mux": 1, "version": protocolVersion, "client": clientName, "compress": []string{"deflate"}}
	if err = json.NewEncoder(transport).Encode(hello); err != nil {
		return "", err
	}
	welcome, err := readWelcome(br)
	if err != nil {
		return "", fmt.Errorf("%s did not answer; is Kero running on the host? %w", absent, err)
	}
	if welcome.Error != "" {
		err = errors.New(welcome.Error)
		return "", err
	}
	if welcome.Mux != 1 {
		err = errors.New("unsupported Kero mux version")
		return "", err
	}
	var wire io.ReadWriteCloser = &bridge{Reader: br, Writer: transport, close: transport.Close}
	if welcome.Compress == "deflate" {
		wire = newFlateConn(wire)
	} else if welcome.Compress != "" {
		err = errors.New("unsupported compression")
		return "", err
	}
	muxConfig := yamux.DefaultConfig()
	muxConfig.KeepAliveInterval = 5 * time.Second
	muxConfig.ConnectionWriteTimeout = 10 * time.Second
	muxConfig.LogOutput = io.Discard
	mux, err := yamux.Client(wire, muxConfig)
	if err != nil {
		return "", err
	}
	if err = c.addResource(func() { c.mux = mux }); err != nil {
		mux.Close()
		return "", err
	}
	control, err := mux.Open()
	if err != nil {
		return "", err
	}
	if err = c.addResource(func() { c.control = control }); err != nil {
		control.Close()
		return "", err
	}
	// Bound the control welcome too: a host that stops answering must not hold
	// the attempt forever.
	control.SetDeadline(time.Now().Add(15 * time.Second))
	if err = json.NewEncoder(control).Encode(map[string]any{"kind": "control", "version": protocolVersion}); err != nil {
		return "", err
	}
	reader := bufio.NewReaderSize(control, 64<<10)
	line, err := readLine(reader, maxLine)
	if err != nil {
		return "", err
	}
	var cw struct {
		Version int    `json:"version"`
		Error   string `json:"error"`
	}
	if err = json.Unmarshal(line, &cw); err != nil {
		return "", err
	}
	if cw.Error != "" {
		err = errors.New(cw.Error)
		return "", err
	}
	if cw.Version != protocolVersion {
		err = fmt.Errorf("Kero protocol mismatch: phone supports %d; host uses %d. Update the appropriate client manually", protocolVersion, cw.Version)
		return "", err
	}
	control.SetDeadline(time.Time{})
	go c.read(reader)
	go func() { <-mux.CloseChan(); c.shutdown("Connection to the machine closed") }()
	return string(welcome.Host), nil
}

type muxWelcome struct {
	Mux      int             `json:"mux"`
	Compress string          `json:"compress"`
	Host     json.RawMessage `json:"host"`
	Error    string          `json:"error"`
}

func readWelcome(r *bufio.Reader) (muxWelcome, error) {
	// Shell startup files may print a few banners before the bridge answers.
	for range 100 {
		line, err := readLine(r, 64<<10)
		if err != nil {
			return muxWelcome{}, err
		}
		var w muxWelcome
		if json.Unmarshal(line, &w) == nil && (w.Mux != 0 || w.Error != "") {
			return w, nil
		}
	}
	return muxWelcome{}, errors.New("no mux welcome after shell banners")
}

func checkHostKey(expected, actual, algorithm string) error {
	if expected == actual {
		return nil
	}
	code := "host_key_unknown"
	if expected != "" {
		code = "host_key_changed"
	}
	payload, _ := json.Marshal(map[string]string{"code": code, "fingerprint": actual, "algorithm": algorithm})
	return errors.New(string(payload))
}

func quote(s string) string { return "'" + strings.ReplaceAll(s, "'", "'\"'\"'") + "'" }

func bridgeCommand(executable string) string {
	// Force POSIX syntax even when the account's login shell is fish/csh.
	script := `for k in "$HOME/.local/bin/kero" "$HOME/.config/kero/bin/kero" "$HOME/Library/Application Support/kero/bin/kero" /Applications/Kero.app/Contents/MacOS/Kero kero; do if command -v "$k" >/dev/null 2>&1; then exec "$k" __bridge --via ssh; fi; done; exit 127`
	if executable != "" {
		script = "exec " + quote(executable) + " __bridge --via ssh"
	}
	return "sh -c " + quote(script)
}

// Call sends one control request and returns its JSON result. Do not replay
// mutation calls automatically after a timeout: execution may have succeeded.
func (c *Client) Call(method, parameters string) (string, error) {
	var params json.RawMessage
	if parameters != "" {
		if !json.Valid([]byte(parameters)) {
			return "", errors.New("invalid request JSON")
		}
		params = json.RawMessage(parameters)
	}
	ch := make(chan reply, 1)
	c.mu.Lock()
	if c.closed || c.control == nil {
		c.mu.Unlock()
		return "", errors.New("not connected")
	}
	c.nextID++
	id := c.nextID
	c.pending[id] = ch
	control := c.control
	c.mu.Unlock()
	defer func() { c.mu.Lock(); delete(c.pending, id); c.mu.Unlock() }()
	request := struct {
		ID     int64           `json:"id"`
		Method string          `json:"method"`
		Params json.RawMessage `json:"params,omitempty"`
	}{id, method, params}
	c.wmu.Lock()
	control.SetWriteDeadline(time.Now().Add(15 * time.Second))
	err := json.NewEncoder(control).Encode(request)
	control.SetWriteDeadline(time.Time{})
	c.wmu.Unlock()
	if err != nil {
		return "", err
	}
	timer := time.NewTimer(15 * time.Second)
	defer timer.Stop()
	select {
	case res := <-ch:
		return string(res.result), res.err
	case <-timer.C:
		return "", errors.New("request timed out; its outcome is unknown. Refresh before retrying")
	}
}

func (c *Client) read(r *bufio.Reader) {
	for {
		line, err := readLine(r, maxLine)
		if err != nil {
			c.shutdown("Connection to the machine closed")
			return
		}
		var m struct {
			ID     int64           `json:"id"`
			Event  string          `json:"event"`
			Result json.RawMessage `json:"result"`
			Error  string          `json:"error"`
		}
		if json.Unmarshal(line, &m) != nil {
			c.shutdown("Invalid response from the machine")
			return
		}
		if m.Event != "" {
			if c.observer != nil {
				c.observer.Event(string(line))
			}
			continue
		}
		c.mu.Lock()
		ch := c.pending[m.ID]
		delete(c.pending, m.ID)
		c.mu.Unlock()
		if ch != nil {
			var callErr error
			if m.Error != "" {
				callErr = errors.New(m.Error)
			}
			ch <- reply{m.Result, callErr}
		}
	}
}

// Close disconnects this client only; remote sessions continue running.
func (c *Client) Close() { c.shutdown("") }

func (c *Client) shutdown(message string) {
	c.mu.Lock()
	if c.closed {
		c.mu.Unlock()
		return
	}
	c.closed = true
	tcp, sc, mux, ws := c.tcp, c.ssh, c.mux, c.ws
	for id, ch := range c.pending {
		ch <- reply{err: errors.New("disconnected")}
		delete(c.pending, id)
	}
	c.mu.Unlock()
	if tcp != nil {
		tcp.Close()
	}
	if mux != nil {
		mux.Close()
	}
	if sc != nil {
		sc.Close()
	}
	// A Tailscale connection that failed before the mux existed still owns the socket.
	if ws != nil {
		ws.Close()
	}
	if message != "" && c.observer != nil {
		c.observer.Closed(message)
	}
}

type bridge struct {
	io.Reader
	io.Writer
	close func() error
}

func (b *bridge) Close() error { return b.close() }

func readLine(r *bufio.Reader, max int) ([]byte, error) {
	var line []byte
	for {
		part, err := r.ReadSlice('\n')
		if len(line)+len(part) > max {
			return nil, errors.New("response exceeds protocol limit")
		}
		line = append(line, part...)
		if err == nil {
			return line, nil
		}
		if err != bufio.ErrBufferFull {
			return nil, err
		}
	}
}
