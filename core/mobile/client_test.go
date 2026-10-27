package mobile

import (
	"bufio"
	"encoding/json"
	"strings"
	"testing"
)

func TestHostKeyTrust(t *testing.T) {
	for _, tc := range []struct{ expected, actual, code string }{
		{"", "SHA256:new", "host_key_unknown"},
		{"SHA256:old", "SHA256:new", "host_key_changed"},
		{"SHA256:same", "SHA256:same", ""},
	} {
		err := checkHostKey(tc.expected, tc.actual, "ssh-ed25519")
		if tc.code == "" {
			if err != nil {
				t.Fatal(err)
			}
			continue
		}
		if err == nil {
			t.Fatal("untrusted server accepted")
		}
		var result map[string]string
		if err := json.Unmarshal([]byte(err.Error()), &result); err != nil {
			t.Fatal(err)
		}
		if result["code"] != tc.code || result["fingerprint"] != tc.actual {
			t.Fatal(result)
		}
	}
}
func TestReadLineBound(t *testing.T) {
	if _, err := readLine(bufio.NewReader(strings.NewReader("12345\n")), 5); err == nil {
		t.Fatal("oversized line accepted")
	}
	line, err := readLine(bufio.NewReader(strings.NewReader("1234\n")), 5)
	if err != nil || string(line) != "1234\n" {
		t.Fatalf("%q %v", line, err)
	}
}
func TestWelcomeSkipsBanner(t *testing.T) {
	w, err := readWelcome(bufio.NewReader(strings.NewReader("Welcome to the server\n{\"mux\":1,\"host\":{\"os\":\"linux\"}}\n")))
	if err != nil || w.Mux != 1 {
		t.Fatalf("%+v %v", w, err)
	}
}
func TestExecutableIsQuoted(t *testing.T) {
	got := bridgeCommand("/path with spaces/kero'; echo unsafe")
	if got != "sh -c "+quote("exec "+quote("/path with spaces/kero'; echo unsafe")+" __bridge --via ssh") {
		t.Fatal(got)
	}
}
func TestCloseIsIdempotent(t *testing.T) {
	c := NewClient(nil)
	c.Close()
	c.Close()
	if _, err := c.Call("state", ""); err == nil {
		t.Fatal("closed client accepted request")
	}
}
