package mobile

import (
	"bufio"
	"bytes"
	"net"
	"testing"
	"time"
)

func TestTerminalWelcomeOutputAndResizeStayOrdered(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	var wire []byte
	wire = appendFrames(wire, FrameOutput, []byte("old"))
	wire = appendFrames(wire, FrameResized, resizePayload(40, 12))
	wire = appendFrames(wire, FrameOutput, []byte("new"))
	terminal := &Terminal{conn: client, reader: bufio.NewReader(bytes.NewReader(wire)), cols: 120, rows: 40, sizePending: true}
	for i, want := range []struct {
		kind byte
		data []byte
	}{{'s', resizePayload(120, 40)}, {FrameOutput, []byte("old")}, {FrameResized, resizePayload(40, 12)}, {FrameOutput, []byte("new")}} {
		frame, err := terminal.Next()
		if err != nil || frame.Kind != want.kind || !bytes.Equal(frame.Data, want.data) {
			t.Fatalf("frame %d: got %+v, %v; want %q %v", i, frame, err, want.kind, want.data)
		}
		size := terminal.Size()
		wantCols := int32(120)
		if i >= 2 {
			wantCols = 40
		}
		if size.Cols != wantCols {
			t.Fatalf("frame %d: confirmed cols %d, want %d", i, size.Cols, wantCols)
		}
	}
}

func TestTerminalRequestDoesNotChangeConfirmedSize(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	server.SetDeadline(time.Now().Add(5 * time.Second))
	terminal := &Terminal{conn: client, cols: 120, rows: 40}
	done := make(chan error, 1)
	go func() {
		if err := terminal.Resize(40, 12, 9, 18); err != nil {
			done <- err
			return
		}
		done <- terminal.Claim()
	}()
	kind, payload, err := readFrameFrom(server)
	if err != nil || kind != FrameResize || !bytes.Equal(payload, resizePixelsPayload(40, 12, 9, 18)) {
		t.Fatalf("resize: %q %v %v", kind, payload, err)
	}
	kind, payload, err = readFrameFrom(server)
	if err != nil || kind != FrameClaim || len(payload) != 0 {
		t.Fatalf("claim: %q %v %v", kind, payload, err)
	}
	if err = <-done; err != nil {
		t.Fatal(err)
	}
	if size := terminal.Size(); size.Cols != 120 || size.Rows != 40 {
		t.Fatalf("request changed confirmed size: %+v", size)
	}
}

func TestTerminalRejectsInvalidConfirmedResize(t *testing.T) {
	for _, payload := range [][]byte{{1, 2, 3}, {0, 0, 0, 24}} {
		client, server := net.Pipe()
		terminal := &Terminal{conn: client, reader: bufio.NewReader(bytes.NewReader(appendFrames(nil, FrameResized, payload)))}
		_, err := terminal.Next()
		client.Close()
		server.Close()
		if err == nil {
			t.Fatalf("accepted resize %v", payload)
		}
	}
}
