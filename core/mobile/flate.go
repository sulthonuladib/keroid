package mobile

import (
	"bufio"
	"compress/flate"
	"io"
	"sync"
)

// Compression covers the entire mux stream, flushed after every write.
type flateConn struct {
	raw      io.ReadWriteCloser
	reader   io.ReadCloser
	writer   *flate.Writer
	buffered *bufio.Writer
	mu       sync.Mutex
	rmu      sync.Mutex
}

func newFlateConn(raw io.ReadWriteCloser) *flateConn {
	buffered := bufio.NewWriterSize(raw, 64<<10)
	writer, _ := flate.NewWriter(buffered, flate.BestSpeed)
	return &flateConn{raw: raw, reader: flate.NewReader(raw), writer: writer, buffered: buffered}
}
func (c *flateConn) Read(p []byte) (int, error) {
	c.rmu.Lock()
	defer c.rmu.Unlock()
	return c.reader.Read(p)
}
func (c *flateConn) Write(p []byte) (int, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	n, err := c.writer.Write(p)
	if err == nil {
		err = c.writer.Flush()
	}
	if err == nil {
		err = c.buffered.Flush()
	}
	return n, err
}
func (c *flateConn) Close() error {
	// Close the transport first to unblock Read, then serialize decompressor
	// cleanup: flate.Reader.Close is not safe concurrently with Read.
	err := c.raw.Close()
	c.rmu.Lock()
	c.reader.Close()
	c.rmu.Unlock()
	return err
}
