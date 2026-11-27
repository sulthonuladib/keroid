# Audit checklist

Run `scripts/audit.sh` first. The script catches mechanical drift; the manual
pass catches decisions a grep cannot judge. Both are required.

## Severity

| Severity | Meaning | Action |
| --- | --- | --- |
| ERROR | Broken behavior, accessibility blocker, or a second design system in the app | Fix before shipping |
| WARN | Drift from M3 that will age badly and compound | Fix in this change, or record a reason |
| INFO | Heuristic or opportunity; may be intentional | Review, then keep or fix |

## Automated rules

| ID | Sev | Detects | Fix |
| --- | --- | --- | --- |
| E001 | ERROR | `import androidx.compose.material.*` (icons excluded) | Migrate the screen to `material3`; if the theme is still M2, migrate the theme first |
| E002 | ERROR | `fontSize = …dp` | Use `sp` through a typography role |
| W101 | WARN | Inline `fontSize = …sp` | `style = MaterialTheme.typography.*` |
| W102 | WARN | `Color(0x…)`, `Color.White`, etc. in feature code | A `colorScheme` role; theme definitions are exempt |
| W103 | WARN | `colorScheme.x.copy(alpha = …)` | `onSurfaceVariant`, a container role, or a state layer |
| W104 | WARN | `tween(...)` in feature code | `MaterialTheme.motionScheme.*Spec()`; keep tween for progress/canvas |
| W105 | WARN | `LinearEasing`, custom `CubicBezierEasing` | Motion scheme, or an M3 easing token |
| W106 | WARN | `Divider` | `HorizontalDivider` / `VerticalDivider` |
| W107 | WARN | `MaterialTheme.colors` | `MaterialTheme.colorScheme` |
| I201 | INFO | `FastOutSlowInEasing` and friends | M3 easing tokens or a spring |
| I202 | INFO | Inline `spring(...)` | `MaterialTheme.motionScheme` unless defining the theme |
| I203 | INFO | `Modifier.shadow(...)` | `Surface(tonalElevation/shadowElevation)` or a container role |
| I204 | INFO | Fixed `.size(<48.dp)` | Confirm the interactive target is enlarged |
| I205 | INFO | `contentDescription = ""` | `null` for decorative, a real label otherwise |
| I206 | INFO | Inline `TextStyle(...)` | A typography role |
| I207 | INFO | `clickable` without `role =` | `role = Role.X` plus indication |
| I208 | INFO | Ripple configuration override | Confirm it is an intentional escape hatch |
| F301 | INFO | `Scaffold` padding never consumed | Pass `innerPadding` to the content or its scrollable |
| F302 | INFO | `Icon` in a file with no `contentDescription` | Label icon-only controls |

Suppress a reviewed false positive on its line:

```kotlin
val outline = Color(0xFF79747E) // m3-audit-ignore: exported brand token
```

## Manual checks

| ID | Check | Fail condition |
| --- | --- | --- |
| M01 | Theme completeness | A scheme defines only a few roles, or light and dark disagree structurally |
| M02 | Dynamic color | Crashes below API 31, or ignores the user's wallpaper choice |
| M03 | Contrast | Body text under 4.5:1, icons/borders under 3:1 |
| M04 | Type roles | Any screen text bypasses `MaterialTheme.typography` |
| M05 | Shape and elevation | Ad-hoc corners or shadows outside the theme |
| M06 | Motion source | Animations use magic durations, or color uses spatial springs |
| M07 | Motion resilience | Interrupted animation snaps; UI unusable at animator scale 0 |
| M08 | State coverage | Missing pressed, selected, disabled, loading, error, or empty state |
| M09 | Accessibility | Unlabeled control, missing role/state, target under 48dp |
| M10 | Font scale and RTL | Clipping at 200% or mirrored layout breakage |
| M11 | Navigation and overlays | Back leaves the screen with an overlay open, or vice versa |
| M12 | Emphasis | Two primary actions in one region; wrong component for the intent |
| M13 | Expressive coherence | Standard and expressive variants of the same component mixed in one cluster |
| M14 | Scrolling and motion performance | Nested same-axis scrollables; animated width/height on complex trees |

## Fix order

1. **Errors** — M2 imports, dp text. They block everything else.
2. **Tokens** — colors, type, shape, elevation. One theme edit fixes many
   findings.
3. **Motion** — replace ad-hoc timing with the scheme.
4. **Accessibility** — labels, roles, targets, focus.
5. **States and polish** — loading, empty, error, expressive accents.

Re-run the script after step 2 and after step 5.

## Report contract

Produce a table with one row per finding, in file order. Do not summarize away
locations.

```markdown
| Rule | Sev | Location | Evidence | Fix | Status |
| --- | --- | --- | --- | --- | --- |
| E002 | ERROR | feature/Inbox.kt:88 | `fontSize = 14.dp` | `style = typography.bodyMedium` | fixed |
| W102 | WARN | feature/Inbox.kt:41 | `Color(0xFF6750A4)` | `colorScheme.primary` | fixed |
| M09 | ERROR | feature/Inbox.kt:63 | Icon button without label | `contentDescription = "Search"` | fixed |
```

End the report with:

- counts by severity;
- which fixes were applied and which were deferred;
- the exact verification performed (build, previews, font scale, TalkBack).

## Verify after fixing

- `./gradlew assembleDebug` (or the module's compile task) succeeds.
- Previews render in light and dark.
- The audit script exits 0 at the chosen threshold.
- Font scale 200% and a screen reader pass on the touched screens.
