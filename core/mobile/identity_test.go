package mobile

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"strings"
	"testing"

	"golang.org/x/crypto/ssh"
)

func TestInspectIdentity(t *testing.T) {
	_, key, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	public, err := ssh.NewPublicKey(key.Public())
	if err != nil {
		t.Fatal(err)
	}
	for _, encrypted := range []bool{false, true} {
		var block *pem.Block
		if encrypted {
			block, err = ssh.MarshalPrivateKeyWithPassphrase(key, "test", []byte("test-only-passphrase"))
		} else {
			block, err = ssh.MarshalPrivateKey(key, "test")
		}
		if err != nil {
			t.Fatal(err)
		}
		encoded := string(pem.EncodeToMemory(block))
		result, err := InspectIdentity(encoded)
		if err != nil {
			t.Fatal(err)
		}
		var info identityInfo
		if err := json.Unmarshal([]byte(result), &info); err != nil {
			t.Fatal(err)
		}
		if info.RequiresPassphrase != encrypted || info.Algorithm != public.Type() || info.Fingerprint != ssh.FingerprintSHA256(public) {
			t.Fatal("incorrect public metadata")
		}
		if strings.Contains(result, "PRIVATE KEY") || strings.Contains(result, "passphrase") || strings.Contains(result, encoded) {
			t.Fatal("inspection leaked secret material")
		}
	}
}

func TestInspectPEMIdentity(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	block := &pem.Block{Type: "RSA PRIVATE KEY", Bytes: x509.MarshalPKCS1PrivateKey(key)}
	result, err := InspectIdentity(string(pem.EncodeToMemory(block)))
	if err != nil {
		t.Fatal(err)
	}
	var info identityInfo
	if err := json.Unmarshal([]byte(result), &info); err != nil {
		t.Fatal(err)
	}
	if info.RequiresPassphrase || info.Algorithm != "ssh-rsa" || info.Fingerprint == "" {
		t.Fatal("incorrect PEM metadata")
	}
}

func TestInspectIdentityRejectsUnsupportedAndOversizeKeysWithoutEchoingInput(t *testing.T) {
	for _, key := range []string{"", "test-only-not-a-key", strings.Repeat("x", (128<<10)+1), "ssh-ed25519 public-keys-are-not-private-keys"} {
		result, err := InspectIdentity(key)
		if err == nil || result != "" {
			t.Fatal("invalid key accepted")
		}
		if key != "" && strings.Contains(err.Error(), key) {
			t.Fatal("error echoed input")
		}
	}
}
