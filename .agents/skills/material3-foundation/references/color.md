# Color

## The model

Six key colors (primary, secondary, tertiary, neutral, neutral-variant, error)
generate tonal palettes of thirteen tones. Roles map tones to jobs. Components
consume roles; your code consumes roles. Nothing consumes a tone directly.

```
key color → tonal palette (tone 0–100) → role (e.g. primaryContainer) → component
```

Because the mapping is indirect, a role survives a brand change, a dark theme,
and wallpaper-derived dynamic color. A hex value does not.

## Roles

| Group | Roles | Job |
| --- | --- | --- |
| Primary accent | `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `inversePrimary` | Highest-emphasis actions, active navigation, selection. |
| Secondary accent | `secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer` | Quieter emphasis: filter chips, tonal controls, supporting actions. |
| Tertiary accent | `tertiary`, `onTertiary`, `tertiaryContainer`, `onTertiaryContainer` | Contrasting accent that balances primary and secondary. |
| Error | `error`, `onError`, `errorContainer`, `onErrorContainer` | Destructive actions and error messaging. |
| Base surface | `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `surfaceTint` | Screen background and default text. `surfaceTint` is for tonal elevation, not for painting. |
| Surface containers | `surfaceDim`, `surfaceBright`, `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` | Nested and raised surfaces: sheets, menus, cards, sidebars. |
| Outline | `outline`, `outlineVariant` | Component boundaries and dividers. `outlineVariant` is the quieter one. |
| Inverse | `inverseSurface`, `inverseOnSurface`, `inversePrimary` | Surfaces that invert the theme: snackbars, tooltips, some scrims. |
| Fixed | `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant` (and secondary/tertiary equivalents) | Same value in light and dark. Use only when an element must keep its identity across themes. |
| Scrim | `scrim` | Behind modal content. Usually consumed with alpha by the component. |

`background`/`onBackground` still exist for compatibility. Prefer
`surface`/`onSurface` for new code: M3 surfaces are the themed canvas.

## Choosing a role

Answer two questions: how much emphasis, and is it a container or content?

| Need | Role |
| --- | --- |
| Main action on a screen | `primary` container with `onPrimary` content |
| Selected item in a list or nav | `secondaryContainer` / `onSecondaryContainer`, or the component's selected colors |
| Prominent but not primary (e.g. a highlighted card) | `primaryContainer` / `onPrimaryContainer` |
| Supporting accent | `tertiaryContainer` / `onTertiaryContainer` |
| Screen background | `surface` |
| Card or section on the background | `surfaceContainerLow` or `surfaceContainer` |
| Menu, sheet, or floating panel | `surfaceContainerHigh` or the component default |
| Body text | `onSurface` |
| Secondary text, captions, metadata | `onSurfaceVariant` |
| Divider | `outlineVariant` |
| Input border (resting) | `outline` |
| Input border (focused) | `primary` |
| Snackbar | `inverseSurface` / `inverseOnSurface` |
| Destructive text or icon | `error` |
| Error container | `errorContainer` / `onErrorContainer` |

Never pair an `on*` role with a container it does not belong to. `onPrimary` is
readable on `primary`, not on `surface`.

## Contrast

- Body text: 4.5:1 against its background.
- Large text (≥ 18sp regular or ≥ 14sp bold): 3:1.
- Icons, borders, focus rings, and other non-text UI: 3:1.
- Disabled content is exempt in WCAG, but must still be legible enough to
  understand the affordance.

The generated `on*` pairs meet these targets. Custom overrides may not. Check
any hand-picked pair with a contrast checker, and prefer changing the role over
changing the color.

## Schemes

Generate a full scheme from a source color with
[Material Theme Builder](https://material.io/material-theme-builder), or hand
write one. A complete scheme is easier to reason about than scattered defaults.

```kotlin
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val BrandLight = lightColorScheme(
    primary = Color(0xFF476810),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7F089),
    onPrimaryContainer = Color(0xFF102000),
    secondary = Color(0xFF57633F),
    // … every role the app uses. Unset roles fall back to baseline M3,
    // which can look unrelated to the brand.
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFACD370),
    onPrimary = Color(0xFF213600),
    primaryContainer = Color(0xFF324F00),
    onPrimaryContainer = Color(0xFFC7F089),
    // …
)
```

Do not define only `primary`. A partial scheme mixes brand colors with the
baseline palette and produces surfaces and containers that do not belong
together.

## Dynamic color

Dynamic color derives a scheme from the user's wallpaper on Android 12+. Keep a
brand scheme as the fallback and offer the choice if the product cares about
brand expression.

```kotlin
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberScheme(darkTheme: Boolean = isSystemInDarkTheme()): ColorScheme {
    val context = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when {
        dynamicAvailable && darkTheme -> dynamicDarkColorScheme(context)
        dynamicAvailable && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }
}
```

Dynamic color changes what every role resolves to. Any screen that hardcodes a
color breaks here first.

Expressive has matching builders (`expressiveLightColorScheme()`,
`expressiveDarkColorScheme()`). They are experimental; opt in and verify the
signature in the API reference.

## Fixed roles

`primaryFixed`, `secondaryFixed`, and `tertiaryFixed` keep the same value in
light and dark. Use them when an element must not flip — a brand badge on media,
or a colored chip whose meaning depends on the color. They are a deliberate
exception, not a shortcut for "I liked the light color".

## Anti-patterns

| Smell | Replace with |
| --- | --- |
| `Color(0xFF6750A4)` in a composable | The role that job needs |
| `Color.Black` / `Color.White` for text | `onSurface` / `onPrimary`, etc. |
| `colorScheme.primary.copy(alpha = 0.6f)` for secondary text | `onSurfaceVariant`, or a disabled role |
| `surfaceTint` as a background | `surfaceContainer*` |
| `if (isDark) …` inside a screen | A role that already flips |
| Alpha on a container to fake elevation | A `surfaceContainer*` level |
| A one-off `Color` for "just this card" | A local `MaterialTheme` override with a role |

## Verify

- Switch system dark mode and dynamic color; no screen keeps a hardcoded hue.
- Screenshot each scheme.
- Run Android Accessibility Scanner or an equivalent contrast check on the
  primary screens.
