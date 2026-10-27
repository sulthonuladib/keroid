# Third-party notices

Keroid is MIT-licensed (see [LICENSE](LICENSE)), © 2026 Sulthonul Adib. It is
**not** an official Kero product. The software it builds on, links, bundles or
takes data from:

| Component | Used for | Licence | Source |
|---|---|---|---|
| [Kero](https://github.com/egoist/kero) `v2.1.24` | Wire protocol, control/attach frames, theme palette defaults. Protocol code was mirrored; no desktop Kero code is modified or shipped. | MIT, © 2026 EGOIST | Kero repository (read-only reference in `repos/`, not pushed) |
| [libghostty-vt](https://github.com/ghostty-org/ghostty) | The terminal emulator core (statically linked into `libkeroid_term.so`) | MIT, © 2024 Mitchell Hashimoto, Ghostty contributors | `scripts/build-ghostty.sh` pins the commit |
| [hashicorp/yamux](https://github.com/hashicorp/yamux) `v0.1.2` | Stream multiplexing over one connection | MPL-2.0, © 2014 HashiCorp, Inc. Unmodified; source available at the link. | Go module |
| [coder/websocket](https://github.com/coder/websocket) `v1.8.14` | Tailscale transport (WebSocket client) | ISC, © 2025 Coder | Go module |
| [golang.org/x/crypto](https://pkg.go.dev/golang.org/x/crypto) | SSH client and key parsing | BSD-3-Clause, © 2009 The Go Authors | Go module |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) Nerd Font variant | Bundled terminal font (`app/src/main/res/font`, four faces) | SIL Open Font License 1.1 | Licence text: `app/src/main/assets/licenses/jetbrains_mono_nerd_OFL.txt` |
| [Ghostty's shipped themes](https://ghostty.org) via [iTerm2-Color-Schemes](https://github.com/mbadolato/iTerm2-Color-Schemes) | The 617-entry terminal theme catalogue (`TerminalThemes.kt`) | Collection MIT, © 2011 to Present Mark Badolato; each theme is its author's copyright | iTerm2-Color-Schemes; provenance recorded in the generated file and `scripts/gen-terminal-themes.py` |
| AndroidX / Compose / Material 3, Hilt, Kotlin, kotlinx.coroutines, kotlinx.serialization, Navigation 3 | App framework and UI | Apache License 2.0 | Respective AndroidX/JetBrains/Kotlinx releases |
| Go standard library, Go mobile (`gomobile`) | Bindings and runtime | BSD-3-Clause, © The Go Authors | Go distribution |

## Licence texts not repeated above

- **MPL-2.0** (yamux): the full text is in [licenses/MPL-2.0.txt](licenses/MPL-2.0.txt).
  Yamux is used unmodified, and its source is available at the link above, as the
  licence requires.
- **SIL OFL 1.1** (JetBrains Mono Nerd Font): bundled with the font at
  `app/src/main/assets/licenses/jetbrains_mono_nerd_OFL.txt`, and it ships inside
  the APK.
- **MIT** (Kero, libghostty-vt, iTerm2-Color-Schemes themes) and **BSD-3-Clause**
  (Go, x/crypto) and **ISC** (coder/websocket) are permissive notices reproduced
  in the table; each requires the copyright notice and permission notice be
  retained, which the table and the bundled files do.
- **Apache-2.0** (AndroidX, Compose, Material 3, Hilt, Kotlin and Kotlinx): the
  Gradle dependencies carry their licence metadata; the APK's `META-INF` keeps the
  notices that the Android build packages.

If you redistribute a build of this app, keep this file, `LICENSE`, the
`app/src/main/assets/licenses/` files and, for MPL-2.0 compliance, a pointer to
the yamux source (or the text in `licenses/`).
