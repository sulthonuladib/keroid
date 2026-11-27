# Actions

## Buttons

| Component | Emphasis | Use |
| --- | --- | --- |
| `Button` | High | The primary action of a region |
| `FilledTonalButton` | Medium-high | A secondary action that still needs weight |
| `OutlinedButton` | Medium | An alternative action, especially next to a filled one |
| `TextButton` | Low | A tertiary action, inline action, or dialog dismissal |
| `ElevatedButton` | Medium | A button on a busy or image background |

```kotlin
Button(onClick = onSave) {
    Icon(Icons.Default.Save, contentDescription = null) // decorative
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text("Save")
}
```

Rules:

- One filled button per region. Pair it with text or outlined buttons, not
  another filled button.
- Button labels are verbs and fit one line. Put the object in the screen, not the
  label ("Save", not "Save changes to draft").
- The component owns height (40dp), shape (`full`), and touch target (48dp via
  `minimumInteractiveComponentSize`). Do not wrap it in padding that shrinks the
  target.
- Loading: keep the button in place, disable it, and replace the label with a
  progress indicator or show progress beside it. Do not change its width
  mid-press.
- Disabled is not an explanation. If the action is unavailable, show why.

## Icon buttons

```kotlin
IconButton(onClick = onSearch) {
    Icon(Icons.Default.Search, contentDescription = "Search")
}

IconToggleButton(checked = starred, onCheckedChange = onStar) {
    Icon(
        imageVector = if (starred) Icons.Filled.Star else Icons.Outlined.Star,
        contentDescription = if (starred) "Remove from favorites" else "Add to favorites",
    )
}
```

- `IconButton` is for a well-known compact action repeated in bars and rows.
- `FilledIconButton` / `FilledTonalIconButton` raise emphasis; use them for a
  selected or primary compact action.
- `IconToggleButton` carries the checked state. Do not fake a toggle with an
  `IconButton` whose icon swaps.
- Every icon-only control needs a label. The label describes the action, not the
  glyph; TalkBack appends "button".

## FAB

| Component | Size | Use |
| --- | --- | --- |
| `SmallFloatingActionButton` | 40dp | Dense layouts, secondary floating action |
| `FloatingActionButton` | 56dp | The default single screen action |
| `LargeFloatingActionButton` | 96dp | Expressive hero action |
| `ExtendedFloatingActionButton` | 56dp tall | When the action needs a label |

```kotlin
ExtendedFloatingActionButton(
    onClick = onCompose,
    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
    text = { Text("Compose") },
)
```

- One FAB per screen. It is the highest-emphasis action, not a shortcut for
  "add".
- The FAB must not cover the last list item or the navigation bar; let
  `Scaffold` place it and add content padding.
- Destructive actions belong in an overflow menu or a dialog, not on a FAB.
- On scroll, FABs may hide; restore them when scrolling up, not on every frame.

## Split button and button groups

Expressive additions, `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

- `SplitButton` — a primary action plus a menu of related actions. Use when the
  primary is obvious but alternatives exist.
- `ButtonGroup` + `ToggleButton` — a row of related actions with connected
  shapes. Use for view modes or formatting, not for navigation.
- Keep the group to 2–5 items. Beyond that, use a menu.

## Segmented buttons

```kotlin
SingleChoiceSegmentedButtonRow {
    options.forEachIndexed { index, label ->
        SegmentedButton(
            selected = index == selectedIndex,
            onClick = { selectedIndex = index },
            shape = SegmentedButtonDefaults.itemShape(index, options.size),
        ) { Text(label) }
    }
}
```

- Single-choice for a mode switch (Day/Week/Month).
- Multi-choice for attribute filters that are not chips.
- 2–5 options. Segmented buttons are not tabs; they change a mode in place.
- The selected segment needs more than color: the component also uses a check or
  a filled container.

## Common mistakes

| Mistake | Fix |
| --- | --- |
| Two filled buttons side by side | One filled, one text/outlined |
| `Button` wrapped in `Modifier.size(32.dp)` | Let the component own its size |
| `IconButton` with `contentDescription = null` | Describe the action |
| FAB used for "next" navigation | A button in the content or bottom bar |
| Loading by swapping the whole button for a spinner | Keep layout stable; disable and show progress |
| `clickable` on a `Row` styled like a button | Use the button component |
| Segmented control with 8 options | A dropdown menu |

## Verify

- The primary action is unambiguous at a glance.
- Every action is reachable with TalkBack and a keyboard.
- Pressed feedback appears on touch-down, not on release.
- Labels survive 200% font scale without truncating the verb.
