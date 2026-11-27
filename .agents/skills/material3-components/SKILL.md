---
name: material3-components
description: Choose and compose Material 3 components in Jetpack Compose — buttons
  and FABs, app bars and navigation, cards, lists and chips, text fields and
  pickers, dialogs, sheets, snackbars, progress, and their accessibility. Use when
  building a screen, deciding which M3 component or variant fits an intent, or
  fixing component states, slots, and semantics. Use material3-foundation for
  tokens and motion, and material3-audit to review existing UI.
metadata:
  last-updated: '2026-10-28'
  keywords:
  - Jetpack Compose
  - Material 3
  - Material components
  - buttons
  - navigation
  - bottom sheet
  - accessibility
---

# Material 3 components

A component is a contract: intent, emphasis, states, slots, and semantics. The
right family with the wrong variant is still a design bug.

## Workflow

1. **Name the intent.** Act, navigate, contain, input, inform, or overlay.
2. **Pick the family** from the intent table.
3. **Pick the variant by emphasis**, not by appearance.
4. **Compose with defaults and slots.** Reach for `*Defaults` only when the theme
   cannot express the change.
5. **Wire state and events.** Hoist state; keep the content composable stateless.
6. **Check semantics.** Label, role, state, and touch target.
7. **Preview the states.** Enabled, pressed, selected, disabled, loading, error,
   empty, long text, 200% font scale.

## Intent → family

| Intent | Family |
| --- | --- |
| Trigger an action | Button variants, FAB, icon button |
| Move between destinations | Navigation bar, rail, drawer, tabs |
| Title and actions for a screen | Top app bar, bottom app bar |
| Group related content | Card, list item, carousel |
| Filter or select attributes | Chips, segmented buttons |
| Collect text or a choice | Text field, checkbox, radio, switch, slider, picker |
| Confirm or block | Dialog, bottom sheet |
| Report a transient result | Snackbar |
| Show progress | Progress or loading indicator |
| Explain a control | Tooltip, supporting text |
| Dismiss or reorder an item | Swipe to dismiss, drag handle |
| Refresh content | Pull to refresh |

## Emphasis ladder

For actions, emphasis is a scarce resource:

```
FAB  >  filled button  >  filled tonal  >  outlined  >  text button
```

- One primary action per region. A screen with two filled buttons has no primary
  action.
- Use a text button for a secondary action next to a filled one, not a second
  filled button.
- FAB is for the single most important action of a screen, not for navigation or
  for a toolbar.
- Icon buttons are for compact, repeated, or well-known actions. Label them for
  accessibility.

## Rules

| Rule | Because |
| --- | --- |
| Prefer the built-in component over `Box` + `clickable`. | You inherit states, semantics, shape, motion, and touch targets. |
| Override with `*Defaults`, not by rebuilding. | Defaults stay theme-aware across versions. |
| Use slots for content. | The component owns padding, typography, and color. |
| Consume `Scaffold`'s `innerPadding`. | Otherwise content hides under bars and insets. |
| Hoist state to the caller. | The screen owns truth; the component stays previewable and testable. |
| One scroll container per axis. | Nested same-axis scrolling traps gestures. |
| Do not add alpha to express state. | The component's color roles already encode it. |
| Every icon-only control has a label. | TalkBack otherwise announces "button". |
| Keep touch targets at least 48dp. | M3 components enforce it; custom surfaces must too. |
| Match platform back behavior. | Sheets and dialogs dismiss on back; do not intercept it. |

## Component map

| Family | Components |
| --- | --- |
| Actions | `Button`, `FilledTonalButton`, `OutlinedButton`, `TextButton`, `ElevatedButton`, `FloatingActionButton`, `SmallFloatingActionButton`, `LargeFloatingActionButton`, `ExtendedFloatingActionButton`, `IconButton`, `FilledIconButton`, `FilledTonalIconButton`, `OutlinedIconButton`, `SplitButton`, `ButtonGroup`, `ToggleButton` |
| Navigation | `TopAppBar`, `CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`, `AppBarRow`, `AppBarColumn`, `BottomAppBar`, `NavigationBar`, `NavigationRail`, `ModalNavigationDrawer`, `PermanentNavigationDrawer`, `DismissibleNavigationDrawer`, `PrimaryTabRow`, `SecondaryTabRow`, `Tab`, `FloatingToolbar` |
| Containers | `Card`, `ElevatedCard`, `OutlinedCard`, `ListItem`, `AssistChip`, `FilterChip`, `InputChip`, `SuggestionChip`, `HorizontalMultiBrowseCarousel`, `HorizontalCenteredHeroCarousel`, `Badge`, `HorizontalDivider`, `VerticalDivider` |
| Input | `TextField`, `OutlinedTextField`, `SearchBar`, `DockedSearchBar`, `Checkbox`, `RadioButton`, `Switch`, `Slider`, `RangeSlider`, `DatePicker`, `DateRangePicker`, `DatePickerDialog`, `TimePicker`, `TimeInput`, `DropdownMenu`, `ExposedDropdownMenuBox` |
| Feedback | `Snackbar`, `SnackbarHost`, `AlertDialog`, `BasicAlertDialog`, `ModalBottomSheet`, `BottomSheetScaffold`, `LinearProgressIndicator`, `CircularProgressIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`, `LoadingIndicator`, `ContainedLoadingIndicator`, `TooltipBox`, `PullToRefreshBox`, `SwipeToDismissBox` |
| Structure | `Scaffold`, `Surface`, `MaterialTheme`, `ProvideTextStyle`, `LocalContentColor` |

Several of these (`TopAppBar` variants, `SearchBar`, `ModalBottomSheet`,
`DatePicker`, `TimePicker`, `TooltipBox`, `PullToRefreshBox`, `SwipeToDismissBox`,
`Carousel`, expressive components) require `@OptIn(ExperimentalMaterial3Api::class)`
or `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` in the current release.
Check the signature before compiling; do not assume an API is stable.

## Screen skeleton

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    uiState: InboxUiState,
    onOpen: (MessageId) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title) },
                actions = {
                    IconButton(onClick = { /* search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* compose */ }) {
                Icon(Icons.Default.Edit, contentDescription = "Compose")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = innerPadding, // consume it, don't ignore it
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                ListItem(
                    headlineContent = { Text(message.subject) },
                    supportingContent = { Text(message.preview) },
                    leadingContent = { /* avatar */ },
                    modifier = Modifier.clickable { onOpen(message.id) },
                )
            }
        }
    }
}
```

`Scaffold` is a layout contract, not a component choice. Pass its padding to the
one scrollable that owns the screen content so content can draw under bars while
still being reachable.

## References

| File | Read when |
| --- | --- |
| `references/actions.md` | Choosing a button, FAB, icon button, split button, or segmented control. |
| `references/navigation.md` | Building app bars, navigation bar/rail/drawer, tabs, and back behavior. |
| `references/containers.md` | Cards, list items, chips, carousels, dividers, badges. |
| `references/inputs.md` | Text fields, search, checkboxes, switches, sliders, pickers, menus. |
| `references/feedback.md` | Dialogs, sheets, snackbars, progress, loading, tooltips, swipe and refresh. |
| `references/accessibility.md` | Semantics, labels, focus order, touch targets, and testing. |
