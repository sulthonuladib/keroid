# Navigation and app bars

## Top app bars

| Component | Height | Use |
| --- | --- | --- |
| `TopAppBar` | 64dp | Default title and actions |
| `CenterAlignedTopAppBar` | 64dp | Centered title, often with one action |
| `MediumTopAppBar` | 112dp | Collapsing title with more presence |
| `LargeTopAppBar` | 152dp | Collapsing hero title on top-level screens |

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
TopAppBar(
    title = { Text("Inbox") },
    navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    },
    actions = {
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
    },
    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
)
```

Scroll behaviors:

| Behavior | Effect |
| --- | --- |
| `pinnedScrollBehavior()` | Stays visible |
| `enterAlwaysScrollBehavior()` | Hides on scroll down, returns on scroll up |
| `exitUntilCollapsedScrollBehavior()` | Collapses to the small height (medium/large bars) |

- Use one bar per screen. A second bar below it is a section header, not an app
  bar.
- Title text is `titleLarge`; the component supplies it. Do not restyle it.
- Actions are icon buttons with labels. Two actions maximum before an overflow
  menu.
- `AppBarRow` / `AppBarColumn` are expressive layouts for composing multiple app
  bar elements; opt in and check the signature.

## Bottom app bar

`BottomAppBar` holds screen-level actions at the bottom. It is not a navigation
bar. Use it when actions belong to the content (editor, detail screen), and keep
it to 3–5 actions.

## Navigation bar, rail, and drawer

| Component | Window | Destinations | Placement |
| --- | --- | --- | --- |
| `NavigationBar` | Compact | 3–5 | Bottom |
| `NavigationRail` | Medium | 3–7 | Leading edge |
| `ModalNavigationDrawer` | Compact/medium | 5+ | Overlay from leading edge |
| `PermanentNavigationDrawer` | Expanded | 5+ | Always visible |
| `DismissibleNavigationDrawer` | Expanded | 5+ | Visible, collapsible |

```kotlin
NavigationBar {
    destinations.forEach { destination ->
        NavigationBarItem(
            selected = destination == current,
            onClick = { onNavigate(destination) },
            icon = { Icon(destination.icon, contentDescription = null) },
            label = { Text(destination.label) },
        )
    }
}
```

- Always pass a label. Icon-only navigation fails discoverability and TalkBack
  gives less context.
- 3–5 destinations in a bottom bar. More belongs in a drawer.
- Every destination keeps its own back stack when the app uses multiple stacks;
  reselecting the active destination should pop to its root.
- Do not combine a navigation bar and a navigation drawer for the same
  destinations. Pick one per window size.
- The navigation component owns the selected indicator; do not add your own
  background behind the item.

See the `adaptive` skill for switching between bar, rail, and drawer by window
size class.

## Tabs

```kotlin
PrimaryTabRow(selectedTabIndex = selectedIndex) {
    tabs.forEachIndexed { index, tab ->
        Tab(
            selected = index == selectedIndex,
            onClick = { selectedIndex = index },
            text = { Text(tab.title) },
        )
    }
}
```

- Tabs switch peer content in place. They are not navigation destinations and do
  not use the navigation bar.
- Keep 2–5 tabs visible. More needs scrollable tabs or a different structure.
- `PrimaryTabRow` for top-level content, `SecondaryTabRow` for content nested
  inside a screen.
- The tab row does not swipe by default; if the app supports swipe, sync the
  pager state with the selected tab.

## Back

- System back dismisses the topmost overlay first: menu, then sheet/dialog, then
  screen.
- Do not intercept back to show a custom confirmation unless the user would lose
  meaningful work.
- Predictive back: keep the back callback lightweight and let the system animate;
  do not run navigation in `onBackPressed` before the gesture resolves.
- After a destructive action, back does not undo it. Offer undo in a snackbar
  instead.

## Common mistakes

| Mistake | Fix |
| --- | --- |
| 6 items in a `NavigationBar` | Drawer, or reduce destinations |
| Navigation labels hidden | Always show labels |
| A drawer and a bottom bar for the same destinations | One per window size |
| App bar title hardcoded to `bodyLarge` | Let the bar own the title style |
| Back intercepted to show "exit?" on every screen | Only for unsaved work |
| Selected state drawn manually | Use the component's selected colors and indicator |
| Two app bars stacked | One bar; use a section header in content |

## Verify

- Rotate and resize: the correct navigation component appears for each window.
- Select the active destination again; the stack pops to its root.
- TalkBack announces each destination as selected or not selected.
- Back closes overlays before leaving the screen.
