package com.sulthonuladib.keroid.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.sulthonuladib.keroid.data.*
import com.sulthonuladib.keroid.term.TerminalAppearance

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val inspector = object : IdentityInspector {
        override fun inspect(privateKey: String) = IdentityInfo("SHA256:public", "ssh-ed25519", false)
    }
    private val prefs = object : Preferences {
        private val mode = MutableStateFlow(ThemeMode.System)
        override val theme = mode
        override fun setTheme(mode: ThemeMode) { this.mode.value = mode }
        private val appearance = MutableStateFlow(TerminalAppearance())
        override val terminalAppearance = appearance
        override fun setTerminalAppearance(appearance: TerminalAppearance) {
            this.appearance.value = appearance
        }
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun vm(factory: Factory = Factory(), vault: Vault = Vault(), state: SavedStateHandle = SavedStateHandle()): MainViewModel {
        val vm = MainViewModel(factory, vault, inspector, prefs, state, StandardTestDispatcher(dispatcher.scheduler, name = "io"))
        stores += ViewModelStore().apply { put("main", vm) }
        return vm
    }

    @Test fun invalidFormNeverSavesOrStartsSsh() = runTest(dispatcher) {
        val factory = Factory()
        val vault = Vault()
        val vm = vm(factory, vault)
        advanceUntilIdle()
        vm.beginMachine("new")
        vm.saveMachine("new")
        advanceUntilIdle()
        assertNotNull(vm.ui.value.editorError)
        assertEquals(0, vault.writes)
        assertTrue(factory.clients.isEmpty())
    }

    @Test fun keysPassphrasesAndTerminalInputNeverEnterSavedState() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val vm = vm(state = state)
        advanceUntilIdle()
        vm.beginMachine("new")
        vm.updateDraft(ConnectionDraft("Dev", "alice@devbox", identityId = "key"))
        vm.beginIdentity("other")
        vm.updateKey("other", "test-only-key")
        vm.updatePassphrase("test-only-passphrase")
        vm.updatePaneDraft(PaneTarget("a", "session", "pane"), "test-only-input")
        assertEquals(setOf("selectedMachine", "draftOwner", "machineDraft"), state.keys())
        assertTrue(state.keys().none { state.get<Any>(it).toString().contains("test-only") })
    }

    @Test fun machinesReuseAnIdentityAndSwitchingDoesNotDisconnect() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b")
        advanceUntilIdle()
        vm.selectMachine("b")
        assertTrue(vm.ui.value.connection("a").connected)
        assertTrue(vm.ui.value.connection("b").connected)
        assertEquals("b", vm.ui.value.selectedMachineId)
        assertTrue(factory.clients.all { it.disconnects == 0 && it.key == "test-only-key" })
    }

    @Test fun editingOneMachineCancelsOnlyThatClientAndKeepsOtherState() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        vm.beginMachine("a")
        vm.updateDraft(vm.ui.value.draft.copy(address = "alice@newbox"))
        vm.saveMachine("a"); advanceUntilIdle()
        assertEquals(1, factory.clients[0].disconnects)
        assertEquals(0, factory.clients[1].disconnects)
        assertTrue(vm.ui.value.connection("b").connected)
        assertFalse(vm.ui.value.connection("a").connected)
    }

    @Test fun deleteMachineKeepsSharedIdentityAndHostTrust() = runTest(dispatcher) {
        val vault = Vault()
        val vm = vm(vault = vault)
        advanceUntilIdle()
        vm.deleteMachine("a"); advanceUntilIdle()
        assertEquals(listOf("b"), vm.ui.value.machines.map { it.id })
        assertEquals(1, vm.ui.value.identities.size)
        assertEquals("SHA256:host", vault.library.pin(Endpoint.of("devbox", 22)))
        vm.deleteIdentity("key"); advanceUntilIdle()
        assertEquals(1, vm.ui.value.identities.size)
        assertNotNull(vm.ui.value.editorError)
    }

    @Test fun addIdentityReturnsToIntactDraftWithNewIdentitySelected() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val vm = vm(state = state)
        advanceUntilIdle()
        vm.beginMachine("new")
        vm.updateDraft(ConnectionDraft("New", "alice@other", "2222", "/opt/kero"))
        vm.beginIdentity("new-key")
        vm.updateKey("new-key", "test-only-import")
        vm.saveIdentity("new-key", "Work")
        advanceUntilIdle()
        assertEquals(ConnectionDraft("New", "alice@other", "2222", "/opt/kero", "new-key"), vm.ui.value.draft)
        assertTrue(vm.privateKey.value.isEmpty())
        val recreated = vm(state = state)
        advanceUntilIdle()
        assertEquals(vm.ui.value.draft, recreated.ui.value.draft)
    }

    private fun tailscaleLibrary() = sampleLibrary(pins = emptyList()).withMachine(
        Machine("t", "Tail", host = "", user = "", identityId = "",
            transport = Transport.Tailscale, endpoint = "http://devbox:7373"))

    @Test fun tailscaleMachineConnectsWithoutKeyPassphraseOrHostPin() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory, Vault(tailscaleLibrary()))
        advanceUntilIdle()
        vm.connect("t"); advanceUntilIdle()
        assertTrue(vm.ui.value.connection("t").connected)
        val remote = factory.clients.last()
        assertEquals(Transport.Tailscale, remote.profile?.transport)
        assertEquals("http://devbox:7373", remote.profile?.endpoint)
        assertEquals("no SSH host pin for a tailnet machine", "", remote.profile?.fingerprint)
        assertEquals("no key is sent", "", remote.key)
        assertEquals("", remote.phrase)
        assertNull("never asks for a passphrase", vm.ui.value.unlock)
    }

    @Test fun tailscaleRefusalIsAuthorizationAndNeverAsksForHostTrust() = runTest(dispatcher) {
        val refusal = """{"code":"tailnet_denied","message":"alice@example.com may not connect: the owner lets them in with listen-allow"}"""
        val factory = Factory { FakeRemote().apply { failure = refusal } }
        val vault = Vault(tailscaleLibrary())
        val vm = vm(factory, vault)
        advanceUntilIdle()
        vm.connect("t"); advanceUntilIdle()
        val connection = vm.ui.value.connection("t")
        assertEquals(ConnectionStatus.Error, connection.status)
        assertTrue("kerod's own words are shown", connection.error!!.contains("listen-allow"))
        assertNull("refusal is not a host-key problem", connection.hostKey)
        assertTrue("nothing is pinned for a tailnet machine", vault.library.pins.isEmpty())
    }

    @Test fun tailscaleMachineSavesWithoutAnIdentity() = runTest(dispatcher) {
        val vault = Vault(sampleLibrary())
        val vm = vm(vault = vault)
        advanceUntilIdle()
        vm.beginMachine("new")
        vm.updateDraft(ConnectionDraft(name = "Tail", transport = Transport.Tailscale,
            endpoint = "https://devbox.tail1234.ts.net"))
        vm.saveMachine("new"); advanceUntilIdle()
        assertNull(vm.ui.value.editorError)
        val machine = vault.library.machines.first { it.id == "new" }
        assertTrue(machine.isTailscale)
        assertEquals("", machine.identityId)
        assertEquals("https://devbox.tail1234.ts.net:443", machine.endpoint)
    }

    @Test fun unknownHostNeedsExplicitTrustAndPersistsPinBeforeRetry() = runTest(dispatcher) {
        val vault = Vault(sampleLibrary(pins = emptyList()))
        val factory = Factory { index -> FakeRemote().apply { if (index == 0) failure = unknown("SHA256:first") } }
        val vm = vm(factory, vault)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        assertEquals(ConnectionStatus.VerifyHost, vm.ui.value.connection("a").status)
        assertTrue(vault.library.pins.isEmpty())
        assertEquals(1, factory.clients.size)
        vm.trustHost("a"); advanceUntilIdle()
        assertTrue(vm.ui.value.connection("a").connected)
        assertEquals("SHA256:first", factory.clients.last().profile?.fingerprint)
        assertEquals("SHA256:first", vault.library.pin(Endpoint.of("devbox", 22)))
    }

    @Test fun switchingIdentitiesCannotBypassChangedHostKey() = runTest(dispatcher) {
        val extra = StoredIdentity(Identity("other", "Other", requiresPassphrase = false), "test-only-other")
        val initial = sampleLibrary().withIdentity(extra)
        val factory = Factory { FakeRemote().apply { failure = changed() } }
        val vm = vm(factory, Vault(initial))
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        vm.trustHost("a"); advanceUntilIdle()
        assertEquals(1, factory.clients.size)
        vm.beginMachine("a")
        vm.updateDraft(vm.ui.value.draft.copy(identityId = "other"))
        vm.saveMachine("a"); advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        assertEquals("SHA256:host", factory.clients.last().profile?.fingerprint)
        assertFalse(vm.ui.value.connection("a").connected)
    }

    @Test fun concurrentTrustPromptsCannotOverwriteAnEndpointPin() = runTest(dispatcher) {
        val initial = sampleLibrary(pins = emptyList())
        val shared = initial.withMachine(initial.machines[1].copy(host = "DEVBOX."))
        val factory = Factory { index -> FakeRemote().apply { if (index < 2) failure = unknown(if (index == 0) "SHA256:first" else "SHA256:second") } }
        val vault = Vault(shared)
        val vm = vm(factory, vault)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        vm.trustHost("a"); vm.trustHost("b"); advanceUntilIdle()
        assertEquals("SHA256:first", vault.library.pin(Endpoint.of("devbox", 22)))
        assertEquals("host_key_changed", vm.ui.value.connection("b").hostKey?.code)
        assertFalse(vm.ui.value.connection("b").connected)
    }

    @Test fun passphraseClearedAfterAuthErrorAndPromptCancel() = runTest(dispatcher) {
        val encrypted = sampleLibrary(encrypted = true)
        val factory = Factory { FakeRemote().apply { failure = "cannot unlock private key" } }
        val vm = vm(factory, Vault(encrypted))
        advanceUntilIdle()
        vm.connect("a")
        vm.updatePassphrase("test-only-phrase")
        vm.unlockAndConnect(); advanceUntilIdle()
        assertTrue(vm.passphrase.value.isEmpty())
        assertNull(vm.ui.value.unlock)
        assertEquals(ConnectionStatus.Error, vm.ui.value.connection("a").status)
        vm.connect("a"); vm.updatePassphrase("another"); vm.cancelUnlock()
        assertTrue(vm.passphrase.value.isEmpty())
    }

    @Test fun passphraseRetainedOnlyForSameAttemptHostApproval() = runTest(dispatcher) {
        val factory = Factory { index -> FakeRemote().apply { if (index == 0) failure = unknown("SHA256:first") } }
        val vm = vm(factory, Vault(sampleLibrary(encrypted = true, pins = emptyList())))
        advanceUntilIdle()
        vm.connect("a"); vm.updatePassphrase("test-only-phrase"); vm.unlockAndConnect(); advanceUntilIdle()
        assertTrue(vm.passphrase.value.isEmpty())
        vm.trustHost("a"); advanceUntilIdle()
        assertTrue(factory.clients.last().phrase == "test-only-phrase")
        vm.disconnect("a"); vm.connect("a")
        assertNotNull(vm.ui.value.unlock)
        assertTrue(vm.passphrase.value.isEmpty())
    }

    @Test fun lateReplacedClientCannotOverwriteNewClientOrOtherMachine() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val factory = Factory { index -> FakeRemote().apply { if (index == 0) connectGate = gate } }
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); runCurrent()
        vm.disconnect("a"); vm.connect("a"); vm.connect("b"); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        factory.clients.first().connectionError.value = "late failure"
        factory.clients.first().state.value = KeroState()
        runCurrent()
        assertTrue(vm.ui.value.connection("a").connected)
        assertTrue(vm.ui.value.connection("b").connected)
        assertEquals(1, vm.ui.value.connection("a").state.sessions.size)
    }

    @Test fun collidingSessionPaneIdsAlwaysUseRouteMachineNotSelection() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        val a = PaneTarget("a", "session", "pane")
        val b = PaneTarget("b", "session", "pane")
        vm.startReading(a); vm.startReading(b); runCurrent()
        vm.selectMachine("b")
        vm.send(a, "a-input"); vm.send(b, "b-input"); runCurrent()
        assertEquals(listOf("a-input"), factory.clients[0].sent)
        assertEquals(listOf("b-input"), factory.clients[1].sent)
        assertEquals("devbox", vm.ui.value.panes[a]?.text)
        assertEquals("buildbox", vm.ui.value.panes[b]?.text)
        vm.disconnect("a")
        assertTrue(vm.ui.value.connection("b").connected)
        vm.stopReading(b)
    }

    @Test fun terminalRemembersMeasurementDuringAttachAndReplaysAfterReconnect() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val remote = factory.clients.single()
        remote.attachGate = CompletableDeferred()
        val first = RecordingTerminal()
        remote.terminal = first
        val target = PaneTarget("a", "session", "pane")
        vm.terminalActive(target, true)
        vm.startTerminal(target, 80, 24)
        runCurrent()
        vm.terminalResize(target, 40, 20, 9, 18)
        remote.attachGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("resize:40,20,9,18", "claim"), first.commands)

        val second = RecordingTerminal()
        remote.terminal = second
        vm.terminalResize(target, 70, 10, 9, 18)
        vm.startTerminal(target, 80, 24)
        advanceUntilIdle()
        assertEquals(70 to 10, remote.attachSizes.last())
        assertEquals(listOf("resize:70,10,9,18", "claim"), second.commands)
    }

    @Test fun terminalAttachmentCompletingInBackgroundDoesNotClaim() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val remote = factory.clients.single()
        remote.attachGate = CompletableDeferred()
        val stream = RecordingTerminal()
        remote.terminal = stream
        val target = PaneTarget("a", "session", "pane")
        vm.terminalResize(target, 40, 20, 9, 18)
        vm.terminalActive(target, true)
        vm.startTerminal(target, 80, 24)
        runCurrent()
        vm.terminalActive(target, false)
        remote.attachGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("resize:40,20,9,18"), stream.commands)
    }

    @Test fun openingProjectUsesDesktopSequenceOnTheRouteMachineAndAddsPendingState() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        vm.selectMachine("b")
        var opened: KeroSession? = null
        vm.openProject("a", "/home/alice/repo") { opened = it }
        advanceUntilIdle()
        assertEquals(listOf("project:/home/alice/repo", "create"), factory.clients[0].actions)
        assertTrue(factory.clients[1].actions.isEmpty())
        assertEquals("opened-project", factory.clients[0].createRequest?.project)
        assertEquals("/home/alice/repo", factory.clients[0].createRequest?.dir)
        assertEquals("created-session", opened?.id)
        assertTrue(vm.ui.value.connection("a").state.sessions.any { it.id == opened?.id })
        assertTrue(vm.ui.value.connection("a").state.projects.any { it.id == "opened-project" })
        // An earlier state event must not hide a response pending its state event.
        factory.clients[0].state.value = KeroState(sessions = listOf(KeroSession("session", "project", name = "old", panes = listOf(KeroPane("pane")))))
        runCurrent()
        assertTrue(vm.ui.value.connection("a").state.sessions.any { it.id == opened?.id })
    }

    @Test fun duplicateSessionActionsAreSuppressedAndDisconnectRejectsLateResults() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val remote = factory.clients.single()
        remote.createGate = CompletableDeferred()
        var callbacks = 0
        vm.newSession("a") { callbacks++ }
        vm.newSession("a") { callbacks++ }
        runCurrent()
        assertEquals(listOf("create"), remote.actions)
        vm.disconnect("a")
        remote.createGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, callbacks)
        assertFalse(vm.ui.value.connection("a").state.sessions.any { it.id == "created-session" })
    }

    @Test fun splitUsesPaneIdAndReturnedFocusWithoutChangingSessionIdentity() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val target = PaneTarget("a", "session", "pane")
        vm.terminalResize(target, 42, 30, 9, 18)
        var updated: KeroSession? = null
        vm.splitPane(target, "down") { updated = it }
        advanceUntilIdle()
        assertEquals("pane", factory.clients.single().createRequest?.split)
        assertEquals("down", factory.clients.single().createRequest?.direction)
        assertEquals("session", updated?.id)
        assertEquals("split-pane", updated?.focus)
        assertEquals(2, vm.ui.value.connection("a").state.sessions.single().panes.size)
    }

    @Test fun renameUsesRouteMachineAndWaitsForSubscribedState() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        vm.selectMachine("b")
        var callbacks = 0
        vm.renameSession("a", "session", "  Review changes  ") { callbacks++ }
        advanceUntilIdle()
        assertEquals(listOf("rename:session:Review changes"), factory.clients[0].actions)
        assertTrue(factory.clients[1].actions.isEmpty())
        assertEquals(1, callbacks)
        assertEquals("", vm.ui.value.connection("a").state.sessions.single().name)
        factory.clients[0].state.value = KeroState(sessions = listOf(
            KeroSession("session", "project", name = "Review changes", panes = listOf(KeroPane("pane")))))
        runCurrent()
        assertEquals("Review changes", vm.ui.value.connection("a").state.sessions.single().name)
        vm.renameSession("a", "session", "   ")
        advanceUntilIdle()
        assertEquals("rename:session:", factory.clients[0].actions.last())
    }

    @Test fun closePaneUsesPaneIdAndFinalPaneUsesSessionClose() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        vm.selectMachine("b")
        val remote = factory.clients[0]
        remote.state.value = KeroState(sessions = listOf(KeroSession("session", "project",
            panes = listOf(KeroPane("pane"), KeroPane("other", url = "https://example.test")))))
        runCurrent()
        vm.closePane(PaneTarget("a", "session", "other"))
        advanceUntilIdle()
        assertEquals(listOf("pane.close:other"), remote.actions)
        // Only the subscribed snapshot may remove a pane from the UI.
        assertEquals(2, vm.ui.value.connection("a").state.sessions.single().panes.size)
        remote.state.value = KeroState(sessions = listOf(KeroSession("session", "project", panes = listOf(KeroPane("pane")))))
        runCurrent()
        vm.closePane(PaneTarget("a", "session", "pane"))
        advanceUntilIdle()
        assertEquals(listOf("pane.close:other", "close:session"), remote.actions)
        assertTrue(factory.clients[1].actions.isEmpty())
    }

    @Test fun closingPendingSessionCannotResurrectItWhenSnapshotPrecedesReply() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        vm.newSession("a") {}
        advanceUntilIdle()
        val remote = factory.clients.single()
        remote.mutationGate = CompletableDeferred()
        var closed = false
        vm.closeSession("a", "created-session") { closed = true }
        runCurrent()
        // The daemon may publish removal before acknowledging close.
        remote.state.value = remote.state.value.copy(projects = listOf(KeroProject("project", "Updated")))
        runCurrent()
        remote.mutationGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(closed)
        assertFalse(vm.ui.value.connection("a").state.sessions.any { it.id == "created-session" })
    }

    @Test fun closeClearsOnlyTargetPaneReadersAndDrafts() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); vm.connect("b"); advanceUntilIdle()
        val a = PaneTarget("a", "session", "pane")
        val b = PaneTarget("b", "session", "pane")
        vm.startReading(a); vm.startReading(b); runCurrent()
        vm.updatePaneDraft(a, "a draft"); vm.updatePaneDraft(b, "b draft")
        vm.closeSession("a", "session"); runCurrent()
        assertFalse(vm.ui.value.panes.containsKey(a))
        assertEquals("b draft", vm.ui.value.panes[b]?.draft)
        vm.stopReading(b)
    }

    @Test fun mutationsAreSingleFlightAndDoNotApplyLateResultsAfterReconnect() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val old = factory.clients.single()
        old.mutationGate = CompletableDeferred()
        var callbacks = 0
        vm.closeSession("a", "session") { callbacks++ }
        vm.renameSession("a", "session", "ignored")
        vm.newSession("a") { callbacks++ }
        runCurrent()
        assertEquals(listOf("close:session"), old.actions)
        vm.disconnect("a")
        vm.connect("a"); runCurrent()
        vm.updatePaneDraft(PaneTarget("a", "session", "pane"), "new connection")
        old.mutationGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, callbacks)
        assertTrue(vm.ui.value.connection("a").connected)
        assertEquals("new connection", vm.ui.value.panes[PaneTarget("a", "session", "pane")]?.draft)
        assertFalse(vm.ui.value.actions["a"]?.busy == true)
    }

    @Test fun failedMutationIsNotReplayedAndStaleTargetsAreRejected() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val remote = factory.clients.single()
        vm.closeSession("a", "gone")
        vm.renameSession("a", "gone", "Name")
        vm.closePane(PaneTarget("a", "session", "gone"))
        assertTrue(remote.actions.isEmpty())
        remote.mutationFailure = true
        var callbacks = 0
        vm.closeSession("a", "session") { callbacks++ }
        advanceUntilIdle()
        assertEquals(listOf("close:session"), remote.actions)
        assertNotNull(vm.ui.value.actions["a"]?.error)
        assertFalse(vm.ui.value.actions["a"]!!.busy)
        assertEquals(0, callbacks)
        assertEquals(1, vm.ui.value.connection("a").state.sessions.size)
        advanceUntilIdle()
        assertEquals(1, remote.actions.size)
    }

    @Test fun staleFolderListingsCannotReplaceNewerRequests() = runTest(dispatcher) {
        val factory = Factory()
        val vm = vm(factory)
        advanceUntilIdle()
        vm.connect("a"); advanceUntilIdle()
        val remote = factory.clients.single()
        val stale = CompletableDeferred<Unit>()
        remote.listGate = stale
        vm.browseDirectory("a", "/old"); runCurrent()
        remote.listGate = null
        vm.browseDirectory("a", "/new"); runCurrent()
        stale.complete(Unit)
        advanceUntilIdle()
        assertEquals("/new", vm.ui.value.browser.directory?.dir)
    }

    @Test fun failedLibraryReadAndWriteNeverOverwriteExistingConnections() = runTest(dispatcher) {
        val vault = Vault().apply { failLoad = true }
        val vm = vm(vault = vault)
        advanceUntilIdle()
        vm.beginMachine("a"); vm.saveMachine("a"); advanceUntilIdle()
        assertNotNull(vm.ui.value.libraryError)
        assertEquals(0, vault.writes)
        vault.failLoad = false
        vm.restore(); advanceUntilIdle()
        vault.failWrite = true
        vm.deleteMachine("a"); advanceUntilIdle()
        assertEquals(2, vm.ui.value.machines.size)
        assertEquals(2, vault.library.machines.size)
    }
}

private fun unknown(fingerprint: String) = """{"code":"host_key_unknown","fingerprint":"$fingerprint","algorithm":"ssh-ed25519"}"""
private fun changed() = """{"code":"host_key_changed","fingerprint":"SHA256:different","algorithm":"ssh-ed25519"}"""
private fun sampleLibrary(encrypted: Boolean = false, pins: List<HostPin> = listOf(HostPin(Endpoint.of("devbox", 22), "SHA256:host"))) = ConnectionLibrary(
    identities = listOf(StoredIdentity(Identity("key", "Work", requiresPassphrase = encrypted), "test-only-key")),
    machines = listOf(Machine("a", "Dev", "devbox", "alice", "key"), Machine("b", "Build", "buildbox", "alice", "key")), pins = pins,
)
private class Vault(var library: ConnectionLibrary = sampleLibrary()) : LibraryVault {
    var writes = 0
    var failLoad = false
    var failWrite = false
    override fun load(): ConnectionLibrary { check(!failLoad); return library }
    override fun save(library: ConnectionLibrary) { check(!failWrite); writes++; this.library = library }
}
private class Factory(private val make: (Int) -> FakeRemote = { FakeRemote() }) : RemoteMachineFactory {
    val clients = mutableListOf<FakeRemote>()
    override fun create() = make(clients.size).also { clients += it }
}
private class RecordingTerminal : TerminalStream {
    val commands = mutableListOf<String>()
    override fun next(): TerminalChunk? = null
    override fun write(bytes: ByteArray) {}
    override fun resize(cols: Int, rows: Int, cellWidth: Int, cellHeight: Int) {
        commands += "resize:$cols,$rows,$cellWidth,$cellHeight"
    }
    override fun claim() { commands += "claim" }
    override fun close() {}
}

private class FakeRemote : RemoteMachine {
    override val state = MutableStateFlow(KeroState(sessions = listOf(KeroSession("session", "project", panes = listOf(KeroPane("pane"))))))
    override val connectionError = MutableStateFlow<String?>(null)
    var profile: ConnectionProfile? = null
    var key = ""
    var phrase = ""
    var failure: String? = null
    var disconnects = 0
    var connectGate: CompletableDeferred<Unit>? = null
    val sent = mutableListOf<String>()
    override suspend fun connect(profile: ConnectionProfile, privateKey: String, passphrase: String) {
        this.profile = profile; key = privateKey; phrase = passphrase
        connectGate?.let { withContext(NonCancellable) { it.await() } }
        failure?.let { error(it) }
    }
    override suspend fun read(paneId: String) = profile!!.host
    override suspend fun send(paneId: String, text: String) { sent += text }
    val actions = mutableListOf<String>()
    var createGate: CompletableDeferred<Unit>? = null
    var listGate: CompletableDeferred<Unit>? = null
    var listFailure = false
    override suspend fun listDirectory(dir: String, hidden: Boolean): RemoteDirectory {
        listGate?.let { withContext(NonCancellable) { it.await() } }
        check(!listFailure)
        return RemoteDirectory(dir, entries = listOf(RemoteDirectoryEntry("repo", dir = true, repo = true)))
    }
    override suspend fun openProject(dir: String): KeroProject {
        actions += "project:$dir"
        return KeroProject("opened-project", "repo", dir)
    }
    var createRequest: CreateSessionRequest? = null
    override suspend fun createSession(request: CreateSessionRequest): KeroSession {
        createRequest = request
        actions += if (request.split.isBlank()) "create" else "split"
        createGate?.await()
        return if (request.split.isBlank()) {
            KeroSession("created-session", request.project, dir = request.dir, focus = "created-pane", panes = listOf(KeroPane("created-pane")))
        } else {
            state.value.sessions.single().copy(focus = "split-pane", panes = listOf(KeroPane("pane"), KeroPane("split-pane")))
        }
    }
    var mutationGate: CompletableDeferred<Unit>? = null
    var mutationFailure = false
    private suspend fun mutate(action: String) {
        actions += action
        mutationGate?.let { withContext(NonCancellable) { it.await() } }
        check(!mutationFailure)
    }
    override suspend fun renameSession(sessionId: String, name: String) = mutate("rename:$sessionId:$name")
    override suspend fun closeSession(sessionId: String) = mutate("close:$sessionId")
    override suspend fun closePane(paneId: String) = mutate("pane.close:$paneId")
    var attachGate: CompletableDeferred<Unit>? = null
    var terminal: TerminalStream? = null
    val attachSizes = mutableListOf<Pair<Int, Int>>()
    override suspend fun attach(paneId: String, cols: Int, rows: Int): TerminalStream? {
        attachSizes += cols to rows
        attachGate?.await()
        return terminal
    }
    override fun disconnect() { disconnects++ }
}
