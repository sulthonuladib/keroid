# Keroid

An Android client for [Kero](https://github.com/egoist/kero). Connect to sessions
on remote machines over SSH or Tailscale, with multiple saved machines and
reusable SSH identities.

- Native Kotlin, Jetpack Compose and Material 3 with dynamic colour.
- libghostty-vt terminal with scrollback, selection, extra keys and Ghostty themes.
- Open remote projects; create, rename and close sessions; split or close panes.
  Desktop splits appear as tabs on mobile.

Keroid is a client only: the remote `kerod` owns sessions, PTYs and agents.
This is an independent client, not an official Kero product.

## Install

Download the signed universal APK from [GitHub Releases](https://github.com/sulthonuladib/keroid/releases).
It supports arm64 Android phones and x86_64 emulators running Android 7.0+;
32-bit devices are not supported. GitHub also provides source ZIP/tar.gz archives.
For checksums and release automation, see [releases](docs/releases.md).

## Connect

Install Kero on the remote machine, then add that machine in Keroid:

- **SSH:** enter `user@host`, import an authorized private key and verify the
  host fingerprint independently. Keroid pins trust to the host and port.
- **Tailscale:** connect the Android Tailscale app and enter the host's kerod
  address, such as `http://devbox:7373` or
  `https://devbox.tail1234.ts.net`. The host needs kerod already running with a
  tailnet listener or Tailscale Serve; no SSH key is needed. Access is decided
  by kerod's owner/`listen-allow` rules. See Kero's
  [remote-host docs](https://github.com/egoist/kero/blob/v2.1.24/docs/remote.md)
  for host setup.

Private keys are encrypted with Android Keystore; passphrases are kept only
for the connection attempt.

## Session actions

Use a session card’s **⋮** menu to rename or close it. In a terminal, open
**Terminal actions** to rename the session, close the selected pane or close
all panes in the session. Closing requires confirmation and keeps linked
worktrees. Leave a session name blank to restore automatic naming.

Independent pane renaming is not supported by the pinned Kero protocol.

## Build

Requires an Android SDK/NDK installation; toolchain setup is in
[development](docs/development.md).

```sh
mise trust && mise install
./scripts/build-core.sh
./scripts/build-ghostty.sh
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (arm64 and x86_64).
For local release signing, see [release builds](docs/development.md#release-builds).

## Documentation

- [Development](docs/development.md) — setup, verification and local fixtures.
- [Architecture](docs/architecture.md) — connection, terminal and security contracts.
- [Roadmap](docs/roadmap.md) — remaining work.
- [Releases](docs/releases.md) — CI, signing setup and publishing APKs.

## Licence

[MIT](LICENSE). See [third-party notices](THIRD-PARTY-NOTICES.md) for bundled
components and attribution.
