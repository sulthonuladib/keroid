#!/usr/bin/env bash
#
# Material 3 conformance audit for Jetpack Compose.
#
# Scans Kotlin sources for token bypasses, Material 2 leftovers, ad-hoc motion,
# and common accessibility traps. Dependency-free: bash + grep.
#
# Usage:
#   audit.sh [--root DIR] [--theme-dir SUBSTRING] [--strict] [--include-tests]
#            [--list-rules] [--help]
#
# Output (stdout), one finding per line:
#   path:line:SEVERITY:RULE:message
#
# Exit codes: 0 clean, 1 findings at the failure threshold, 2 usage error.

set -uo pipefail

ROOT="."
THEME_DIR="ui/theme"
STRICT=0
INCLUDE_TESTS=0

usage() {
  cat <<'EOF'
Material 3 conformance audit for Jetpack Compose.

Options:
  --root DIR          Directory to scan (default: .)
  --theme-dir SUBSTR  Path substring treated as theme code, where token
                      definitions are allowed (default: ui/theme). Pass "" to
                      scan theme code with all rules.
  --strict            Fail on warnings as well as errors (default: errors only)
  --include-tests     Include src/test and src/androidTest sources
  --list-rules        Print the rule catalog and exit
  --help              Show this help

Suppress a finding with a trailing comment on the same line:
  val c = Color(0xFF000000) // m3-audit-ignore
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --root)
      ROOT="${2:?--root needs a value}"
      shift 2
      ;;
    --theme-dir)
      THEME_DIR="${2-}"
      shift 2
      ;;
    --strict)
      STRICT=1
      shift
      ;;
    --include-tests)
      INCLUDE_TESTS=1
      shift
      ;;
    --list-rules)
      LIST_RULES=1
      shift
      ;;
    --help | -h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown option: %s\n\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ ! -d "$ROOT" ]; then
  printf 'Not a directory: %s\n' "$ROOT" >&2
  exit 2
fi

list_files() {
  if [ "$INCLUDE_TESTS" -eq 0 ]; then
    find "$ROOT" -type f -name '*.kt' \
      -not -path '*/build/*' \
      -not -path '*/.gradle/*' \
      -not -path '*/.idea/*' \
      -not -path '*/generated/*' \
      -not -path '*/src/test/*' \
      -not -path '*/src/androidTest/*' \
      -print0
  else
    find "$ROOT" -type f -name '*.kt' \
      -not -path '*/build/*' \
      -not -path '*/.gradle/*' \
      -not -path '*/.idea/*' \
      -not -path '*/generated/*' \
      -print0
  fi
}

ERRORS=0
WARNINGS=0
INFOS=0
TOTAL=0
FILES=0

print_rule_catalog() {
  cat <<'EOF'
Rule catalog (see references/checklist.md for fixes):

E001 ERROR  Material 2 import (androidx.compose.material.*)
E002 ERROR  Text size in dp instead of sp
W101 WARN   Inline fontSize in sp (bypasses the type scale)
W102 WARN   Hardcoded Color(...) in feature code
W103 WARN   Alpha applied to a theme color role
W104 WARN   Ad-hoc tween(...) instead of a motion scheme spec
W105 WARN   Linear or custom easing in UI motion
W106 WARN   Deprecated Divider
W107 WARN   Material 2 MaterialTheme.colors
I201 INFO   Legacy easing constant
I202 INFO   Inline spring(...) instead of the motion scheme
I203 INFO   Modifier.shadow(...) instead of elevation tokens
I204 INFO   Fixed size under 48dp (check the touch target)
I205 INFO   contentDescription = "" instead of null or a label
I206 INFO   Inline TextStyle(...) instead of a type role
I207 INFO   clickable without an explicit role
I208 INFO   Ripple configuration override
F301 INFO   Scaffold padding never consumed in the file
F302 INFO   Icon without any contentDescription in the file
EOF
}

if [ "${LIST_RULES:-0}" -eq 1 ]; then
  print_rule_catalog
  exit 0
fi

sanitize_path() {
  local p="$1"
  p="${p#./}"
  printf '%s' "$p"
}

in_theme_dir() {
  local f="$1"
  [ -n "$THEME_DIR" ] || return 1
  case "$f" in
    *"$THEME_DIR"*) return 0 ;;
    *) return 1 ;;
  esac
}

emit() {
  local file="$1" line="$2" sev="$3" rule="$4" msg="$5"
  printf '%s:%s:%s:%s:%s\n' "$(sanitize_path "$file")" "$line" "$sev" "$rule" "$msg"
  TOTAL=$((TOTAL + 1))
  case "$sev" in
    ERROR) ERRORS=$((ERRORS + 1)) ;;
    WARN) WARNINGS=$((WARNINGS + 1)) ;;
    *) INFOS=$((INFOS + 1)) ;;
  esac
}

# check RULE SEVERITY PATTERN MESSAGE [SKIP_THEME] [EXCLUDE_PATTERN]
check() {
  local rule="$1" sev="$2" pattern="$3" msg="$4"
  local skip_theme="${5:-1}" exclude="${6:-}"
  local f hit line content

  while IFS= read -r -d '' f; do
    if [ "$skip_theme" -eq 1 ] && in_theme_dir "$f"; then
      continue
    fi
    while IFS= read -r hit; do
      line="${hit%%:*}"
      content="${hit#*:}"
      case "$content" in
        *m3-audit-ignore*) continue ;;
      esac
      if [ -n "$exclude" ] && printf '%s' "$content" | grep -qE -- "$exclude"; then
        continue
      fi
      emit "$f" "$line" "$sev" "$rule" "$msg"
    done < <(grep -nE -- "$pattern" "$f" 2>/dev/null)
  done < <(list_files)
}

# file_check RULE SEVERITY TRIGGER_PATTERN REQUIRED_PATTERN MESSAGE
file_check() {
  local rule="$1" sev="$2" trigger="$3" required="$4" msg="$5"
  local f line

  while IFS= read -r -d '' f; do
    grep -qE -- "$trigger" "$f" 2>/dev/null || continue
    if ! grep -qE -- "$required" "$f" 2>/dev/null; then
      line=$(grep -nE -- "$trigger" "$f" 2>/dev/null | head -n 1 | cut -d: -f1)
      emit "$f" "${line:-1}" "$sev" "$rule" "$msg"
    fi
  done < <(list_files)
}

FILES=0
while IFS= read -r -d '' _; do
  FILES=$((FILES + 1))
done < <(list_files)

# --- Token and API rules ---------------------------------------------------

check E001 ERROR '^import androidx\.compose\.material\.' \
  "Material 2 import; migrate to androidx.compose.material3" 0 \
  'androidx\.compose\.material\.icons'

check E002 ERROR 'fontSize[[:space:]]*=[[:space:]]*[0-9.]+\.dp' \
  "Text size in dp does not scale; use a typography role (sp)" 1

check W101 WARN 'fontSize[[:space:]]*=[[:space:]]*[0-9.]+\.sp' \
  "Inline fontSize bypasses the type scale; use MaterialTheme.typography" 1

check W102 WARN 'Color\(0x|Color\.(Black|White|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b' \
  "Hardcoded color; use a MaterialTheme.colorScheme role" 1

check W103 WARN 'MaterialTheme\.colorScheme\.[A-Za-z]+\.copy\([[:space:]]*alpha' \
  "Alpha on a theme role fakes emphasis; use the matching on/container role" 1

check W104 WARN '\btween[[:space:]]*(<[^>]*>)?[[:space:]]*\(' \
  "Ad-hoc tween; use MaterialTheme.motionScheme.*Spec()" 1

check W105 WARN '\bLinearEasing\b|\bCubicBezierEasing\(' \
  "Custom easing; prefer a motion scheme spec unless this is a tween fallback" 1

check W106 WARN '^import androidx\.compose\.material3\.Divider$|\bDivider[[:space:]]*\(' \
  "Divider is deprecated; use HorizontalDivider or VerticalDivider" 0

check W107 WARN 'MaterialTheme\.colors\b' \
  "Material 2 color accessor; use MaterialTheme.colorScheme" 0

check I201 INFO '\b(FastOutSlowInEasing|LinearOutSlowInEasing|FastOutLinearInEasing)\b' \
  "Legacy easing constant; use the M3 easing tokens or a spring" 1

check I202 INFO '\bspring[[:space:]]*(<[^>]*>)?[[:space:]]*\(' \
  "Inline spring; prefer MaterialTheme.motionScheme unless defining the theme" 1

check I203 INFO '\.shadow[[:space:]]*\(' \
  "Modifier.shadow; use Surface tonal/shadow elevation or a container role" 1

check I204 INFO '\.size\([[:space:]]*([0-3]?[0-9]|4[0-7])\.dp[[:space:]]*\)' \
  "Fixed size under 48dp; confirm the interactive target is enlarged" 1

check I205 INFO 'contentDescription[[:space:]]*=[[:space:]]*""' \
  "Empty contentDescription; use null for decorative icons or a real label" 1

check I206 INFO 'style[[:space:]]*=[[:space:]]*TextStyle\(' \
  "Inline TextStyle; use a MaterialTheme.typography role" 1

check I207 INFO '\.clickable[[:space:]]*[({]' \
  "clickable without an explicit role; add role = Role.X for custom controls" 1 \
  '\brole[[:space:]]*='

check I208 INFO '\b(LocalRippleConfiguration|LocalRippleThemeConfiguration|RippleConfiguration)\b' \
  "Ripple override; confirm this is an intentional escape hatch" 1

# --- File-level heuristics -------------------------------------------------

file_check F301 INFO '\bScaffold[[:space:]]*[({]' \
  'innerPadding|paddingValues|contentPadding' \
  "Scaffold padding is never consumed; content may sit under bars or insets"

file_check F302 INFO '\bIcon[[:space:]]*\(' \
  'contentDescription' \
  "File uses Icon but never sets contentDescription; check icon-only controls"

# --- Summary ---------------------------------------------------------------

printf '\nScanned %s Kotlin files: %s errors, %s warnings, %s info\n' \
  "$FILES" "$ERRORS" "$WARNINGS" "$INFOS" >&2

if [ "$ERRORS" -gt 0 ]; then
  exit 1
fi
if [ "$STRICT" -eq 1 ] && [ "$WARNINGS" -gt 0 ]; then
  exit 1
fi
exit 0
