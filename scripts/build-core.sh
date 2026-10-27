#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../core"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/29.0.14206865}"
mkdir -p ../app/libs ../.tools/bin
# Both tool versions are pinned by core/go.mod; no global gomobile is required.
go build -o ../.tools/bin/gobind golang.org/x/mobile/cmd/gobind
export PATH="$(cd ../.tools/bin && pwd):$PATH"
go tool gomobile bind -target=android/arm64,android/amd64 -androidapi=24 -javapkg=com.sulthonuladib.keroid -o ../app/libs/keroid-core.aar ./mobile
