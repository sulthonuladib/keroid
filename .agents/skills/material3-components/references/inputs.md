# Inputs

## Text fields

| Component | Use |
| --- | --- |
| `OutlinedTextField` | Default for forms; the border defines the field clearly |
| `TextField` | Filled style; dense forms and search-like input |

```kotlin
var email by rememberSaveable { mutableStateOf("") }
var touched by rememberSaveable { mutableStateOf(false) }
val isValid = email.contains("@")

OutlinedTextField(
    value = email,
    onValueChange = { email = it; touched = true },
    label = { Text("Email") },
    supportingText = {
        Text(if (touched && !isValid) "Enter a valid email" else "We never share it")
    },
    isError = touched && !isValid,
    singleLine = true,
    keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    ),
)
```

- Hoist the value when the screen owns it; use `rememberSaveable` for local form
  state.
- `label` is required for anything that is not self-evident. Placeholder text is
  not a label: it disappears on input.
- `supportingText` is for guidance or an error, not both at once. Swap content,
  do not stack.
- Set `isError` and the error text together; a red border with no message is
  noise.
- `singleLine = true` for names, emails, and search. Use `minLines` for
  composers.
- Password: `visualTransformation = PasswordVisualTransformation()` plus a
  reveal toggle with a label.
- Numeric input: `KeyboardType.Number` / `Decimal`; do not rely on the keyboard
  to validate.
- Autofill: pass `contentType` via `Modifier.semantics { contentType = … }` for
  forms.
- Do not use `BasicTextField` unless you are building a custom field; you lose
  the M3 states and decoration.

## Search

`SearchBar` and `DockedSearchBar` are experimental and stateful. The contract:

- A `state` object owns expanded/collapsed and the query.
- The `inputField` slot renders search as the leading element, a clear action,
  and the text field.
- The content slot shows suggestions when expanded and results when submitted.
- Collapse the bar when a result is opened; preserve the query if the user
  returns.

```kotlin
// Sketch: verify the current state object and slot names in the API reference.
val searchState = rememberSearchBarState()
SearchBar(
    state = searchState,
    inputField = {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { onSearch(query) },
            expanded = searchState.expanded,
            onExpandedChange = { searchState.expanded = it },
            placeholder = { Text("Search messages") },
        )
    },
) {
    // suggestions / results
}
```

## Selection controls

```kotlin
Row(
    Modifier
        .fillMaxWidth()
        .toggleable(
            value = enabled,
            role = Role.Switch,
            onValueChange = onEnabledChange,
        )
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text("Notifications", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Alerts for new messages",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Switch(checked = enabled, onCheckedChange = null) // row owns the toggle
}
```

- Make the whole row toggle, not just the control; pass `onCheckedChange = null`
  to the control so there is one semantic action, not two.
- `Checkbox` for independent choices, `RadioButton` for one of a set, `Switch`
  for an immediate on/off setting.
- Never use a switch to submit a form; switches apply immediately.
- Radio groups need a visible group label and a selected value.
- Tri-state checkbox: `ToggleableState.Indeterminate`; explain what it means.

## Slider

```kotlin
Slider(
    value = volume,
    onValueChange = { volume = it },
    valueRange = 0f..100f,
    steps = 9,
    onValueChangeFinished = { onCommit(volume) },
)
```

- Use a slider for a continuous value where approximate is fine. Use a stepper
  or text field for exact values.
- Show the current value; do not make the user infer it from thumb position.
- `onValueChangeFinished` is the commit point; do not write to a database on
  every frame.
- `RangeSlider` for a range; label both ends.

## Pickers

Date:

```kotlin
val state = rememberDatePickerState(initialSelectedDateMillis = now)
DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = { onConfirm(state.selectedDateMillis) }) { Text("OK") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
) {
    DatePicker(state = state)
}
```

Time: `rememberTimePickerState` plus `TimePicker` (dial) or `TimeInput`
(keyboard). Prefer `TimeInput` when the user must type an exact time.

- Pickers are modal flows: confirm and dismiss actions are mandatory.
- Respect the user's 12/24-hour setting; do not hardcode a format.
- Prefer the platform picker when the app has no strong reason for a custom one.

## Menus and dropdowns

```kotlin
Box {
    OutlinedButton(onClick = { expanded = true }) { Text(selectedLabel) }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelect(option); expanded = false },
            )
        }
    }
}
```

- A menu holds 3–10 related actions. Two actions belong inline; twenty belong in
  a searchable screen.
- `ExposedDropdownMenuBox` for selecting a value from a list; it is a form
  control, not an action menu.
- Menu items are verbs or values, never both mixed.
- Dismiss on selection and on outside tap.

## Common mistakes

| Mistake | Fix |
| --- | --- |
| Placeholder used as label | `label = { Text(...) }` |
| Error state without a message | `isError` + `supportingText` |
| Row label not clickable, only the switch | `toggleable` on the row |
| Switch used to save a form | Checkbox or radio, then a save action |
| `onValueChange` writing to disk | Commit in `onValueChangeFinished` |
| Hardcoded date format | Locale-aware formatter |
| Dropdown with 30 items | A dedicated picker screen |

## Verify

- Keyboard type, IME action, and focus order match the form.
- Errors are announced by TalkBack and visible without color.
- The form survives rotation and process death (`rememberSaveable` or hoisted
  state).
- Every control works at 200% font scale without clipping.
