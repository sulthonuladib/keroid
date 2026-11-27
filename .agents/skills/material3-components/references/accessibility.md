# Accessibility

Accessibility is part of the component contract, not a later pass. Most of it
comes free when you use M3 components and their slots; the rest is labeling and
layout discipline.

## Labels

```kotlin
Icon(Icons.Default.Search, contentDescription = "Search")

Icon(Icons.Default.Star, contentDescription = null) // decorative, next to a label

IconButton(onClick = onClose) {
    Icon(Icons.Default.Close, contentDescription = "Close")
}
```

- `contentDescription = null` for decorative icons that repeat adjacent text.
- A short action label for icon-only controls: "Search", "Close", "Add to
  favorites". Do not include the control type; TalkBack adds "button".
- Describe the action, not the glyph. "Delete" beats "trash can".
- For a toggle, describe the action the tap performs and let the state be
  announced separately: "Add to favorites" / "Remove from favorites".
- Image content that carries meaning needs a description; a purely stylistic
  image is `null`.

## Semantics

```kotlin
Modifier.semantics {
    role = Role.Button
    stateDescription = if (enabled) "On" else "Off"
    heading()
}
```

| Property | Use |
| --- | --- |
| `role` | Identify custom controls (`Button`, `Checkbox`, `Switch`, `Tab`, `Image`) |
| `stateDescription` | Human-readable state for a custom control |
| `heading()` | Mark section titles so screen readers can jump |
| `contentDescription` | Only when no visible text labels the element |
| `customActions` | Expose actions that are not a simple click |
| `liveRegion` | Announce changing status without moving focus |
| `testTag` | Test automation only; it is not announced |

- Merge a compound row into one node when it acts as one control:
  `Modifier.semantics(mergeDescendants = true) { role = Role.Button }`.
- Do not set a `contentDescription` on a container that already has child text;
  it duplicates or hides the children.
- Use `liveRegion = LiveRegionMode.Polite` for a status line that updates
  (sync result, validation summary). Reserve assertive announcements for
  errors that interrupt.

## Touch targets

- Minimum 48×48dp for anything interactive.
- M3 components enforce it with `minimumInteractiveComponentSize()`. Custom
  surfaces must apply it or size themselves.
- Visual size can be smaller than the target; the extra space is invisible but
  tappable.
- Keep at least 8dp between adjacent targets so a near-miss does not hit the
  wrong control.
- Do not shrink a target to fit a dense layout; reduce the content instead.

```kotlin
Box(
    Modifier
        .minimumInteractiveComponentSize()
        .size(32.dp)
        .clickable(role = Role.Button, onClick = onClick),
) { /* icon */ }
```

## Contrast and color

- Text 4.5:1, large text and icons 3:1, focus indicators 3:1.
- Never encode state in color alone: selected needs an icon, check, or shape
  change too.
- Error is not just red; add an icon and a message.
- See the color reference in the `material3-foundation` skill for the role pairs.

## Font scale and layout

- All text in `sp`; all containers flexible enough to grow.
- At 200%, rows wrap or grow; nothing clips or overlaps.
- Avoid fixed heights for text containers. Use `heightIn(min = …)`.
- Truncation needs an accessible alternative to the full value.
- Test with the largest display size and the bold-text setting.

## Focus and keyboard

- Every interactive element is reachable by Tab/D-pad and activatable with
  Enter/Space.
- Focus order follows composition order; do not reorder with `traversalIndex`
  unless the visual order genuinely differs.
- Focus is always visible and high-contrast.
- Dialogs and sheets trap focus while open and restore it on dismiss.
- Provide keyboard shortcuts for high-frequency actions on desktop/tablet; see
  the `adaptive` skill.

## Motion

- Compose animations honor the system animator duration scale.
- Never make motion the only signal that something changed.
- Avoid parallax, autoplay, and looping decoration in essential flows.
- Gate custom frame loops on `ValueAnimator.areAnimatorsEnabled()`.

## Testing

Manual:

1. TalkBack: every control has a name, role, and state. Navigate a full task
   without sight.
2. Switch access: every control is reachable in a sensible order.
3. Keyboard: complete the primary task without a pointer.
4. Font scale 200% and display size largest.
5. Android Accessibility Scanner on the main screens.

Automated (Compose UI tests):

```kotlin
composeTestRule.onNodeWithContentDescription("Search").assertHasClickAction()

composeTestRule
    .onNodeWithText("Notifications")
    .assertIsToggleable()
    .assertIsOn()
```

Use `assertIsSelected`, `assertIsOn`, `assertIsEnabled`, and
`assertContentDescriptionEquals`. Prefer matching by visible text or
content description over `testTag` so the test also proves the label exists.

## Common mistakes

| Mistake | Fix |
| --- | --- |
| Every icon described, including decorative ones | `null` when adjacent text says it |
| `contentDescription = "Button"` | Describe the action |
| Custom `Box` clickable with no role or label | `role` + label + touch target |
| State in color only | Icon or text plus color |
| `Modifier.size(24.dp)` on an icon button | Keep the 48dp target, shrink the glyph |
| Dialog does not restore focus | Let the M3 dialog handle dismissal |
| Testing only with `testTag` | Also assert the accessible label |
