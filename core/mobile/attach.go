package mobile

// Attach streams: a terminal's output and input for one session, over their
// own mux stream, in the frames of frame.go. Protocol v3, mux v1.

import (
	"bufio"
	"encoding/json"
	"errors"
	"net"
	"sync"
	"time"
)

// Frame is one attach frame as it crosses to Android: a kind and its payload.
// Kinds are the Frame* constants; callers compare against them.
type Frame struct {
	Kind byte
	Data []byte
}

// Terminal is one attached pane. Create it with Client.Attach; Close it when
// leaving the pane, which detaches this client only.
type Terminal struct {
	mu     sync.Mutex
	conn   net.Conn
	reader *bufio.Reader
	closed bool
	cols   int32
	rows   int32
	// sizePending makes the first Next report the size the daemon took, in the
	// shape of a resize frame, before its own frames.
	sizePending bool
}

// Size is a terminal's size in cells, as the daemon reported it.
type Size struct {
	Cols int32
	Rows int32
}

// Size returns the last confirmed size read from this attachment. Resize only
// requests a size; it does not change this until Next reads FrameResized.
func (t *Terminal) Size() *Size {
	t.mu.Lock()
	defer t.mu.Unlock()
	return &Size{Cols: t.cols, Rows: t.rows}
}

// Attach opens the attach stream for a pane and waits for its welcome. Kerod
// calls a pane's id the session of an attach stream. cols and rows are the size
// asked for; the daemon sends the current screen as the first FrameOutput and
// reports the size it took, which Size returns.
func (c *Client) Attach(pane string, cols, rows int32) (*Terminal, error) {
	c.mu.Lock()
	if c.closed || c.mux == nil {
		c.mu.Unlock()
		return nil, errors.New("not connected")
	}
	mux, closed := c.mux, c.closed
	c.mu.Unlock()
	if closed {
		return nil, errors.New("not connected")
	}
	stream, err := mux.Open()
	if err != nil {
		return nil, err
	}
	stream.SetDeadline(time.Now().Add(15 * time.Second))
	hello := map[string]any{
		"kind":    "attach",
		"version": protocolVersion,
		"session": pane,
		"cols":    int(cols),
		"rows":    int(rows),
	}
	if err = json.NewEncoder(stream).Encode(hello); err != nil {
		stream.Close()
		return nil, err
	}
	reader := bufio.NewReaderSize(stream, 64<<10)
	welcome, err := readAttachWelcome(reader)
	if err != nil {
		stream.Close()
		return nil, err
	}
	stream.SetDeadline(time.Time{})
	return &Terminal{
		conn: stream, reader: reader,
		cols: welcome.cols, rows: welcome.rows, sizePending: true,
	}, nil
}

// Next reads one frame, blocking until it arrives. It returns an error once the
// stream is closed, by the daemon or by Close.
func (t *Terminal) Next() (*Frame, error) {
	t.mu.Lock()
	if t.closed || t.conn == nil {
		t.mu.Unlock()
		return nil, errors.New("terminal closed")
	}
	if t.sizePending {
		t.sizePending = false
		size := resizePayload(int(t.cols), int(t.rows))
		t.mu.Unlock()
		// Kind 's': the size the daemon took, before its frames.
		return &Frame{Kind: 's', Data: size}, nil
	}
	reader := t.reader
	t.mu.Unlock()
	for {
		kind, payload, err := readFrameFrom(reader)
		if err != nil {
			return nil, err
		}
		if kind == FrameResized {
			cols, rows, ok := parseResize(payload)
			if !ok || cols <= 0 || rows <= 0 {
				return nil, errors.New("invalid terminal resize frame")
			}
			t.mu.Lock()
			t.cols, t.rows = int32(cols), int32(rows)
			t.mu.Unlock()
		}
		// Output past one frame's bound arrives as several frames, which the
		// caller reads as one stream of bytes. Hand each on as it comes.
		if len(payload) > 0 {
			return &Frame{Kind: kind, Data: payload}, nil
		}
		if kind != FrameOutput {
			return &Frame{Kind: kind}, nil
		}
	}
}

// Write sends what the user typed (UTF-8 bytes, escape sequences included).
func (t *Terminal) Write(data []byte) error {
	if len(data) == 0 {
		return nil
	}
	return t.writeFrame(FrameInput, data)
}

// Resize tells the daemon the terminal's size; cellWidth and cellHeight are the
// cell's pixels, or 0 when unknown.
func (t *Terminal) Resize(cols, rows, cellWidth, cellHeight int32) error {
	return t.writeFrame(FrameResize, resizePixelsPayload(int(cols), int(rows), int(cellWidth), int(cellHeight)))
}

// Claim tells the daemon the user is at this terminal: it takes the size this
// client asked for last.
func (t *Terminal) Claim() error { return t.writeFrame(FrameClaim, nil) }

// Clear asks the session's terminal to clear (ClearScreen, ClearScrollback).
func (t *Terminal) Clear(what byte) error { return t.writeFrame(FrameClear, []byte{what}) }

func (t *Terminal) writeFrame(kind byte, payload []byte) error {
	t.mu.Lock()
	defer t.mu.Unlock()
	if t.closed || t.conn == nil {
		return errors.New("terminal closed")
	}
	buf := appendFrames(nil, kind, payload)
	t.conn.SetWriteDeadline(time.Now().Add(15 * time.Second))
	_, err := t.conn.Write(buf)
	t.conn.SetWriteDeadline(time.Time{})
	return err
}

// Close detaches this client's stream. The session keeps running.
func (t *Terminal) Close() error {
	t.mu.Lock()
	if t.closed {
		t.mu.Unlock()
		return nil
	}
	t.closed = true
	conn := t.conn
	t.conn = nil
	t.reader = nil
	t.mu.Unlock()
	if conn != nil {
		return conn.Close()
	}
	return nil
}
