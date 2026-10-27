package com.sulthonuladib.keroid

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.util.UUID
import com.sulthonuladib.keroid.ui.main.*
import com.sulthonuladib.keroid.ui.settings.SettingsScreen
import com.sulthonuladib.keroid.ui.settings.TerminalAppearanceScreen
import com.sulthonuladib.keroid.ui.settings.TerminalThemePickerScreen
import com.sulthonuladib.keroid.term.GhosttyTerminal
import com.sulthonuladib.keroid.term.TerminalProbeScreen

@Composable
fun MainNavigation(viewModel: MainViewModel, dark: Boolean) {
    val backStack = rememberNavBackStack(Main)
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val key by viewModel.privateKey.collectAsStateWithLifecycle()
    val passphrase by viewModel.passphrase.collectAsStateWithLifecycle()
    val active = backStack.lastOrNull()
    ProtectSecrets(active is IdentityEditor || state.unlock != null)
    LaunchedEffect(active) {
        if (active !is IdentityEditor) viewModel.discardIdentityDraft()
    }
    val back: () -> Unit = {
        if (!(state.saving && (active is MachineEditor || active is IdentityEditor)) && backStack.size > 1) {
            if (active is IdentityEditor) viewModel.discardIdentityDraft()
            if (active is MachineEditor) viewModel.discardMachineDraft()
            backStack.removeLastOrNull()
        }
    }
    val editMachine: (String?) -> Unit = { id ->
        val machineId = id ?: UUID.randomUUID().toString()
        viewModel.beginMachine(machineId)
        backStack.add(MachineEditor(machineId))
    }
    val editIdentity: (String?) -> Unit = { id ->
        val identityId = id ?: UUID.randomUUID().toString()
        viewModel.beginIdentity(identityId)
        backStack.add(IdentityEditor(identityId))
    }
    val tabs = active is Main || active is Connections
    val switchTab: (Boolean) -> Unit = { toHome ->
        if (toHome) {
            if (active !is Main) while (backStack.size > 1) backStack.removeLastOrNull()
        } else if (active !is Connections) {
            backStack.add(Connections)
        }
    }
    BoxWithConstraints {
    // Wide windows get a rail, which also keeps the list from stretching across a
    // tablet or a landscape phone. Rotation and resizing both land here.
    val wide = maxWidth >= 600.dp
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (tabs && !wide) {
                NavigationBar {
                    NavigationBarItem(
                        selected = active is Main,
                        onClick = { switchTab(true) },
                        modifier = Modifier.testTag("tab-sessions"),
                        icon = {
                            Icon(
                                if (active is Main) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = null,
                            )
                        },
                        label = { Text("Sessions") },
                    )
                    NavigationBarItem(
                        selected = active is Connections,
                        onClick = { switchTab(false) },
                        modifier = Modifier.testTag("tab-machines"),
                        icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                        label = { Text("Machines") },
                    )
                }
            }
        },
    ) { navPadding ->
    Row(Modifier.fillMaxSize().padding(navPadding).consumeWindowInsets(navPadding)) {
    if (tabs && wide) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            windowInsets = WindowInsets(0),
        ) {
            NavigationRailItem(
                selected = active is Main,
                onClick = { switchTab(true) },
                modifier = Modifier.testTag("tab-sessions"),
                icon = {
                    Icon(
                        if (active is Main) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = null,
                    )
                },
                label = { Text("Sessions") },
            )
            NavigationRailItem(
                selected = active is Connections,
                onClick = { switchTab(false) },
                modifier = Modifier.testTag("tab-machines"),
                icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                label = { Text("Machines") },
            )
        }
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.weight(1f),
        onBack = back,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Main> {
                HomeScreen(state, viewModel::selectMachine,
                    onSession = { machine, session -> backStack.add(SessionDetail(machine, session)) },
                    onAddMachine = { editMachine(null) },
                    onConnections = { backStack.add(Connections) },
                    onConnect = viewModel::connect, onDisconnect = viewModel::disconnect,
                    onRetryStorage = viewModel::restore,
                    onOpenProject = { backStack.add(ProjectBrowser(it)) },
                    onNewSession = { machine, project -> viewModel.newSession(machine, project) {
                        if (backStack.lastOrNull() is Main) backStack.add(SessionDetail(machine, it.id))
                    } },
                    onRenameSession = viewModel::renameSession,
                    onCloseSession = viewModel::closeSession)
            }
            entry<Connections> {
                ConnectionsScreen(state, back, onSelect = { viewModel.selectMachine(it); back() },
                    onMachine = editMachine, onIdentity = editIdentity,
                    onSettings = { backStack.add(Settings) })
            }
            entry<Settings> {
                val theme by viewModel.theme.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val debuggable =
                    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val terminalTest: (() -> Unit)? =
                    if (debuggable && GhosttyTerminal.available) {
                        { backStack.add(TerminalProbe) }
                    } else {
                        null
                    }
                SettingsScreen(theme, viewModel::setTheme, back, onTerminalTest = terminalTest,
                    onTerminalAppearance = { backStack.add(TerminalAppearanceSettings) })
            }
            entry<TerminalProbe> {
                val appearance by viewModel.terminalAppearance.collectAsStateWithLifecycle()
                TerminalProbeScreen(onBack = back, appearance = appearance, dark = dark)
            }
            entry<TerminalAppearanceSettings> {
                val appearance by viewModel.terminalAppearance.collectAsStateWithLifecycle()
                TerminalAppearanceScreen(appearance, dark, viewModel::setTerminalAppearance,
                    onPickTheme = { isDark -> backStack.add(TerminalThemePicker(isDark)) }, onBack = back)
            }
            entry<TerminalThemePicker> { route ->
                val appearance by viewModel.terminalAppearance.collectAsStateWithLifecycle()
                TerminalThemePickerScreen(route.dark,
                    selected = if (route.dark) appearance.darkTheme else appearance.lightTheme,
                    onSelect = { name ->
                        viewModel.setTerminalAppearance(if (route.dark) appearance.copy(darkTheme = name)
                        else appearance.copy(lightTheme = name))
                    }, onBack = back)
            }
            entry<MachineEditor> { route ->
                LaunchedEffect(route.machineId, state.restoring) { if (!state.restoring) viewModel.beginMachine(route.machineId) }
                MachineEditorScreen(state, state.machines.firstOrNull { it.id == route.machineId },
                    viewModel::updateDraft, back, onAddIdentity = { editIdentity(null) },
                    onSave = { viewModel.saveMachine(route.machineId) { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() } },
                    onDelete = { viewModel.deleteMachine(route.machineId) { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() } })
            }
            entry<IdentityEditor> { route ->
                val existing = state.identities.firstOrNull { it.id == route.identityId }
                var name by rememberSaveable(route.identityId) { mutableStateOf(existing?.name.orEmpty()) }
                var nameInitialized by rememberSaveable(route.identityId) { mutableStateOf(existing != null || !state.restoring) }
                // Load the initial name once, but don't replace an intentionally blank draft.
                LaunchedEffect(route.identityId, existing?.id) {
                    if (!nameInitialized && existing != null) { name = existing.name; nameInitialized = true }
                }
                LaunchedEffect(route.identityId) { viewModel.beginIdentity(route.identityId) }
                val done: () -> Unit = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() }
                IdentityEditorScreen(existing, name, key, state.saving || state.restoring || state.libraryError != null, state.editorError,
                    onName = { name = it; nameInitialized = true }, onKey = { viewModel.updateKey(route.identityId, it) },
                    onError = viewModel::reportEditorError, onBack = back,
                    onSave = { viewModel.saveIdentity(route.identityId, name, done) },
                    onDelete = { viewModel.deleteIdentity(route.identityId, done) })
            }
            entry<ProjectBrowser> { route ->
                ProjectBrowserScreen(route.machineId,
                    state.machines.firstOrNull { it.id == route.machineId }?.name ?: "Removed machine",
                    state.connection(route.machineId), state.browser,
                    state.actions[route.machineId] ?: SessionActionState(), back,
                    onBrowse = { dir, hidden -> viewModel.browseDirectory(route.machineId, dir, hidden) },
                    onOpen = { dir -> viewModel.openProject(route.machineId, dir) { created ->
                        if (backStack.lastOrNull() == route) {
                            backStack.removeLastOrNull()
                            backStack.add(SessionDetail(route.machineId, created.id))
                        }
                    } })
            }
            entry<SessionDetail> { route ->
                PaneScreen(route.machineId, state.machines.firstOrNull { it.id == route.machineId }?.name ?: "Removed machine",
                    state.connection(route.machineId).state.sessions.firstOrNull { it.id == route.sessionId }, state,
                    terminalAppearance = viewModel.terminalAppearance.collectAsStateWithLifecycle().value,
                    darkTerminal = dark,
                    onBack = { if (backStack.lastOrNull() == route) back() },
                    onStartReading = viewModel::startReading, onStopReading = viewModel::stopReading,
                    onInput = viewModel::updatePaneDraft, onSend = viewModel::send,
                    terminalOutput = viewModel.terminalOutput,
                    onStartTerminal = viewModel::startTerminal, onStopTerminal = viewModel::stopTerminal,
                    onTerminalInput = viewModel::terminalInput, onTerminalResize = viewModel::terminalResize,
                    onTerminalActive = viewModel::terminalActive,
                    onNewSession = { target -> viewModel.newSession(route.machineId, from = target) {
                        if (backStack.lastOrNull() == route) backStack.add(SessionDetail(route.machineId, it.id))
                    } },
                    onSplit = { target, direction, select -> viewModel.splitPane(target, direction) { updated ->
                        if (backStack.lastOrNull() == route) {
                            select(updated.focus.ifBlank { updated.panes.lastOrNull()?.id.orEmpty() })
                        }
                    } },
                    onRenameSession = { name, done -> viewModel.renameSession(route.machineId, route.sessionId, name, done) },
                    onCloseSession = { done -> viewModel.closeSession(route.machineId, route.sessionId) {
                        done()
                        if (backStack.lastOrNull() == route) backStack.removeLastOrNull()
                    } },
                    onClosePane = viewModel::closePane)
            }
        },
    )
    }
    }
    }
    ConnectionPrompts(state, passphrase, viewModel::updatePassphrase, viewModel::unlockAndConnect,
        viewModel::cancelUnlock, viewModel::trustHost, viewModel::dismissHost)
}
