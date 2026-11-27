# Shape and elevation

## Shape scale

| Token | Corner | Default use |
| --- | --- | --- |
| `extraSmall` | 4dp | Menus, snackbars, small inputs |
| `small` | 8dp | Chips, compact containers |
| `medium` | 12dp | Cards, list containers |
| `large` | 16dp | FAB, extended FAB, prominent containers |
| `largeIncreased` | 20dp | Expressive step between large and extra large |
| `extraLarge` | 28dp | Dialogs, large FABs, bottom sheet tops |
| `extraLargeIncreased` | 32dp | Expressive step above extra large |
| `extraExtraLarge` | 48dp | Expressive hero containers |
| `full` | `CircleShape` | Buttons, pills, avatars |
| `none` | `RectangleShape` | Full-bleed surfaces |

`largeIncreased`, `extraLargeIncreased`, and `extraExtraLarge` are expressive
additions. Older material3 versions only expose the first five; fall back rather
than referencing missing properties.

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

Change the scale, not individual components. If a single card needs a different
corner, the design probably has a second shape tier — add it to the theme rather
than passing a `RoundedCornerShape` at the call site.

Component defaults already resolve to the scale. `Card` uses `medium`,
`Button` uses `full`, dialogs use `extraLarge`. Overriding one component
locally is a deliberate exception.

## Expressive polygon shapes

`MaterialShapes` provides 35 predefined polygons, each a normalized
`RoundedPolygon`: `Circle`, `Square`, `Slab`, `Pill`, `Oval`, `Diamond`,
`Triangle`, `Pentagon`, `Arch`, `Arrow`, `Boom`, `Bun`, `Burst`, `ClamShell`,
`Clover4Leaf`, `Clover8Leaf`, `Cookie4Sided`, `Cookie6Sided`, `Cookie7Sided`,
`Cookie9Sided`, `Cookie12Sided`, `Fan`, `Flower`, `Gem`, `Ghostish`, `Heart`,
`Puffy`, `PuffyDiamond`, `SemiCircle`, `Slanted`, `SoftBoom`, `SoftBurst`,
`Sunny`, `VerySunny`, `PixelCircle`, `PixelTriangle`.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveBadge() {
    // toShape() is a @Composable extension and remembers the generated path.
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text("New", Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
```

Rules:

- Expressive shapes are accent, not default chrome. Use them on one focal
  element per screen.
- Keep the container's content padding generous; corners cut into content.
- Do not use a polygon where a circle or rounded rectangle communicates the
  affordance (buttons, avatars, toggles).

### Morphing

`Morph` interpolates between two `RoundedPolygon`s. Drive it with the motion
scheme so it obeys the theme.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingShape(selected: Boolean) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Clover4Leaf) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "shapeMorph",
    )
    // asCubics(progress) returns the interpolated outline. Draw it into a Path,
    // or use Morph.toPath(progress, path, startAngle).
    val cubics = morph.asCubics(progress)
    Canvas(Modifier.size(48.dp)) {
        // build and draw a path from cubics
    }
}
```

`Morph` maps curves by proximity, so pick shapes with a comparable silhouette.
A circle to a 4-leaf clover reads cleanly; a circle to a sharp triangle does not,
because there is no sensible curve correspondence.

## Elevation

M3 encodes depth in tone first. A raised surface shifts its surface tone using
`surfaceTint`; a shadow is added only when the layer truly floats.

| Level | dp | Use |
| --- | --- | --- |
| 0 | 0 | Background, flat content |
| 1 | 1 | Resting raised card, low emphasis |
| 2 | 3 | Sticky app bar, raised panel |
| 3 | 6 | FAB, menu, floating control |
| 4 | 8 | Dialog, modal panel |
| 5 | 12 | Highest transient layer |

```kotlin
Surface(
    tonalElevation = 2.dp,   // tone shift; the M3 default for most components
    shadowElevation = 0.dp,  // keep at 0 unless the surface floats
) {
    // content
}
```

- `tonalElevation` changes the container color toward `surfaceTint`. It is the
  default depth cue and works in dark mode.
- `shadowElevation` draws a shadow. Use it for menus, dialogs, sheets, and FABs,
  where the layer overlaps unrelated content.
- Do not stack `Modifier.shadow()` on top of `Surface(shadowElevation = …)`.

### Surface containers instead of elevation

Expressive favors nesting with `surfaceContainer*` roles over continuous tonal
elevation:

```kotlin
Card(colors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
)) { /* … */ }
```

Use the lowest container that separates the layer from its parent, and step up
only when a child needs more separation. The role handles light and dark; the
number is not "elevation dp".

## Anti-patterns

| Smell | Replace with |
| --- | --- |
| `RoundedCornerShape(10.dp)` at a call site | The matching `MaterialTheme.shapes` token |
| Every card with a different corner | One scale, consistent tiers |
| `Modifier.shadow(8.dp)` on a flat card | A container role or level 1 tonal elevation |
| `tonalElevation` and `shadowElevation` both high | Pick the cue the layer needs |
| Polygon shape on a button | `full` or `large` from the scale |
| Morphing unrelated silhouettes | Shapes with comparable curve structure |
| Hardcoded `Color` with a new shape | Shape changes silhouette, not palette |

## Verify

- Toggle light/dark: tonal elevation must still separate layers.
- Inspect at the largest font scale: corner radius must not clip content.
- Count focal polygon shapes per screen — one is a decision, five is decoration.
