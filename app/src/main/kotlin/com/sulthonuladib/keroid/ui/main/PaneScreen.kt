package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.sulthonuladib.keroid.data.KeroPane
import com.sulthonuladib.keroid.data.KeroSession
import com.sulthonuladib.keroid.term.*
import com.sulthonuladib.keroid.ui.components.AgentBadge
import com.sulthonuladib.keroid.ui.components.SessionStatusLabel
import com.sulthonuladib.keroid.ui.theme.KeroidMono
import com.sulthonuladib.keroid.ui.theme.keroid
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaneScreen(
    machineId: String,
    machineName: String,
    session: KeroSession?,
    state: MainUiState,
    onBack: () -> Unit,
    onStartReading: (PaneTarget) -> Unit,
    onStopReading: (PaneTarget) -> Unit,
    onInput: (PaneTarget, String) -> Unit,
    onSend: (PaneTarget, String, () -> Unit) -> Unit,
    terminalOutput: SharedFlow<TerminalOutput>,
    onStartTerminal: (PaneTarget, Int, Int) -> Unit,
    onStopTerminal: (PaneTarget) -> Unit,
    onTerminalInput: (PaneTarget, ByteArray) -> Unit,
    onTerminalResize: (PaneTarget, Int, Int, Int, Int) -> Unit,
    onTerminalActive: (PaneTarget, Boolean) -> Unit,
    terminalAppearance: TerminalAppearance = TerminalAppearance(),
    darkTerminal: Boolean = true,
    onNewSession: (PaneTarget) -> Unit = {},
    onSplit: (PaneTarget, String, (String) -> Unit) -> Unit = { _, _, _ -> },
    onRenameSession: (String, () -> Unit) -> Unit = { _, _ -> },
    onCloseSession: (() -> Unit) -> Unit = {},
    onClosePane: (PaneTarget, () -> Unit) -> Unit = { _, _ -> },
) {
    var selectedId by rememberSaveable(machineId, session?.id) {
        mutableStateOf(session?.focus?.ifBlank { session.lead }.orEmpty())
    }
    val pane = session?.panes?.firstOrNull { it.id == selectedId }
        ?: session?.panes?.firstOrNull { it.id == session.focus } ?: session?.panes?.firstOrNull()
    val target = PaneTarget(machineId, session?.id.orEmpty(), pane?.id.orEmpty())
    val connection = state.connection(machineId)
    val output = state.panes[target] ?: PaneUiState()
    val action = state.actions[machineId] ?: SessionActionState()
    val readable = pane != null && pane.url.isBlank() && connection.connected
    val scope = rememberCoroutineScope()
    val term = remember(target, readable, connection.revision) {
        if (GhosttyTerminal.available && readable) TerminalSession(80, 24, scope) else null
    }
    var terminalEnded by remember(term) { mutableStateOf(false) }
    var measuredGrid by remember(term) { mutableStateOf<TerminalGeometry?>(null) }
    var fitToScreen by rememberSaveable(machineId, session?.id) { mutableStateOf(false) }
    var keysOpen by rememberSaveable(machineId, session?.id) { mutableStateOf(false) }
    var ctrl by remember(target) { mutableStateOf(false) }
    var inputContext by remember(target) { mutableIntStateOf(0) }
    var showKeyboard by remember(target) { mutableIntStateOf(0) }
    var panX by rememberSaveable(target) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(target) { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    var actionKind by rememberSaveable(machineId, session?.id) { mutableStateOf<String?>(null) }
    var actionPaneId by rememberSaveable(machineId, session?.id) { mutableStateOf("") }
    LaunchedEffect(connection.connected) { if (!connection.connected) actionKind = null }
    LaunchedEffect(session?.id, connection.connected) {
        if (session == null && connection.connected) onBack()
    }
    val dialogPane = session?.panes?.firstOrNull { it.id == actionPaneId }
    if (actionKind != null && session != null && connection.connected &&
        (actionKind != SessionActionKind.ClosePane.name || dialogPane != null)) {
        SessionActionDialog(
            kind = SessionActionKind.valueOf(actionKind!!), machineName = machineName,
            session = session, pane = dialogPane, action = action, enabled = true,
            onDismiss = { actionKind = null },
            onConfirm = { name ->
                val done = { actionKind = null }
                when (SessionActionKind.valueOf(actionKind!!)) {
                    SessionActionKind.Rename -> onRenameSession(name, done)
                    SessionActionKind.CloseSession -> onCloseSession(done)
                    SessionActionKind.ClosePane -> onClosePane(PaneTarget(machineId, session.id, actionPaneId), done)
                }
            },
        )
    }
    DisposableEffect(target, term) {
        onDispose { onStopTerminal(target); term?.close() }
    }
    LifecycleResumeEffect(target, term, terminalEnded) {
        if (term != null && !terminalEnded) onTerminalActive(target, true)
        onPauseOrDispose { onTerminalActive(target, false) }
    }
    LaunchedEffect(target, term) {
        if (term == null) return@LaunchedEffect
        terminalOutput.onSubscription {
            // An immediate welcome must not arrive before its subscriber exists.
            // Subscribe first, but attach only after settled measurement. This
            // avoids an 80x24 attach/replay immediately followed by another one.
            val grid = snapshotFlow { measuredGrid }.filterNotNull().first()
            onStartTerminal(target, grid.cols, grid.rows)
        }.collect { chunk ->
            if (chunk.target != target) return@collect
            when (chunk.kind) {
                's'.code.toByte(), 'z'.code.toByte() -> if (chunk.data.size == 4) {
                    val cols = ((chunk.data[0].toInt() and 255) shl 8) or (chunk.data[1].toInt() and 255)
                    val rows = ((chunk.data[2].toInt() and 255) shl 8) or (chunk.data[3].toInt() and 255)
                    term.adoptSize(cols, rows)
                }
                'o'.code.toByte() -> term.offer(chunk.data)
                'c'.code.toByte() -> terminalEnded = true
            }
        }
    }
    LifecycleStartEffect(target, readable, term, terminalEnded) {
        if (readable && (term == null || terminalEnded)) onStartReading(target)
        onStopOrDispose { onStopReading(target) }
    }
    val split: (String) -> Unit = { direction ->
        menu = false
        onSplit(target, direction) { selectedId = it }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(session?.label ?: "Session ended", maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleLarge)
                        if (pane != null) SessionStatusLabel(pane.status)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(machineName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("·", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text(
                            session?.dir?.takeIf { it.isNotBlank() } ?: "~",
                            fontFamily = KeroidMono, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(Modifier.size(6.dp).background(
                            if (connection.connected) MaterialTheme.keroid.success else MaterialTheme.colorScheme.outline,
                            androidx.compose.foundation.shape.CircleShape))
                        Text(if (connection.connected) "attached" else "disconnected",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connection.connected) MaterialTheme.keroid.success
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Terminal actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New session") }, enabled = readable && !action.busy,
                            onClick = { menu = false; onNewSession(target) })
                        DropdownMenuItem(text = { Text("Split right · new pane tab") }, enabled = readable && !action.busy,
                            onClick = { split("right") })
                        DropdownMenuItem(text = { Text("Split down · new pane tab") }, enabled = readable && !action.busy,
                            onClick = { split("down") })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Rename session") },
                            enabled = session != null && connection.connected && !action.busy,
                            onClick = { menu = false; actionKind = SessionActionKind.Rename.name })
                        if (session != null && session.panes.size > 1) {
                            DropdownMenuItem(text = { Text("Close pane") },
                                enabled = pane != null && connection.connected && !action.busy,
                                onClick = { menu = false; actionPaneId = target.paneId; actionKind = SessionActionKind.ClosePane.name })
                        }
                        DropdownMenuItem(text = { Text("Close session") },
                            enabled = session != null && connection.connected && !action.busy,
                            onClick = { menu = false; actionKind = SessionActionKind.CloseSession.name })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(if (fitToScreen) "Readable size" else "Fit grid to screen") },
                            onClick = { fitToScreen = !fitToScreen; panX = 0f; panY = 0f; menu = false })
                        DropdownMenuItem(text = { Text(if (keysOpen) "Hide extra keys" else "Show extra keys") },
                            onClick = { keysOpen = !keysOpen; menu = false })
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (session == null || pane == null) {
                Text("This session is no longer running.", modifier = Modifier.padding(24.dp))
                return@Column
            }
            // A single pane needs no tab strip. Multiple panes remain tabs, even
            // when the desktop represents the same session as a split layout.
            if (session.panes.size > 1) Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    session.panes.forEachIndexed { index, item ->
                        PaneTab(
                            label = "${index + 1} · ${item.label}",
                            status = item.status,
                            selected = pane.id == item.id,
                            onClick = { selectedId = item.id },
                        )
                    }
                }
                Surface(
                    onClick = { split("right") },
                    enabled = readable && !action.busy,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.keroid.raised,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, "Add pane", modifier = Modifier.size(20.dp))
                    }
                }
            }
            action.error?.let { ErrorCard(it) }
            output.error?.let { ErrorCard(it) }
            if (action.busy || output.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (pane.url.isNotEmpty()) {
                Text("Browser panes aren’t supported yet.", modifier = Modifier.padding(24.dp))
                return@Column
            }
            if (term != null && !terminalEnded) {
                val terminalTheme = remember(terminalAppearance, darkTerminal) {
                    terminalAppearance.theme(darkTerminal)
                }
                val terminalFamily = remember(terminalAppearance.font) {
                    terminalFontFamily(terminalAppearance.font)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(terminalTheme.background),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        TerminalChrome(
                            pane = pane,
                            session = session,
                            grid = measuredGrid,
                            connected = readable,
                            theme = terminalTheme,
                        )
                        TerminalDockLayout(
                            Modifier.weight(1f).fillMaxWidth().testTag("terminal-canvas"),
                            dock = {
                                key(target) {
                                    TerminalKeyBar(expanded = keysOpen, onToggle = { keysOpen = !keysOpen },
                                        ctrl = ctrl, onCtrl = { ctrl = it },
                                        onBytes = { bytes -> term.input(bytes) { onTerminalInput(target, it) } },
                                        onKey = { key ->
                                            inputContext++
                                            term.key(key) { onTerminalInput(target, it) }
                                        },
                                        modifier = Modifier.padding(8.dp))
                                }
                            },
                        ) { bottomOcclusion ->
                            TerminalView(session = term, modifier = Modifier.padding(horizontal = 4.dp),
                                fontFamily = terminalFamily, fontSize = terminalAppearance.fontSize.sp,
                                lineHeight = terminalAppearance.lineHeightSp.sp, theme = terminalTheme,
                                fitToScreen = fitToScreen, pan = Offset(panX, panY), bottomOcclusion = bottomOcclusion,
                                onPanClamped = { panX = it.x; panY = it.y }, onTap = { showKeyboard++ },
                                onGrid = { cols, rows, cw, ch ->
                                    onTerminalResize(target, cols, rows, cw, ch)
                                    measuredGrid = TerminalGeometry(cols, rows, cw, ch)
                                })
                            TerminalInputLayer(ctrl = ctrl, resetContext = inputContext, showKeyboard = showKeyboard,
                                onBytes = { bytes -> term.input(bytes) { onTerminalInput(target, it) } }, onCtrlUsed = { ctrl = false },
                                onKey = { key -> term.key(key) { onTerminalInput(target, it) } },
                                modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                Text(if (terminalEnded) "Terminal closed · text snapshot" else "Text snapshot",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
                SelectionContainer(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    val snapshotTheme = remember(terminalAppearance, darkTerminal) {
                        terminalAppearance.theme(darkTerminal)
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(snapshotTheme.background),
                        modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                            Text(output.text.ifEmpty { if (output.loading) "Reading the pane…" else "No output yet." },
                                fontFamily = terminalFontFamily(terminalAppearance.font),
                                fontSize = terminalAppearance.fontSize.sp,
                                lineHeight = terminalAppearance.lineHeightSp.sp,
                                color = Color(snapshotTheme.foreground))
                        }
                    }
                }
                OutlinedTextField(value = output.draft, onValueChange = { onInput(target, it) },
                    label = { Text("Send input") }, minLines = 1, maxLines = 3,
                    enabled = connection.connected && !output.sending,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    trailingIcon = {
                        IconButton(enabled = connection.connected && output.draft.isNotBlank(), onClick = {
                            onSend(target, output.draft + "\r") { onInput(target, "") }
                        }) { Icon(Icons.AutoMirrored.Outlined.Send, "Send and press Enter") }
                    })
            }
        }
    }
}

@Composable
private fun PaneTab(label: String, status: String, selected: Boolean, onClick: () -> Unit) {
    val dot = when (status) {
        "waiting" -> MaterialTheme.keroid.attention
        "working" -> MaterialTheme.colorScheme.primary
        "error" -> MaterialTheme.keroid.danger
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.keroid.raised,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** 34 dp strip above the grid: agent, path, measured size and attach state. */
@Composable
private fun TerminalChrome(
    pane: KeroPane,
    session: KeroSession,
    grid: TerminalGeometry?,
    connected: Boolean,
    theme: TerminalTheme,
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp)
            .background(Color(theme.background))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AgentBadge(pane.agent.ifBlank { session.agent })
        Text(
            pane.dir.ifBlank { session.dir }.ifBlank { "~" },
            fontFamily = KeroidMono,
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${grid?.cols ?: 80}×${grid?.rows ?: 24}",
            fontFamily = KeroidMono,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.size(6.dp).background(
            if (connected) MaterialTheme.keroid.success else MaterialTheme.colorScheme.outline,
            androidx.compose.foundation.shape.CircleShape))
        Text(
            if (connected) "attached" else "offline",
            fontFamily = KeroidMono,
            fontSize = 9.sp,
            color = if (connected) MaterialTheme.keroid.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
