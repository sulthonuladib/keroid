# Development

## Setup and build

Tool versions and SDK paths live in `.mise.toml`; Gradle uses the wrapper.
Install the Android SDK platform matching `compileSdk` in
`app/build.gradle.kts`, NDK at `ANDROID_NDK_HOME`, platform-tools and an
x86_64 emulator image. Adjust the SDK paths for your environment.

```sh
mise trust && mise install
./scripts/build-core.sh
./scripts/build-ghostty.sh
./gradlew :app:assembleDebug
```

| Changed | Rebuild before Gradle |
|---|---|
| `core/mobile/` | `./scripts/build-core.sh` → `app/libs/keroid-core.aar` |
| `native/` | `./scripts/build-ghostty.sh` → `app/src/main/jniLibs/` |
| Kotlin, resources | Nothing extra |

Both generated libraries are gitignored and package arm64 + x86_64.
The native script pins Ghostty's source, uses Zig from mise and links the VT
core statically into the JNI library. First build needs network access.
`.tools/` contains disposable toolchains and fixtures, not source dependencies.

For protocol reference, `./scripts/reference-repos.sh` creates `repos/kero`
at the pinned release. Check an existing clone before running it: the script
may move its revision. The clone is not an Android build dependency.

### libghostty-vt pin

The native terminal library tracks Ghostty **v1.4.0**
(`83fb95624b0439ee660c2679b43b38c8b63769cd`) and is built with Zig 0.17.0.
It is independent of the Kero reference pin. libghostty-vt's C API is still
pre-1.0, so rebuild the native library and rerun the terminal Android tests
when moving the pin.

### Kero reference compatibility

The current reference is **v2.1.24** (`53ee0e29562bb68c85d4d7434c5f91e8d2a3de0d`).
Compared with v2.1.21, all of `internal/` is unchanged, including protocol v3,
mux v1, SSH/WebSocket transports, control RPCs and attach frames. No mobile
runtime or dependency changes are required for this update.

The intervening releases change desktop Split Right with no session and update
MyGo for image-clearing, relaunch/quit and macOS window/Dock fixes. Keroid has a
separate New session action, exposes splits within a session, does not use MyGo
and does not render Kitty images. Keep the independent libghostty-vt pin and
text-snapshot fallback. Font assets are unchanged; their bundled provenance
continues to identify the v2.1.21 source from which they were copied.

## Verification

Run after code changes, rebuilding the affected libraries first:

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest \
          :app:lintDebug :app:lintRelease :app:connectedDebugAndroidTest
(cd core && go test -race ./...)
```

Connected tests require a running emulator. Run the Go suite independently so
an Android failure does not skip it. Lint must have zero errors; explain new
warnings rather than suppressing them (existing baseline: 12 per variant).

Tests and previews use fakes or local fixtures, never real hosts, credentials or
active user sessions. Coverage belongs beside the implementation:

- `core/mobile/`: SSH/host trust, bounded protocol parsing and local SSH/WS mux
  round-trips, Tailscale refusal/address filtering and dropped connections.
- `app/src/test/`: repository isolation, credential lifetime, migration,
  terminal output/resize ordering, sizing/claim lifecycle and viewport bounds;
  session-action payloads, single-flight mutations and late-response rejection.
- `app/src/androidTest/`: Keystore, Compose state restoration, terminal input,
  native rendering/themes/fonts, dock/IME geometry and selection/scrollback;
  rename drafts, close confirmation/cancel, failure feedback and close navigation.

Compose tests use the v2 `createComposeRule` with queued coroutine execution;
synchronize with `waitForIdle`/`runOnIdle`. For geometry tests, wait for settled
canvas bounds rather than assuming a docked keyboard or that `hide()` succeeded.
A floating IME can be visible with zero bottom inset. Use
`TerminalProbeScreen` for offline terminal checks.

Emulator app data is disposable; connected tests may uninstall/reset it.
After successful Android verification, reinstall and leave the app running.
Choose the emulator serial explicitly so a connected phone is never targeted:

```sh
adb devices
adb -s EMULATOR_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s EMULATOR_SERIAL shell am start -n com.sulthonuladib.keroid/.MainActivity
```

Tailscale connectivity has also been verified manually from a physical phone
connected to the development host; refusal and address filtering remain covered
by local automated fixtures.

## UI iteration

Previews need neither a device nor a connection:

```sh
android preview list :app
android preview render :app --fqn com.sulthonuladib.keroid.ui.main.HomeScreenKt.HomePreview -o .tools/previews-out
```

For install-on-change, `scripts/dev-loop.sh --no-core` watches UI edits
(requires `inotifywait`); omit `--no-core` to rebuild Go too. It does not rebuild
the native library. Set `ANDROID_SERIAL` to the emulator first, since the loop
uses adb's default target. Each install restarts the activity; this is not hot reload.

## Local SSH fixture

With OpenSSH installed and an emulator running:

```sh
ANDROID_SERIAL=EMULATOR_SERIAL ./scripts/test-ssh.sh start
```

The script prints the address, port and fingerprint, and exports the disposable
key to emulator Downloads as `keroid-test-key`. Import it in the app.
It uses an unprivileged localhost listener and separate keys under
`.tools/ssh-test/`; it does not read your SSH identity or change system sshd.

The emulator reaches host loopback at `10.0.2.2`. If needed, use
`adb -s EMULATOR_SERIAL reverse tcp:2222 tcp:2222` and connect to
`127.0.0.1:2222`. Kero must be installed on the host; set the machine's
**Kero executable** if it is not discoverable.

This fixture has your account's powers, not a sandbox. Never authorize its key
on another machine. Disconnect in the app, then remove it with
`ANDROID_SERIAL=EMULATOR_SERIAL ./scripts/test-ssh.sh clean`.
Stopping the listener does not end existing connections or Kero sessions.

## Release builds

App versions come from `version.properties`; increase `versionCode` for every
published update. See [releases](releases.md) for the GitHub CI, signing environment
and tag-triggered publication process.

`./gradlew :app:assembleRelease` signs with the local sideload identity if present:

```text
~/.local/share/keroid/signing/release.p12   # PKCS12, alias keroid-release
~/.local/share/keroid/signing/password      # one line
```

Keep the directory mode 700 and files mode 600. Gitignored `local.properties`
can override `keroid.storeFile`, `keroid.storePassword`, `keroid.keyAlias` and
`keroid.keyPassword`. Without signing material the release is unsigned.
Find the APK under `app/build/outputs/apk/release/`.

Back up the keystore and password outside the repository: updates require the
same identity. Never regenerate it for an existing install or bypass signature
verification to fix an install failure. Keep signing material out of version
control, logs and screenshots. Play Store signing would be a separate setup.
