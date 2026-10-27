# Keroid

Android client only; sessions and PTYs belong to the remote `kerod`.

- Treat `repos/` as read-only reference. Ask before changing a reference clone's
  revision; never modify desktop Kero to build this app.
- Use only emulators for device automation. Build APKs for the owner to install
  on physical phones.
- Keep libghostty-vt behind the Compose renderer and retain the text-snapshot
  fallback.
- Keep credentials and session content out of commits, logs, screenshots and
  saveable state.
- Run long builds/tests in background sessions and close them when done.
  After successful Android verification, reinstall and launch on the emulator.

Read [development](docs/development.md) for setup, builds and verification.
For terminal, transport, session-state or credential changes, read
[architecture](docs/architecture.md) before editing; it defines the shared-grid
and security contracts. [Roadmap](docs/roadmap.md) holds remaining work.
