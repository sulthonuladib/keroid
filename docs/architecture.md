# Architecture

Keroid is an Android client; the remote `kerod` owns PTYs, agents and session
state. Kotlin/Compose UI uses Navigation 3, Hilt and ViewModel + StateFlow.
The Go library in `core/mobile/` owns connections and the wire protocol.

## Connections

```text
Compose → Go mobile library
  SSH: embedded x/crypto/ssh → remote kero __bridge --via ssh (exec, no PTY)
  Tailscale: WebSocket /kero/mux → kerod listener or Tailscale Serve
→ mux v1 + DEFLATE + yamux
  control stream: JSON requests, responses and state events (protocol v3)
  attach stream per pane: binary terminal frames
```

Mirror the release pinned in `scripts/reference-repos.sh`, not an independently
invented protocol. Reference code lives in `repos/kero/internal/proto`,
`internal/remote` and `internal/daemon/rpc.go`; our frame codec is
`core/mobile/frame.go`. Transports differ only in the byte pipe.

### SSH and credentials

- Host trust is endpoint-bound (host + port), not identity-bound. Switching
  identities or deleting/re-adding a machine must not bypass a changed pin.
- Reject unknown hosts before user-key authentication; approval requires an
  explicit fingerprint prompt. Changed keys block connection, with no one-tap
  replacement or silent pin update.
- Private keys use an Android Keystore AES-GCM key and `AtomicFile` under
  `noBackupFilesDir`; backups exclude them. Passphrases live only for the
  connection attempt and are then cleared.
- Credentials and terminal content stay out of saved state, navigation
  arguments, logs, crash reports, screenshots and previews. The identity
  editor sets `FLAG_SECURE`. App UI remains visually distinct from untrusted
  remote output.

### Tailscale

The installed Tailscale app carries traffic; there is no embedded login.
Kerod must already run: the app cannot start it over the tailnet.

- WebSocket `/kero/mux` uses `Kero-Client`, no `Origin`, binary messages,
  no WebSocket compression and a 64 MiB read limit. DEFLATE is across the mux.
- Plain HTTP resolves and dials only `100.64.0.0/10`, `fd7a:115c:a1e0::/48`
  or loopback; validate the actual connected address too. Reject other
  destinations before sending any bytes.
- HTTPS uses system certificate verification. No skip-verify, downgrade,
  custom pinning or fallback to SSH.
- Kerod alone authorizes the caller (Tailscale identity or Serve's
  `Tailscale-User-Login`, owner/`listen-allow`). Send no identity headers,
  tokens or logins. Report 401/403 as a refusal with kerod's message, not
  an unreachable host; do not retry through another transport.

## Terminal

```text
attach frames → TerminalSession / TerminalEventQueue
→ libghostty-vt via native/ghostty_jni.c → Compose TerminalView
```

The VT core has a plain C ABI without platform UI dependencies. It is statically
linked into `libkeroid_term.so` to avoid Ghostty's versioned shared-library
SONAME conflicting with Android APK loading. Compose draws background and
styled text runs at exact column offsets; Ghostty handles emulation, not drawing.
Retain the text-snapshot fallback for builds without the native library.

### Shared-grid contract

The daemon owns one grid per pane. Local layout changes request geometry;
they never directly resize the local VT.

- Resize the VT only on attach welcome (`'s'`) or daemon-confirmed resize
  (`'z'`). Pan and fit-to-screen are renderer viewport transforms.
- `TerminalSizing` retains desired columns, rows and cell pixels before attach
  and across reconnects. One IO writer per attachment sends resize before claim.
- A deliberately active pane claims sizing once per lifecycle activation,
  not per composition/measurement. Reject queued claims from older activations
  or a background pane. Subscribe before opening attach so welcome is not lost.
- `TerminalEventQueue` orders output and confirmed sizes off the main thread.
  Batch adjacent output only; resize is a batch boundary. Select dimensions
  and capture cells under the same native lock to avoid mixed-grid snapshots.
- Geometry requests wait for settled IME animation and a short quiet period.
  Use `WindowInsets.isImeVisible`, not bottom-inset height, for keyboard state.
  The floating key dock never changes canvas bounds or renegotiates the grid;
  handle occlusion through viewport transforms. `TerminalDockLayout` measures
  the dock first and supplies clearance read only by drawing/pointer handling.
  The requested rows reserve the dock height plus one clear row, so the live
  cursor is never hidden behind the keys and folding the dock open by less than
  a row keeps the same grid. A grid still larger than the unobscured area (a
  desktop-owned size, or a resize the daemon has not confirmed yet) follows its
  live bottom row; panning stays relative to that alignment. Fit mode uses the
  unobscured height. None of these change shared-grid measurement.

`TerminalSizingTest`, `TerminalEventQueueTest` and `PaneScreenTest` encode these
contracts; preserve them when changing layout.

### Input, history and appearance

- IME text and encoded keys share an ordered queue. Encode keys with Ghostty's
  API (application cursor mode, modifiers, Kitty protocol); byte tables are
  fallback only. Preserve composing text rather than resetting per keystroke.
- The 1 dp IME editor handles keyboard input only. TerminalView owns focus
  gestures, scrolling and selection; overlays must not intercept them.
- Scrollback is local history navigation, not PTY input or resize. Selection
  uses native tracked references; copy uses Ghostty's plain formatter with
  soft-wrap unwrapping. The protocol does not transmit alternate-screen history.
- `TerminalAppearance` is separate from the app's Material palette. App
  light/dark mode selects a terminal theme; it does not replace app colours.
  Apply themes to the VT so indexed/already-drawn cells and OSC queries agree.
  Programs may subsequently override colours.
- `TerminalThemes.kt` is generated by `scripts/gen-terminal-themes.py`, not
  hand-edited. Retain its source checksum, provenance and licence notes.
  Unknown names fall back to `Keroid`, the desktop-matching default.
- The bundled JetBrains Mono Nerd Font has four real faces and loads
  synchronously for stable first-frame cell metrics, in both native and
  fallback views. App typography is independent.

## Screens and session state

Use route-scoped screens receiving state/callbacks, with ViewModels owning work.
App chrome uses Material theme tokens; terminal colours live in terminal code.
Bottom tabs become a rail at ≥600 dp. Scaffold consumes safe-drawing insets.
Terminal status shares the title bar, a single pane has no tabs, and extra keys
float over the canvas. Preserve ≥48 dp touch targets, icon descriptions and LTR
terminal-arrow meaning under RTL.

Non-secret UI state is saveable and keyed by machine/session where applicable.
Control descriptions are test selectors; changing them requires updating tests.
Previews are offline, including light/dark, large-text and wider-window states.

Remote actions use the existing desktop control protocol:

- Browse folders with `fs.list`; opening a project calls `project.create`
  then `create` with its id/directory.
- New session uses `create`; from a terminal it inherits project/current pane
  directory and uses `after`.
- Splits use `split` with pane/direction. Preserve the remote layout, show panes
  as tabs and select returned `focus`; mobile does not lay out desktop splits.
- Rename uses `rename` with session id/name; an empty name restores automatic
  naming. Pane titles are program-controlled; the pinned protocol has no pane rename.
- Confirm before `close` (session) or `pane.close` (pane). Closing the final pane
  uses session close. Send only the id: keep worktrees and never request force/removal.

Bind actions to the route's machine, not the current home selection.
Mutations are single-flight per machine, never automatically replayed after
timeouts; ignore late results after disconnect. Creation/split responses carry
sessions, merged as pending state until a subscribed snapshot confirms them.
Rename/close replies are boolean acknowledgements; subscribed state remains
authoritative. Keep raw snapshots separate from pending creations so closing
an unconfirmed session cannot resurrect it.
