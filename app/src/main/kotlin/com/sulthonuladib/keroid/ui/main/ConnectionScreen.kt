package com.sulthonuladib.keroid.ui.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sulthonuladib.keroid.data.*
import com.sulthonuladib.keroid.ui.components.AddFab
import com.sulthonuladib.keroid.ui.components.CountBadge
import com.sulthonuladib.keroid.ui.components.DetailBar
import com.sulthonuladib.keroid.ui.components.CookieTile
import com.sulthonuladib.keroid.ui.components.GroupDivider
import com.sulthonuladib.keroid.ui.components.GroupedCard
import com.sulthonuladib.keroid.ui.components.LargeHeader
import com.sulthonuladib.keroid.ui.components.SegmentedGroup
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.keroid

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
    val connected = state.machines.count { state.connection(it.id).connected }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxHeight()) {
            LargeHeader(
                title = "Machines",
                titleTag = "machines-title",
                subtitle = {
                    Text(
                        "${state.machines.size} saved · $connected connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SegmentedGroup(
                options = listOf("Machines", "Identities"),
                counts = listOf(state.machines.size, state.identities.size),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.libraryError?.let { item(key = "library-error", contentType = "error") { ErrorCard(it) } }
                if (tab == 0) {
                    if (state.machines.isEmpty()) item(key = "empty-machines", contentType = "empty") {
                        EmptyConnections("No machines saved", "Add a machine to open its sessions.")
                    }
                    val connectedMachines = state.machines.filter { state.connection(it.id).connected }
                    val savedMachines = state.machines.filterNot { state.connection(it.id).connected }
                    if (connectedMachines.isNotEmpty()) {
                        item(key = "connected-label") {
                            SectionTitle("Connected", connectedMachines.size)
                        }
                        items(connectedMachines, key = { "c:${it.id}" }, contentType = { "machine" }) { machine ->
                            MachineCard(state, machine, onSelect = onSelect, onMachine = onMachine)
                        }
                    }
                    if (savedMachines.isNotEmpty()) {
                        item(key = "saved-label") { SectionTitle("Saved", savedMachines.size) }
                        items(savedMachines, key = { "s:${it.id}" }, contentType = { "machine" }) { machine ->
                            MachineCard(state, machine, onSelect = onSelect, onMachine = onMachine)
                        }
                    }
                    item(key = "trust-note") {
                        Row(
                            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Host keys are pinned on first connect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    if (state.identities.isEmpty()) item(key = "empty-identities", contentType = "empty") {
                        EmptyConnections("No identities saved", "Add a private key once and reuse it across machines.")
                    }
                    if (state.identities.isNotEmpty()) {
                        item(key = "identities-label") { SectionTitle("Saved", state.identities.size) }
                        item(key = "identity-card") {
                            GroupedCard {
                                state.identities.forEachIndexed { index, identity ->
                                    if (index > 0) GroupDivider()
                                    IdentityRow(identity, state, onIdentity)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!state.restoring && state.libraryError == null) {
            AddFab(
                onClick = { if (tab == 0) onMachine(null) else onIdentity(null) },
                contentDescription = if (tab == 0) "Add machine" else "Add identity",
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(name: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        CountBadge(count)
    }
}

@Composable
private fun MachineCard(
    state: MainUiState,
    machine: Machine,
    onSelect: (String) -> Unit,
    onMachine: (String?) -> Unit,
) {
    val connection = state.connection(machine.id)
    val identity = state.identities.firstOrNull { it.id == machine.identityId }
    val color = when (connection.status) {
        ConnectionStatus.Connected -> MaterialTheme.keroid.success
        ConnectionStatus.Connecting, ConnectionStatus.VerifyHost -> MaterialTheme.keroid.attention
        ConnectionStatus.Error -> MaterialTheme.keroid.danger
        ConnectionStatus.Offline -> MaterialTheme.colorScheme.outline
    }
    Surface(
        onClick = { onSelect(machine.id) },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.keroid.window,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CookieTile(machine.name, selected = false, connected = connection.connected)
            Column(Modifier.weight(1f)) {
                Text(machine.name, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    if (machine.isTailscale) machine.endpoint
                    else "${machine.profile().address}:${machine.port}",
                    fontFamily = KeroidMono,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(7.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                    Text(
                        buildString {
                            if (!connection.connected && machine.isTailscale) {
                                append("Tailscale · ${connection.status.label}")
                            } else {
                                append(connection.status.label)
                                if (connection.connected) {
                                    val count = connection.state.sessions.size
                                    append(" · $count ${if (count == 1) "session" else "sessions"}")
                                } else if (identity != null) {
                                    append(" · ${identity.name}")
                                }
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
            IconButton(onClick = { onMachine(machine.id) }) {
                Icon(Icons.Outlined.MoreVert, "Edit ${machine.name}")
            }
        }
    }
}

@Composable
private fun IdentityRow(identity: Identity, state: MainUiState, onIdentity: (String?) -> Unit) {
    val count = state.machines.count { it.identityId == identity.id }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onIdentity(identity.id) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.keroid.raised, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(identity.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${identity.algorithm.ifBlank { "SSH key" }} · $count ${if (count == 1) "machine" else "machines"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (identity.fingerprint.isNotBlank()) Text(
                identity.fingerprint,
                fontFamily = KeroidMono,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (identity.requiresPassphrase) MaterialTheme.keroid.raised else Color.Transparent,
        ) {
            Text(
                if (identity.requiresPassphrase) "passphrase" else "no passphrase",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                fontFamily = KeroidMono,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyConnections(title: String, body: String) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.keroid.window,
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
            shape = MaterialTheme.shapes.medium)
        // Two ways in, as desktop Kero has: SSH runs the bridge, Tailscale reaches
        // kerod's own tailnet listener or Serve and sends no key at all.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val selectedChip = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            )
            FilterChip(selected = draft.transport == Transport.Ssh,
                onClick = { onDraft(draft.copy(transport = Transport.Ssh)) }, enabled = enabled,
                label = { Text("SSH") }, leadingIcon = { Icon(Icons.Outlined.Key, null) },
                colors = selectedChip, modifier = Modifier.weight(1f))
            FilterChip(selected = draft.transport == Transport.Tailscale,
                onClick = { onDraft(draft.copy(transport = Transport.Tailscale)) }, enabled = enabled,
                label = { Text("Tailscale") }, leadingIcon = { Icon(Icons.Outlined.Public, null) },
                colors = selectedChip, modifier = Modifier.weight(1f))
        }
        if (draft.transport == Transport.Tailscale) {
            OutlinedTextField(value = draft.endpoint, onValueChange = { onDraft(draft.copy(endpoint = it)) },
                label = { Text("Tailscale address") },
                placeholder = { Text("http://devbox:7373") },
                supportingText = { Text("What the host's app shows after \u201cLet other machines connect\u201d, or https://name.tailnet.ts.net behind Tailscale Serve. kerod must already be running there.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium)
            Text("No SSH key is used. kerod's owner and listen-allow rules decide who connects.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(value = draft.address, onValueChange = { onDraft(draft.copy(address = it)) },
                    label = { Text("SSH address") }, placeholder = { Text("user@hostname") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    singleLine = true, enabled = enabled, modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium)
                OutlinedTextField(value = draft.port, onValueChange = { onDraft(draft.copy(port = it)) },
                    label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, enabled = enabled, modifier = Modifier.width(112.dp),
                    shape = MaterialTheme.shapes.medium)
            }
            Box {
                OutlinedButton(onClick = { identitiesOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text(state.identities.firstOrNull { it.id == draft.identityId }?.name ?: "Choose identity", Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(expanded = identitiesOpen, onDismissRequest = { identitiesOpen = false },
                    shape = MaterialTheme.shapes.medium) {
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
                    shape = MaterialTheme.shapes.medium)
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
            shape = MaterialTheme.shapes.medium)
        if (existing == null) {
            OutlinedTextField(value = privateKey, onValueChange = onKey, label = { Text("Private key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                minLines = 2, maxLines = 3, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium)
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
                    shape = MaterialTheme.shapes.medium)
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
        topBar = { DetailBar(title, onBack, modifier = Modifier.statusBarsPadding()) }) { padding ->
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
