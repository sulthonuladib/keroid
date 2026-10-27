package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sulthonuladib.keroid.data.KeroProject
import com.sulthonuladib.keroid.data.KeroSession
import com.sulthonuladib.keroid.data.KeroState
import com.sulthonuladib.keroid.data.Machine
import com.sulthonuladib.keroid.ui.theme.KeroidTheme

private enum class SessionFilter(val label: String) {
    All("All"),
    Attention("Needs you"),
    Working("Working"),
}

/**
 * The home screen: a search button in the app bar, the machine switcher, and the
 * selected machine's sessions. The search field only appears once it is asked
 * for, so a connected machine's list stays the first thing on screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    ui: MainUiState,
    onSelectMachine: (String) -> Unit,
    onSession: (String, String) -> Unit,
    onAddMachine: () -> Unit,
    onConnections: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onRetryStorage: () -> Unit = {},
    onOpenProject: (String) -> Unit = {},
    onNewSession: (String, String) -> Unit = { _, _ -> },
    onRenameSession: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    onCloseSession: (String, String, () -> Unit) -> Unit = { _, _, _ -> },
) {
    var createMenu by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SessionFilter.All.name) }
    val filter = SessionFilter.valueOf(filterName)
    val enabled = !ui.restoring && ui.libraryError == null
    val machine = ui.selectedMachine
    val connection = ui.connection(machine?.id)
    val state = connection.state
    var actionSessionId by rememberSaveable(machine?.id) { mutableStateOf<String?>(null) }
    var actionKind by rememberSaveable(machine?.id) { mutableStateOf(SessionActionKind.Rename.name) }
    LaunchedEffect(connection.connected) { if (!connection.connected) actionSessionId = null }
    val actionSession = state.sessions.firstOrNull { it.id == actionSessionId }
    if (actionSession != null && machine != null && connection.connected) {
        SessionActionDialog(
            kind = SessionActionKind.valueOf(actionKind), machineName = machine.name,
            session = actionSession, action = ui.actions[machine.id] ?: SessionActionState(),
            enabled = enabled, onDismiss = { actionSessionId = null },
            onConfirm = { name ->
                val done = { actionSessionId = null }
                if (actionKind == SessionActionKind.Rename.name)
                    onRenameSession(machine.id, actionSession.id, name, done)
                else onCloseSession(machine.id, actionSession.id, done)
            },
        )
    }
    val sessions = remember(state.sessions, query, filter) {
        state.sessions.filter { session ->
            val matchesFilter = when (filter) {
                SessionFilter.All -> true
                SessionFilter.Attention -> session.needsAttention
                SessionFilter.Working -> session.status == "working"
            }
            val haystack = buildString {
                append(session.label).append(' ').append(session.message).append(' ').append(session.agent)
                append(' ').append(session.dir).append(' ')
                append(state.projects.firstOrNull { it.id == session.project }?.name.orEmpty())
            }
            matchesFilter && (query.isBlank() || haystack.contains(query.trim(), ignoreCase = true))
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (searching) {
                SearchAppBar(query, onQuery = { query = it }, onClose = { searching = false; query = "" })
            } else {
                TopAppBar(
                    title = { Text("Keroid", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        Box {
                            IconButton(onClick = { createMenu = true }, enabled = connection.connected && ui.actions[machine?.id]?.busy != true) {
                                Icon(Icons.Outlined.Add, "New session or project")
                            }
                            DropdownMenu(expanded = createMenu, onDismissRequest = { createMenu = false }) {
                                DropdownMenuItem(text = { Text("Open project…") }, onClick = {
                                    createMenu = false; machine?.let { onOpenProject(it.id) }
                                })
                                DropdownMenuItem(text = { Text("New session") }, onClick = {
                                    createMenu = false; machine?.let { onNewSession(it.id, "") }
                                })
                            }
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search sessions")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                if (ui.restoring) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
                ui.libraryError?.let { message ->
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ErrorCard(message)
                        TextButton(onClick = onRetryStorage) { Text("Retry saved connections") }
                    }
                }
                ui.error?.let { message ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ErrorCard(message) }
                }
                ui.actions[machine?.id]?.error?.let { ErrorCard(it) }
                when {
                    machine == null && !ui.restoring && ui.libraryError == null -> EmptyState(
                        title = "No machines yet",
                        body = "Save a machine and an SSH identity to see its sessions here.",
                        action = "Add a machine",
                        onAction = onAddMachine,
                    )

                    machine != null -> Column(Modifier.fillMaxSize()) {
                        MachineStrip(
                            ui = ui,
                            selected = machine.id,
                            enabled = enabled,
                            onSelect = onSelectMachine,
                            onAdd = onAddMachine,
                        )
                        when (connection.status) {
                            ConnectionStatus.Connected -> ConnectedHeader(
                                connection = connection,
                                filter = filter,
                                onFilter = { filterName = it.name },
                                onDisconnect = { onDisconnect(machine.id) },
                                enabled = enabled,
                            )

                            else -> ConnectPanel(
                                machine = machine,
                                connection = connection,
                                enabled = enabled,
                                onConnect = { onConnect(machine.id) },
                                onDisconnect = { onDisconnect(machine.id) },
                            )
                        }
                        SessionList(
                            machineId = machine.id,
                            state = state,
                            sessions = sessions,
                            connected = connection.connected,
                            onSession = onSession,
                            onNewSession = onNewSession,
                            creating = ui.actions[machine.id]?.busy == true,
                            onAction = { session, kind -> actionSessionId = session.id; actionKind = kind.name },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAppBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search sessions") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = MaterialTheme.typography.titleMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun MachineStrip(
    ui: MainUiState,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.machines, key = { it.id }) { machine ->
            FilterChip(
                selected = machine.id == selected,
                onClick = { onSelect(machine.id) },
                enabled = enabled,
                label = {
                    Text(machine.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { StatusDot(ui.connection(machine.id).status) },
            )
        }
        item(key = "add-machine") {
            FilterChip(
                selected = false,
                onClick = onAdd,
                enabled = enabled,
                label = { Text("New") },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ConnectedHeader(
    connection: MachineUiState,
    filter: SessionFilter,
    onFilter: (SessionFilter) -> Unit,
    onDisconnect: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(connection.status)
            Text(
                connection.status.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDisconnect, enabled = enabled) {
            Icon(Icons.Outlined.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Disconnect", modifier = Modifier.padding(start = 6.dp))
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionFilter.entries.forEach { option ->
            FilterChip(
                selected = option == filter,
                onClick = { onFilter(option) },
                label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun ConnectPanel(
    machine: Machine,
    connection: MachineUiState,
    enabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(connection.status)
                Text(connection.status.label, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                connection.error ?: when (connection.status) {
                    ConnectionStatus.Connecting -> if (machine.isTailscale)
                        "Reaching kerod over Tailscale at ${machine.endpoint}."
                    else "Opening an SSH connection and the Kero bridge on ${machine.user}@${machine.host}."
                    ConnectionStatus.VerifyHost -> "Review this machine's host key before signing in."
                    ConnectionStatus.Error -> if (machine.isTailscale)
                        "Could not reach kerod at ${machine.endpoint}. It must already be running there."
                    else "The connection ended. Reconnect when the machine is reachable."
                    else -> "Connect to list ${machine.name}'s sessions."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val busy = connection.status == ConnectionStatus.Connecting ||
                    connection.status == ConnectionStatus.VerifyHost
                if (busy) {
                    TextButton(onClick = onDisconnect, enabled = enabled) { Text("Cancel") }
                } else {
                    Button(onClick = onConnect, enabled = enabled) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Connect", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionList(
    machineId: String,
    state: KeroState,
    sessions: List<KeroSession>,
    connected: Boolean,
    onSession: (String, String) -> Unit,
    onNewSession: (String, String) -> Unit,
    creating: Boolean,
    onAction: (KeroSession, SessionActionKind) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (connected) {
            if (sessions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (state.sessions.isEmpty()) "No sessions on this machine." else "No matching sessions.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val known = state.projects.map { it.id }.toSet()
            val groups = state.projects + if (sessions.any { it.project !in known }) {
                listOf(KeroProject("", "Other sessions"))
            } else {
                emptyList()
            }
            groups.forEach { project ->
                val groupSessions = sessions.filter { if (project.id.isEmpty()) it.project !in known else it.project == project.id }
                if (groupSessions.isNotEmpty() || (project.id.isNotEmpty() && sessions.size == state.sessions.size)) {
                    item(key = "project:$machineId:${project.id}") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (project.id.isNotEmpty()) IconButton(enabled = !creating,
                                onClick = { onNewSession(machineId, project.id) }) {
                                Icon(Icons.Outlined.Add, "New session in ${project.name}")
                            }
                        }
                    }
                    items(groupSessions, key = { "session:$machineId:${it.id}" }) { session ->
                        SessionCard(session, enabled = !creating,
                            onClick = { onSession(machineId, session.id) },
                            onAction = { onAction(session, it) })
                    }
                }
            }
        } else {
            item(key = "offline") {
                Text(
                    "Sessions appear here once this machine is connected.",
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionCard(session: KeroSession, enabled: Boolean, onClick: () -> Unit,
                        onAction: (SessionActionKind) -> Unit) {
    var menu by rememberSaveable(session.id) { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusLabel(session.status)
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled) {
                        Icon(Icons.Outlined.MoreVert, "Actions for ${session.label}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename session") }, enabled = enabled,
                            onClick = { menu = false; onAction(SessionActionKind.Rename) })
                        DropdownMenuItem(text = { Text("Close session") }, enabled = enabled,
                            onClick = { menu = false; onAction(SessionActionKind.CloseSession) })
                    }
                }
            }
            if (session.message.isNotBlank()) {
                Text(
                    session.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    session.agent.ifBlank {
                        "${session.panes.size} ${if (session.panes.size == 1) "pane" else "panes"}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.unread) {
                    Text("• Unread", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (session.progress in 0..100 && session.status == "working") {
                LinearProgressIndicator(
                    progress = { session.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val previewState = KeroState(
    projects = listOf(KeroProject("project", "keroid")),
    sessions = listOf(
        KeroSession(
            "review", "project", name = "Review SSH connection", agent = "Pi", status = "waiting",
            message = "Ready for you to review the changes.", unread = true,
        ),
        KeroSession(
            "build", "project", name = "Android build", agent = "Codex", status = "working",
            message = "Checking connection state and key storage", progress = 65,
        ),
        KeroSession("shell", "project", name = "Development", status = "idle"),
    ),
)

@Preview(name = "Home · light", widthDp = 412, heightDp = 915)
@Preview(name = "Home · dark", widthDp = 412, heightDp = 915, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Home · large text", widthDp = 412, heightDp = 915, fontScale = 1.5f)
@Preview(name = "Home · tablet", widthDp = 900, heightDp = 1000)
@Preview(name = "Home · landscape", widthDp = 915, heightDp = 412)
@Preview(name = "Home · small", widthDp = 360, heightDp = 640)
@Composable
private fun HomePreview() {
    KeroidTheme(dynamicColor = false) {
        HomeScreen(
            MainUiState(
                restoring = false,
                machines = listOf(Machine("dev", "Dev machine", "devbox", "alice", "key")),
                selectedMachineId = "dev",
                connections = mapOf("dev" to MachineUiState(ConnectionStatus.Connected, previewState)),
            ),
            {}, { _, _ -> }, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Home · empty", widthDp = 412, heightDp = 915)
@Composable
private fun HomeEmptyPreview() {
    KeroidTheme(dynamicColor = false) {
        HomeScreen(MainUiState(restoring = false), {}, { _, _ -> }, {}, {}, {}, {})
    }
}

@Preview(name = "Home · offline", widthDp = 412, heightDp = 915)
@Composable
private fun HomeOfflinePreview() {
    KeroidTheme(dynamicColor = false) {
        HomeScreen(
            MainUiState(
                restoring = false,
                machines = listOf(Machine("dev", "Dev machine", "devbox", "alice", "key")),
                selectedMachineId = "dev",
            ),
            {}, { _, _ -> }, {}, {}, {}, {},
        )
    }
}
