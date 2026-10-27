package mobile

import (
	"bufio"
	"context"
	"encoding/json"
	"io"
	"net"
	"net/netip"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/coder/websocket"
	"github.com/hashicorp/yamux"
)

// testTailnet is kerod's tailnet listener, local and ephemeral: it checks the
// same header, speaks the same mux handshake and serves the same control and
// attach behaviour. It never reaches a real machine and runs no program.
type testTailnet struct {
	server *httptest.Server
	input  chan string
	claim  chan struct{}

	mu           sync.Mutex
	header       string // the Kero-Client value last seen
	origin       string // must always be empty
	hello        map[string]any
	muxVersion   int    // welcome's mux, 1 unless a test wants a mismatch
	welcomeError string // fatal error kerod would report
	refuse       string // non-empty: answer 403 with this text, before the upgrade
	closeAfter   bool   // hang up right after the upgrade
	accepted     []*websocket.Conn
}

func startTestTailnet(t *testing.T) *testTailnet {
	t.Helper()
	s := &testTailnet{input: make(chan string, 10), claim: make(chan struct{}, 4), muxVersion: 1}
	s.server = httptest.NewServer(http.HandlerFunc(s.handle))
	t.Cleanup(s.server.Close)
	return s
}

func (s *testTailnet) endpoint() string { return s.server.URL }

// hangup ends the connections this fixture took, as kerod stopping would.
func (s *testTailnet) hangup() {
	s.mu.Lock()
	conns := s.accepted
	s.accepted = nil
	s.mu.Unlock()
	for _, conn := range conns {
		conn.CloseNow()
	}
}

func (s *testTailnet) configuration(transport, endpoint string) string {
	b, _ := json.Marshal(config{Transport: transport, Endpoint: endpoint})
	return string(b)
}

func (s *testTailnet) handle(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != muxPath {
		http.NotFound(w, r)
		return
	}
	s.mu.Lock()
	s.header = r.Header.Get(clientHeader)
	s.origin = r.Header.Get("Origin")
	refuse := s.refuse
	s.mu.Unlock()
	// As kerod: no browser (it always sends an Origin and cannot send the header).
	if r.Header.Get("Origin") != "" || r.Header.Get(clientHeader) == "" {
		http.Error(w, "this is Kero's: connect with Kero", http.StatusForbidden)
		return
	}
	if refuse != "" {
		http.Error(w, refuse, http.StatusForbidden)
		return
	}
	ws, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		CompressionMode:    websocket.CompressionDisabled,
		InsecureSkipVerify: true,
	})
	if err != nil {
		return
	}
	s.mu.Lock()
	s.accepted = append(s.accepted, ws)
	s.mu.Unlock()
	if s.closeAfter {
		ws.Close(websocket.StatusInternalError, "test disconnect")
		return
	}
	ws.SetReadLimit(readLimit)
	// The WebSocket's net.Conn is the same byte stream the SSH bridge gives.
	s.serve(websocket.NetConn(context.Background(), ws, websocket.MessageBinary))
}

func (s *testTailnet) serve(conn io.ReadWriteCloser) {
	defer conn.Close()
	reader := bufio.NewReader(conn)
	line, err := readLine(reader, 64<<10)
	if err != nil {
		return
	}
	var hello map[string]any
	if json.Unmarshal(line, &hello) != nil || hello["kind"] != "mux" {
		return
	}
	s.mu.Lock()
	s.hello = hello
	version, welcomeError := s.muxVersion, s.welcomeError
	s.mu.Unlock()
	json.NewEncoder(conn).Encode(map[string]any{"mux": version, "compress": "deflate", "error": welcomeError,
		"host": map[string]any{"os": "linux", "version": protocolVersion, "home": "/home/test"}})
	if welcomeError != "" {
		return
	}
	compressed := newFlateConn(&bridge{Reader: reader, Writer: conn, close: conn.Close})
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
	encoder.Encode(map[string]any{"version": protocolVersion})
	for {
		line, err := readLine(cr, maxLine)
		if err != nil {
			return
		}
		var req struct {
			ID     int64
			Method string
			Params struct{ Data string }
		}
		if json.Unmarshal(line, &req) != nil {
			return
		}
		var result any = true
		switch req.Method {
		case "subscribe":
			encoder.Encode(map[string]any{"event": "state", "state": map[string]any{"projects": []any{}, "sessions": []any{}}})
		case "read":
			result = map[string]string{"text": "hello over the tailnet\n"}
		case "send":
			s.input <- req.Params.Data
		}
		if encoder.Encode(map[string]any{"id": req.ID, "result": result}) != nil {
			return
		}
	}
}

func (s *testTailnet) serveAttach(stream net.Conn) {
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
	}
	if json.Unmarshal(line, &hello) != nil || hello.Kind != "attach" || hello.Version != protocolVersion {
		return
	}
	json.NewEncoder(stream).Encode(map[string]any{"version": protocolVersion, "cols": 80, "rows": 24})
	writeFrameTo(stream, FrameOutput, []byte("tailnet snapshot"))
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
			s.claim <- struct{}{}
		case FrameClosed:
			return
		}
	}
}

func TestTailnetConnectsAndServesControlAndTerminal(t *testing.T) {
	s := startTestTailnet(t)
	observer := &testObserver{events: make(chan string, 8), closed: make(chan string, 4)}
	client := NewClient(observer)
	defer client.Close()
	host, err := client.Connect(s.configuration("tailscale", s.endpoint()), "", "")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(host, "/home/test") {
		t.Fatalf("host metadata: %s", host)
	}
	s.mu.Lock()
	if s.header != clientName || s.origin != "" {
		t.Fatalf("handshake header %q origin %q", s.header, s.origin)
	}
	if s.hello["kind"] != "mux" || s.hello["mux"] != float64(1) || s.hello["client"] != clientName {
		t.Fatalf("mux hello: %v", s.hello)
	}
	s.mu.Unlock()

	if _, err = client.Call("subscribe", "{}"); err != nil {
		t.Fatal(err)
	}
	select {
	case event := <-observer.events:
		if !strings.Contains(event, `"state"`) {
			t.Fatalf("state event: %s", event)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("no state event")
	}
	result, err := client.Call("read", `{"id":"p1"}`)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(result, "hello over the tailnet") {
		t.Fatalf("read result: %s", result)
	}

	terminal, err := client.Attach("s1", 80, 24)
	if err != nil {
		t.Fatal(err)
	}
	frame, err := terminal.Next()
	if err != nil || frame.Kind != 's' || string(frame.Data) != string(resizePayload(80, 24)) {
		t.Fatalf("size frame: %+v %v", frame, err)
	}
	frame, err = terminal.Next()
	if err != nil || frame.Kind != FrameOutput || string(frame.Data) != "tailnet snapshot" {
		t.Fatalf("output frame: %+v %v", frame, err)
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
	if err = terminal.Claim(); err != nil {
		t.Fatal(err)
	}
	select {
	case <-s.claim:
	case <-time.After(3 * time.Second):
		t.Fatal("no claim seen by kerod")
	}
	terminal.Close()
}

func TestTailnetRefusalIsAuthorizationNotReachability(t *testing.T) {
	s := startTestTailnet(t)
	s.refuse = "alice@example.com may not connect: the machine's owner lets them in with listen-allow"
	client := NewClient(nil)
	defer client.Close()
	_, err := client.Connect(s.configuration("tailscale", s.endpoint()), "", "")
	if err == nil {
		t.Fatal("refused connection succeeded")
	}
	var payload struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	}
	if json.Unmarshal([]byte(err.Error()), &payload) != nil {
		t.Fatalf("refusal is not structured: %q", err)
	}
	if payload.Code != "tailnet_denied" || !strings.Contains(payload.Message, "listen-allow") {
		t.Fatalf("refusal payload: %+v", payload)
	}
}

func TestTailnetProtocolMismatchAndWelcomeErrorsAreReported(t *testing.T) {
	t.Run("mux mismatch", func(t *testing.T) {
		s := startTestTailnet(t)
		s.muxVersion = 2
		client := NewClient(nil)
		defer client.Close()
		if _, err := client.Connect(s.configuration("tailscale", s.endpoint()), "", ""); err == nil ||
			!strings.Contains(err.Error(), "unsupported Kero mux version") {
			t.Fatalf("mismatch error: %v", err)
		}
	})
	t.Run("welcome error", func(t *testing.T) {
		s := startTestTailnet(t)
		s.welcomeError = "kerod is restarting"
		client := NewClient(nil)
		defer client.Close()
		if _, err := client.Connect(s.configuration("tailscale", s.endpoint()), "", ""); err == nil ||
			!strings.Contains(err.Error(), "kerod is restarting") {
			t.Fatalf("welcome error: %v", err)
		}
	})
	t.Run("gone after upgrade", func(t *testing.T) {
		s := startTestTailnet(t)
		s.closeAfter = true
		client := NewClient(nil)
		defer client.Close()
		if _, err := client.Connect(s.configuration("tailscale", s.endpoint()), "", ""); err == nil {
			t.Fatal("connection to a closed listener succeeded")
		}
	})
	t.Run("unreachable", func(t *testing.T) {
		s := startTestTailnet(t)
		endpoint := s.endpoint()
		s.server.Close()
		client := NewClient(nil)
		defer client.Close()
		if _, err := client.Connect(s.configuration("tailscale", endpoint), "", ""); err == nil {
			t.Fatal("connection to a stopped listener succeeded")
		}
	})
}

func TestTailnetDisconnectClosesTheClient(t *testing.T) {
	s := startTestTailnet(t)
	observer := &testObserver{events: make(chan string, 8), closed: make(chan string, 4)}
	client := NewClient(observer)
	defer client.Close()
	if _, err := client.Connect(s.configuration("tailscale", s.endpoint()), "", ""); err != nil {
		t.Fatal(err)
	}
	s.hangup()
	select {
	case message := <-observer.closed:
		if !strings.Contains(message, "closed") {
			t.Fatalf("closed message: %q", message)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("no closed notification after kerod went away")
	}
}

func TestPlainHTTPReachesTailnetAddressesOnly(t *testing.T) {
	// A public address resolves without any network access and is refused before
	// a single byte is sent: no downgrade, no fallback to SSH.
	client := NewClient(nil)
	defer client.Close()
	_, err := client.Connect(`{"transport":"tailscale","endpoint":"http://203.0.113.10:7373"}`, "", "")
	if err == nil || !strings.Contains(err.Error(), "not on your tailnet") {
		t.Fatalf("public http address: %v", err)
	}
	// The same shape over https is allowed to any host: Tailscale Serve holds
	// the certificate, and kerod still decides who gets in.
	if _, err := parseTailnetEndpoint("https://devbox.tail1234.ts.net"); err != nil {
		t.Fatalf("serve endpoint: %v", err)
	}
}

func TestTailnetEndpointParsing(t *testing.T) {
	tests := []struct {
		in    string
		host  string
		port  string
		serve bool
		bad   bool
	}{
		{in: "http://devbox:7373", host: "devbox", port: "7373"},
		{in: "http://devbox", host: "devbox", port: "7373"},
		{in: "devbox", host: "devbox", port: "7373"},
		{in: "devbox:8080", host: "devbox", port: "8080"},
		{in: "https://devbox.tail1234.ts.net", host: "devbox.tail1234.ts.net", port: "443", serve: true},
		{in: "https://devbox.tail1234.ts.net:8443", host: "devbox.tail1234.ts.net", port: "8443", serve: true},
		{in: "100.101.102.103", host: "100.101.102.103", port: "7373"},
		{in: "[fd7a:115c:a1e0::1]", host: "fd7a:115c:a1e0::1", port: "7373"},
		{in: "http://devbox:7373/", host: "devbox", port: "7373"},
		{in: "", bad: true},
		{in: "   ", bad: true},
		{in: "ftp://devbox", bad: true},
		{in: "ssh://devbox", bad: true},
		{in: "http://user:pw@devbox:7373", bad: true},
		{in: "http://devbox:7373/path", bad: true},
		{in: "http://devbox:7373?x=1", bad: true},
		{in: "http://devbox:7373#f", bad: true},
		{in: "http://devbox:0", bad: true},
		{in: "http://devbox:70000", bad: true},
		{in: "http://devbox:abc", bad: true},
		{in: "http://dev box:7373", bad: true},
		{in: "http://-devbox:7373", bad: true},
		{in: "http://", bad: true},
	}
	for _, tc := range tests {
		got, err := parseTailnetEndpoint(tc.in)
		if tc.bad {
			if err == nil {
				t.Errorf("%q parsed as %+v, want an error", tc.in, got)
			}
			continue
		}
		if err != nil {
			t.Errorf("%q: %v", tc.in, err)
			continue
		}
		if got.host != tc.host || got.port != tc.port || got.serve != tc.serve {
			t.Errorf("%q parsed as %+v, want %s:%s serve=%v", tc.in, got, tc.host, tc.port, tc.serve)
		}
	}
}

func TestTailnetAddressSpace(t *testing.T) {
	tests := map[string]bool{
		"100.64.0.1":        true,
		"100.127.255.254":   true,
		"100.63.255.255":    false,
		"100.128.0.1":       false,
		"127.0.0.1":         true,
		"::1":               true,
		"fd7a:115c:a1e0::1": true,
		"fd7a:115c:a1b0::1": false,
		"2001:db8::1":       false,
		"8.8.8.8":           false,
	}
	for ip, want := range tests {
		parsed := net.ParseIP(ip)
		if parsed == nil {
			t.Fatalf("%q is not an IP", ip)
		}
		addr, ok := netip.AddrFromSlice(parsed)
		if !ok {
			t.Fatalf("%q is not an IP address", ip)
		}
		if got := isTailnetAddr(addr); got != want {
			t.Errorf("%s: tailnet %v, want %v", ip, got, want)
		}
	}
}

func TestDeniedErrorNamesTheCodeForTheApp(t *testing.T) {
	err := &deniedError{message: "not allowed"}
	var payload struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	}
	if json.Unmarshal([]byte(err.Error()), &payload) != nil {
		t.Fatalf("not JSON: %s", err)
	}
	if payload.Code != "tailnet_denied" || payload.Message != "not allowed" {
		t.Fatalf("payload: %+v", payload)
	}
}
