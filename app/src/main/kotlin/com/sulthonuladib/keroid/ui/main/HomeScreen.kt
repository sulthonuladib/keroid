package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sulthonuladib.keroid.data.KeroProject
import com.sulthonuladib.keroid.data.KeroSession
import com.sulthonuladib.keroid.data.KeroState
import com.sulthonuladib.keroid.data.Machine
import com.sulthonuladib.keroid.ui.components.AddFab
import com.sulthonuladib.keroid.ui.components.AgentBadge
import com.sulthonuladib.keroid.ui.components.CountBadge
import com.sulthonuladib.keroid.ui.components.CookieTile
import com.sulthonuladib.keroid.ui.components.GroupDivider
import com.sulthonuladib.keroid.ui.components.GroupedCard
import com.sulthonuladib.keroid.ui.components.LargeHeader
import com.sulthonuladib.keroid.ui.components.ProjectHeader
import com.sulthonuladib.keroid.ui.components.SegmentedGroup
import com.sulthonuladib.keroid.ui.components.SessionStatusLabel
import com.sulthonuladib.keroid.ui.components.StatusDot
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.KeroidTheme
import com.sulthonuladib.keroid.ui.theme.keroid

private enum class SessionFilter(val label: String) {
    All("All"),
    Attention("Needs you"),
    Working("Working"),
}

/**
 * Sessions, rebuilt from the Doop design: one large title, a cookie-tile
 * machine rail, a segmented filter and compact grouped rows. A session is a
 * 66 dp row — no preview, no card per row — and the FAB opens a three-action
 * menu instead of a dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var newSessionSheet by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SessionFilter.All.name) }
    var foldedProjects by rememberSaveable { mutableStateOf("") }
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
    val counts = remember(state.sessions) {
        listOf(
            state.sessions.size,
            state.sessions.count { it.needsAttention },
            state.sessions.count { it.status == "working" },
        )
    }
    val other = remember(state.projects, state.sessions) {
        val known = state.projects.map { it.id }.toSet()
        if (state.sessions.any { it.project !in known }) listOf(KeroProject("", "Other sessions")) else emptyList()
    }
    val groups = state.projects + other
    val folded = remember(foldedProjects) {
        foldedProjects.split(',').filter { it.isNotBlank() }.toSet()
    }
    val toggleProject: (String) -> Unit = { id ->
        foldedProjects = if (id in folded) (folded - id).joinToString(",") else (folded + id).joinToString(",")
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxHeight()) {
            if (searching) {
                SearchHeader(query, onQuery = { query = it }, onClose = { searching = false; query = "" })
            } else {
                LargeHeader(
                    title = "Sessions",
                    titleTag = "sessions-title",
                    subtitle = {
                        if (machine != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(machine.name, fontFamily = KeroidMono, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                Text(if (machine.isTailscale) "Tailscale" else "SSH",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                Text(
                                    if (connection.connected) "connected" else connection.status.label.lowercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (connection.connected) MaterialTheme.keroid.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text("No machine selected", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Outlined.Search, "Search sessions")
                        }
                        ConnectionMenu(
                            connection = connection,
                            onDisconnect = { machine?.let { onDisconnect(it.id) } },
                            onManage = onConnections,
                            onRetry = onRetryStorage,
                        )
                    },
                )
            }

            if (ui.restoring) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
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
            ui.actions[machine?.id]?.error?.let { message ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ErrorCard(message) }
            }

            when {
                machine == null && !ui.restoring && ui.libraryError == null -> EmptyState(
                    title = "No machines yet",
                    body = "Save a machine and an SSH identity to see its sessions here.",
                    action = "Add a machine",
                    onAction = onAddMachine,
                )

                machine != null -> {
                    if (!searching) {
                        MachineRail(
                            ui = ui,
                            selected = machine.id,
                            enabled = enabled,
                            onSelect = onSelectMachine,
                            onAdd = onAddMachine,
                        )
                        SegmentedGroup(
                            options = SessionFilter.entries.map { it.label },
                            counts = counts,
                            selected = filter.ordinal,
                            onSelect = { filterName = SessionFilter.entries[it].name },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    } else {
                        SegmentedGroup(
                            options = SessionFilter.entries.map { it.label },
                            counts = counts,
                            selected = filter.ordinal,
                            onSelect = { filterName = SessionFilter.entries[it].name },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    if (connection.connected) {
                        SessionList(
                            machineId = machine.id,
                            state = state,
                            groups = groups,
                            sessions = sessions,
                            folded = folded,
                            searching = searching,
                            onToggleProject = toggleProject,
                            onSession = onSession,
                            onNewSession = onNewSession,
                            creating = ui.actions[machine.id]?.busy == true,
                            onAction = { session, kind -> actionSessionId = session.id; actionKind = kind.name },
                        )
                    } else {
                        ConnectPanel(
                            machine = machine,
                            connection = connection,
                            enabled = enabled,
                            onConnect = { onConnect(machine.id) },
                            onDisconnect = { onDisconnect(machine.id) },
                        )
                    }
                }
            }
        }

        if (machine != null && connection.connected && !searching) {
            AddFab(
                onClick = { createMenu = true },
                contentDescription = "New session or project",
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }

        if (createMenu) FabMenu(
            onDismiss = { createMenu = false },
            onNewSession = { createMenu = false; newSessionSheet = true },
            onOpenProject = {
                createMenu = false
                machine?.let { onOpenProject(it.id) }
            },
            onAddMachine = { createMenu = false; onAddMachine() },
        )

        if (newSessionSheet && machine != null) {
            NewSessionSheet(
                machine = machine,
                projects = state.projects,
                onDismiss = { newSessionSheet = false },
                onStart = { project ->
                    newSessionSheet = false
                    onNewSession(machine.id, project)
                },
                onOpenProject = {
                    newSessionSheet = false
                    onOpenProject(machine.id)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHeader(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close search") }
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Search sessions") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.keroid.sunken,
                    unfocusedContainerColor = MaterialTheme.keroid.sunken,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Outlined.Close, "Clear search")
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
    }
}

@Composable
private fun MachineRail(
    ui: MainUiState,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.height((62 + 14 * LocalDensity.current.fontScale.coerceIn(1f, 2f)).dp),
    ) {
        items(ui.machines, key = { it.id }) { machine ->
            val connected = ui.connection(machine.id).connected
            Column(
                Modifier.width(60.dp).clickable(enabled = enabled) { onSelect(machine.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CookieTile(machine.name, selected = machine.id == selected, connected = connected)
                Text(
                    machine.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (machine.id == selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item(key = "add-machine") {
            Column(
                Modifier.width(60.dp).clickable(enabled = enabled, onClick = onAdd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Surface(
                    onClick = onAdd,
                    enabled = enabled,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.keroid.raised,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, "Add machine", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Add", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConnectionMenu(
    connection: MachineUiState,
    onDisconnect: () -> Unit,
    onManage: () -> Unit,
    onRetry: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "Session list actions") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Machines and identities") }, onClick = { open = false; onManage() })
            if (connection.connected) DropdownMenuItem(
                text = { Text("Disconnect") },
                onClick = { open = false; onDisconnect() },
            )
            if (connection.status == ConnectionStatus.Error) DropdownMenuItem(
                text = { Text("Retry saved connections") },
                onClick = { open = false; onRetry() },
            )
        }
    }
}

@Composable
private fun SessionList(
    machineId: String,
    state: KeroState,
    groups: List<KeroProject>,
    sessions: List<KeroSession>,
    folded: Set<String>,
    searching: Boolean,
    onToggleProject: (String) -> Unit,
    onSession: (String, String) -> Unit,
    onNewSession: (String, String) -> Unit,
    creating: Boolean,
    onAction: (KeroSession, SessionActionKind) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sessions.isEmpty()) {
            item(key = "empty") {
                Text(
                    if (state.sessions.isEmpty()) "No sessions on this machine." else "No matching sessions.",
                    modifier = Modifier.padding(vertical = 28.dp, horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (searching) {
            items(sessions, key = { "flat:${it.id}" }) { session ->
                GroupedCard {
                    SessionRow(session, enabled = !creating,
                        onClick = { onSession(machineId, session.id) },
                        onAction = { onAction(session, it) })
                }
            }
            return@LazyColumn
        }
        val known = state.projects.map { it.id }.toSet()
        groups.forEach { project ->
            val groupSessions = sessions.filter {
                if (project.id.isEmpty()) it.project !in known else it.project == project.id
            }
            if (groupSessions.isEmpty()) return@forEach
            val isFolded = project.id in folded
            item(key = "project:$machineId:${project.id}") {
                if (isFolded) {
                    FoldedProjectRow(
                        project = project,
                        sessions = groupSessions,
                        onToggle = { onToggleProject(project.id) },
                    )
                } else {
                    ProjectHeader(
                        name = project.name,
                        count = groupSessions.size,
                        expanded = true,
                        onToggle = { onToggleProject(project.id) },
                        onAdd = if (project.id.isNotEmpty()) {
                            { onNewSession(machineId, project.id) }
                        } else null,
                    )
                }
            }
            if (!isFolded) {
                item(key = "rows:$machineId:${project.id}") {
                    GroupedCard {
                        groupSessions.forEachIndexed { index, session ->
                            if (index > 0) GroupDivider()
                            SessionRow(session, enabled = !creating,
                                onClick = { onSession(machineId, session.id) },
                                onAction = { onAction(session, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldedProjectRow(project: KeroProject, sessions: List<KeroSession>, onToggle: () -> Unit) {
    val waiting = sessions.count { it.needsAttention }
    val working = sessions.count { it.status == "working" }
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .background(MaterialTheme.keroid.raised, RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(project.name, style = MaterialTheme.typography.titleSmall)
        Text("${sessions.size}", fontFamily = KeroidMono, fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        val (label, color) = when {
            waiting > 0 -> "$waiting needs you" to MaterialTheme.keroid.attention
            working > 0 -> "$working working" to MaterialTheme.colorScheme.primary
            else -> "${sessions.size} idle" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun SessionRow(
    session: KeroSession,
    enabled: Boolean,
    onClick: () -> Unit,
    onAction: (SessionActionKind) -> Unit,
) {
    var menu by rememberSaveable(session.id) { mutableStateOf(false) }
    val colors = MaterialTheme.keroid
    val dot = when {
        session.needsAttention -> colors.attention
        session.status == "working" -> MaterialTheme.colorScheme.primary
        session.status == "error" -> colors.danger
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 66.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(8.dp).background(dot, androidx.compose.foundation.shape.CircleShape),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    session.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 1.dp),
                ) {
                    AgentBadge(session.agent)
                    val meta = session.message.ifBlank {
                        session.dir.takeIf { it.isNotBlank() } ?: "${session.panes.size} panes"
                    }
                    Text(
                        meta,
                        fontFamily = KeroidMono,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SessionStatusLabel(session.status)
                val second = when {
                    session.status == "working" && session.progress in 0..100 -> "${session.progress}%"
                    session.status == "working" -> "working"
                    session.panes.size > 1 -> "${session.panes.size} panes"
                    session.unread -> "new"
                    else -> ""
                }
                if (second.isNotEmpty()) Text(
                    second,
                    fontFamily = KeroidMono,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.MoreVert, "Actions for ${session.label}",
                        modifier = Modifier.size(18.dp), tint = colors.quiet)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename session") }, enabled = enabled,
                        onClick = { menu = false; onAction(SessionActionKind.Rename) })
                    DropdownMenuItem(text = { Text("Close session") }, enabled = enabled,
                        onClick = { menu = false; onAction(SessionActionKind.CloseSession) })
                }
            }
        }
        if (session.status == "working") {
            Box(
                Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp)
                    .fillMaxWidth().height(3.dp)
                    .background(colors.progressTrack, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
            ) {
                Box(
                    Modifier.fillMaxWidth(if (session.progress in 0..100) session.progress / 100f else 0.35f)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
                )
            }
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.keroid.window,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DotFor(connection.status)
                Text(connection.status.label, style = MaterialTheme.typography.titleMedium)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val busy = connection.status == ConnectionStatus.Connecting ||
                    connection.status == ConnectionStatus.VerifyHost
                if (busy) {
                    TextButton(onClick = onDisconnect, enabled = enabled) { Text("Cancel") }
                } else {
                    Button(onClick = onConnect, enabled = enabled) {
                        Icon(Icons.Outlined.Link, null, modifier = Modifier.size(18.dp))
                        Text("Connect", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DotFor(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.Connected -> MaterialTheme.keroid.success
        ConnectionStatus.Connecting, ConnectionStatus.VerifyHost -> MaterialTheme.keroid.attention
        ConnectionStatus.Error -> MaterialTheme.keroid.danger
        ConnectionStatus.Offline -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
}

@Composable
private fun FabMenu(
    onDismiss: () -> Unit,
    onNewSession: () -> Unit,
    onOpenProject: () -> Unit,
    onAddMachine: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FabMenuItem("Open project…", Icons.Outlined.FolderOpen, onOpenProject)
            FabMenuItem("New session", Icons.Outlined.Terminal, onNewSession)
            FabMenuItem("Add machine", Icons.Outlined.Add, onAddMachine)
            Surface(
                onClick = onDismiss,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.keroid.high,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Close menu") }
            }
        }
    }
}

@Composable
private fun FabMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.keroid.high) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(48.dp)
                .background(MaterialTheme.keroid.high, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    machine: Machine,
    projects: List<KeroProject>,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
    onOpenProject: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.keroid.window,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("New session", style = MaterialTheme.typography.headlineSmall)
            Text(machine.name, fontFamily = KeroidMono, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            GroupedCard {
                SheetRow("Shell", "Start in the default project", Icons.Outlined.Terminal) { onStart("") }
                projects.forEach { project ->
                    GroupDivider()
                    SheetRow(project.name, project.dir.ifBlank { "Project" }, Icons.Outlined.FolderOpen) {
                        onStart(project.id)
                    }
                }
                GroupDivider()
                SheetRow("Open project…", "Browse folders on ${machine.name}", Icons.Outlined.FolderOpen, onOpenProject)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val previewState = KeroState(
    projects = listOf(KeroProject("project", "keroid")),
    sessions = listOf(
        KeroSession(
            "review", "project", name = "Host-key pin on reconnect", agent = "codex", status = "waiting",
            message = "3 files changed · tests pass", unread = true,
        ),
        KeroSession(
            "build", "project", name = "Android build · smoke", agent = "claude", status = "working",
            message = "24 tests passed · installing", progress = 68,
        ),
        KeroSession("shell", "project", name = "Polish the terminal key dock", agent = "codex", status = "idle",
            message = "8 files changed · 214 insertions"),
    ),
)

@Preview(name = "Sessions · dark", widthDp = 390, heightDp = 844, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Sessions · light", widthDp = 390, heightDp = 844)
@Preview(name = "Sessions · large text", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Preview(name = "Sessions · tablet", widthDp = 900, heightDp = 1000)
@Composable
private fun HomePreview() {
    KeroidTheme(dynamicColor = false) {
        HomeScreen(
            MainUiState(
                restoring = false,
                machines = listOf(
                    Machine("dev", "Dev machine", "devbox", "alice", "key"),
                    Machine("lab", "Homelab", "homelab", "alice", "key"),
                ),
                selectedMachineId = "dev",
                connections = mapOf("dev" to MachineUiState(ConnectionStatus.Connected, previewState)),
            ),
            {}, { _, _ -> }, {}, {}, {}, {},
        )
    }
}

@Preview(name = "Sessions · empty", widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() {
    KeroidTheme(dynamicColor = false) {
        HomeScreen(MainUiState(restoring = false), {}, { _, _ -> }, {}, {}, {}, {})
    }
}
