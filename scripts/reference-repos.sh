#!/usr/bin/env bash
# Local, gitignored reference sources. Not Android build dependencies.
#
# Pinned to the released tag this app targets, not to main: what users install
# is the latest release, so the protocol we mirror should be the tagged one.
# Override with KERO_REF=... (a tag, or "main" for a deliberate newer look).
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
ref="${KERO_REF:-v2.1.24}"
dir="$root/repos/kero"
mkdir -p "$root/repos"

if [[ -d "$dir/.git" ]]; then
    if [[ -n "$(git -C "$dir" status --porcelain)" ]]; then
        echo "repos/kero has local changes; leaving it unchanged. Requested ref: $ref" >&2
        echo "current: $(git -C "$dir" describe --tags --always 2>/dev/null || echo unknown)" >&2
        exit 0
    fi
    current="$(git -C "$dir" describe --tags --exact-match 2>/dev/null || true)"
    if [[ "$current" == "$ref" ]]; then
        echo "Using existing Kero reference at $dir ($ref)."
        exit 0
    fi
    echo "Fetching Kero reference $ref into $dir (was: ${current:-$(git -C "$dir" describe --tags --always)})..."
    git -C "$dir" fetch --depth=1 origin "refs/tags/${ref}:refs/tags/${ref}"
    git -C "$dir" -c advice.detachedHead=false checkout --detach "$ref"
    echo "Now at $ref. Read-only reference: do not pull or reset it without asking."
elif [[ -e "$dir" ]]; then
    echo "repos/kero exists but is not a Git clone; refusing to overwrite it." >&2
    exit 1
else
    git clone --depth=1 --branch "$ref" https://github.com/egoist/kero.git "$dir"
    echo "Cloned $ref. Read-only reference: do not pull or reset it without asking."
fi
