package com.sulthonuladib.keroid.data

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import com.sulthonuladib.keroid.mobile.Client
import com.sulthonuladib.keroid.mobile.Mobile
import com.sulthonuladib.keroid.mobile.Observer

interface RemoteMachine {
    val state: StateFlow<KeroState>
    val connectionError: StateFlow<String?>
    suspend fun connect(profile: ConnectionProfile, privateKey: String, passphrase: String)
    suspend fun read(paneId: String): String
    suspend fun send(paneId: String, text: String)
    suspend fun listDirectory(dir: String, hidden: Boolean): RemoteDirectory
    suspend fun openProject(dir: String): KeroProject
    suspend fun createSession(request: CreateSessionRequest): KeroSession
    suspend fun renameSession(sessionId: String, name: String)
    /** Closes processes but keeps any linked worktree on disk. */
    suspend fun closeSession(sessionId: String)
    suspend fun closePane(paneId: String)
    /** Opens a terminal stream for a pane; null when the host refuses. */
    suspend fun attach(paneId: String, cols: Int, rows: Int): TerminalStream?
    fun disconnect()
}

/** One attach frame: its kind (see Kero's proto/frame.go) and its payload. */
data class TerminalChunk(val kind: Byte, val data: ByteArray)

/**
 * One pane's attach stream, as the UI needs it. Reading blocks; close it to
 * unblock and detach. The daemon owns the terminal's size, so the UI never
 * reflows the grid itself: it asks with [resize] and takes what the stream
 * reports.
 */
interface TerminalStream {
    fun next(): TerminalChunk?
    fun write(bytes: ByteArray)
    fun resize(cols: Int, rows: Int, cellWidth: Int, cellHeight: Int)
    /** Makes this client the one whose size the session takes. */
    fun claim()
    fun close()
}

interface RemoteMachineFactory { fun create(): RemoteMachine }

class KeroRepository @Inject constructor() : RemoteMachineFactory {
    override fun create(): RemoteMachine = SshRemoteMachine()
}

class GoIdentityInspector @Inject constructor() : IdentityInspector {
    override fun inspect(privateKey: String): IdentityInfo =
        wireJson.decodeFromString(Mobile.inspectIdentity(privateKey))
}

/** One-shot wrapper: never reuse a native client for a different machine/attempt. */
private class SshRemoteMachine : RemoteMachine {
    private val _state = MutableStateFlow(KeroState())
    override val state = _state.asStateFlow()
    private val _connectionError = MutableStateFlow<String?>(null)
    override val connectionError = _connectionError.asStateFlow()
    private val lock = Any()
    private var active = true
    private var started = false
    private val client: Client = Mobile.newClient(object : Observer {
        override fun event(message: String) = synchronized(lock) {
            if (!active) return@synchronized
            try {
                val event = wireJson.decodeFromString<StateEvent>(message)
                if (event.event == "state") event.state?.let { _state.value = it }
            } catch (_: Exception) {
                _connectionError.value = "The machine sent an unsupported state response."
            }
        }
        override fun closed(message: String) = synchronized(lock) {
            if (active) _connectionError.value = message.ifBlank { "Connection closed." }
        }
    })

    override suspend fun connect(profile: ConnectionProfile, privateKey: String, passphrase: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { check(active && !started); started = true }
        try {
            client.connect(wireJson.encodeToString(profile), privateKey, passphrase)
            client.call("subscribe", "{}")
            Unit
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    override suspend fun read(paneId: String): String = withContext(Dispatchers.IO) {
        val result = client.call("read", wireJson.encodeToString(ReadRequest(paneId)))
        wireJson.decodeFromString<ReadResult>(result).text
    }

    override suspend fun send(paneId: String, text: String) = withContext(Dispatchers.IO) {
        client.call("send", wireJson.encodeToString(mapOf("id" to paneId, "data" to text)))
        Unit
    }

    override suspend fun listDirectory(dir: String, hidden: Boolean): RemoteDirectory = withContext(Dispatchers.IO) {
        wireJson.decodeFromString(client.call("fs.list", wireJson.encodeToString(DirectoryRequest(dir, hidden))))
    }

    override suspend fun openProject(dir: String): KeroProject = withContext(Dispatchers.IO) {
        wireJson.decodeFromString(client.call("project.create", wireJson.encodeToString(mapOf("dir" to dir))))
    }

    override suspend fun createSession(request: CreateSessionRequest): KeroSession = withContext(Dispatchers.IO) {
        val method = if (request.split.isNotBlank()) "split" else "create"
        wireJson.decodeFromString(client.call(method, wireJson.encodeToString(request)))
    }

    override suspend fun renameSession(sessionId: String, name: String) =
        acknowledge("rename", wireJson.encodeToString(RenameSessionRequest(sessionId, name)))

    override suspend fun closeSession(sessionId: String) =
        acknowledge("close", wireJson.encodeToString(IdRequest(sessionId)))

    override suspend fun closePane(paneId: String) =
        acknowledge("pane.close", wireJson.encodeToString(IdRequest(paneId)))

    private suspend fun acknowledge(method: String, params: String) = withContext(Dispatchers.IO) {
        check(wireJson.decodeFromString<Boolean>(client.call(method, params))) { "Action was not acknowledged." }
    }

    override suspend fun attach(paneId: String, cols: Int, rows: Int): TerminalStream? = withContext(Dispatchers.IO) {
        val terminal = try {
            client.attach(paneId, cols, rows)
        } catch (_: Exception) {
            return@withContext null
        }
        object : TerminalStream {
            override fun next(): TerminalChunk? = try {
                terminal.next()?.let { frame -> TerminalChunk(frame.kind, frame.data) }
            } catch (_: Exception) {
                null
            }

            override fun write(bytes: ByteArray) {
                runCatching { terminal.write(bytes) }
            }

            override fun resize(cols: Int, rows: Int, cellWidth: Int, cellHeight: Int) {
                runCatching { terminal.resize(cols, rows, cellWidth, cellHeight) }
            }

            override fun claim() {
                runCatching { terminal.claim() }
            }

            override fun close() {
                runCatching { terminal.close() }
            }
        }
    }

    override fun disconnect() {
        synchronized(lock) { active = false }
        client.close()
    }
}
