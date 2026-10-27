# Roadmap

Session rename and session/pane close are implemented; see
[session actions](../README.md#session-actions) for usage.

Remaining work:

- **Session tools:** read-only diffs and attachments. Independent pane renaming
  needs upstream protocol support.
- **Connection UX:** deliberate reconnection and background notifications
  within Android's delivery constraints.
- **Terminal polish:** pan inertia/fling, draggable selection handles,
  selection autoscroll and viewport-only cursor avoidance for the expanded dock.
- **Target SDK 37:** after an API 37 emulator is available for behaviour testing.

Out of scope unless requested: kitty graphics/images, ligatures/font-weight
thickening, Windows shell probing, SSH config/ProxyJump import, embedded
Tailscale login and an alternative terminal engine.
