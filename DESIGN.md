# Keroid design system — Material 3 Expressive (v3, flat/compact)

Status: **implemented in the app.**
Canvas: `wI0C4NSHv6` · prototype `/c/wI0C4NSHv6/proto` (starts at Sessions · Mocha)
Canonical frames: Sessions `_c3RgwGIAy` (Latte `amYgWcrTYm`), Session `MfYgM1QyCa`, dock `UQUrukhwyi`.

This document is the engineering contract. The canvas is the visual truth; the Compose implementation follows it and records its small deviations in section 12.

---

## 1. Product

Keroid is the Android client for Kero. A remote `kerod` owns the PTYs, agents and session state;
the phone is a window into them. The app connects over SSH or Tailscale, shows what is running,
attaches to a pane and sends input/keys — never a local terminal, never a file editor.

The scene: 23:40, one hand, dark. One row says codex is waiting for a review. Two lines of
metadata are enough to decide. The row becomes the terminal; answer with the pinned ctrl key.
Maya never leaves the thumb zone.

---

## 2. Direction

**Google Material 3 Expressive with a compact, Android-first information architecture.**

- Big emphasized type (32/40 screen titles, 22/28 sections, 16/22 rows) over a **dense list**:
  sessions are grouped rows, not large cards, and carry no terminal preview.
- **Flat chrome.** The only gradients allowed are the theme's own wash/swatch gradient (Kero
  themes) — never a decorative gradient invented by the UI.
- State is a **status pill/dot**, not a coloured surface: needs-you is an amber pill, working is
  primary text + a 3 px progress bar, idle is muted. The row surface never turns amber.
- One accent: ANSI blue. Attention yellow, success green, danger red, info cyan, agent magenta.
- Android M3 components, used deliberately: large app bar, navigation bar with 3 tabs,
  button group, grouped list, FAB menu, floating key toolbar, dialog/sheet elevation.
- One authored spatial moment: a session row expands into the terminal. The FAB menu and key
  toolbar are the two supporting micro-interactions.

The previous version's 28 px preview cards are retired. The list is compact, the row is the object.

---

## 3. Theme engine

One **pair** drives the terminal and the interface: a light slot and a dark slot, each one of the
10 Kero washes (petal, mist, meadow, dusk, tide, apricot, aurora, dune, paper, plain) or one of the
618 Ghostty themes. Defaults **Catppuccin Latte / Mocha**; `System` picks the slot.

Derivation:
```
window  = theme.background
surface = OKLCH(theme.background, L − 0.030)
sunken  = OKLCH(theme.background, L − 0.035)
fg      = theme.foreground
muted   = mix(fg, surface, 12–18%) clamped to ≥4.5:1
primary = ANSI[4] (blue), not the cursor
attention = ANSI[3] · success = 2 · danger = 1 · info = 6 · agent = 5
```

- Attention is a **status role**: `#F9E2AF` pill with `#2A2413` ink. It never fills a row/card.
- Theme gradients: Kero washes and theme swatches may show the theme's own 4-stop gradient. The
  chrome (rows, bars, sheets) stays flat. No other gradients.
- Theme swatches may be gradients; UI surfaces may not.

### Roles

| role | Mocha | Latte | use |
|---|---|---|---|
| surface | `#171727` | `#E5E7EB` | page, app-bar ground |
| window | `#1E1E2E` | `#EFF1F5` | grouped list, nav, terminal pane |
| sunken | `#161625` | `#E3E5E9` | fields, code blocks |
| raised | `#262637` | `#EAECF1` | chips, folded rows, keycaps |
| high | `#2B2C3D` | `#E6E8ED` | floating toolbar, dialogs |
| onSurface | `#CDD6F4` | `#4C4F69` | primary text |
| onSurfaceVariant | `#A6ADC8` | `#5E6178` | metadata ≥4.5 |
| quiet | `#9399B2` | `#7B7E92` | tertiary only |
| outline | `#6C7086` | `#B1B3C0` | dividers |
| primary | `#89B4FA` | `#1E66F5` | actions, selection |
| onPrimary | `#11111B` | `#FFFFFF` | text on primary |
| primaryContainer | `#333C57` | `#D2DEF5` | active nav, selected rows |
| secondary | `#94E2D5` | `#179299` | info / claude |
| tertiary | `#F5C2E7` | `#EA76CB` | codex |
| error | `#F38BA8` | `#BC0028` | destructive |
| attention | `#F9E2AF` | `#F9E2AF` | status pill only |
| success | `#A6E3A1` | `#007500` | connected |
| hairline | `rgba(255,255,255,.07)` | `rgba(18,20,42,.075)` | row dividers |

Light grouped lists use one soft shadow; dark uses the hairline. Dialogs use
`surfaceContainerHigh` (`#2B2C3D`) with **one** shadow and **no border**. Sheets use `window`.

Visible gradients are only the theme's own four stops (Kero cards, appearance hero). The app's
chrome never has a gradient.

---

## 4. Type and terminal fonts

**Google Sans Flex** for UI; **JetBrains Mono** for terminal output, paths, counts, ports,
fingerprints, keycaps. Load Google Sans Flex and JetBrains Mono in mocks; bundle both in the APK.

| role | size/line | weight | use |
|---|---|---|---|
| screen title | 32/40 | 600 | large app bar |
| detail title | 20/26 | 600 | pushed screens |
| section | 22/28 | 620 | project headers |
| row title | 16/22 | 620 | session rows |
| body | 14/20 | 430 | descriptions |
| secondary | 12/16 | 450 | metadata |
| label | 13/18 | 600 | buttons, nav |
| micro | 10.5/14 | 600 | chips, counts |
| terminal | 10.4/15.4 | 400 | mono |

**Terminal font feature** (`Terminal type`, `vGi1TOP2TT`):
- **JetBrains Mono** — bundled, default, Nerd Font glyphs, real bold/italic.
- **System monospace** — Android's built-in mono face.
- **Roboto Mono** — phone font when installed.
- **Noto Sans Mono** — phone font with wide language coverage.
- A live preview in the chosen face; font size 10–22 sp (default 13), line height 1.0–1.8×
  (default 1.30). Missing Nerd glyphs fall back to a box, never to a different face.
- If a phone font is absent, hide its row rather than substituting another family.

---

## 5. Shape, space, elevation

Shape: 6 tag · 10 keycap · 18 folded row · 24 grouped list · 28 machine card · 32 dialog/sheet ·
full pill. Cookie machine tile: `r = R(1 + 0.05 cos 6θ)`, 56 dp / 44 dp in lists.
Space: 4 dp grid, 16 margin, 14 row padding, 8 list gap, 18 project gap, 24 section gap.
App bar 96 (24 status + 72); detail app bar 64; navigation 80 + 24; touch 48.
Rows 66 high. Grouped list: one `window` container r24 with 1 px dividers; no per-row card.
Elevation: grouped list = hairline (dark) / soft shadow (light); dialog = light fill + single
shadow; FAB/key toolbar = shadow; no border + big shadow on the same layer.

---

## 6. Components

| component | spec |
|---|---|
| Large app bar | 96; title 32/40; subtitle 12.5 muted with mono machine; search + overflow 48 |
| Detail app bar | 64; back 48; title 20/26 + 11.5 subtitle |
| Navigation bar | 104; 3 tabs — Sessions, Machines, **Settings**; 64×32 r16 indicator; icon 24; label 12 |
| Machine rail | 56 cookie + 11.5 label; selected primary; status dot 9 with 2.5 ring |
| Filter group | 52 r26 raised; 44 r22 segments; selected primary; mono counts |
| Project header | 44; chevron 20 + 20/26 name + mono count + add 40 |
| Folded project | 52 r18 raised; chevron; 14/18 name; summary in the semantic colour |
| **Session row** | 66 high; grouped list r24; dot + title 16/22 + meta 11; status column; time |
| Attention | amber pill `Needs you` + amber dot; row stays `window` |
| Working | primary `Working` + `12m · 68%`; 3 px primary bar at the row bottom |
| FAB / FAB menu | 56 r16 primary; menu = 3 labelled small FABs 48 r16 + close FAB; scrim 45% |
| Key toolbar (collapsed) | 56 r20; pinned ctrl, esc, tab, ←, →; divider; `Keys` button |
| Key panel (expanded) | 392 high r24; Modifiers = connected group (ctrl locked); Navigation/Symbols = 8-key grids; Input = ABC/space/enter/del |
| Fields | 56 r18 sunken; 1 px outline; focus 1.5 primary |
| Dialog / sheet | 320 r32 / top r32 + handle; 24 padding; dialog `#2B2C3D` + one shadow; sheet `#1E1E2E` |
| Theme swatch | 64×36 r10; Kero card 167×92 with the theme's **gradient** |
| Snackbar | inverse surface, r14, 48 |

Detailed measurements live on `Foundations · Components` and the screen frames.

---

## 7. Motion

Springs (expressive): fast spatial `.6/800`, default `.8/380`, slow `.8/200`; effects
`1/3800`, `1/1600`, `1/800`.

| interaction | pattern | spec |
|---|---|---|
| Session row → terminal | container transform | 380 ms; back reverses |
| Tab switch | fade through | 150 ms, scale .97→1 |
| Push detail | shared axis X | 300 ms, 24 px |
| Project fold | height + chevron | 300 ms; rows fade 120 |
| FAB → FAB menu | scale/translate from the FAB | 240 ms; items stagger 20 |
| Key toolbar → panel | rise from bottom | 320 ms; groups stagger 12 |
| Theme change | role cross-fade | 320 ms; terminal lines 30 ms; one haptic |
| Sheet / dialog | slide up / fade + scale | 300 / 200 ms; scrim 200 |
| Needs-you dot | halo pulse | 2.4 s; reduced motion off |

Reduced motion: 120 ms cross-fade or instant; no pulses, no container transform.

---

## 8. Screens and flows

Tabs: **Sessions / Machines / Settings**. Appearance is a detail reached from Settings.
Detail screens hide the navigation bar.

1. **Sessions** (`_c3RgwGIAy`, Latte `amYgWcrTYm`, folded `KBMkPj1LcF`, FAB menu `C4rTGZr4Ms`):
   large app bar, machine rail, filter group, grouped project rows (three/four 66 px rows), FAB.
   The FAB opens the **FAB menu**: New session, Open project, Add machine. A folded project row
   keeps its working/needs-you summary. Rows have no preview; state is a pill/bar.
2. **Search sessions** (`NKtaI21oh8`): focused search field, All/Needs/Idle result filters, flat
   result rows with preview-free metadata, needs-you pill. Reached from the app-bar search.
3. **Session** (`MfYgM1QyCa`): 64 app bar, pane tabs, terminal window with a 34 px chrome strip,
   compact key toolbar (ctrl, esc, tab, arrows, Keys). The dock expands (`UQUrukhwyi`) into
   grouped Modifiers / Navigation / Symbols / Input, with a lockable ctrl.
4. **Machines** (`DioYuQRdaP`): 96 header, Machines/Identities segmented, connected/saved groups,
   compact machine cards; no gear (Settings is the third tab). FAB adds a machine.
5. **Identities** (`WF2kfIWrUF`): key rows with fingerprints, machine counts, passphrase tag and the
   Keystore note.
6. **Machine editor** (`Fi6wExgfBS`): SSH/Tailscale, identity, executable, kerod info, Test/Save.
7. **Identity editor** (`WRNGl_pJWC`): protected pill, masked PEM, passphrase, parsed key, delete.
8. **Project browser** (`ln5tqgYals`): path/Go, parent, hidden, filter, folder rows, open CTA.
9. **First run** (`GC5oQdAAeo`): title, mini terminal, three setup steps, CTA.
10. **Settings** (`64a5KB4JV7`): tab with 96 header and nav. Theme → Appearance; Terminal type →
    `vGi1TOP2TT`; Host keys; Notifications (soon); About; Licences; Developer.
11. **Appearance** (`nWLRinsfwM`): detail with back; Latte/Mocha pair hero, System/Light/Dark
    segmented, two theme rows, one-source/two-surfaces explainer, terminal type row, reset.
12. **Theme picker** (`Uq-6yopeuN`): search, Kero/Ghostty/All group, Kero cards with real theme
    **gradients**, Ghostty rows with swatches and dark/light tags.
13. **Theme search** (`rxu_eJPv6h`): `cat`, 4 of 618, Catppuccin rows, no-Kero note.
14. **Terminal type** (`vGi1TOP2TT`): live preview, JetBrains Mono / System monospace / Roboto
    Mono / Noto Sans Mono, size and line-height steppers.
15. **New session sheet** (`pEJGUhfQJr`, overlay 390×470): shell / resume Codex / Claude / open
    project, Start session.
16. **Tablet** (`pq-auJmffO`): rail with Sessions/Machines/Settings, compact list pane, terminal.
17. **States** (`yv7cDt01y2`), **Dialogs** (`beqq1UKX43`), **Flows** (`6XVyakXjHl`).

---

## 9. States

Session row: needs you (amber pill + dot), working (Working + 3 px bar + 68%), idle, error (exit
code), unread dot, long title, 200% type. Project: expanded, folded working, folded needs-you,
empty. Connection: offline, connecting, verify host, error, connected. Empty: no machines, no
sessions, no results, no folders. Loading: 3-bar skeleton and wavy connecting line. Fields and
buttons: rest, focus, filled, error, disabled. Keycaps: rest, pressed, locked. The needs-you halo
is 2.4 s and disabled by reduced motion.

---

## 10. Accessibility and security

Body ≥4.5:1, large ≥3:1, icons/borders ≥3:1. 48 dp targets, 8 dp apart. sp type; layouts grow at
200%. System back; edge-to-edge insets once. Identity editor keeps `FLAG_SECURE`; never show a key,
passphrase or fingerprint in a preview/screenshot/saved state. Host keys pin on first connect;
changed keys block with no one-tap bypass.

---

## 11. Implementation plan

1. Theme engine: theme pair, Kero wash → Ghostty → Catppuccin resolution, derived ColorScheme +
   semantic roles; feed the same theme to `TerminalAppearance`; remove the UI/terminal split.
2. Navigation: 3 tabs Sessions/Machines/Settings; Settings hosts theme + terminal type; Appearance
   is a pushed detail; remove gear entry points.
3. Sessions: compact grouped rows (66), no previews, attention pill, FAB menu, folded projects,
   search screen.
4. Session: terminal window, compact key toolbar + grouped expanded panel, pinned ctrl; keep the
   shared-grid contract and text-snapshot fallback.
5. Terminal type: JetBrains Mono default, system monospace, Roboto Mono/Noto Sans Mono when
   available; size/line-height; persist with the appearance.
6. Machines/Identities/editors/browser/Settings/Appearance/Theme picker/appearance hero.
7. Motion: row→terminal, FAB menu, dock, theme cross-fade, reduced motion; keep tests green.
8. Verify both themes, 200% font, tablet; screenshots.

Do not copy HTML/CSS into the app; translate tokens and measurements to Compose. Do not add
decorative gradients or dynamic colour. Do not change protocol, credentials or shared-grid code.

---

## 12. Implementation status and open items

Implemented:
- Theme engine derives the Material scheme and semantic roles from the selected terminal
  pair; Catppuccin Latte/Mocha are the defaults, and the same pair colours the terminal.
- Ten Kero washes are available as theme pairs (`KeroWash`); their light stops come from
  the foundations frame, their dark slots are hue-preserving derivations. Wash gradients
  appear only in picker swatches, never in chrome.
- Google Sans Flex (OFL) is bundled for UI; JetBrains Mono remains the terminal face.
- Three root tabs, large headers, grouped session rows, FAB menu, new-session sheet,
  appearance studio, theme picker, terminal type, machines/identities and rewritten
  project browser.
- The terminal uses a rounded window with a 34 dp chrome strip; the compact key bar
  gains a grouped Modifiers/Navigation/Input panel when the IME is dismissed.

Open:
- Roboto Mono / Noto Sans Mono are not bundled, so Terminal type offers the bundled face
  and Android's system monospace only.
- Wash dark slots are derived locally rather than generated from desktop `tone` parameters;
  replace them if desktop exposes exact dark wash tokens.
- Long-press modifier locking is not wired; ctrl remains a tap toggle and sticky keys stay
  lit until the next key.
- The key panel keeps the compact row while the IME is open (a taller panel would move the
  live cursor); the full grouped panel appears after the keyboard is dismissed.
- Verified on the emulator in Mocha and Latte; no push.

