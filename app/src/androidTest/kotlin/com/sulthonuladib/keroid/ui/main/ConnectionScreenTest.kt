package com.sulthonuladib.keroid.ui.main

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.sulthonuladib.keroid.MainNavigation
import com.sulthonuladib.keroid.data.*
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.ui.theme.KeroidTheme

class ConnectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun after() { store.clear() }

    private val actionCalls = mutableListOf<String>()
    private var mutationFails = false

    private fun viewModel(library: ConnectionLibrary = ConnectionLibrary(), initialState: KeroState = KeroState()): MainViewModel {
        val vault = object : LibraryVault {
            var current = library
            override fun load() = current
            override fun save(library: ConnectionLibrary) { current = library }
        }
        val factory = object : RemoteMachineFactory {
            override fun create(): RemoteMachine = object : RemoteMachine {
                override val state = MutableStateFlow(initialState)
                override val connectionError = MutableStateFlow<String?>(null)
                override suspend fun connect(profile: ConnectionProfile, privateKey: String, passphrase: String) {}
                override suspend fun listDirectory(dir: String, hidden: Boolean) = RemoteDirectory("/home/test", entries = listOf(RemoteDirectoryEntry("work", dir = true)))
                override suspend fun openProject(dir: String) = KeroProject("project", "work", dir)
                override suspend fun createSession(request: CreateSessionRequest) =
                    KeroSession("new-session", request.project, dir = request.dir, panes = listOf(KeroPane("new-pane")))
                override suspend fun renameSession(sessionId: String, name: String) {
                    actionCalls += "rename:$sessionId:$name"
                    check(!mutationFails)
                    state.value = state.value.copy(sessions = state.value.sessions.map {
                        if (it.id == sessionId) it.copy(name = name) else it
                    })
                }
                override suspend fun closeSession(sessionId: String) {
                    actionCalls += "close:$sessionId"
                    check(!mutationFails)
                    state.value = state.value.copy(sessions = state.value.sessions.filterNot { it.id == sessionId })
                }
                override suspend fun closePane(paneId: String) {
                    actionCalls += "pane.close:$paneId"
                    check(!mutationFails)
                    state.value = state.value.copy(sessions = state.value.sessions.map {
                        val panes = it.panes.filterNot { pane -> pane.id == paneId }
                        it.copy(panes = panes, focus = panes.firstOrNull()?.id.orEmpty())
                    })
                }
                override suspend fun read(paneId: String) = ""
                override suspend fun send(paneId: String, text: String) {}
                override suspend fun attach(paneId: String, cols: Int, rows: Int): TerminalStream? = null
                override fun disconnect() {}
            }
        }
        val inspector = object : IdentityInspector {
            override fun inspect(privateKey: String) = IdentityInfo("SHA256:test-public", "ssh-ed25519", false)
        }
        return MainViewModel(factory, vault, inspector, preferences(), SavedStateHandle(), Dispatchers.IO).also { store.put("main", it) }
    }
    private fun preferences(): Preferences = object : Preferences {
        private val mode = MutableStateFlow(ThemeMode.System)
        override val theme = mode
        override fun setTheme(mode: ThemeMode) { this.mode.value = mode }
        private val appearance = MutableStateFlow(TerminalAppearance())
        override val terminalAppearance = appearance
        override fun setTerminalAppearance(appearance: TerminalAppearance) {
            this.appearance.value = appearance
        }
    }
    private fun start(vm: MainViewModel) {
        compose.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
    }

    @Test fun openProjectBrowsesRemoteFoldersAndNavigatesToCreatedSession() {
        val library = ConnectionLibrary(
            listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key")))
        val vm = viewModel(library)
        start(vm)
        compose.onNodeWithText("Connect", substring = false).performClick()
        compose.waitUntil { vm.ui.value.connection("a").connected }
        compose.onNodeWithContentDescription("New session or project").performClick()
        compose.onNodeWithText("Open project…").performClick()
        compose.onNodeWithText("Remote folder").assertExists()
        compose.waitUntil { vm.ui.value.browser.directory != null }
        compose.onNodeWithText("work", substring = false).assertExists()
        compose.onNodeWithText("Open folder and start session").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.any { it.id == "new-session" } }
        compose.onNodeWithContentDescription("Terminal actions").assertExists()
    }

    private fun actionLibrary() = ConnectionLibrary(
        listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
        listOf(Machine("a", "Dev", "devbox", "alice", "key")))

    private fun actionState() = KeroState(sessions = listOf(KeroSession("session", "project", name = "Original",
        focus = "pane", panes = listOf(KeroPane("pane"), KeroPane("other", title = "Other")))))

    private fun connect(vm: MainViewModel) {
        compose.onNodeWithText("Connect", substring = false).performClick()
        compose.waitUntil { vm.ui.value.connection("a").connected }
    }

    @Test fun homeRenameDraftSurvivesRestorationAndCancelDoesNotMutate() {
        val vm = viewModel(actionLibrary(), actionState())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
        connect(vm)
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Rename session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Draft name")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Session name").assertTextContains("Draft name")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(actionCalls.isEmpty()) }
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Rename session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Renamed")
        compose.onNodeWithText("Save name").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.single().name == "Renamed" }
        compose.onNodeWithText("Renamed").assertExists()
        compose.runOnIdle { assertEquals(listOf("rename:session:Renamed"), actionCalls) }
    }

    @Test fun homeCloseRequiresConfirmationAndKeepsWorktree() {
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNodeWithText("Any linked worktree will be kept.", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(actionCalls.isEmpty()) }
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.isEmpty() }
        compose.onNodeWithText("No sessions on this machine.").assertExists()
        compose.runOnIdle { assertEquals(listOf("close:session"), actionCalls) }
    }

    @Test fun terminalCanRenameCloseSelectedPaneAndCloseSessionBackToHome() {
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Rename session").performClick()
        compose.onNodeWithText("Session name").performTextReplacement("Terminal name")
        compose.onNodeWithText("Save name").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.single().name == "Terminal name" }
        compose.onNodeWithText("2 · Other").performClick()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Close pane").performClick()
        compose.onNodeWithText("pane “Other”", substring = true).assertExists()
        compose.runOnIdle { assertEquals(1, actionCalls.size) }
        compose.onNode(hasText("Close pane") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.single().panes.size == 1 }
        compose.onNodeWithContentDescription("Terminal actions").assertExists()
        compose.onNodeWithText("2 · Other").assertDoesNotExist()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Close pane").assertDoesNotExist()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.isEmpty() }
        compose.onNodeWithContentDescription("Search sessions").assertExists()
        compose.runOnIdle { assertEquals(listOf("rename:session:Terminal name", "pane.close:other", "close:session"), actionCalls) }
    }

    @Test fun closingNewSessionReturnsToPreviousTerminalNotPastIt() {
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithText("Original").performClick()
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("New session").performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.any { it.id == "new-session" } }
        compose.onNodeWithContentDescription("Terminal actions").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.connection("a").state.sessions.none { it.id == "new-session" } }
        compose.onNodeWithText("Original").assertExists()
        compose.onNodeWithContentDescription("Terminal actions").assertExists()
        compose.onNodeWithContentDescription("Search sessions").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("close:new-session"), actionCalls) }
    }

    @Test fun failedCloseShowsUncertainResultWithoutAutomaticRetry() {
        mutationFails = true
        val vm = viewModel(actionLibrary(), actionState())
        start(vm); connect(vm)
        compose.onNodeWithContentDescription("Actions for Original").performClick()
        compose.onNodeWithText("Close session").performClick()
        compose.onNode(hasText("Close session") and hasClickAction()).performClick()
        compose.waitUntil { vm.ui.value.actions["a"]?.error != null }
        compose.onNode(hasText("The action may not have completed.", substring = true) and
            hasAnyAncestor(isDialog())).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf("close:session"), actionCalls) }
        compose.onNodeWithText("Original").assertExists()
    }

    @Test fun machineEditorOffersTailscaleWithoutAnIdentityOrSshFields() {
        val vm = viewModel()
        start(vm)
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Machine name").performTextInput("Tail")
        // SSH is the default and asks for an identity.
        compose.onNodeWithText("SSH address").assertExists()
        compose.onNodeWithText("Choose identity").assertExists()
        compose.onNodeWithText("Tailscale").performClick()
        compose.onNodeWithText("SSH address").assertDoesNotExist()
        compose.onNodeWithText("Choose identity").assertDoesNotExist()
        compose.onNodeWithText("Tailscale address").performTextInput("https://devbox.tail1234.ts.net")
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.machines.any { it.name == "Tail" } }
        val machine = vm.ui.value.machines.first { it.name == "Tail" }
        assertTrue("saved as a Tailscale machine", machine.isTailscale)
        assertEquals("", machine.identityId)
        assertEquals("https://devbox.tail1234.ts.net:443", machine.endpoint)
        // The machines list shows the tailnet address, not an SSH destination.
        compose.onNodeWithTag("tab-machines").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tail").assertExists()
        compose.onNodeWithText("https://devbox.tail1234.ts.net:443").assertExists()
        compose.onNodeWithText("Tailscale · Disconnected", substring = true).assertExists()
    }

    @Test fun tailscaleEditorRejectsABadAddressAndSaysWhy() {
        val vm = viewModel()
        start(vm)
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Machine name").performTextInput("Tail")
        compose.onNodeWithText("Tailscale").performClick()
        compose.onNodeWithText("Tailscale address").performTextInput("ssh://devbox")
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Use http:// for kerod's tailnet listener or https:// for Tailscale Serve.")
            .assertExists()
        assertTrue(vm.ui.value.machines.isEmpty())
    }

    @Test fun compactHomeIsSearchBehindAButtonWithoutMarketingOrOnboardingForm() {
        start(viewModel())
        compose.onNodeWithText("Keroid").assertExists()
        compose.onNodeWithContentDescription("Search sessions").assertExists()
        compose.onNodeWithTag("tab-sessions").assertExists()
        compose.onNodeWithTag("tab-machines").assertExists()
        compose.onNodeWithText("No machines yet").assertExists()
        compose.onNodeWithText("Add a machine").assertExists()
        compose.onNodeWithText("Your agents.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("A little less watching", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Private key").assertDoesNotExist()
        // The search field is not on screen until it is asked for.
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun searchOpensFromTheAppBarAndFiltersSessions() {
        val library = ConnectionLibrary(
            listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key")))
        val vm = viewModel(library)
        start(vm)
        compose.onNodeWithContentDescription("Search sessions").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun addIdentityReturnsToMachineDraftAndSavesReusableSelection() {
        val vm = viewModel()
        start(vm)
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Machine name").performTextInput("Dev")
        compose.onNodeWithText("SSH address").performTextInput("alice@devbox")
        compose.onNodeWithText("Add identity").performScrollTo().performClick()
        compose.onNodeWithText("Identity name").performTextInput("Work")
        compose.onNodeWithText("Private key").performTextInput("test-only-key")
        compose.onNodeWithText("Save identity").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.identities.size == 1 }
        compose.onNodeWithText("Machine name").assertTextContains("Dev")
        compose.onNodeWithText("SSH address").assertTextContains("alice@devbox")
        compose.onNodeWithText("Work").assertExists()
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.waitUntil { vm.ui.value.machines.size == 1 }
        compose.onNodeWithText("Dev").assertExists()
        assertEquals(vm.ui.value.identities.single().id, vm.ui.value.machines.single().identityId)
        assertTrue(vm.privateKey.value.isEmpty())
    }

    @Test fun portIsVisibleWithoutAdvancedAndDraftSurvivesRestoration() {
        val vm = viewModel()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
        compose.onNodeWithText("Add a machine").performClick()
        compose.onNodeWithText("Save machine").performScrollTo().performClick()
        compose.onNodeWithText("Give this machine a name.").assertExists()
        compose.onNodeWithText("Machine name").performScrollTo().performTextInput("Draft")
        compose.onNodeWithText("Port").performScrollTo().assertExists()
        compose.onNodeWithText("Port").performTextReplacement("2222")
        compose.onNodeWithText("Advanced options").performScrollTo().performClick()
        compose.onNodeWithText("Kero executable (optional)").performScrollTo().performTextInput("/opt/kero/kero")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Port").performScrollTo().assertTextContains("2222")
        compose.onNodeWithText("Machine name").performScrollTo().assertTextContains("Draft")
        compose.onNodeWithText("Kero executable (optional)").performScrollTo().assertTextContains("/opt/kero/kero")
    }

    @Test fun machineSelectorSwitchesAndSearchSurvivesNavigationRestoration() {
        val library = ConnectionLibrary(
            listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = false), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key"), Machine("b", "Build", "buildbox", "alice", "key")))
        val vm = viewModel(library)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { KeroidTheme(dynamicColor = false) { MainNavigation(vm, dark = false) } }
        compose.waitUntil { !vm.ui.value.restoring }
        compose.onNodeWithContentDescription("Search sessions").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithText("Build").performClick()
        compose.runOnIdle { assertEquals("b", vm.ui.value.selectedMachineId) }
        compose.onNodeWithTag("tab-machines").performClick()
        compose.onNodeWithText("Identities").performClick()
        compose.onNodeWithText("Work").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Work").assertExists()
        compose.onNodeWithTag("tab-sessions").performClick()
        compose.onNodeWithText("query").assertExists()
        compose.onNodeWithText("Build").assertExists()
    }

    @Test fun identityManagerPreventsDeletingAnInUseKey() {
        val library = ConnectionLibrary(listOf(StoredIdentity(Identity("key", "Work"), "test-only-key")),
            listOf(Machine("a", "Dev", "devbox", "alice", "key")))
        val vm = viewModel(library)
        start(vm)
        compose.onNodeWithTag("tab-machines").performClick()
        compose.onNodeWithText("Identities").performClick()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Delete identity").performScrollTo().performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.waitUntil { vm.ui.value.editorError != null }
        compose.onNodeWithText("This identity is used by a machine.", substring = true).assertExists()
        assertEquals(1, vm.ui.value.identities.size)
    }

    @Test fun changedHostPromptHasNoTrustOrReplaceAction() {
        val machine = Machine("a", "Dev", "devbox", "alice", "key")
        compose.setContent { KeroidTheme(dynamicColor = false) {
            ConnectionPrompts(MainUiState(restoring = false, machines = listOf(machine), connections = mapOf("a" to MachineUiState(
                ConnectionStatus.VerifyHost, hostKey = HostKeyProblem("host_key_changed", "SHA256:different", "ssh-ed25519")))),
                "", {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Host key changed").assertExists()
        compose.onNodeWithText("Trust and connect").assertDoesNotExist()
        compose.onNodeWithText("Close").assertExists()
    }
}
