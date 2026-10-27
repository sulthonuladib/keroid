// smoke checks this project's disposable localhost OpenSSH fixture. It lists
// counts only: it neither sends input to nor closes any Kero session.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"

	"golang.org/x/crypto/ssh"
	"keroid/core/mobile"
)

func main() {
	directory := flag.String("fixture", "../.tools/ssh-test", "disposable SSH fixture directory")
	flag.Parse()
	if err := check(*directory); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func check(directory string) error {
	privateKey, err := os.ReadFile(filepath.Join(directory, "client_ed25519"))
	if err != nil {
		return err
	}
	defer clear(privateKey)
	publicHostKey, err := os.ReadFile(filepath.Join(directory, "host_ed25519.pub"))
	if err != nil {
		return err
	}
	key, _, _, _, err := ssh.ParseAuthorizedKey(publicHostKey)
	if err != nil {
		return err
	}
	cfg, _ := json.Marshal(map[string]any{
		"host": "127.0.0.1", "port": 2222, "user": os.Getenv("USER"),
		"fingerprint": ssh.FingerprintSHA256(key),
	})
	client := mobile.NewClient(nil)
	defer client.Close()
	if _, err := client.Connect(string(cfg), string(privateKey), ""); err != nil {
		return err
	}
	response, err := client.Call("state", "{}")
	if err != nil {
		return err
	}
	var state struct {
		Projects []json.RawMessage
		Sessions []json.RawMessage
	}
	if err := json.Unmarshal([]byte(response), &state); err != nil {
		return err
	}
	fmt.Printf("Real OpenSSH → Kero bridge → compressed mux succeeded: %d projects, %d sessions.\n", len(state.Projects), len(state.Sessions))
	fmt.Println("No session input was sent; no session was closed.")
	return nil
}
