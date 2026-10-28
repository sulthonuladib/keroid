package com.sulthonuladib.keroid.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sulthonuladib.keroid.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onMachine: (String?) -> Unit,
    onIdentity: (String?) -> Unit,
    onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (!state.restoring && state.libraryError == null) ExtendedFloatingActionButton(
                onClick = { if (tab == 0) onMachine(null) else onIdentity(null) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(if (tab == 0) "Add machine" else "Add identity") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Machines") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Identities") })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.libraryError?.let { item(key = "library-error", contentType = "error") { ErrorCard(it) } }
                if (tab == 0) {
                    if (state.machines.isEmpty()) item(key = "empty-machines", contentType = "empty") {
                        EmptyConnections("No machines saved", "Machines appear here once you add one.")
                    }
                    items(state.machines, key = { it.id }, contentType = { "machine" }) { machine ->
                        val connection = state.connection(machine.id)
                        val identity = state.identities.firstOrNull { it.id == machine.identityId }
                        SettingsRow(
                            onClick = { onSelect(machine.id) },
                            icon = Icons.Outlined.Computer,
                            title = machine.name,
                            lines = listOf(
                                // A tailnet address already carries its port.
                                if (machine.isTailscale) machine.endpoint
                                else "${machine.profile().address} · ${machine.port}",
                                // A Tailscale machine has no identity to be missing.
                                (if (machine.isTailscale) "Tailscale"
                                else identity?.name ?: "Missing identity") + " · ${connection.status.label}",
                            ),
                            selected = state.selectedMachineId == machine.id,
                            trailing = {
                                IconButton(onClick = { onMachine(machine.id) }) { Icon(Icons.Outlined.Edit, "Edit ${machine.name}") }
                            },
                        )
                    }
                } else {
                    if (state.identities.isEmpty()) item(key = "empty-identities", contentType = "empty") {
                        EmptyConnections("No identities saved", "Add a private key once and reuse it across machines.")
                    }
                    items(state.identities, key = { it.id }, contentType = { "identity" }) { identity ->
                        val count = state.machines.count { it.identityId == identity.id }
                        SettingsRow(
                            onClick = { onIdentity(identity.id) },
                            icon = Icons.Outlined.Key,
                            title = identity.name,
                            lines = listOfNotNull(
                                "${identity.algorithm.ifBlank { "SSH key" }} · ${if (identity.requiresPassphrase) "Passphrase required" else "No passphrase"}",
                                identity.fingerprint.takeIf { it.isNotEmpty() },
                                "Used by $count ${if (count == 1) "machine" else "machines"}",
                            ),
                            trailing = { Icon(Icons.Outlined.ChevronRight, "Edit ${identity.name}") },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SettingsRow(
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    lines: List<String>,
    selected: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(22.dp)) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
    }
}

@Composable
private fun EmptyConnections(title: String, body: String) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal val ConnectionStatus.label: String get() = when (this) {
    ConnectionStatus.Offline -> "Disconnected"
    ConnectionStatus.Connecting -> "Connecting…"
    ConnectionStatus.VerifyHost -> "Verify host key"
    ConnectionStatus.Connected -> "Connected"
    ConnectionStatus.Error -> "Connection failed"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineEditorScreen(
    state: MainUiState,
    existing: Machine?,
    onDraft: (ConnectionDraft) -> Unit,
    onBack: () -> Unit,
    onAddIdentity: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var identitiesOpen by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.restoring && !state.saving && state.libraryError == null
    val draft = state.draft
    EditorScaffold(if (existing == null) "Add machine" else "Edit machine", onBack, state.saving) {
        OutlinedTextField(value = draft.name, onValueChange = { onDraft(draft.copy(name = it)) },
            label = { Text("Machine name") }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large)
        // Two ways in, as desktop Kero has: SSH runs the bridge, Tailscale reaches
        // kerod's own tailnet listener or Serve and sends no key at all.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = draft.transport == Transport.Ssh,
                onClick = { onDraft(draft.copy(transport = Transport.Ssh)) }, enabled = enabled,
                label = { Text("SSH") }, leadingIcon = { Icon(Icons.Outlined.Key, null) },
                modifier = Modifier.weight(1f))
            FilterChip(selected = draft.transport == Transport.Tailscale,
                onClick = { onDraft(draft.copy(transport = Transport.Tailscale)) }, enabled = enabled,
                label = { Text("Tailscale") }, leadingIcon = { Icon(Icons.Outlined.Public, null) },
                modifier = Modifier.weight(1f))
        }
        if (draft.transport == Transport.Tailscale) {
            OutlinedTextField(value = draft.endpoint, onValueChange = { onDraft(draft.copy(endpoint = it)) },
                label = { Text("Tailscale address") },
                placeholder = { Text("http://devbox:7373") },
                supportingText = { Text("What the host's app shows after \u201cLet other machines connect\u201d, or https://name.tailnet.ts.net behind Tailscale Serve. kerod must already be running there.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large)
            Text("No SSH key is used. kerod's owner and listen-allow rules decide who connects.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(value = draft.address, onValueChange = { onDraft(draft.copy(address = it)) },
                    label = { Text("SSH address") }, placeholder = { Text("user@hostname") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    singleLine = true, enabled = enabled, modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large)
                OutlinedTextField(value = draft.port, onValueChange = { onDraft(draft.copy(port = it)) },
                    label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = enabled, modifier = Modifier.width(112.dp),
                    shape = MaterialTheme.shapes.large)
            }
            Box {
                OutlinedButton(onClick = { identitiesOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text(state.identities.firstOrNull { it.id == draft.identityId }?.name ?: "Choose identity", Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(expanded = identitiesOpen, onDismissRequest = { identitiesOpen = false },
                    shape = MaterialTheme.shapes.large) {
                    state.identities.forEach { identity ->
                        DropdownMenuItem(text = { Text(identity.name) }, onClick = {
                            identitiesOpen = false; onDraft(draft.copy(identityId = identity.id))
                        })
                    }
                    DropdownMenuItem(text = { Text("Add identity") }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        onClick = { identitiesOpen = false; onAddIdentity() })
                }
            }
            OutlinedButton(onClick = onAddIdentity, enabled = enabled) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add identity")
            }
            TextButton(onClick = { advanced = !advanced }, enabled = enabled) {
                Text(if (advanced) "Hide advanced options" else "Advanced options")
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
            }
            if (advanced) {
                OutlinedTextField(value = draft.executable, onValueChange = { onDraft(draft.copy(executable = it)) },
                    label = { Text("Kero executable (optional)") }, supportingText = { Text("Leave empty to discover Kero on Linux or macOS.") },
                    singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large)
            }
        }
        state.editorError?.let { ErrorCard(it) }
        Button(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save machine") }
        if (existing != null) TextButton(onClick = { deleting = true }, enabled = enabled) { Text("Delete machine") }
    }
    if (deleting) ConfirmDelete("Delete machine?", "Only this saved machine and its connection will be removed. Its reusable identity and host trust stay saved. Remote sessions keep running.",
        onDismiss = { deleting = false }, onDelete = { deleting = false; onDelete() })
}

@Composable
fun IdentityEditorScreen(
    existing: Identity?, name: String, privateKey: String, saving: Boolean, error: String?,
    onName: (String) -> Unit, onKey: (String) -> Unit, onError: (String) -> Unit,
    onBack: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit,
) {
    var deleting by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importKey = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try {
                require(uri.scheme == "content")
                val key = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = ByteArray(128 * 1024 + 1)
                        try {
                            var total = 0
                            while (total < bytes.size) {
                                val count = input.read(bytes, total, bytes.size - total)
                                if (count < 0) break
                                require(count > 0)
                                total += count
                            }
                            require(total in 1..128 * 1024)
                            String(bytes, 0, total, Charsets.UTF_8)
                        } finally { bytes.fill(0) }
                    } ?: error("Cannot open key")
                }
                onKey(key)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { onError("Could not import this key. Choose a text private-key file smaller than 128 KiB.") }
        }
    }
    EditorScaffold(if (existing == null) "Add identity" else "Edit identity", onBack, saving) {
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Identity name") },
            enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large)
        if (existing == null) {
            OutlinedTextField(value = privateKey, onValueChange = onKey, label = { Text("Private key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                minLines = 2, maxLines = 3, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large)
            OutlinedButton(onClick = { importKey.launch("*/*") }, enabled = !saving) {
                Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Import key file")
            }
            Text("OpenSSH or PEM. Encrypted on this device; passphrases are requested only when connecting and never saved.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(existing.algorithm.ifBlank { "SSH private key" })
            if (existing.fingerprint.isNotBlank()) SelectionContainer { Text(existing.fingerprint, fontFamily = FontFamily.Monospace) }
            Text("To use another key, add a new identity and reassign the machine.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { ErrorCard(it) }
        Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save identity") }
        if (existing != null) TextButton(onClick = { deleting = true }, enabled = !saving) { Text("Delete identity") }
    }
    if (deleting) ConfirmDelete("Delete identity?", "Remove this key from the encrypted library. Identities used by a machine cannot be deleted.",
        onDismiss = { deleting = false }, onDelete = { deleting = false; onDelete() })
}

@Composable
internal fun ConnectionPrompts(state: MainUiState, passphrase: String, onPassphrase: (String) -> Unit,
    onUnlock: () -> Unit, onCancelUnlock: () -> Unit, onTrust: (String) -> Unit, onDismissHost: (String) -> Unit) {
    state.unlock?.let { prompt ->
        AlertDialog(onDismissRequest = onCancelUnlock,
            title = { Text("Unlock ${prompt.identityName}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.machines.firstOrNull { it.id == prompt.machineId }?.name.orEmpty())
                OutlinedTextField(value = passphrase, onValueChange = onPassphrase, label = { Text("Key passphrase") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    shape = MaterialTheme.shapes.large)
                Text("Memory only. Never saved.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = onUnlock) { Text("Connect") } },
            dismissButton = { TextButton(onClick = onCancelUnlock) { Text("Cancel") } })
        return
    }
    val entry = state.connections.entries.firstOrNull { it.value.hostKey != null } ?: return
    val machine = state.machines.firstOrNull { it.id == entry.key } ?: return
    val problem = entry.value.hostKey ?: return
    val changed = problem.code == "host_key_changed"
    AlertDialog(onDismissRequest = { onDismissHost(machine.id) },
        icon = { Icon(if (changed) Icons.Outlined.Warning else Icons.Outlined.VerifiedUser, null) },
        title = { Text(if (changed) "Host key changed" else "Verify host key") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${machine.name} · ${machine.address}")
            Text(if (changed) "The host key differs from the one you trusted. Connection blocked. Verify the change independently; choosing another identity will not replace host trust."
                else "Compare this fingerprint with the server’s SSH host key through a trusted channel before connecting.")
            Text(problem.algorithm, style = MaterialTheme.typography.labelMedium)
            SelectionContainer { Text(problem.fingerprint, fontFamily = FontFamily.Monospace) }
        } },
        confirmButton = { TextButton(onClick = { if (changed) onDismissHost(machine.id) else onTrust(machine.id) }) { Text(if (changed) "Close" else "Trust and connect") } },
        dismissButton = { if (!changed) TextButton(onClick = { onDismissHost(machine.id) }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(title: String, onBack: () -> Unit, saving: Boolean,
                           content: @Composable ColumnScope.() -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Column(Modifier.fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            // Feedback overlays a fixed slot; adding a progress row moved every
            // field (and clamped the scroll position) just before navigating away.
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, message: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
}

@Composable
internal fun ErrorCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// All leases are acquired/released by Compose effects on the UI thread. An
// outgoing identity entry and an unlock prompt may protect the same window;
// neither may clear the other's protection or a flag set by an outside owner.
private class WindowProtection(val wasSecure: Boolean, var owners: Int = 0)
private val protectedWindows = java.util.WeakHashMap<android.view.Window, WindowProtection>()

@Composable
internal fun ProtectSecrets(protect: Boolean) {
    if (!protect) return
    val window = LocalContext.current.activity()?.window ?: return
    DisposableEffect(window) {
        val protection = protectedWindows.getOrPut(window) {
            WindowProtection(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        if (protection.owners++ == 0 && !protection.wasSecure)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (--protection.owners == 0) {
                protectedWindows.remove(window)
                if (!protection.wasSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
