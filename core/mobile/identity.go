package mobile

import (
	"encoding/json"
	"errors"

	"golang.org/x/crypto/ssh"
)

type identityInfo struct {
	Fingerprint        string `json:"fingerprint"`
	Algorithm          string `json:"algorithm"`
	RequiresPassphrase bool   `json:"requiresPassphrase"`
}

// InspectIdentity returns only public metadata. Encrypted PEM keys may not expose
// a public key until unlocked; importing them never requires saving a passphrase.
func InspectIdentity(privateKey string) (string, error) {
	if len(privateKey) == 0 || len(privateKey) > 128<<10 {
		return "", errors.New("private key must be smaller than 128 KiB")
	}
	bytes := []byte(privateKey)
	defer clear(bytes)
	signer, err := ssh.ParsePrivateKey(bytes)
	var info identityInfo
	if err == nil {
		info.Fingerprint = ssh.FingerprintSHA256(signer.PublicKey())
		info.Algorithm = signer.PublicKey().Type()
	} else {
		var encrypted *ssh.PassphraseMissingError
		if !errors.As(err, &encrypted) {
			return "", errors.New("unsupported private key: import an OpenSSH or PEM private key")
		}
		info.RequiresPassphrase = true
		if encrypted.PublicKey != nil {
			info.Fingerprint = ssh.FingerprintSHA256(encrypted.PublicKey)
			info.Algorithm = encrypted.PublicKey.Type()
		}
	}
	payload, err := json.Marshal(info)
	return string(payload), err
}
