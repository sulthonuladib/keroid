#!/usr/bin/env bash
# Install-on-change loop for the emulator: the closest thing to hot reload that
# needs no IDE. Not Compose Hot Reload (JVM-only) and not Live Edit (Android
# Studio-only): Kotlin changes cost an incremental build plus a reinstall, and the
# activity restarts, so saved machines survive but in-memory UI state does not.
#
#   ./scripts/dev-loop.sh              # watch app/ and core/
#   ./scripts/dev-loop.sh --no-core    # Kotlin/UI only, skip the Go AAR rebuild
#
# Pure UI iteration is still fastest with previews:
#   android preview render :app --fqn com.sulthonuladib.keroid.ui.main.HomeScreenKt.HomePreview
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
app_id="com.sulthonuladib.keroid"
rebuild_core=1
[[ "${1:-}" == "--no-core" ]] && rebuild_core=0

command -v inotifywait >/dev/null || { echo "inotifywait is required (inotify-tools)" >&2; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "no device: start the emulator first" >&2; exit 1; }

# Build whatever changed, install, and bring the app back to the front.
rebuild() {
    local started=$SECONDS
    echo "==> $(date +%H:%M:%S) rebuilding"
    if (( rebuild_core )) && ! ./scripts/build-core.sh >/dev/null; then
        echo "!! Go build failed; keeping the app as it is" >&2
        return
    fi
    if ! ./gradlew :app:assembleDebug >/tmp/keroid-dev-loop.log 2>&1; then
        echo "!! Kotlin build failed; see /tmp/keroid-dev-loop.log" >&2
        grep -E '^e: ' /tmp/keroid-dev-loop.log | head -20 >&2 || true
        return
    fi
    adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
    adb shell am start -n "$app_id/.MainActivity" >/dev/null
    echo "==> installed in $((SECONDS - started))s"
}

rebuild
echo "Watching app/ and core/ for changes (Ctrl+C to stop)..."
while inotifywait -qq -r -e modify,create,delete,move \
    --exclude '(^|/)(build|\.gradle|\.tools|repos)/' \
    app/src core native scripts 2>/dev/null; do
    sleep 0.3   # let editors finish writing
    rebuild
done
