#!/usr/bin/env bash
# Disposable, unprivileged, localhost-only SSH fixture for emulator testing.
# Never reads ~/.ssh, edits authorized_keys, or changes system SSH services.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
state="$root/.tools/ssh-test"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
port=2222

stop_server() {
    if [[ -f "$state/sshd.pid" ]]; then
        local pid args
        read -r pid < "$state/sshd.pid"
        if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
            args="$(ps -p "$pid" -o args=)"
            if [[ "$args" == *sshd* && "$args" == *"$state/sshd_config"* ]]; then
                kill "$pid"
                echo "Stopped Keroid's isolated SSH listener."
            else
                echo "PID file no longer identifies this fixture; refusing to stop another process." >&2
                exit 1
            fi
        fi
        rm -f "$state/sshd.pid"
    fi
}

case "${1:-start}" in
    stop)
        stop_server
        exit 0
        ;;
    clean)
        stop_server
        rm -rf -- "$state"
        # Delete ONLY this fixture's exported key, not any other device files.
        if [[ -x "$sdk/platform-tools/adb" ]]; then
            "$sdk/platform-tools/adb" -s "${ANDROID_SERIAL:-emulator-5554}" shell rm -f /sdcard/Download/keroid-test-key 2>/dev/null || true
        fi
        echo "Removed disposable SSH fixture and exported emulator key."
        exit 0
        ;;
    start) ;;
    *) echo "Usage: $0 [start|stop|clean]" >&2; exit 2 ;;
esac

umask 077
mkdir -p "$state"
chmod 700 "$state"

if [[ ! -f "$state/client_ed25519" ]]; then
    ssh-keygen -q -t ed25519 -N '' -C 'keroid-disposable-local-test' -f "$state/client_ed25519"
fi
if [[ ! -f "$state/host_ed25519" ]]; then
    ssh-keygen -q -t ed25519 -N '' -C 'keroid-disposable-test-host' -f "$state/host_ed25519"
fi
# Only the new test key is authorized, in the isolated fixture's own file.
printf 'restrict ' > "$state/authorized_keys"
head -n 1 "$state/client_ed25519.pub" >> "$state/authorized_keys"
user="$(id -un)"
cat > "$state/sshd_config" <<EOF
Port $port
ListenAddress 127.0.0.1
HostKey "$state/host_ed25519"
PidFile "$state/sshd.pid"
AuthorizedKeysFile "$state/authorized_keys"
AllowUsers $user
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
UsePAM no
PermitRootLogin no
PermitTTY no
DisableForwarding yes
PermitUserEnvironment no
StrictModes yes
LogLevel ERROR
EOF
sshd="$(command -v sshd)"
"$sshd" -t -f "$state/sshd_config"
if [[ -f "$state/sshd.pid" ]] && kill -0 "$(< "$state/sshd.pid")" 2>/dev/null; then
    echo "Test SSH listener is already running."
else
    # OpenSSH daemonizes after startup. No root privileges or systemd unit needed.
    "$sshd" -f "$state/sshd_config" -E "$state/server.log"
fi

# Trust the test host's locally generated PUBLIC key, not a network scan.
{
    printf '[127.0.0.1]:%s ' "$port"
    head -n 1 "$state/host_ed25519.pub"
} > "$state/known_hosts"
ssh -F /dev/null -T -i "$state/client_ed25519" -p "$port" \
    -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=5 \
    -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$state/known_hosts" \
    "$user@127.0.0.1" true

# Copy only the disposable key to the emulator. No actual user key is accessed.
if [[ -x "$sdk/platform-tools/adb" ]]; then
    "$sdk/platform-tools/adb" -s "${ANDROID_SERIAL:-emulator-5554}" push \
        "$state/client_ed25519" /sdcard/Download/keroid-test-key
fi

printf '\nEmulator address: %s@10.0.2.2\nPort: %s\nKey: Downloads/keroid-test-key\nPassphrase: leave empty (disposable test key only)\n\nHost fingerprint:\n' "$user" "$port"
ssh-keygen -lf "$state/host_ed25519.pub"
echo
echo "Stop listener: ./scripts/test-ssh.sh stop"
echo "Remove fixture and exported key: ./scripts/test-ssh.sh clean"
