# Typography

## The scale

15 roles in five groups, three sizes each. Sizes are the M3 defaults; treat them
as the baseline, not as suggestions to tune per screen.

| Role | Size / line height | Weight | Typical job |
| --- | --- | --- | --- |
| `displayLarge` | 57 / 64 | Regular | Hero numbers, marketing moments |
| `displayMedium` | 45 / 52 | Regular | Large hero |
| `displaySmall` | 36 / 44 | Regular | Short hero |
| `headlineLarge` | 32 / 40 | Regular | Page title on large screens |
| `headlineMedium` | 28 / 36 | Regular | Page title |
| `headlineSmall` | 24 / 32 | Regular | Section title |
| `titleLarge` | 22 / 28 | Medium | App bar title, prominent card title |
| `titleMedium` | 16 / 24 | Medium | Card title, list section |
| `titleSmall` | 14 / 20 | Medium | Compact title |
| `bodyLarge` | 16 / 24 | Regular | Primary reading text |
| `bodyMedium` | 14 / 20 | Regular | Dense body text |
| `bodySmall` | 12 / 16 | Regular | Supporting text, captions |
| `labelLarge` | 14 / 20 | Medium | Button labels |
| `labelMedium` | 12 / 16 | Medium | Navigation labels, chips |
| `labelSmall` | 11 / 16 | Medium | Badges, overlines |

Weights: display, headline, and body are regular; title and label are medium.
Keeping that contrast is what makes the scale read as hierarchy rather than as
seven font sizes.

## Choosing a role

| Content | Role |
| --- | --- |
| Screen title | `headlineMedium` or `headlineSmall` |
| App bar title | `titleLarge` (component default) |
| Section header | `titleMedium` |
| Card title | `titleMedium` |
| Body copy | `bodyLarge`; `bodyMedium` in dense lists |
| Metadata under a title | `bodyMedium` or `bodySmall` with `onSurfaceVariant` |
| Button and chip label | `labelLarge` |
| Navigation label | `labelMedium` |
| Badge or overline | `labelSmall` |

If the role you want does not exist, the content probably needs a different
hierarchy, not a new size.

## Emphasized styles

M3 Expressive adds an emphasized variant of every role: same metrics, heavier
weight. They exist to give a hero or a selected state more presence without
breaking the scale.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
Text(
    text = "Focus",
    style = MaterialTheme.typography.headlineSmallEmphasized,
)
```

Use at most one emphasized element per visual cluster. When you construct
`Typography` from a `FontFamily`, the emphasized roles default to their base
counterparts, so applying them is safe even before a full expressive type pass.

## Custom fonts

```kotlin
val AppFont = FontFamily(
    Font(R.font.brand_regular, FontWeight.Normal),
    Font(R.font.brand_medium, FontWeight.Medium),
    Font(R.font.brand_semibold, FontWeight.SemiBold),
)

val AppTypography = Typography(
    fontFamily = AppFont,
    // Sizes, line heights, and letter spacing stay at the M3 defaults.
)
```

Rules:

- Provide a real file for every weight the scale uses. A missing weight is
  synthesized and looks wrong at large sizes.
- Change `fontFamily`, weight, and letter spacing for brand expression before
  changing size.
- If you must change metrics, change the role definition once — all screens
  inherit it.
- Variable fonts: load with `Font(..., variationSettings = FontVariation.Settings(...))`
  when you need an axis the weight API does not expose.

## Applying type

```kotlin
Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
)
```

- Do not pass `fontSize` to `Text`. The style owns it.
- `LocalTextStyle` supplies a default inside components; use `ProvideTextStyle`
  when building your own.
- Color is separate from type. Set `color` explicitly only when the default
  (`LocalContentColor`) is wrong for the surface.

## Font scale and long text

- All text uses `sp`. `dp` text does not scale and fails accessibility.
- Test at 200% font scale. Fixed-height rows clip; use `heightIn(min = …)` and
  let content grow.
- Prefer wrapping over truncation. When truncation is required, add
  `maxLines` + `overflow = TextOverflow.Ellipsis` and make the full value
  reachable (tooltip, detail screen, or accessibility action).
- Avoid all-caps transforms for long strings; they break some scripts and
  letter-spacing assumptions.

## Anti-patterns

| Smell | Replace with |
| --- | --- |
| `fontSize = 14.sp` in a screen | `style = MaterialTheme.typography.bodyMedium` |
| `TextStyle(fontSize = …, fontWeight = …)` inline | A theme role, or a named local style |
| `fontSize = 14.dp` | `sp` |
| Separate sizes for "mobile" and "tablet" text | One scale; let layout adapt |
| `fontWeight = FontWeight.Bold` to add emphasis | The emphasized role, or a larger role |
| Hardcoded `Color.Black` text | `onSurface` / `onSurfaceVariant` |
| `lineHeight = 1.0.em` on multi-line body | The role's line height |

## Verify

- Preview at font scale 1.0 and 2.0.
- Render a screen in a right-to-left locale; no clipping or mirrored punctuation.
- Check that exactly one role is doing each hierarchical job per screen.
