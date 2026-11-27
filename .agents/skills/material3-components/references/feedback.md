# Feedback and overlays

## Snackbar

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()

Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    // …
    Button(onClick = {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Message archived",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }) { Text("Archive") }
}
```

- A snackbar reports something that already happened, transiently. It does not
  ask a question; use a dialog for that.
- One at a time. `showSnackbar` suspends until the current one leaves, which is
  the throttle you want.
- Put the action first ("Undo") and the message second in importance. Keep both
  short.
- Never put a snackbar over a FAB or a bottom navigation bar; `Scaffold` places
  the host and the FAB together.
- Accessibility: the host announces politely. Do not build a custom toast from a
  `Box`.

## Dialogs

```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
    title = { Text("Delete draft?") },
    text = { Text("This cannot be undone.") },
    confirmButton = {
        TextButton(onClick = onDelete) { Text("Delete") }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) { Text("Cancel") }
    },
)
```

- Dialogs are for a decision that blocks progress: destructive confirmation,
  permission rationale, a short required choice.
- One decision per dialog. Long forms belong in a sheet or a screen.
- Confirm label is the verb ("Delete"), not "OK". Dismiss is "Cancel".
- Destructive confirm uses `error` color, not a filled primary button.
- `dismissOnBackPress` and `dismissOnClickOutside` default to true. Set them to
  false only when dismissal would lose data, and then provide an explicit close.
- `BasicAlertDialog` for custom content; you own padding, shape, and actions.
- Do not stack dialogs. If a second decision is needed, use a flow.

## Bottom sheets

```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
var showSheet by remember { mutableStateOf(false) }

if (showSheet) {
    ModalBottomSheet(
        onDismissRequest = { showSheet = false },
        sheetState = sheetState,
    ) {
        // content
    }
}
```

- `ModalBottomSheet` for a focused task or a menu of actions. It blocks the
  screen behind it.
- `BottomSheetScaffold` for a persistent, draggable sheet that coexists with
  content (media controls, a map).
- Partial expansion is for content that is useful at a glance. If the sheet is
  only useful fully open, set `skipPartiallyExpanded = true`.
- Programmatic dismissal: call `sheetState.hide()` inside a coroutine, then set
  the visibility flag. Setting the flag alone skips the exit animation.
- The drag handle is the affordance. Do not remove it without another obvious
  way to dismiss.
- Content must scroll independently of the sheet drag; use a nested scroll
  connection when the sheet contains a long list.

## Progress and loading

| Situation | Component |
| --- | --- |
| Known duration or percentage | `LinearProgressIndicator(progress = { value })` / `CircularProgressIndicator(progress = { value })` |
| Unknown duration, blocking a region | Indeterminate `CircularProgressIndicator` |
| Unknown duration, inline or page-level | `LinearProgressIndicator()` |
| A short operation (< ~1s) | No indicator; keep the UI responsive |
| Expressive, branded loading | `LoadingIndicator` / `ContainedLoadingIndicator` (experimental) |

- Determinate progress for uploads, downloads, multi-step imports. Indeterminate
  only when you truly cannot estimate.
- Keep the indicator in the same place as the content it replaces so layout does
  not jump.
- Do not show a full-screen spinner for a local operation. Disable the trigger
  and show progress in place.
- After ~10 seconds, add an explanation and a cancel action.
- Skeleton screens are a valid alternative for content-shaped loading; they are
  not an M3 component, so build them from `surfaceContainer` shapes and mark
  them `semantics { invisibleToUser() }` if decorative.

## Tooltips

```kotlin
TooltipBox(
    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    tooltip = { PlainTooltip { Text("Copy link") } },
    state = rememberTooltipState(),
) {
    IconButton(onClick = onCopy) {
        Icon(Icons.Default.Link, contentDescription = "Copy link")
    }
}
```

- A tooltip explains an unfamiliar control. It is not a place for essential
  information, errors, or long help.
- Plain tooltip: one short line. Rich tooltip: a title plus a short body.
- Appears on long-press (touch) and hover (pointer). Keyboard focus should also
  reveal it.
- Do not attach a tooltip to a control that already has a visible label.

## Pull to refresh

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = onRefresh,
) {
    LazyColumn { /* … */ }
}
```

- Use when content changes without user action and the list is already visible.
- Keep the previous content on screen while refreshing; do not replace it with a
  spinner.
- Refresh must be cancellable and must not fight an in-flight load.
- Do not add pull to refresh to a screen with no remote source.

## Swipe to dismiss

```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            onArchive(); true
        } else {
            false
        }
    },
)

SwipeToDismissBox(
    state = dismissState,
    backgroundContent = { /* icon + container */ },
) {
    ListItem(/* … */)
}
```

- Swipe is a shortcut, never the only way to act. Provide the same action in a
  menu or a detail screen.
- Always offer undo via snackbar; deletion by swipe is easy to trigger by
  accident.
- One direction, one action. Two-direction swipe needs distinct, labelled
  backgrounds.
- `confirmValueChange` returning false keeps the item in place; use it to block
  destructive swipes until confirmed.

## Empty and error states

There is no M3 "empty state" component. Compose one from existing parts:

- Icon or illustration (decorative, `contentDescription = null`).
- Short title (`titleMedium` or `headlineSmall`).
- One sentence of explanation (`bodyMedium`, `onSurfaceVariant`).
- One action (filled or text button) that changes the situation.
- For errors: say what failed and what to do, not an error code alone.

## Common mistakes

| Mistake | Fix |
| --- | --- |
| Toast or custom overlay for a result | `Snackbar` |
| Dialog for a long form | Bottom sheet or screen |
| Setting sheet visibility without `hide()` | Run `hide()` then dismiss |
| Indeterminate spinner for a known upload | Determinate progress |
| Full-screen spinner for a 300ms call | Inline state, no spinner |
| Tooltip carrying the only explanation | Visible label or supporting text |
| Swipe-only delete | Menu action plus undo |
| Error dialog with a stack trace | Human message plus recovery action |

## Verify

- Every overlay dismisses with back and with an explicit action.
- TalkBack announces snackbars and dialog titles.
- Rotation does not leave a sheet or dialog in a broken state.
- Progress indicators stop and resolve; no infinite spinner after failure.
