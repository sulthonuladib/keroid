package com.sulthonuladib.keroid

import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.util.UUID
import com.sulthonuladib.keroid.ui.main.*
import com.sulthonuladib.keroid.ui.settings.AppearanceScreen
import com.sulthonuladib.keroid.ui.settings.SettingsScreen
import com.sulthonuladib.keroid.ui.settings.TerminalThemePickerScreen
import com.sulthonuladib.keroid.ui.settings.TerminalTypeScreen
import com.sulthonuladib.keroid.term.GhosttyTerminal
import com.sulthonuladib.keroid.term.TerminalProbeScreen
import com.sulthonuladib.keroid.ui.theme.keroid

private enum class RootTab(val label: String, val icon: ImageVector, val tag: String, val destination: NavKey) {
    Sessions("Sessions", Icons.Outlined.Terminal, "tab-sessions", Main),
    Machines("Machines", Icons.Outlined.Devices, "tab-machines", Connections),
    SettingsTab("Settings", Icons.Outlined.Settings, "tab-settings", Settings),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainNavigation(viewModel: MainViewModel, dark: Boolean) {
    val navigationMotion = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val backStack = rememberNavBackStack(Main)
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val key by viewModel.privateKey.collectAsStateWithLifecycle()
    val passphrase by viewModel.passphrase.collectAsStateWithLifecycle()
    val appearance by viewModel.terminalAppearance.collectAsStateWithLifecycle()
    val mode by viewModel.theme.collectAsStateWithLifecycle()
    val active = backStack.lastOrNull()
    val root = RootTab.entries.firstOrNull {
        when (it) {
            RootTab.Sessions -> active is Main
            RootTab.Machines -> active is Connections
            RootTab.SettingsTab -> active is Settings
        }
    }
    ProtectSecrets(state.unlock != null)
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
    val switchTab: (RootTab) -> Unit = { tab ->
        if (root != tab) {
            while (backStack.size > 1) backStack.removeLastOrNull()
            if (tab.destination != Main) backStack.add(tab.destination)
        }
    }
    BoxWithConstraints {
    // Wide windows get a rail, which also keeps the list from stretching across a
    // tablet or a landscape phone. Rotation and resizing both land here.
    val wide = maxWidth >= 600.dp
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (root != null && !wide) {
                NavigationBar(
                    containerColor = MaterialTheme.keroid.window,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    RootTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = root == tab,
                            onClick = { switchTab(tab) },
                            modifier = Modifier.testTag(tab.tag),
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { navPadding ->
    Row(Modifier.fillMaxSize().padding(navPadding).consumeWindowInsets(navPadding)) {
    if (root != null && wide) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            windowInsets = WindowInsets(0),
            containerColor = MaterialTheme.keroid.window,
        ) {
            RootTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = root == tab,
                    onClick = { switchTab(tab) },
                    modifier = Modifier.testTag(tab.tag),
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.weight(1f),
        onBack = back,
        // Keep each destination opaque: cross-fading complete forms superimposes
        // their text and looks like the editor flashing back over the saved list.
        transitionSpec = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, navigationMotion) togetherWith
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, navigationMotion)
        },
        popTransitionSpec = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navigationMotion) togetherWith
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navigationMotion)
        },
        predictivePopTransitionSpec = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, navigationMotion) togetherWith
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, navigationMotion)
        },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<Main> {
                HomeScreen(state, viewModel::selectMachine,
                    onSession = { machine, session -> backStack.add(SessionDetail(machine, session)) },
                    onAddMachine = { editMachine(null) },
                    onConnections = { switchTab(RootTab.Machines) },
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
                    onSettings = { switchTab(RootTab.SettingsTab) })
            }
            entry<Settings> {
                val context = LocalContext.current
                val debuggable =
                    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val terminalTest: (() -> Unit)? =
                    if (debuggable && GhosttyTerminal.available) {
                        { backStack.add(TerminalProbe) }
                    } else {
                        null
                    }
                SettingsScreen(mode, viewModel::setTheme, back, onTerminalTest = terminalTest,
                    onTerminalAppearance = { backStack.add(TerminalAppearanceSettings) },
                    appearance = appearance,
                    showBack = false,
                    onTerminalType = { backStack.add(TerminalTypeSettings) })
            }
            entry<TerminalProbe> {
                TerminalProbeScreen(onBack = back, appearance = appearance, dark = dark)
            }
            entry<TerminalAppearanceSettings> {
                AppearanceScreen(appearance, dark, mode, viewModel::setTheme,
                    onChange = viewModel::setTerminalAppearance,
                    onPickTheme = { isDark -> backStack.add(TerminalThemePicker(isDark)) },
                    onBack = back,
                    onTerminalType = { backStack.add(TerminalTypeSettings) })
            }
            entry<TerminalTypeSettings> {
                TerminalTypeScreen(appearance, dark, viewModel::setTerminalAppearance, onBack = back)
            }
            entry<TerminalThemePicker> { route ->
                TerminalThemePickerScreen(route.dark,
                    selected = if (route.dark) appearance.darkTheme else appearance.lightTheme,
                    onSelect = { name ->
                        viewModel.setTerminalAppearance(if (route.dark) appearance.copy(darkTheme = name)
                        else appearance.copy(lightTheme = name))
                    }, onBack = back)
            }
            entry<MachineEditor> { route ->
                LaunchedEffect(route.machineId, state.restoring) { if (!state.restoring) viewModel.beginMachine(route.machineId) }
                // A save publishes the new library before this entry finishes
                // exiting. Do not turn Add into Edit halfway through that exit.
                val existing = remember(route.machineId, state.restoring) {
                    state.machines.firstOrNull { it.id == route.machineId }
                }
                MachineEditorScreen(state, existing,
                    viewModel::updateDraft, back, onAddIdentity = { editIdentity(null) },
                    onSave = { viewModel.saveMachine(route.machineId) { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() } },
                    onDelete = { viewModel.deleteMachine(route.machineId) { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() } })
            }
            entry<IdentityEditor> { route ->
                // NavDisplay keeps this entry composed throughout its exit.
                // Keep the window protected for exactly that visible lifetime.
                ProtectSecrets(true)
                val existing = remember(route.identityId, state.restoring) {
                    state.identities.firstOrNull { it.id == route.identityId }
                }
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
                    terminalAppearance = appearance,
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
