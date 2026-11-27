---
name: material3-foundation
description: Build or change the Material 3 design foundation of a Jetpack Compose
  Android app — color roles and dynamic color, the type scale, the shape scale,
  tonal/shadow elevation, interaction state layers, and motion (MotionScheme,
  spring physics, easing and duration tokens). Use when theming an app, defining
  or migrating design tokens, styling screens, adding transitions or animations,
  or adopting M3 Expressive. Use material3-components to choose components and
  material3-audit to review existing UI.
metadata:
  last-updated: '2026-10-28'
  keywords:
  - Jetpack Compose
  - Material 3
  - Material You
  - Material 3 Expressive
  - design tokens
  - theming
  - motion
  - springs
  - dynamic color
---

# Material 3 foundation

A Material 3 UI changes in one direction:

```
tokens → MaterialTheme(colorScheme, typography, shapes, motionScheme) → component defaults → screen
```

Styling a screen directly skips the chain and makes the next theme change a hunt.
Every rule here keeps the chain intact.

## Workflow

Work top-down. Each step has a completion bar; do not start the next step until
it is met.

### 1. Pin the theme contract

Find the theme files, the single `MaterialTheme` or `MaterialExpressiveTheme`
call, and the resolved `androidx.compose.material3` version (version catalog or
BOM). Record the version: expressive APIs are not available everywhere.

Completion: one theme entry point wraps the app; no second full theme deeper in
the tree; the material3 version is known.

If the project still imports `androidx.compose.material.*`, stop and run
`material3-audit` — theming a half-migrated app compounds the problem.

### 2. Color

Define complete light and dark schemes from roles, not a handful of overrides.
Support dynamic color on Android 12+ with a brand scheme as fallback. Let the
user choose system/dynamic vs brand if the product allows it.

Read `references/color.md` for the full role map, contrast rules, and the
dynamic-color switch.

Completion: no `Color(0x…)`, `Color.White`, or `MaterialTheme.colorScheme.x.copy(alpha=…)`
in feature code; both schemes render; a preview shows each.

### 3. Typography

Start from the 15-role type scale. Change `fontFamily` and weights to express
the brand before changing sizes, line heights, or letter spacing. Expressive adds
an emphasized variant of every role.

Read `references/typography.md` for the role table and selection rules.

Completion: no inline `fontSize = …` or bare `TextStyle(...)` in feature code;
every text style comes from `MaterialTheme.typography`; layout survives 200%
font scale.

### 4. Shape

Override the shape scale, not individual components. Expressive adds
`largeIncreased`, `extraLargeIncreased`, and `extraExtraLarge`, plus expressive
polygon shapes that can morph.

Read `references/shape-elevation.md` for the dp scale and the `MaterialShapes`
API.

Completion: component shapes resolve to `MaterialTheme.shapes`; custom
`RoundedCornerShape` appears only where the brand genuinely departs from the
scale.

### 5. Elevation

Prefer tonal elevation and the `surfaceContainer*` roles. Reach for shadow only
for things that float above the UI (menus, dialogs, FABs).

Read `references/shape-elevation.md` for the level table.

Completion: no ad-hoc `Modifier.shadow(...)`; each raised surface has a reason
and uses a level from the table.

### 6. Motion

Choose one motion scheme for the theme. Animate position, bounds, and shape with
**spatial** specs; animate color and alpha with **effects** specs. Pick
fast/default/slow by the size and frequency of the change, never by feel.

Read `references/motion.md` for the spring token table, transition patterns, and
the API map.

Completion: no magic `tween(300)` in feature code; every animation resolves to
`MaterialTheme.motionScheme`; interrupted animations do not snap; the UI remains
usable with the system animator scale at zero.

### 7. Prove it

Add previews for light, dark, and 200% font scale. Add a screenshot test for
each core screen. Toggle dynamic color off and on.

Completion: previews and tests exist for every scheme; a reviewer can see the
foundation without running the app.

## Rules

| Rule | Why |
| --- | --- |
| Color by role, never by value. | Dynamic color and dark mode only work when components read roles. |
| Type by role, never by size. | The scale keeps hierarchy consistent across screens and font scales. |
| Shape from the scale. | Components share one silhouette language; overrides drift. |
| Space on a 4dp grid. | Consistent rhythm, aligned baselines, and no eyeballed 13dp. |
| Tonal elevation before shadow. | M3 encodes depth in surface tone; shadows are for floating layers. |
| Motion from the scheme, not the clock. | `MotionScheme` is theme-aware, interruptible, and consistent with components. |
| Spatial for bounds, effects for color. | Spatial springs overshoot; color and alpha must not. |
| One motion per relationship. | Animating parent and child together multiplies travel and reads as noise. |
| State layers only on custom surfaces. | Built-in components already implement hover, focus, press, and drag. |
| Expressive is a per-surface decision. | A bouncy hero next to a utilitarian form reads as two apps. |
| Theme once, override narrowly. | Deep `MaterialTheme` calls hide the real token values. |

## Core API

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

private val LightScheme = lightColorScheme(/* complete role set */)
private val DarkScheme = darkColorScheme(/* complete role set */)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        motionScheme = MotionScheme.expressive(), // or MotionScheme.standard()
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

// Read motion anywhere below the theme:
val scheme = MaterialTheme.motionScheme
```

`MaterialTheme` takes the same four parameters and is enough when you are not
using expressive defaults. Access the current scheme with
`MaterialTheme.motionScheme`.

## Iconography

- Use Material Symbols, not hand-drawn vectors, unless the brand has its own set.
- Default size 24dp; 20dp for dense UI; 40dp for feature or empty-state icons.
- Tint with `LocalContentColor` or `onSurfaceVariant` for secondary icons. Do not
  assign arbitrary colors.
- Use one style per surface: filled or outlined, not both.
- `contentDescription = null` when the icon repeats adjacent text; a short action
  label otherwise. Icon-only controls need a 48dp target.
- Icons are not buttons. Wrap them in `IconButton` or a component that owns the
  interaction state.

## Version guard

- `MaterialTheme.motionScheme` and `MaterialExpressiveTheme` require a current
  material3 release. If the resolved version lacks them, stay on
  `MaterialTheme` with color, type, and shape, and use `tween`/`spring` directly
  until the dependency is upgraded.
- `MaterialShapes`, `Morph`, `toShape()`, emphasized type roles, and expressive
  components need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
- When a signature matters, check
  <https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary>
  rather than guessing.

## Sources

- <https://developer.android.com/develop/ui/compose/designsystems/material3>
- <https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary>
- <https://m3.material.io/>

Token values and API availability were verified on 2026-10-28 against
`androidx.compose.material3.tokens` (`MotionTokens`, `StandardMotionTokens`,
`ExpressiveMotionTokens`, `ShapeTokens`) and the Material Web
`_md-sys-motion.scss` token file. Material 3 moves; when a signature matters,
check the API reference before compiling.

## References

| File | Read when |
| --- | --- |
| `references/color.md` | Choosing roles, generating a scheme, adding dynamic color, checking contrast. |
| `references/typography.md` | Picking a type role, loading a custom font, handling font scale. |
| `references/shape-elevation.md` | Mapping shapes to components, using expressive polygons, choosing an elevation level. |
| `references/motion.md` | Adding any animation or transition, tuning springs, matching a Material motion pattern. |
| `references/state-layers.md` | Building a custom interactive surface, focus rings, ripples, disabled states. |
| `references/layout-spacing.md` | Setting margins, grouping, reading width, density, or inset handling. |
| `references/expressive.md` | Adopting M3 Expressive deliberately instead of piecemeal. |
