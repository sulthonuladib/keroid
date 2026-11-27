# Containers

## Cards

| Component | Depth cue | Use |
| --- | --- | --- |
| `Card` | Filled container | Default grouping |
| `ElevatedCard` | Shadow | A card that must float above a busy background |
| `OutlinedCard` | Border | A card on a surface where tone alone is too subtle |

```kotlin
Card(
    onClick = { onOpen(item.id) },
    modifier = Modifier.fillMaxWidth(),
) {
    Column(Modifier.padding(16.dp)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        Text(
            item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- Use the clickable `Card` overload, not a `Card` wrapped in `Modifier.clickable`.
  The overload supplies ripple, semantics, and the pressed state.
- One card, one idea. A card that contains a list of cards is a layout smell.
- Cards are not buttons. If the whole card is an action, the card is the button;
  do not put another filled button inside it.
- Do not add `Modifier.shadow`; choose the card variant and let tonal elevation
  work.

## List items

```kotlin
ListItem(
    headlineContent = { Text(message.subject) },
    supportingContent = { Text(message.preview, maxLines = 2) },
    overlineContent = { Text(message.folder.uppercase()) },
    leadingContent = { Avatar(message.sender) },
    trailingContent = { Text(message.time) },
    modifier = Modifier.clickable { onOpen(message.id) },
)
```

Slots:

| Slot | Content |
| --- | --- |
| `overlineContent` | Small label above the headline |
| `headlineContent` | Primary line |
| `supportingContent` | Secondary line |
| `leadingContent` | Avatar, icon, or checkbox |
| `trailingContent` | Metadata, switch, or menu |

- Use `ListItem` instead of building a row from scratch. It owns height, padding,
  alignment, and text styles.
- One line of headline, at most two of supporting text in a list.
- The click target is the whole row. If the row has an inner control (switch,
  checkbox, menu), make the row toggle and give the inner control its own
  semantic action.
- Dividers between rows are optional; use `HorizontalDivider` with
  `outlineVariant`, not a custom `Box`.
- For dense lists with many rows, prefer a plain row with explicit spacing over
  `ListItem` if the three-line height is too tall; do not shrink `ListItem`
  below its touch target.

## Chips

| Chip | Meaning | Interactive |
| --- | --- | --- |
| `AssistChip` | Helps complete a task | Yes, triggers an action |
| `FilterChip` | Narrows a result set | Yes, toggles selected |
| `InputChip` | A value the user entered | Yes, can be removed |
| `SuggestionChip` | A suggested query or action | Yes, triggers |

```kotlin
FilterChip(
    selected = selected,
    onClick = { selected = !selected },
    label = { Text("Unread") },
    leadingIcon = if (selected) {
        { Icon(Icons.Default.Check, contentDescription = null) }
    } else null,
)
```

- Chips are not buttons. Use a button for a primary action.
- Filter chips show selected state with a check plus a container change, not hue
  alone.
- Input chips need a remove affordance with a label ("Remove …").
- Keep chip labels to a few words; a sentence is a list item.

## Carousel

Expressive, experimental. Use a carousel for a browsable set of large visual
items, not for a form or a short list.

- `HorizontalMultiBrowseCarousel` — several items visible, one focal.
- `HorizontalCenteredHeroCarousel` — one large hero item centered.
- Provide stable keys and paging semantics; a carousel is a lazy list.
- Do not auto-advance. Motion on a timer steals control and burns battery.
- Always pair with a visible scroll affordance (peek of the next item).

Check the current signature; the carousel API has changed across releases.

## Badges and dividers

```kotlin
BadgedBox(badge = { Badge { Text("9") } }) {
    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
}
HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
```

- A badge is a count or a dot, not a status message. Use a chip or a banner for
  anything longer.
- Cap counts ("99+") and give the icon a content description that includes the
  count.
- Dividers separate list sections. Inside a card, prefer spacing.
- `Divider` is deprecated; use `HorizontalDivider` / `VerticalDivider`.

## Common mistakes

| Mistake | Fix |
| --- | --- |
| Clickable `Card` wrapped in `Modifier.clickable` | Use the `onClick` overload |
| Card with a shadow and a border and tonal elevation | Pick one depth cue |
| Row of `Text`s pretending to be a list item | `ListItem` |
| `FilterChip` selected only by color | Check icon plus container change |
| Carousel for three items | A row or a list |
| Badge with a sentence | A chip or banner |
| Divider as a heavy `Box` | `HorizontalDivider` with `outlineVariant` |

## Verify

- Long titles and 200% font scale do not clip list rows.
- The whole row is clickable and announces its role and content.
- A card with an inner control has two distinct accessibility actions.
