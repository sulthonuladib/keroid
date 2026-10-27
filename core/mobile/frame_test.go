package mobile

import (
	"bytes"
	"errors"
	"io"
	"testing"
)

func TestFrameRoundTrip(t *testing.T) {
	var buf bytes.Buffer
	if err := writeFrameTo(&buf, FrameOutput, []byte("hello")); err != nil {
		t.Fatal(err)
	}
	kind, payload, err := readFrameFrom(&buf)
	if err != nil {
		t.Fatal(err)
	}
	if kind != FrameOutput || string(payload) != "hello" {
		t.Fatalf("got %q %q", kind, payload)
	}
}

func TestFrameRejectsOversizePayload(t *testing.T) {
	// A header claiming more than maxFrame must fail before allocating it.
	hdr := []byte{FrameOutput, 0x7f, 0xff, 0xff, 0xff}
	_, _, err := readFrameFrom(bytes.NewReader(hdr))
	if err == nil {
		t.Fatal("expected an error for an oversized frame")
	}
	// A truncated payload is an error, never a short read passed on as data.
	truncated := []byte{FrameInput, 0, 0, 0, 4, 'a', 'b'}
	if _, _, err = readFrameFrom(bytes.NewReader(truncated)); !errors.Is(err, io.ErrUnexpectedEOF) {
		t.Fatalf("got %v, want unexpected EOF", err)
	}
}

func TestAppendFramesSplitsLongOutput(t *testing.T) {
	payload := bytes.Repeat([]byte("x"), maxOutput+10)
	frames := appendFrames(nil, FrameOutput, payload)
	r := bytes.NewReader(frames)
	var total []byte
	for range 2 {
		kind, data, err := readFrameFrom(r)
		if err != nil {
			t.Fatal(err)
		}
		if kind != FrameOutput {
			t.Fatalf("kind %q", kind)
		}
		total = append(total, data...)
	}
	if len(total) != len(payload) {
		t.Fatalf("read %d bytes, want %d", len(total), len(payload))
	}
	if r.Len() != 0 {
		t.Fatalf("%d bytes left over", r.Len())
	}
}

func TestResizePayloads(t *testing.T) {
	cols, rows, cw, ch, ok := parseResizePixels(resizePixelsPayload(120, 40, 9, 18))
	if !ok || cols != 120 || rows != 40 || cw != 9 || ch != 18 {
		t.Fatalf("got %d %d %d %d %v", cols, rows, cw, ch, ok)
	}
	// Sizes are clamped to uint16 and at least one cell.
	cols, rows, ok = parseResize(resizePayload(0, 70000))
	if !ok || cols != 1 || rows != 0xFFFF {
		t.Fatalf("got %d %d %v", cols, rows, ok)
	}
	if _, _, ok = parseResize([]byte{1, 2, 3}); ok {
		t.Fatal("accepted a short resize payload")
	}
}

func TestThemePayloadRoundTrip(t *testing.T) {
	theme := colorTheme{Foreground: [4]byte{1, 2, 3, 4}, Background: [4]byte{5, 6, 7, 8}, Cursor: [4]byte{9, 10, 11, 12}}
	for i := range theme.Palette {
		theme.Palette[i] = [4]byte{byte(i), 0, 0, 255}
	}
	got, ok := parseTheme(theme.payload())
	if !ok || got != theme {
		t.Fatalf("theme did not round-trip: %+v", got)
	}
	if _, ok = parseTheme([]byte{1, 2, 3}); ok {
		t.Fatal("accepted a short theme payload")
	}
}
