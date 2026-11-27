# Layout and spacing

Material 3 does not ship spacing tokens, so the risk is arbitrary values. Use a
small scale and apply it everywhere.

## The 4dp grid

Every dimension is a multiple of 4dp. Component heights, icon sizes, and
padding align to the grid, which keeps text baselines and touch targets
consistent across screens.

| Step | Use |
| --- | --- |
| 2dp | Hairline separation inside a control |
| 4dp | Icon-to-label gap, tight related elements |
| 8dp | Related elements, button gap, compact padding |
| 12dp | List item vertical padding, dense card padding |
| 16dp | Default horizontal screen margin, card padding |
| 24dp | Section gap, medium/expanded screen margin |
| 32dp | Large section separation |
| 48dp | Hero spacing, empty-state breathing room |
| 64dp+ | Full-bleed feature spacing only |

Prefer 8dp multiples for rhythm and 4dp for fine alignment. Values like 13dp or
18dp are usually a sign that the layout is being tuned by eye instead of by
structure.

## Screen margins

| Window | Horizontal margin |
| --- | --- |
| Compact | 16dp |
| Medium | 24dp |
| Expanded | 24dp, with a centered content column |

Do not add margins inside a component that already has them (`Card` padding,
`ListItem` padding, `Scaffold` insets). Double padding is the most common
spacing bug in Compose.

## Grouping

| Relationship | Space |
| --- | --- |
| Icon and its label | 4–8dp |
| Label and supporting text | 2–4dp |
| Items in a list | 0 (the item owns its padding) |
| Related controls | 8dp |
| Distinct groups | 16–24dp |
| Sections | 24–32dp |
| Screen edge to first content | 16dp (compact) |

Use `Arrangement.spacedBy(…)` on a column or row instead of inserting `Spacer`s
between every item. Spacing belongs to the layout, not to the children.

```kotlin
Column(
    modifier = Modifier.padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Header()
    Body()
}
```

## Reading width

Long lines are hard to read. Cap text-heavy content:

```kotlin
Column(Modifier.widthIn(max = 640.dp)) {
    ArticleBody()
}
```

On expanded windows, cap the reading column and center it; do not stretch body
text to the full width. Multi-pane layout belongs to the `adaptive` skill.

## Touch spacing

- Interactive targets are at least 48×48dp.
- Leave at least 8dp between adjacent targets.
- Dense layouts shrink the visual control, not the target: keep the 48dp box
  and reduce the glyph or padding inside it.
- Do not put two targets so close that a thumb cannot choose between them.

## Insets

- `Scaffold` applies content window insets; consume its `innerPadding`.
- Use `Modifier.imePadding()` on forms so the keyboard does not cover the field.
- Use `Modifier.navigationBarsPadding()` / `statusBarsPadding()` only when you
  are outside `Scaffold` or drawing edge-to-edge yourself.
- Never apply the same inset twice (Scaffold plus a manual status bar padding).
- See the `edge-to-edge` skill for the system-level setup.

## Density

There is no M3 "density token". When a screen needs to be denser:

1. Reduce vertical padding within the 4dp grid (16 → 12 → 8).
2. Reduce the gap between groups (24 → 16).
3. Keep touch targets and text sizes unchanged.
4. If density still fails, the screen has too much content, not too much space.

## Anti-patterns

| Smell | Replace with |
| --- | --- |
| `13.dp`, `7.dp`, `18.dp` | A step from the 4dp scale |
| A `Spacer` between every child | `Arrangement.spacedBy(…)` |
| `padding(16.dp)` inside a card that already pads | Remove the inner padding |
| Screen margins differing per screen | One margin per window size |
| Full-width body text on a tablet | A centered, capped reading column |
| Smaller touch targets to gain space | Reduce content or padding, keep 48dp |
| Manual status bar padding plus `Scaffold` | Consume `innerPadding` once |

## Verify

- Screenshot with a 4dp grid overlay; edges land on the grid.
- Compare two screens: the same relationship uses the same gap.
- Rotate and resize: margins follow the window size, not a hardcoded value.
- Check the keyboard-open state on every form.
