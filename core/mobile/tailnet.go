package mobile

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/coder/websocket"
)

// kerod's tailnet listeners, mirrored from the pinned desktop client
// (kero internal/remote): the WebSocket path, the header browsers cannot send,
// the default ports and the tailnet address space.
const (
	muxPath = "/kero/mux"
	// clientHeader marks a connection as Kero's. kerod refuses requests without
	// it, so no web page can reach a daemon through a browser on the tailnet.
	clientHeader = "Kero-Client"
	// clientName is what kerod logs and `kero clients` shows.
	clientName = "Keroid Android"
	// tailnetPort is kerod's tailnet listener default (http://), servePort is
	// Tailscale Serve's (https://).
	tailnetPort = "7373"
	servePort   = "443"
	// readLimit bounds one message, as desktop's clients do.
	readLimit = 64 << 20
)

// tailnets are the address ranges plain http:// may carry Kero over: Tailscale's
// CGNAT range and its IPv6 range, plus loopback for a tunnel of `ssh -L`.
var tailnets = []netip.Prefix{
	netip.MustParsePrefix("100.64.0.0/10"),
	netip.MustParsePrefix("fd7a:115c:a1e0::/48"),
}

// deniedError is kerod refusing this client (401/403): not a network failure.
// It carries a code so the app can say "refused" rather than "unreachable".
type deniedError struct{ message string }

func (e *deniedError) Error() string {
	payload, _ := json.Marshal(map[string]string{
		"code":    "tailnet_denied",
		"message": e.message,
	})
	return string(payload)
}

// tailnetEndpoint is where one tailnet listener is: plain http to kerod, or
// https behind Tailscale Serve.
type tailnetEndpoint struct {
	host  string
	port  string
	serve bool // https
}

func (e tailnetEndpoint) hostPort() string { return net.JoinHostPort(e.host, e.port) }

func (e tailnetEndpoint) String() string {
	scheme := "http"
	if e.serve {
		scheme = "https"
	}
	return scheme + "://" + e.hostPort()
}

// parseTailnetEndpoint reads what the host's app or `kero daemon status` shows:
// `http://devbox:7373`, `https://devbox.tail1234.ts.net`, or a bare host, which
// is plain http on the listener's default port. It accepts no user, password,
// path or query, as desktop's addresses take none.
func parseTailnetEndpoint(s string) (tailnetEndpoint, error) {
	raw := strings.TrimSpace(s)
	if raw == "" {
		return tailnetEndpoint{}, errors.New("enter the host's Tailscale address, such as http://devbox:7373")
	}
	if strings.ContainsFunc(raw, func(r rune) bool { return r <= ' ' || r == 0x7f }) {
		return tailnetEndpoint{}, fmt.Errorf("%q: a Tailscale address has no spaces", raw)
	}
	if !strings.Contains(raw, "://") {
		raw = "http://" + raw
	}
	u, err := url.Parse(raw)
	if err != nil {
		return tailnetEndpoint{}, fmt.Errorf("%q is not a Tailscale address", s)
	}
	e := tailnetEndpoint{host: u.Hostname(), port: u.Port()}
	switch strings.ToLower(u.Scheme) {
	case "http":
	case "https":
		e.serve = true
	default:
		return tailnetEndpoint{}, fmt.Errorf("%q: use http:// for kerod's tailnet listener or https:// for Tailscale Serve", s)
	}
	if u.User != nil {
		return tailnetEndpoint{}, fmt.Errorf("%q: a Tailscale address takes no user or password", s)
	}
	if u.Path != "" && u.Path != "/" {
		return tailnetEndpoint{}, fmt.Errorf("%q: a Tailscale address has no path", s)
	}
	if u.RawQuery != "" || u.Fragment != "" {
		return tailnetEndpoint{}, fmt.Errorf("%q: a Tailscale address has no query or fragment", s)
	}
	if e.host == "" {
		return tailnetEndpoint{}, fmt.Errorf("%q names no host", s)
	}
	if strings.HasPrefix(e.host, "-") || strings.ContainsAny(e.host, "/\\@") {
		return tailnetEndpoint{}, fmt.Errorf("%q: a host is a name of letters, digits, dots and dashes, or an IP address", s)
	}
	if e.port == "" {
		if e.serve {
			e.port = servePort
		} else {
			e.port = tailnetPort
		}
	}
	port, err := strconv.Atoi(e.port)
	if err != nil || port < 1 || port > 65535 {
		return tailnetEndpoint{}, fmt.Errorf("%q: bad port", s)
	}
	return e, nil
}

// dialTailnet opens one WebSocket to kerod's tailnet listener or to Tailscale
// Serve. kerod decides who may connect; this sends no identity of its own.
func dialTailnet(ctx context.Context, endpoint, who string) (io.ReadWriteCloser, error) {
	e, err := parseTailnetEndpoint(endpoint)
	if err != nil {
		return nil, err
	}
	scheme := "ws"
	if e.serve {
		scheme = "wss"
	}
	header := http.Header{}
	header.Set(clientHeader, who)
	opts := &websocket.DialOptions{
		HTTPHeader: header,
		// Kero compresses across the mux; the WebSocket's own must stay off.
		CompressionMode: websocket.CompressionDisabled,
	}
	if !e.serve {
		// Plain http is only for tailnet peers, never the open internet.
		opts.HTTPClient = &http.Client{Transport: &http.Transport{DialContext: dialTailnetPeer}}
	}
	ws, resp, err := websocket.Dial(ctx, scheme+"://"+e.hostPort()+muxPath, opts)
	if err != nil {
		if resp != nil {
			message := httpError(resp, err)
			if resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden {
				// kerod says why: the owner does not allow this identity, or the
				// request was not Kero's.
				return nil, &deniedError{message}
			}
			return nil, fmt.Errorf("%s answered %s: %s", e, resp.Status, message)
		}
		return nil, fmt.Errorf("cannot reach %s: %w", e, err)
	}
	ws.SetReadLimit(readLimit)
	return websocket.NetConn(context.Background(), ws, websocket.MessageBinary), nil
}

// dialTailnetPeer connects to a resolved tailnet address only, as desktop does:
// a hostname that resolves off the tailnet is refused before anything is sent.
func dialTailnetPeer(ctx context.Context, network, addr string) (net.Conn, error) {
	host, port, err := net.SplitHostPort(addr)
	if err != nil {
		return nil, err
	}
	ips, err := net.DefaultResolver.LookupNetIP(ctx, "ip", host)
	if err != nil {
		return nil, err
	}
	var peer netip.Addr
	for _, ip := range ips {
		if isTailnetAddr(ip) {
			peer = ip
			break
		}
	}
	if !peer.IsValid() {
		return nil, notOnTailnet(host)
	}
	var dialer net.Dialer
	conn, err := dialer.DialContext(ctx, network, net.JoinHostPort(peer.String(), port))
	if err != nil {
		return nil, err
	}
	// The address actually connected to is the one that matters.
	if ap, err := netip.ParseAddrPort(conn.RemoteAddr().String()); err != nil || !isTailnetAddr(ap.Addr()) {
		conn.Close()
		return nil, notOnTailnet(addr)
	}
	return conn, nil
}

func notOnTailnet(host string) error {
	return fmt.Errorf("%s is not on your tailnet: plain http:// reaches Tailscale addresses only, so use https:// or SSH for anything else", host)
}

// isTailnetAddr reports whether an address is one plain http may carry Kero
// over: a tailnet peer's, or this machine's, as through a tunnel of ssh -L.
func isTailnetAddr(ip netip.Addr) bool {
	ip = ip.Unmap()
	if ip.IsLoopback() {
		return true
	}
	for _, prefix := range tailnets {
		if prefix.Contains(ip) {
			return true
		}
	}
	return false
}

// httpError is kerod's own words, or the dial error if it said nothing.
func httpError(resp *http.Response, err error) string {
	if resp.Body != nil {
		text, readErr := io.ReadAll(io.LimitReader(resp.Body, 512))
		resp.Body.Close()
		if readErr == nil {
			if trimmed := strings.TrimSpace(string(text)); trimmed != "" {
				return trimmed
			}
		}
	}
	return err.Error()
}

// dialTimeout bounds a Tailscale connection attempt; kerod may be absent.
const dialTimeout = 20 * time.Second
