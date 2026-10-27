package mobile

import (
	"bufio"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"encoding/pem"
	"io"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/hashicorp/yamux"
	"golang.org/x/crypto/ssh"
)

// This server is local and ephemeral. It never runs commands or reaches a user's
// actual Kero daemon. Its generated identity exists only for the test lifetime.
type testSSHServer struct {
	listener  net.Listener
	hostKey   ssh.Signer
	identity  string
	authCount atomic.Int32
	input     chan string
	claimed   chan struct{}
}

func startTestSSH(t *testing.T) *testSSHServer {
	t.Helper()
	_, host, _ := ed25519.GenerateKey(rand.Reader)
	hostKey, err := ssh.NewSignerFromKey(host)
	if err != nil {
		t.Fatal(err)
	}
	_, private, _ := ed25519.GenerateKey(rand.Reader)
	clientKey, err := ssh.NewSignerFromKey(private)
	if err != nil {
		t.Fatal(err)
	}
	block, err := ssh.MarshalPrivateKey(private, "ephemeral test")
	if err != nil {
		t.Fatal(err)
	}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	s := &testSSHServer{listener: listener, hostKey: hostKey, identity: string(pem.EncodeToMemory(block)), input: make(chan string, 10), claimed: make(chan struct{}, 4)}
	var sockets sync.Map
	cfg := &ssh.ServerConfig{PublicKeyCallback: func(_ ssh.ConnMetadata, key ssh.PublicKey) (*ssh.Permissions, error) {
		s.authCount.Add(1)
		if string(key.Marshal()) != string(clientKey.PublicKey().Marshal()) {
			return nil, io.EOF
		}
		return nil, nil
	}}
	cfg.AddHostKey(hostKey)
	t.Cleanup(func() {
		listener.Close()
		sockets.Range(func(key, _ any) bool { key.(net.Conn).Close(); return true })
	})
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			sockets.Store(conn, true)
			go func() {
				defer sockets.Delete(conn)
				defer conn.Close()
				conn.SetDeadline(time.Now().Add(10 * time.Second))
				server, channels, requests, err := ssh.NewServerConn(conn, cfg)
				if err != nil {
					return
				}
				defer server.Close()
				go ssh.DiscardRequests(requests)
				for channel := range channels {
					if channel.ChannelType() != "session" {
						channel.Reject(ssh.UnknownChannelType, "session only")
						continue
					}
					ch, reqs, err := channel.Accept()
					if err != nil {
						return
					}
					go func() {
						defer ch.Close()
						for req := range reqs {
							var payload struct{ Command string }
							if req.Type != "exec" || ssh.Unmarshal(req.Payload, &payload) != nil || !strings.Contains(payload.Command, "__bridge --via ssh") {
								req.Reply(false, nil)
								continue
							}
							req.Reply(true, nil)
							s.serveBridge(ch)
							return
						}
					}()
				}
			}()
		}
	}()
	return s
}

func (s *testSSHServer) configuration(pin string) string {
	addr := s.listener.Addr().(*net.TCPAddr)
	b, _ := json.Marshal(config{Host: "127.0.0.1", Port: addr.Port, User: "test", Fingerprint: pin})
	return string(b)
}

func (s *testSSHServer) serveBridge(ch ssh.Channel) {
	reader := bufio.NewReader(ch)
	line, err := readLine(reader, 64<<10)
	if err != nil {
		return
	}
	var hello map[string]any
	if json.Unmarshal(line, &hello) != nil || hello["kind"] != "mux" {
		return
	}
	json.NewEncoder(ch).Encode(map[string]any{"mux": 1, "compress": "deflate", "host": map[string]any{"os": "linux", "version": 3}})
	compressed := newFlateConn(&bridge{Reader: reader, Writer: ch, close: ch.Close})
	cfg := yamux.DefaultConfig()
	cfg.LogOutput = io.Discard
	mux, err := yamux.Server(compressed, cfg)
	if err != nil {
		return
	}
	defer mux.Close()
	control, err := mux.Accept()
	if err != nil {
		return
	}
	defer control.Close()
	// Streams after the control one are attach connections.
	go func() {
		for {
			stream, err := mux.Accept()
			if err != nil {
				return
			}
			go s.serveAttach(stream)
		}
	}()
	cr := bufio.NewReader(control)
	if _, err := readLine(cr, 64<<10); err != nil {
		return
	}
	encoder := json.NewEncoder(control)
	encoder.Encode(map[string]any{"version": 3})
	for {
		line, err := readLine(cr, maxLine)
		if err != nil {
			return
		}
		var req struct {
			ID     int64
			Method string
			Params struct {
				ID   string
				Data string
			}
		}
		if json.Unmarshal(line, &req) != nil {
			return
		}
		var result any = true
		switch req.Method {
		case "subscribe":
			encoder.Encode(map[string]any{"event": "state", "state": map[string]any{"projects": []any{}, "sessions": []any{}}})
		case "read":
			result = map[string]string{"text": "hello from an SSH pane\n"}
		case "send":
			s.input <- req.Params.Data
		}
		if encoder.Encode(map[string]any{"id": req.ID, "result": result}) != nil {
			return
		}
	}
}

// serveAttach answers one attach stream: a welcome, the current screen, and
// then an echo of input, a resize acknowledgement, and claim notices.
func (s *testSSHServer) serveAttach(stream net.Conn) {
	defer stream.Close()
	reader := bufio.NewReader(stream)
	line, err := readLine(reader, 64<<10)
	if err != nil {
		return
	}
	var hello struct {
		Kind    string
		Version int
		Session string
		Cols    int
		Rows    int
	}
	if json.Unmarshal(line, &hello) != nil || hello.Kind != "attach" || hello.Version != protocolVersion || hello.Session != "s1" {
		return
	}
	json.NewEncoder(stream).Encode(map[string]any{"version": protocolVersion, "cols": 80, "rows": 24})
	writeFrameTo(stream, FrameOutput, []byte("snapshot"))
	for {
		kind, payload, err := readFrameFrom(reader)
		if err != nil {
			return
		}
		switch kind {
		case FrameInput:
			writeFrameTo(stream, FrameOutput, append([]byte("echo:"), payload...))
		case FrameResize:
			cols, rows, _, _, ok := parseResizePixels(payload)
			if !ok {
				return
			}
			writeFrameTo(stream, FrameResized, resizePayload(cols, rows))
		case FrameClaim:
			s.claimed <- struct{}{}
		case FrameClosed:
			return
		}
	}
}

func TestAttachStreamsFrames(t *testing.T) {
	s := startTestSSH(t)
	client := NewClient(nil)
	defer client.Close()
	if _, err := client.Connect(s.configuration(ssh.FingerprintSHA256(s.hostKey.PublicKey())), s.identity, ""); err != nil {
		t.Fatal(err)
	}
	terminal, err := client.Attach("s1", 80, 24)
	if err != nil {
		t.Fatal(err)
	}
	// The stream starts with the size the daemon took, so the client never lays
	// the grid out at a size the daemon did not use.
	frame, err := terminal.Next()
	if err != nil || frame.Kind != 's' {
		t.Fatalf("size frame: %+v %v", frame, err)
	}
	if cols, rows, ok := parseResize(frame.Data); !ok || cols != 80 || rows != 24 {
		t.Fatalf("size %d %d %v", cols, rows, ok)
	}
	frame, err = terminal.Next()
	if err != nil || frame.Kind != FrameOutput || string(frame.Data) != "snapshot" {
		t.Fatalf("first output frame: %+v %v", frame, err)
	}
	if err = terminal.Write([]byte("ls\r")); err != nil {
		t.Fatal(err)
	}
	frame, err = terminal.Next()
	if err != nil || string(frame.Data) != "echo:ls\r" {
		t.Fatalf("echo frame: %+v %v", frame, err)
	}
	if err = terminal.Resize(100, 30, 8, 16); err != nil {
		t.Fatal(err)
	}
	frame, err = terminal.Next()
	if err != nil || frame.Kind != FrameResized {
		t.Fatalf("resize frame: %+v %v", frame, err)
	}
	if cols, rows, ok := parseResize(frame.Data); !ok || cols != 100 || rows != 30 {
		t.Fatalf("resize %d %d %v", cols, rows, ok)
	}
	if err = terminal.Claim(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-s.claimed:
	case <-time.After(3 * time.Second):
		t.Fatal("no claim seen by the daemon")
	}
	if err = terminal.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err = terminal.Next(); err == nil {
		t.Fatal("read after close succeeded")
	}
}

type testObserver struct {
	events chan string
	closed chan string
}

func (o *testObserver) Event(message string)  { o.events <- message }
func (o *testObserver) Closed(message string) { o.closed <- message }

func TestUnknownAndChangedHostNeverAuthenticate(t *testing.T) {
	for _, pin := range []string{"", "SHA256:wrong"} {
		t.Run(pin, func(t *testing.T) {
			s := startTestSSH(t)
			client := NewClient(nil)
			defer client.Close()
			_, err := client.Connect(s.configuration(pin), s.identity, "")
			if err == nil {
				t.Fatal("untrusted host accepted")
			}
			var problem map[string]string
			if json.Unmarshal([]byte(err.Error()), &problem) != nil {
				t.Fatalf("missing trust error: %v", err)
			}
			if got := s.authCount.Load(); got != 0 {
				t.Fatalf("authenticated %d times before host approval", got)
			}
		})
	}
}

func TestSSHCompressedMuxControlRoundTrip(t *testing.T) {
	s := startTestSSH(t)
	observer := &testObserver{events: make(chan string, 10), closed: make(chan string, 10)}
	client := NewClient(observer)
	defer client.Close()
	_, err := client.Connect(s.configuration(ssh.FingerprintSHA256(s.hostKey.PublicKey())), s.identity, "")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := client.Call("subscribe", "{}"); err != nil {
		t.Fatal(err)
	}
	select {
	case event := <-observer.events:
		if !strings.Contains(event, `"event":"state"`) {
			t.Fatal(event)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("no subscribed state")
	}
	text, err := client.Call("read", `{"id":"pane"}`)
	if err != nil || !strings.Contains(text, "hello from an SSH pane") {
		t.Fatalf("%s %v", text, err)
	}
	if _, err := client.Call("send", `{"id":"pane","data":"hello\r"}`); err != nil {
		t.Fatal(err)
	}
	select {
	case text := <-s.input:
		if text != "hello\r" {
			t.Fatal(text)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("no input received")
	}
	client.Close()
	if _, err := client.Call("read", "{}"); err == nil {
		t.Fatal("request on closed connection succeeded")
	}
}
