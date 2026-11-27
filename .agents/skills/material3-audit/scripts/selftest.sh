#!/usr/bin/env bash
# Self-test for audit.sh. Builds fixtures in a temp dir and asserts findings.
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
AUDIT="$HERE/audit.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/app/src/main/java/com/example/ui/theme"
mkdir -p "$TMP/app/src/main/java/com/example/feature"

cat >"$TMP/app/src/main/java/com/example/ui/theme/Theme.kt" <<'EOF'
package com.example.ui.theme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
val Scheme = lightColorScheme(primary = Color(0xFF6750A4))
EOF

cat >"$TMP/app/src/main/java/com/example/feature/Screen.kt" <<'EOF'
package com.example.feature
import androidx.compose.material.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
fun Screen() {
    Text("x", fontSize = 14.sp)
    Text("y", fontSize = 14.dp)
    val c = Color(0xFF000000)
    val d = Color(0xFF111111) // m3-audit-ignore
    val e = tween<Float>(300)
    val f = spring<Float>(dampingRatio = 0.5f)
    Scaffold { }
}
EOF

cat >"$TMP/app/src/main/java/com/example/feature/Row.kt" <<'EOF'
package com.example.feature
import androidx.compose.foundation.clickable
fun Row() {
    Box(Modifier.clickable { })
}
EOF

OUT=$("$AUDIT" --root "$TMP" || true)

fail=0
expect() {
  if ! printf '%s\n' "$OUT" | grep -q "$1"; then
    printf 'FAIL: expected finding %s\n' "$1" >&2
    fail=1
  fi
}
refute() {
  if printf '%s\n' "$OUT" | grep -q "$1"; then
    printf 'FAIL: unexpected finding %s\n' "$1" >&2
    fail=1
  fi
}

expect 'E001'
expect 'E002'
expect 'W101'
expect 'W102'
expect 'W104'
expect 'I202'
expect 'I207'
expect 'F301'
refute 'Theme.kt.*W102'     # theme code may define raw colors
refute 'Screen.kt:13:W102'  # suppressed with m3-audit-ignore

if [ "$fail" -ne 0 ]; then
  printf '\nAudit output:\n%s\n' "$OUT" >&2
  exit 1
fi

# Clean fixture exits 0.
mkdir -p "$TMP/clean"
cat >"$TMP/clean/Clean.kt" <<'EOF'
package com.example.clean
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
fun Clean() {
    Text("ok", style = MaterialTheme.typography.bodyLarge)
}
EOF
rm -rf "$TMP/app"
if ! "$AUDIT" --root "$TMP" >/dev/null 2>&1; then
  printf 'FAIL: clean fixture should exit 0\n' >&2
  exit 1
fi

printf 'audit self-test passed\n'
