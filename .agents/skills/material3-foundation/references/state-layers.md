# Interaction state layers

## States

| State | Signal | Visible change |
| --- | --- | --- |
| Enabled | Default | Base container and content |
| Hovered | Pointer over | 8% content-color layer |
| Focused | Keyboard / D-pad / switch access | 10% layer plus a focus indicator |
| Pressed | Pointer or touch down | 10% layer, often with ripple |
| Dragged | Active drag | 16% layer plus elevation or scale |
| Selected | Persistent choice | Different container role and a non-color cue |
| Disabled | Unavailable | 38% content, 12% container |

These are the M3 defaults. Built-in components already implement them; do not
reproduce them on top of `Button` or `NavigationBarItem`. The rules below apply
to a surface you compose yourself.

## Custom interactive surface

```kotlin
@Composable
fun CustomTile(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val stateLayer = when {
        pressed -> 0.10f
        hovered -> 0.08f
        focused -> 0.10f
        else -> 0f
    }

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        // State layer sits above the container and below the content.
        if (stateLayer > 0f) {
            Box(Modifier.matchParentSize().background(contentColor.copy(alpha = stateLayer)))
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
```

Selected is not a state layer. It changes the container role and adds a cue that
does not depend on color: a check icon, a leading indicator, or a different
shape. Color alone fails color-vision accessibility.

## Ripple and focus ring

- `ripple()` is the default indication for `clickable` inside an M3 component.
  Pass it explicitly on custom surfaces.
- `LocalRippleConfiguration` overrides ripple color or disables it for a
  subtree (`provides null`). It is an escape hatch, not a theming mechanism.
- Focus indication can be an opacity overlay or an inset focus ring:

```kotlin
CompositionLocalProvider(
    LocalRippleThemeConfiguration provides RippleDefaults.InsetFocusRingThemeConfiguration,
) {
    // M3 components in this subtree draw inset focus rings.
}
```

- A focus indicator must have 3:1 contrast against its surroundings. If the
  container is already `primary`, a `primary` ring disappears.

## Disabled

```kotlin
Box(
    Modifier
        .alpha(0.38f)            // content
        .background(container.copy(alpha = 0.12f))
) { /* … */ }
```

- Disabled content is 38%; the container is 12%.
- Do not use disabled as a way to hide a feature. If the user cannot act, say
  why next to the control.
- Keep the label legible; a 38% label on a 12% container is the floor, not a
  target.

## Keyboard and focus

- Every interactive surface is reachable with Tab/D-pad and activatable with
  Enter/Space.
- Set `role` (`Role.Button`, `Role.Checkbox`, `Role.Tab`, etc.) so TalkBack and
  switch access announce the right thing.
- Use `focusable()` and `focusRequester()` deliberately; avoid stealing focus on
  load.
- Do not remove focus indication to "clean up" the design.

## Anti-patterns

| Smell | Replace with |
| --- | --- |
| Manual hover/press overlay on a `Button` | The component's own states |
| `Modifier.clickable` without indication on a control | `ripple()` or an intentional alternative |
| Selected shown only by hue | Role change plus icon/indicator |
| `alpha = 0.5f` as the disabled treatment | 38% content / 12% container |
| Focus ring drawn in the same color as the container | A contrasting role |
| Gesture-only interaction | A visible control with semantics |

## Verify

- Drive the screen with a keyboard only; focus is always visible.
- Enable TalkBack: role, state, and label are announced.
- Test hover with a mouse on a tablet/desktop form factor.
- Check pressed feedback on a slow device; it must appear on touch-down.
