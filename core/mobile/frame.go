package mobile

// Attach frames, mirrored from Kero's proto/frame.go (MIT, © EGOIST) so the
// wire format cannot drift by re-invention. Keep in step with
// repos/kero/internal/proto/frame.go at the pinned tag.

import (
	"bufio"
	"encoding/binary"
	"encoding/json"
	"errors"
	"io"
)

var errProtocolMismatch = errors.New("Kero protocol mismatch: update the client or the host")

// Frames of an attach connection: a kind, the payload's length as a big-endian
// uint32, then the payload.
const (
	// FrameOutput carries the session's terminal output (daemon → client).
	FrameOutput byte = 'o'
	// FrameInput carries what the user typed (client → daemon).
	FrameInput byte = 'i'
	// FrameResize carries cols, rows and a cell's pixels (client → daemon).
	FrameResize byte = 'r'
	// FrameClosed tells the client the session is gone (daemon → client).
	FrameClosed byte = 'c'
	// FrameResized tells the client the session's terminal took a size
	// (daemon → client), between frames of the old size and the new.
	FrameResized byte = 'z'
	// FrameClear asks the session's terminal to clear (client → daemon).
	FrameClear byte = 'k'
	// FrameTheme carries the terminal's colours (client → daemon).
	FrameTheme byte = 't'
	// FrameClaim tells the daemon the user is at this terminal.
	FrameClaim byte = 'm'
)

// Values of a FrameClear payload.
const (
	ClearScreen     byte = 's'
	ClearScrollback byte = 'b'
)

// maxFrame bounds a frame's payload, against a corrupt stream.
const maxFrame = 16 << 20

// maxOutput bounds one FrameOutput's payload; longer output is split across
// frames that the client reads as one stream.
const maxOutput = 1 << 20

var errFrameTooLarge = errors.New("attach frame too large")

// writeFrameTo writes one frame. Callers serialize writes to a connection.
func writeFrameTo(w io.Writer, kind byte, payload []byte) error {
	buf := make([]byte, 5+len(payload))
	buf[0] = kind
	binary.BigEndian.PutUint32(buf[1:5], uint32(len(payload)))
	copy(buf[5:], payload)
	_, err := w.Write(buf)
	return err
}

// appendFrames appends the frames that carry payload, splitting output past
// maxOutput into frames of its own.
func appendFrames(dst []byte, kind byte, payload []byte) []byte {
	for {
		n := len(payload)
		if kind == FrameOutput {
			n = min(n, maxOutput)
		}
		dst = append(dst, kind, byte(n>>24), byte(n>>16), byte(n>>8), byte(n))
		dst = append(dst, payload[:n]...)
		payload = payload[n:]
		if len(payload) == 0 {
			return dst
		}
	}
}

// readFrameFrom reads one frame.
func readFrameFrom(r io.Reader) (kind byte, payload []byte, err error) {
	var hdr [5]byte
	if _, err = io.ReadFull(r, hdr[:]); err != nil {
		return 0, nil, err
	}
	n := binary.BigEndian.Uint32(hdr[1:])
	if n > maxFrame {
		return 0, nil, errFrameTooLarge
	}
	payload = make([]byte, n)
	if _, err = io.ReadFull(r, payload); err != nil {
		return 0, nil, err
	}
	return hdr[0], payload, nil
}

// resizePayload encodes cols and rows for FrameResized.
func resizePayload(cols, rows int) []byte {
	var b [4]byte
	binary.BigEndian.PutUint16(b[0:], uint16(max(1, min(cols, 0xFFFF))))
	binary.BigEndian.PutUint16(b[2:], uint16(max(1, min(rows, 0xFFFF))))
	return b[:]
}

// parseResize decodes a FrameResized payload.
func parseResize(p []byte) (cols, rows int, ok bool) {
	if len(p) != 4 {
		return 0, 0, false
	}
	return int(binary.BigEndian.Uint16(p[0:])), int(binary.BigEndian.Uint16(p[2:])), true
}

// resizePixelsPayload encodes a size for FrameResize: a cell's pixels are 0
// when unknown.
func resizePixelsPayload(cols, rows, cellWidth, cellHeight int) []byte {
	b := make([]byte, 8)
	copy(b, resizePayload(cols, rows))
	binary.BigEndian.PutUint16(b[4:], uint16(max(0, min(cellWidth, 0xFFFF))))
	binary.BigEndian.PutUint16(b[6:], uint16(max(0, min(cellHeight, 0xFFFF))))
	return b
}

// parseResizePixels decodes a FrameResize payload.
func parseResizePixels(p []byte) (cols, rows, cellWidth, cellHeight int, ok bool) {
	if len(p) != 8 {
		return 0, 0, 0, 0, false
	}
	cols, rows, _ = parseResize(p[:4])
	return cols, rows, int(binary.BigEndian.Uint16(p[4:])), int(binary.BigEndian.Uint16(p[6:])), true
}

// colorTheme is the colours programs ask a terminal about (OSC 4, 10, 11, 12), as
// RGBA; a Cursor with A 0 is the foreground.
type colorTheme struct {
	Foreground, Background, Cursor [4]byte
	Palette                        [16][4]byte
}

// Payload encodes the theme for FrameTheme.
func (t colorTheme) payload() []byte {
	b := make([]byte, 0, 4*19)
	b = append(b, t.Foreground[:]...)
	b = append(b, t.Background[:]...)
	b = append(b, t.Cursor[:]...)
	for _, c := range t.Palette {
		b = append(b, c[:]...)
	}
	return b
}

// parseTheme decodes a FrameTheme payload.
func parseTheme(p []byte) (t colorTheme, ok bool) {
	if len(p) != 4*19 {
		return t, false
	}
	colors := append([]*[4]byte{&t.Foreground, &t.Background, &t.Cursor}, make([]*[4]byte, 16)...)
	for i := range t.Palette {
		colors[3+i] = &t.Palette[i]
	}
	for i, c := range colors {
		copy(c[:], p[4*i:])
	}
	return t, true
}

// attachWelcome is the answer to an attach Hello: its protocol version and the
// size the daemon gave the terminal.
type attachWelcome struct {
	version  int
	cols     int32
	rows     int32
}

// readAttachWelcome reads the JSON line that answers an attach Hello, skipping
// banners a login shell may print first.
func readAttachWelcome(r *bufio.Reader) (attachWelcome, error) {
	for range 100 {
		line, err := readLine(r, 64<<10)
		if err != nil {
			return attachWelcome{}, err
		}
		var w struct {
			Version int    `json:"version"`
			Cols    int32  `json:"cols"`
			Rows    int32  `json:"rows"`
			Error   string `json:"error"`
		}
		if json.Unmarshal(line, &w) != nil {
			continue
		}
		if w.Error != "" {
			return attachWelcome{}, errors.New(w.Error)
		}
		if w.Version != 0 && w.Version != protocolVersion {
			return attachWelcome{}, errProtocolMismatch
		}
		return attachWelcome{version: w.Version, cols: w.Cols, rows: w.Rows}, nil
	}
	return attachWelcome{}, errors.New("no attach welcome after shell banners")
}
