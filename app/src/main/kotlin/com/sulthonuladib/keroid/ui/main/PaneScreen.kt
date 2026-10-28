package com.sulthonuladib.keroid.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.sulthonuladib.keroid.data.KeroSession
import com.sulthonuladib.keroid.term.*
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
            TopAppBar(
                expandedHeight = 56.dp,
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(session?.label ?: "Session ended", maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.titleMedium)
                            if (pane != null) StatusLabel(pane.status)
                        }
                        Text(machineName + if (!connection.connected) " · Disconnected" else "",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
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
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (session == null || pane == null) {
                Text("This session is no longer running.", modifier = Modifier.padding(24.dp))
                return@Column
            }
            // A single pane needs no tab strip. Multiple panes remain tabs, even
            // when the desktop represents the same session as a split layout.
            if (session.panes.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    session.panes.forEachIndexed { index, item ->
                        FilterChip(selected = pane.id == item.id, onClick = { selectedId = item.id },
                            label = { Text("${index + 1} · ${item.label}", maxLines = 1) })
                    }
                }
                IconButton(onClick = { split("right") }, enabled = readable && !action.busy) {
                    Icon(Icons.Outlined.Add, "Add pane")
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
                TerminalDockLayout(Modifier.weight(1f).fillMaxWidth().testTag("terminal-canvas"), dock = {
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
                }) { bottomOcclusion ->
                    TerminalView(session = term, modifier = Modifier.padding(2.dp),
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
            } else {
                Text(if (terminalEnded) "Terminal closed · text snapshot" else "Text snapshot",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    val snapshotTheme = remember(terminalAppearance, darkTerminal) {
                        terminalAppearance.theme(darkTerminal)
                    }
                    Box(Modifier.fillMaxSize().background(Color(snapshotTheme.background))
                        .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(8.dp)) {
                        Text(output.text.ifEmpty { if (output.loading) "Reading the pane…" else "No output yet." },
                            fontFamily = terminalFontFamily(terminalAppearance.font),
                            fontSize = terminalAppearance.fontSize.sp,
                            lineHeight = terminalAppearance.lineHeightSp.sp,
                            color = Color(snapshotTheme.foreground))
                    }
                }
                OutlinedTextField(value = output.draft, onValueChange = { onInput(target, it) },
                    label = { Text("Send input") }, minLines = 1, maxLines = 3,
                    enabled = connection.connected && !output.sending, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = connection.connected && output.draft.isNotBlank(), onClick = {
                            onSend(target, output.draft + "\r") { onInput(target, "") }
                        }) { Icon(Icons.AutoMirrored.Outlined.Send, "Send and press Enter") }
                    })
            }
        }
    }
}
