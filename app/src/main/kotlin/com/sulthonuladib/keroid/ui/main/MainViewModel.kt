package com.sulthonuladib.keroid.ui.main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import com.sulthonuladib.keroid.term.TerminalAppearance
import com.sulthonuladib.keroid.term.TerminalGeometry
import com.sulthonuladib.keroid.term.TerminalSizing
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import com.sulthonuladib.keroid.data.*

enum class ConnectionStatus { Offline, Connecting, VerifyHost, Connected, Error }

data class MachineUiState(
    val status: ConnectionStatus = ConnectionStatus.Offline,
    val state: KeroState = KeroState(),
    val error: String? = null,
    val hostKey: HostKeyProblem? = null,
    val revision: Int = 0,
) { val connected: Boolean get() = status == ConnectionStatus.Connected }

data class PaneTarget(val machineId: String, val sessionId: String, val paneId: String)
data class PaneUiState(val text: String = "", val error: String? = null, val loading: Boolean = false, val sending: Boolean = false, val draft: String = "")

/**
 * One attach frame for a pane. [kind] is a Kero frame kind: 'o' output, 'z' the
 * daemon resized the terminal, 's' the size it took on attach, 'c' closed.
 */
data class TerminalOutput(
    val target: PaneTarget,
    val kind: Byte,
    val data: ByteArray,
    val ended: Boolean = false,
)
data class UnlockPrompt(val machineId: String, val identityId: String, val identityName: String)
data class ProjectBrowserState(
    val machineId: String = "", val directory: RemoteDirectory? = null,
    val loading: Boolean = false, val hidden: Boolean = false, val error: String? = null,
)
data class SessionActionState(val busy: Boolean = false, val error: String? = null)

data class MainUiState(
    val restoring: Boolean = true,
    val libraryError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val editorError: String? = null,
    val machines: List<Machine> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val selectedMachineId: String? = null,
    val connections: Map<String, MachineUiState> = emptyMap(),
    val panes: Map<PaneTarget, PaneUiState> = emptyMap(),
    val draft: ConnectionDraft = ConnectionDraft(),
    val unlock: UnlockPrompt? = null,
    val browser: ProjectBrowserState = ProjectBrowserState(),
    val actions: Map<String, SessionActionState> = emptyMap(),
) {
    val selectedMachine: Machine? get() = machines.firstOrNull { it.id == selectedMachineId }
    fun connection(id: String?) = connections[id] ?: MachineUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val remotes: RemoteMachineFactory,
    private val vault: LibraryVault,
    private val inspector: IdentityInspector,
    private val settings: Preferences,
    private val savedState: SavedStateHandle,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _ui = MutableStateFlow(MainUiState(draft = savedState.get<String>("machineDraft")?.let {
        runCatching { wireJson.decodeFromString<ConnectionDraft>(it) }.getOrNull()
    } ?: ConnectionDraft()))
    val ui = _ui.asStateFlow()
    val theme = settings.theme
    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)
    val terminalAppearance = settings.terminalAppearance
    fun setTerminalAppearance(appearance: TerminalAppearance) = settings.setTerminalAppearance(appearance)
    // Never put these flows or the encrypted library's raw keys in saved state.
    private val _privateKey = MutableStateFlow("")
    val privateKey = _privateKey.asStateFlow()
    private val _passphrase = MutableStateFlow("")
    val passphrase = _passphrase.asStateFlow()
    private var identityDraftId: String? = null
    private var library = ConnectionLibrary()
    private val libraryLock = Mutex()
    private val slots = mutableMapOf<String, Slot>()
    private val readers = mutableMapOf<PaneTarget, Job>()
    private val senders = mutableMapOf<PaneTarget, Job>()
    private val terminals = mutableMapOf<PaneTarget, TerminalStream>()
    private val terminalJobs = mutableMapOf<PaneTarget, Job>()
    private val terminalWriters = mutableMapOf<PaneTarget, Channel<() -> Unit>>()
    private val terminalSizes = mutableMapOf<PaneTarget, TerminalSizing>()
    private val _terminalOutput = MutableSharedFlow<TerminalOutput>(extraBufferCapacity = 256)
    /** Output of attached panes; the pane screen feeds it to libghostty. */
    val terminalOutput = _terminalOutput.asSharedFlow()
    private var revision = 0
    private var browserJob: Job? = null
    private var browserGeneration = 0
    private val actionJobs = mutableMapOf<String, Job>()

    private class Slot(val machine: Machine, val identity: StoredIdentity?, var phrase: String, val remote: RemoteMachine) {
        var job: Job? = null
        val observers = mutableListOf<Job>()
        val pendingSessions = mutableMapOf<String, KeroSession>()
        val pendingProjects = mutableMapOf<String, KeroProject>()
        private var snapshot = KeroState()
        fun observe(state: KeroState): KeroState {
            snapshot = state
            pendingProjects.keys.removeAll(state.projects.map { it.id }.toSet())
            pendingSessions.entries.removeAll { (_, pending) ->
                state.sessions.any { it.id == pending.id && it.panes.map { p -> p.id }.containsAll(pending.panes.map { p -> p.id }) }
            }
            return merge()
        }
        fun merge(): KeroState = snapshot.copy(
            projects = snapshot.projects + pendingProjects.values,
            sessions = snapshot.sessions.map { pendingSessions[it.id] ?: it } +
                pendingSessions.values.filter { p -> snapshot.sessions.none { it.id == p.id } },
        )
        fun clear() { phrase = ""; job?.cancel(); observers.forEach { it.cancel() }; remote.disconnect() }
    }

    init { restore() }

    fun restore() {
        if (_ui.value.saving) return
        _ui.update { it.copy(restoring = true, libraryError = null) }
        viewModelScope.launch {
            try {
                libraryLock.withLock {
                    library = withContext(io) { vault.load() }
                    publishLibrary()
                }
            } catch (_: Exception) {
                _ui.update { it.copy(libraryError = "Could not unlock the saved connections. Nothing has been overwritten. Retry when device storage is available.") }
            } finally { _ui.update { it.copy(restoring = false) } }
        }
    }

    private fun publishLibrary() {
        val selected = savedState.get<String>("selectedMachine") ?: library.selectedMachineId
        val id = selected?.takeIf { choice -> library.machines.any { it.id == choice } } ?: library.machines.firstOrNull()?.id
        savedState["selectedMachine"] = id
        _ui.update { it.copy(machines = library.machines, identities = library.identities.map { stored -> stored.identity }, selectedMachineId = id) }
    }

    private suspend fun commit(change: (ConnectionLibrary) -> ConnectionLibrary) = libraryLock.withLock {
        val next = change(library).validate()
        withContext(io) { vault.save(next) }
        library = next
        publishLibrary()
    }

    private fun editLibrary(change: (ConnectionLibrary) -> ConnectionLibrary, onSaved: () -> Unit = {}) {
        if (_ui.value.restoring || _ui.value.libraryError != null || _ui.value.saving) return
        _ui.update { it.copy(saving = true, editorError = null, error = null) }
        viewModelScope.launch {
            try { commit(change); onSaved() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { reportEditorError(e.message ?: "Check the connection details.") }
            catch (_: Exception) { reportEditorError("Could not save securely. Your previous connections have not been changed.") }
            finally { _ui.update { it.copy(saving = false) } }
        }
    }

    fun selectMachine(id: String) {
        if (library.machines.none { it.id == id }) return
        savedState["selectedMachine"] = id
        _ui.update { it.copy(selectedMachineId = id) }
    }

    fun beginMachine(id: String) {
        if (savedState.get<String>("draftOwner") == id) return
        savedState["draftOwner"] = id
        updateDraft(library.machines.firstOrNull { it.id == id }?.let(ConnectionDraft::from) ?: ConnectionDraft())
    }

    fun updateDraft(draft: ConnectionDraft) {
        if (_ui.value.saving) return
        savedState["machineDraft"] = wireJson.encodeToString(draft)
        _ui.update { it.copy(draft = draft, editorError = null) }
    }

    fun discardMachineDraft() {
        savedState.remove<String>("draftOwner")
        savedState.remove<String>("machineDraft")
        _ui.update { it.copy(draft = ConnectionDraft(), editorError = null) }
    }

    fun saveMachine(id: String, onSaved: () -> Unit = {}) {
        val machine = try { _ui.value.draft.machine(id) }
        catch (e: IllegalArgumentException) { reportEditorError(e.message ?: "Check the machine details."); return }
        val previous = library.machines.firstOrNull { it.id == id }
        editLibrary({ current ->
            // Only SSH needs an identity; kerod decides Tailscale access itself.
            if (!machine.isTailscale) {
                require(current.identities.any { it.identity.id == machine.identityId }) { "Choose an existing SSH identity." }
            }
            current.withMachine(machine)
        }) {
            if (previous != machine) disconnect(id)
            selectMachine(id)
            discardMachineDraft()
            onSaved()
        }
    }

    fun deleteMachine(id: String, onDeleted: () -> Unit = {}) = editLibrary({ it.withoutMachine(id) }) {
        disconnect(id)
        _ui.update { it.copy(connections = it.connections - id) }
        discardMachineDraft()
        onDeleted()
    }

    fun beginIdentity(id: String) {
        if (identityDraftId == id) return
        identityDraftId = id
        _privateKey.value = ""
        _ui.update { it.copy(editorError = null) }
    }

    fun updateKey(id: String, value: String) {
        if (identityDraftId != id || _ui.value.saving) return
        if (value.length > 128 * 1024) { reportEditorError("Private key must be smaller than 128 KiB."); return }
        _privateKey.value = value
        _ui.update { it.copy(editorError = null) }
    }

    fun discardIdentityDraft() {
        identityDraftId = null
        _privateKey.value = ""
        _ui.update { it.copy(editorError = null) }
    }

    fun saveIdentity(id: String, name: String, onSaved: () -> Unit = {}) {
        if (_ui.value.restoring || _ui.value.saving || _ui.value.libraryError != null) return
        if (name.isBlank()) { reportEditorError("Give this identity a name."); return }
        val existing = library.identities.firstOrNull { it.identity.id == id }
        val key = existing?.privateKey ?: _privateKey.value
        if (key.isBlank()) { reportEditorError("Import or paste an SSH private key."); return }
        _ui.update { it.copy(saving = true, editorError = null) }
        viewModelScope.launch {
            try {
                val info = existing?.identity?.let { IdentityInfo(it.fingerprint, it.algorithm, it.requiresPassphrase) }
                    ?: withContext(io) { inspector.inspect(key) }
                commit { it.withIdentity(StoredIdentity(Identity(id, name.trim(), info.fingerprint, info.algorithm, info.requiresPassphrase), key)) }
                if (savedState.get<String>("draftOwner") != null) {
                    // Adding an identity returns to the intact machine draft, selected.
                    val draft = _ui.value.draft.copy(identityId = id)
                    savedState["machineDraft"] = wireJson.encodeToString(draft)
                    _ui.update { it.copy(draft = draft) }
                }
                discardIdentityDraft()
                onSaved()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { reportEditorError("Could not save this identity. Use an OpenSSH or PEM private key and check device storage.") }
            finally { _ui.update { it.copy(saving = false) } }
        }
    }

    fun deleteIdentity(id: String, onDeleted: () -> Unit = {}) = editLibrary({ it.withoutIdentity(id) }) {
        discardIdentityDraft(); onDeleted()
    }

    fun reportEditorError(message: String) { _ui.update { it.copy(editorError = message) } }
    fun updatePassphrase(value: String) { _passphrase.value = value }
    fun cancelUnlock() { _passphrase.value = ""; _ui.update { it.copy(unlock = null) } }

    fun connect(id: String) {
        if (_ui.value.restoring || _ui.value.libraryError != null || _ui.value.saving) return
        if (_ui.value.connection(id).status in listOf(ConnectionStatus.Connecting, ConnectionStatus.VerifyHost, ConnectionStatus.Connected)) return
        val machine = library.machines.firstOrNull { it.id == id } ?: return
        // A Tailscale machine sends no key: kerod identifies the caller itself.
        if (machine.isTailscale) return startConnection(machine, null, "")
        val identity = library.identities.firstOrNull { it.identity.id == machine.identityId } ?: return
        if (identity.identity.requiresPassphrase) {
            cancelUnlock()
            _ui.update { it.copy(unlock = UnlockPrompt(id, identity.identity.id, identity.identity.name)) }
        } else startConnection(machine, identity, "")
    }

    fun unlockAndConnect() {
        val prompt = _ui.value.unlock ?: return
        val machine = library.machines.firstOrNull { it.id == prompt.machineId } ?: return cancelUnlock()
        val identity = library.identities.firstOrNull { it.identity.id == machine.identityId } ?: return cancelUnlock()
        if (identity.identity.id != prompt.identityId) return cancelUnlock()
        val phrase = _passphrase.value
        cancelUnlock()
        startConnection(machine, identity, phrase)
    }

    private fun machineUi(id: String, transform: (MachineUiState) -> MachineUiState) {
        _ui.update { it.copy(connections = it.connections + (id to transform(it.connection(id)))) }
    }

    private fun current(slot: Slot) = slots[slot.machine.id] === slot

    private fun startConnection(machine: Machine, identity: StoredIdentity?, phrase: String) {
        disconnect(machine.id)
        val slot = Slot(machine, identity, phrase, remotes.create())
        slots[machine.id] = slot
        machineUi(machine.id) { MachineUiState(status = ConnectionStatus.Connecting, revision = ++revision) }
        slot.observers += viewModelScope.launch {
            slot.remote.state.collect { state -> if (current(slot)) machineUi(machine.id) { it.copy(state = slot.observe(state)) } }
        }
        slot.observers += viewModelScope.launch {
            slot.remote.connectionError.filterNotNull().collect {
                if (current(slot) && _ui.value.connection(machine.id).connected) failConnection(slot, "Connection to the machine closed. Reconnect explicitly to continue.")
            }
        }
        slot.job = viewModelScope.launch {
            try {
                // Host-key pins are the SSH transport's; kerod decides Tailscale access itself.
                val pin = if (machine.isTailscale) "" else library.pin(machine.sshEndpoint)
                slot.remote.connect(machine.profile(pin), identity?.privateKey.orEmpty(), slot.phrase)
                if (!current(slot) || !isActive) return@launch
                slot.phrase = ""
                if (slot.remote.connectionError.value != null) {
                    failConnection(slot, "Connection to the machine closed.")
                } else machineUi(machine.id) { it.copy(status = ConnectionStatus.Connected, error = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!current(slot) || !isActive) return@launch
                val message = e.message.orEmpty()
                val problem = runCatching { wireJson.decodeFromString<HostKeyProblem>(message) }.getOrNull()
                    ?.takeIf { it.code in listOf("host_key_unknown", "host_key_changed") && it.fingerprint.isNotBlank() }
                val refused = runCatching { wireJson.decodeFromString<TailnetRefusal>(message) }.getOrNull()
                    ?.takeIf { it.code == "tailnet_denied" }
                slot.remote.disconnect()
                if (problem != null) {
                    if (problem.code != "host_key_unknown") slot.phrase = ""
                    machineUi(machine.id) { it.copy(status = ConnectionStatus.VerifyHost, hostKey = problem) }
                } else if (refused != null) {
                    // kerod refused this identity: not a network problem, and the app
                    // never silently retries over SSH or another route.
                    machineUi(machine.id) { it.copy(status = ConnectionStatus.Error,
                        error = if (refused.message.isBlank()) "The host refused this device." else refused.message.take(500)) }
                } else failConnection(slot, message.take(500).ifBlank { "Could not connect." })
            }
        }
    }

    fun trustHost(id: String) {
        val slot = slots[id] ?: return
        val problem = _ui.value.connection(id).hostKey ?: return
        if (problem.code != "host_key_unknown") return
        machineUi(id) { it.copy(hostKey = null, status = ConnectionStatus.Connecting) }
        viewModelScope.launch {
            try {
                // Serialize trust decisions across machines sharing an endpoint. Never
                // overwrite a pin approved by a concurrent attempt with a different key.
                commit { library ->
                    check(current(slot))
                    library.approve(slot.machine.sshEndpoint, problem.fingerprint)
                }
                if (!current(slot)) return@launch
                val phrase = slot.phrase
                startConnection(slot.machine, slot.identity, phrase)
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) {
                if (current(slot)) {
                    slot.phrase = ""
                    machineUi(id) { it.copy(status = ConnectionStatus.VerifyHost, hostKey = problem.copy(code = "host_key_changed")) }
                }
            } catch (_: Exception) { if (current(slot)) failConnection(slot, "Could not save host trust securely. Connection cancelled.") }
        }
    }

    fun dismissHost(id: String) { disconnect(id) }

    private fun failConnection(slot: Slot, message: String) {
        if (!current(slot)) return
        disconnect(slot.machine.id)
        machineUi(slot.machine.id) { it.copy(status = ConnectionStatus.Error, error = message) }
    }

    fun disconnect(id: String) {
        slots.remove(id)?.clear()
        actionJobs.remove(id)?.cancel()
        if (_ui.value.browser.machineId == id) {
            browserGeneration++
            browserJob?.cancel()
            _ui.update { it.copy(browser = ProjectBrowserState()) }
        }
        _ui.update { it.copy(actions = it.actions - id) }
        readers.keys.filter { it.machineId == id }.toList().forEach(::stopReading)
        (terminalJobs.keys + terminals.keys).filter { it.machineId == id }.toList().forEach(::stopTerminal)
        senders.keys.filter { it.machineId == id }.toList().forEach { senders.remove(it)?.cancel() }
        if (_ui.value.unlock?.machineId == id) cancelUnlock()
        _ui.update { it.copy(panes = it.panes.filterKeys { target -> target.machineId != id }) }
        machineUi(id) { it.copy(status = ConnectionStatus.Offline, error = null, hostKey = null) }
    }

    private fun slotFor(target: PaneTarget): Slot? = slots[target.machineId]?.takeIf { slot ->
        _ui.value.connection(target.machineId).connected && _ui.value.connection(target.machineId).state.sessions.any { session ->
            session.id == target.sessionId && session.panes.any { it.id == target.paneId && it.url.isBlank() }
        }
    }

    private fun paneUi(target: PaneTarget, change: (PaneUiState) -> PaneUiState) {
        _ui.update { it.copy(panes = it.panes + (target to change(it.panes[target] ?: PaneUiState()))) }
    }

    fun updatePaneDraft(target: PaneTarget, text: String) { paneUi(target) { it.copy(draft = text) } }

    fun startReading(target: PaneTarget) {
        stopReading(target)
        val slot = slotFor(target) ?: return
        paneUi(target) { it.copy(text = "", error = null, loading = true) }
        readers[target] = viewModelScope.launch {
            while (isActive && current(slot) && _ui.value.connection(target.machineId).connected) {
                try {
                    val text = slot.remote.read(target.paneId)
                    if (isActive && current(slot)) paneUi(target) { it.copy(text = text, loading = false, error = null) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    if (isActive && current(slot)) paneUi(target) { it.copy(loading = false, error = "Could not read this pane.") }
                }
                delay(2000)
            }
        }
    }

    fun stopReading(target: PaneTarget) { readers.remove(target)?.cancel() }

    /** Attaches one pane's terminal stream, feeding [terminalOutput]. */
    fun startTerminal(target: PaneTarget, cols: Int, rows: Int) {
        if (terminals.containsKey(target) || terminalJobs[target]?.isActive == true) return
        val slot = slotFor(target) ?: return
        val sizing = terminalSizes.getOrPut(target) { TerminalSizing() }
        // Lazy start installs the job identity before an immediate/fake attach returns.
        lateinit var job: Job
        job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var attached: TerminalStream? = null
            val commands = Channel<() -> Unit>(Channel.UNLIMITED)
            var writer: Job? = null
            try {
                val desired = sizing.desired
                val stream = slot.remote.attach(target.paneId, desired?.cols ?: cols, desired?.rows ?: rows)
                    ?: return@launch
                attached = stream
                if (!isActive || !current(slot)) return@launch
                terminals[target] = stream
                terminalWriters[target] = commands
                writer = launch(io) { for (command in commands) command() }
                sizing.attach { command ->
                    commands.trySend {
                        when (command) {
                            is TerminalSizing.Command.Resize -> command.geometry.let {
                                stream.resize(it.cols, it.rows, it.cellWidth, it.cellHeight)
                            }
                            is TerminalSizing.Command.Claim -> if (sizing.mayClaim(command)) stream.claim()
                        }
                    }
                }
                while (isActive && current(slot)) {
                    val chunk = withContext(io) { stream.next() } ?: break
                    _terminalOutput.emit(TerminalOutput(target, chunk.kind, chunk.data))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Falls through to the end notice; the pane then shows its snapshot.
            } finally {
                commands.cancel()
                writer?.cancel()
                attached?.close()
                // A cancelled reader may finish after its replacement attached.
                if (terminalJobs[target] === job) {
                    terminals.remove(target)
                    terminalWriters.remove(target)
                    terminalJobs.remove(target)
                    sizing.detach()
                    _terminalOutput.tryEmit(TerminalOutput(target, 'c'.code.toByte(), ByteArray(0), ended = true))
                }
            }
        }
        terminalJobs[target] = job
        job.start()
    }

    fun stopTerminal(target: PaneTarget) {
        terminalSizes[target]?.detach()
        terminalWriters.remove(target)?.cancel()
        terminals.remove(target)?.close() // Unblock next() before cancelling its reader.
        terminalJobs.remove(target)?.cancel()
    }

    fun terminalInput(target: PaneTarget, bytes: ByteArray) {
        val stream = terminals[target] ?: return
        terminalWriters[target]?.trySend { stream.write(bytes) }
    }

    /** Remember even before attach; the local grid changes only on welcome/'z'. */
    fun terminalResize(target: PaneTarget, cols: Int, rows: Int, cellWidth: Int, cellHeight: Int) {
        terminalSizes.getOrPut(target) { TerminalSizing() }
            .request(TerminalGeometry(cols, rows, cellWidth, cellHeight))
    }

    /** Called only by pane resume/pause, not recomposition or layout. */
    fun terminalActive(target: PaneTarget, active: Boolean) {
        terminalSizes.getOrPut(target) { TerminalSizing() }.setActive(active)
    }

    fun browseDirectory(machineId: String, dir: String = "~", hidden: Boolean = false) {
        val slot = slots[machineId]?.takeIf { _ui.value.connection(machineId).connected } ?: return
        val generation = ++browserGeneration
        browserJob?.cancel()
        _ui.update { it.copy(browser = ProjectBrowserState(machineId,
            it.browser.takeIf { b -> b.machineId == machineId }?.directory, true, hidden)) }
        browserJob = viewModelScope.launch {
            try {
                val result = slot.remote.listDirectory(dir, hidden)
                if (isActive && current(slot) && generation == browserGeneration) {
                    _ui.update { it.copy(browser = ProjectBrowserState(machineId, result, hidden = hidden)) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (isActive && current(slot) && generation == browserGeneration) {
                    _ui.update { it.copy(browser = it.browser.copy(loading = false, error = "Could not list this folder.")) }
                }
            }
        }
    }

    fun openProject(machineId: String, dir: String, onCreated: (KeroSession) -> Unit) {
        if (dir.isBlank()) return
        sessionAction(machineId, onCreated) { remote ->
            // Desktop's exact sequence: register/reuse the folder, then create a shell.
            val project = remote.openProject(dir)
            val slot = slots[machineId]
            if (slot?.remote !== remote) throw CancellationException()
            slot.pendingProjects[project.id] = project
            val session = remote.createSession(CreateSessionRequest(project = project.id, dir = project.dir))
            session
        }
    }

    fun newSession(machineId: String, projectId: String = "", from: PaneTarget? = null,
                   onCreated: (KeroSession) -> Unit) {
        val state = _ui.value.connection(machineId).state
        val current = from?.takeIf { it.machineId == machineId }?.let { target -> state.sessions.firstOrNull { it.id == target.sessionId } }
        val pane = current?.panes?.firstOrNull { it.id == from.paneId }
        val project = projectId.ifBlank { current?.project.orEmpty() }
        val dir = pane?.dir.orEmpty().ifBlank { current?.dir.orEmpty() }
            .ifBlank { if (project.isBlank() && state.projects.isEmpty()) "~" else "" }
        sessionAction(machineId, onCreated) { it.createSession(CreateSessionRequest(project = project, dir = dir, after = current?.id.orEmpty())) }
    }

    fun splitPane(target: PaneTarget, direction: String, onCreated: (KeroSession) -> Unit) {
        if (direction !in listOf("right", "down") || slotFor(target) == null) return
        val geometry = terminalSizes[target]?.desired
        sessionAction(target.machineId, onCreated) {
            it.createSession(CreateSessionRequest(split = target.paneId, direction = direction,
                cols = geometry?.cols ?: 0, rows = geometry?.rows ?: 0))
        }
    }

    fun renameSession(machineId: String, sessionId: String, name: String, onRenamed: () -> Unit = {}) {
        if (_ui.value.connection(machineId).state.sessions.none { it.id == sessionId }) return
        machineAction(machineId, { it.renameSession(sessionId, name.trim()) }) { _, _ -> onRenamed() }
    }

    fun closeSession(machineId: String, sessionId: String, onClosed: () -> Unit = {}) {
        if (_ui.value.connection(machineId).state.sessions.none { it.id == sessionId }) return
        machineAction(machineId, { it.closeSession(sessionId) }) { slot, _ ->
            slot.pendingSessions.remove(sessionId)
            machineUi(machineId) { it.copy(state = slot.merge()) }
            forgetPanes(machineId, sessionId)
            onClosed()
        }
    }

    fun closePane(target: PaneTarget, onClosed: () -> Unit = {}) {
        val session = _ui.value.connection(target.machineId).state.sessions.firstOrNull { it.id == target.sessionId } ?: return
        if (session.panes.none { it.id == target.paneId }) return
        // Desktop closes the session when its final pane is closed. Keep worktrees.
        if (session.panes.size == 1) return closeSession(target.machineId, target.sessionId, onClosed)
        machineAction(target.machineId, { it.closePane(target.paneId) }) { slot, _ ->
            slot.pendingSessions.remove(target.sessionId)
            machineUi(target.machineId) { it.copy(state = slot.merge()) }
            forgetPanes(target.machineId, target.sessionId, target.paneId)
            onClosed()
        }
    }

    private fun forgetPanes(machineId: String, sessionId: String, paneId: String? = null) {
        val targets = (readers.keys + senders.keys + terminals.keys + terminalJobs.keys +
            terminalSizes.keys + _ui.value.panes.keys).filter {
            it.machineId == machineId && it.sessionId == sessionId && (paneId == null || it.paneId == paneId)
        }
        targets.forEach { target ->
            stopReading(target)
            stopTerminal(target)
            senders.remove(target)?.cancel()
            terminalSizes.remove(target)
        }
        _ui.update { it.copy(panes = it.panes - targets.toSet()) }
    }

    private fun sessionAction(machineId: String, onCreated: (KeroSession) -> Unit,
                              action: suspend (RemoteMachine) -> KeroSession) {
        machineAction(machineId, action) { slot, session ->
            // Creation responses carry state; bool mutation replies do not. For
            // rename/close, the subscribed snapshot remains authoritative.
            slot.pendingSessions[session.id] = session
            machineUi(machineId) { it.copy(state = slot.merge()) }
            onCreated(session)
        }
    }

    private fun <T> machineAction(machineId: String, action: suspend (RemoteMachine) -> T,
                                 onSuccess: (Slot, T) -> Unit) {
        val slot = slots[machineId]?.takeIf { _ui.value.connection(machineId).connected } ?: return
        if (_ui.value.actions[machineId]?.busy == true) return
        _ui.update { it.copy(actions = it.actions + (machineId to SessionActionState(busy = true))) }
        actionJobs[machineId] = viewModelScope.launch {
            try {
                val result = action(slot.remote)
                if (isActive && current(slot)) onSuccess(slot, result)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (isActive && current(slot)) _ui.update { it.copy(actions = it.actions +
                    (machineId to SessionActionState(error = "The action may not have completed. Check sessions before trying again."))) }
            } finally {
                if (current(slot)) {
                    actionJobs.remove(machineId)
                    _ui.update { it.copy(actions = it.actions + (machineId to (it.actions[machineId] ?: SessionActionState()).copy(busy = false))) }
                }
            }
        }
    }

    fun send(target: PaneTarget, text: String, onSent: () -> Unit = {}) {
        val slot = slotFor(target) ?: return
        if (text.isEmpty() || senders[target]?.isActive == true) return
        paneUi(target) { it.copy(sending = true, error = null) }
        senders[target] = viewModelScope.launch {
            try {
                slot.remote.send(target.paneId, text)
                if (isActive && current(slot)) onSent()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (isActive && current(slot)) paneUi(target) { it.copy(error = "Input may not have arrived. Check the pane before retrying.") }
            } finally { if (isActive && current(slot)) paneUi(target) { it.copy(sending = false) } }
        }
    }

    override fun onCleared() {
        slots.values.forEach(Slot::clear)
        slots.clear()
        (terminalJobs.keys + terminals.keys).toList().forEach(::stopTerminal)
        _privateKey.value = ""
        _passphrase.value = ""
        super.onCleared()
    }
}
