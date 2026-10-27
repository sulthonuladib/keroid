package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.sulthonuladib.keroid.data.KeroPane
import com.sulthonuladib.keroid.data.KeroSession

internal enum class SessionActionKind { Rename, CloseSession, ClosePane }

/** Shared confirmation UI; opening or dismissing it never mutates the host. */
@Composable
internal fun SessionActionDialog(
    kind: SessionActionKind,
    machineName: String,
    session: KeroSession,
    pane: KeroPane? = null,
    action: SessionActionState,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(kind, session.id) { mutableStateOf(session.name) }
    var submitted by rememberSaveable(kind, session.id) { mutableStateOf(false) }
    val canConfirm = enabled && !action.busy
    val confirm = {
        if (canConfirm) {
            submitted = true
            onConfirm(name.trim())
        }
    }
    val title = when (kind) {
        SessionActionKind.Rename -> "Rename session"
        SessionActionKind.CloseSession -> "Close session"
        SessionActionKind.ClosePane -> "Close pane"
    }
    AlertDialog(
        onDismissRequest = { if (!action.busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                if (kind == SessionActionKind.Rename) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Session name") }, singleLine = true,
                        supportingText = { Text("Leave blank to use the automatic name.") },
                        enabled = !action.busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(if (kind == SessionActionKind.ClosePane)
                        "End all processes in pane “${pane?.label.orEmpty()}” of “${session.label}” on $machineName?"
                    else "End all panes and processes in “${session.label}” on $machineName? Any linked worktree will be kept.")
                }
                if (submitted) action.error?.let { ErrorCard(it) }
                if (action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = confirm, enabled = canConfirm,
                colors = if (kind == SessionActionKind.Rename) ButtonDefaults.textButtonColors()
                    else ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (kind == SessionActionKind.Rename) "Save name" else title) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !action.busy) { Text("Cancel") }
        },
    )
}
