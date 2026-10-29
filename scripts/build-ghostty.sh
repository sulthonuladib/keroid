#!/usr/bin/env bash
# Build libghostty-vt for Android and place it where Gradle packages it.
#
# The VT core is Zig. Both the source commit and the Zig version are pinned so a
# rebuild is reproducible; see docs/development.md. Output is gitignored:
# app/src/main/jniLibs/<abi>/libkeroid_term.so
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
commit="${GHOSTTY_COMMIT:-83fb95624b0439ee660c2679b43b38c8b63769cd}"
zig="${ZIG:-zig}"
src="$root/.tools/ghostty/src"
out="$root/.tools/ghostty"
jni="$root/app/src/main/jniLibs"

command -v "$zig" >/dev/null 2>&1 || {
    echo "zig not found: run inside the project with mise active (zig is pinned in .mise.toml)" >&2
    exit 1
}

mkdir -p "$out"
if [[ ! -d "$src/.git" ]]; then
    git clone https://github.com/ghostty-org/ghostty.git "$src"
fi
if [[ "$(git -C "$src" rev-parse HEAD)" != "$commit" ]]; then
    git -C "$src" fetch --depth=1 origin "$commit"
    git -C "$src" -c advice.detachedHead=false checkout --detach "$commit"
fi

# libghostty-vt is a plain C ABI with no platform UI. The shared library links
# only libc and libm on Android; see docs/architecture.md.
build() {
    local zigtarget="$1" abi="$2"
    echo "Building libghostty-vt for $abi (target $zigtarget)..."
    (cd "$src" && "$zig" build -Demit-lib-vt=true -Doptimize=ReleaseFast \
        -Dtarget="$zigtarget" -p "$out/$abi")
    ls -lh "$out/$abi/lib/libghostty-vt.a"
}

build aarch64-linux-android arm64-v8a
build x86_64-linux-android x86_64

# The JNI shim links the VT core statically. Zig gives the shared library a
# versioned SONAME (libghostty-vt.so.0), which the Android loader cannot resolve
# from an APK whose file must be named libghostty-vt.so; one self-contained
# library avoids that mismatch and a second packaged .so.
ndk_bin="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/29.0.14206865}/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -d "$ndk_bin" ]] || { echo "NDK toolchain not found at $ndk_bin" >&2; exit 1; }

shim() {
    local triple="$1" abi="$2"
    echo "Building the JNI shim for $abi..."
    mkdir -p "$jni/$abi"
    "$ndk_bin/${triple}${ANDROID_API:-24}-clang" -shared -O2 -fPIC \
        -I"$src/include" \
        -o "$jni/$abi/libkeroid_term.so" \
        "$root/native/ghostty_jni.c" \
        "$out/$abi/lib/libghostty-vt.a" \
        -lm
    "$ndk_bin/llvm-strip" --strip-unneeded "$jni/$abi/libkeroid_term.so"
    ls -lh "$jni/$abi/libkeroid_term.so"
}

shim aarch64-linux-android arm64-v8a
shim x86_64-linux-android x86_64

echo "Done. The APK packages app/src/main/jniLibs/, which is gitignored."
