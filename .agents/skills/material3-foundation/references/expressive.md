# Material 3 Expressive

Expressive is an expansion of M3, not a second design system. It adds expression
on top of the same roles and components.

## What it adds

| Area | Addition |
| --- | --- |
| Color | `expressiveLightColorScheme()` / `expressiveDarkColorScheme()` with warmer, higher-chroma defaults |
| Typography | An emphasized variant of all 15 roles |
| Shape | `largeIncreased`, `extraLargeIncreased`, `extraExtraLarge`, plus 35 `MaterialShapes` polygons and `Morph` |
| Motion | `MotionScheme.expressive()`: slower, springier spatial motion |
| Components | Button groups, split button, FAB menu, floating toolbar, loading indicator, wavy progress, carousel, app bar row/column |

## Adoption order

Adopt in this order; each step is independently shippable.

1. **Motion scheme.** Set `motionScheme = MotionScheme.expressive()` on the
   theme. This changes every component's feel at once, with no layout risk.
2. **Type.** Use one emphasized role on the hero or focal element of a screen.
3. **Shape.** Replace a focal container with an expressive polygon or a larger
   scale step.
4. **Components.** Adopt one expressive family where it replaces a weaker
   pattern: a split button for a primary action with a menu, a FAB menu for
   multiple floating actions, a loading indicator for long operations.
5. **Color.** Only if the product wants the expressive palette; a brand scheme
   is compatible.

## Theme

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            expressiveDarkColorScheme()
        } else {
            expressiveLightColorScheme()
        },
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
```

`MaterialExpressiveTheme` defaults every parameter; pass only what the app
customizes.

## Rules

- **One expressive moment per screen.** A bouncy hero, a morphing shape, and a
  FAB menu in the same viewport compete. Pick the focal one.
- **Keep utilitarian flows calm.** Settings, forms, and destructive flows read
  better with standard motion. Expressive is for discovery, celebration, and
  brand moments.
- **Expressive motion is slower.** Do not use `slowSpatialSpec()` on an
  interaction that repeats every few seconds.
- **Polygon shapes need breathing room.** Content padding and touch targets grow
  with the corner cut.
- **Opt in explicitly.** Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
  at the smallest scope that needs it, so a future API change is visible.
- **Guard by version.** If the resolved material3 version lacks an expressive
  API, degrade to the standard equivalent rather than raising `minSdk` or
  pinning an alpha build for one animation.

## Version check

Before adopting, confirm the resolved `androidx.compose.material3` version
supports the API. The safe test is compilation with the opt-in annotation; the
reference docs list availability per symbol:

- `MaterialExpressiveTheme`
- `MaterialShapes`, `toShape()`, `Morph`
- `MotionScheme.expressive()`
- emphasized typography roles
- expressive components under `androidx.compose.material3`

## Verify

- The expressive surface still passes contrast and font-scale checks.
- Motion remains interruptible and honors the system animator scale.
- The screen does not mix standard and expressive variants of the same component.
- A low-end device holds frame rate; expressive springs are not free.
