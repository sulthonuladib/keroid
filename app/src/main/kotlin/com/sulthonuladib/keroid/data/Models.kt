package com.sulthonuladib.keroid.data

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val wireJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * How a machine is reached. SSH runs `kero __bridge` on the host; Tailscale
 * reaches kerod's own tailnet listener or Tailscale Serve, and sends no key:
 * kerod identifies the caller itself. Desktop takes the same two ways.
 */
@Serializable
enum class Transport {
    @SerialName("ssh") Ssh,
    @SerialName("tailscale") Tailscale,
}

/**
 * One machine's connection as the Go client takes it. Both transports share the
 * type because everything after the connection is the same protocol; host pins
 * belong to SSH only.
 */
@Serializable
data class ConnectionProfile(
    val name: String,
    val host: String = "",
    val user: String = "",
    val port: Int = 22,
    val fingerprint: String = "",
    val executable: String = "",
    val transport: Transport = Transport.Ssh,
    /** Tailscale only: kerod's listener address, e.g. `https://devbox.tail1234.ts.net`. */
    val endpoint: String = "",
) {
    val isTailscale: Boolean get() = transport == Transport.Tailscale

    /** How the machine reads in a list: an SSH destination or a tailnet address. */
    val address: String
        get() = if (isTailscale) endpoint
        else "$user@${if (host.contains(':')) "[$host]" else host}"
}

@Serializable
data class ConnectionDraft(
    val name: String = "",
    val address: String = "",
    val port: String = "22",
    val executable: String = "",
    val identityId: String = "",
    val transport: Transport = Transport.Ssh,
    /** Tailscale only: http://host:7373, https://host.tailnet.ts.net, or a host. */
    val endpoint: String = "",
) {
    fun machine(id: String): Machine {
        val profile = profile()
        require(profile.isTailscale || identityId.isNotBlank()) { "Choose an SSH identity." }
        return Machine(id, profile.name, profile.host, profile.user, identityId, profile.port, profile.executable,
            profile.transport, profile.endpoint)
    }

    fun profile(): ConnectionProfile {
        require(name.isNotBlank()) { "Give this machine a name." }
        return if (transport == Transport.Tailscale) profileTailscale() else profileSsh()
    }

    private fun profileTailscale(): ConnectionProfile {
        val input = endpoint.trim()
        require(input.isNotEmpty() && input.none(Char::isWhitespace)) {
            "Enter the address the host's app shows, such as http://devbox:7373."
        }
        val parsed = TailnetAddress.parse(input).normalized
        return ConnectionProfile(name.trim(), transport = Transport.Tailscale, endpoint = parsed)
    }

    private fun profileSsh(): ConnectionProfile {
        val input = address.trim()
        require(input.isNotEmpty() && input.none(Char::isWhitespace)) {
            "Enter an SSH address such as user@hostname."
        }
        val uri = try { URI(if (input.startsWith("ssh://")) input else "ssh://$input") }
        catch (_: Exception) { throw IllegalArgumentException("Enter a valid SSH address.") }
        require(uri.scheme == "ssh" && uri.rawQuery == null && uri.rawFragment == null &&
            uri.path.orEmpty().let { it.isEmpty() || it == "/" }) {
            "Use user@hostname. Set the Kero executable separately."
        }
        val user = uri.userInfo.orEmpty()
        require(user.isNotBlank() && !user.contains(':') && !user.contains('%')) {
            "Include your SSH username. Passwords do not belong in the address."
        }
        val host = uri.host.orEmpty().removeSurrounding("[", "]")
        require(host.isNotBlank()) { "Enter a valid hostname or IP address." }
        val portNumber = if (uri.port != -1) uri.port else port.toIntOrNull()
        require(portNumber != null && portNumber in 1..65535) { "SSH port must be between 1 and 65535." }
        return ConnectionProfile(name.trim(), host, user, portNumber, executable = executable.trim())
    }

    companion object {
        fun from(profile: ConnectionProfile) = if (profile.isTailscale) {
            ConnectionDraft(name = profile.name, transport = Transport.Tailscale, endpoint = profile.endpoint)
        } else {
            ConnectionDraft(name = profile.name, address = profile.address, port = profile.port.toString(),
                executable = profile.executable)
        }

        fun from(machine: Machine) = from(machine.profile()).copy(identityId = machine.identityId)
    }
}

@Serializable
data class HostKeyProblem(
    val code: String,
    val fingerprint: String,
    val algorithm: String,
)

/**
 * kerod refused this device over Tailscale: its owner's `listen-allow` rules, or
 * a request that did not look like Kero's. Carries a code so the app can say
 * "refused" rather than "unreachable", and never falls back to another route.
 */
@Serializable
data class TailnetRefusal(val code: String, val message: String = "")

@Serializable
data class KeroProject(val id: String, val name: String, val dir: String = "")

@Serializable
data class KeroPane(
    val id: String,
    val title: String = "",
    val agent: String = "",
    val process: String = "",
    val dir: String = "",
    val status: String = "idle",
    val message: String = "",
    val progress: Int = -1,
    val url: String = "",
) {
    val label: String get() = title.ifBlank { agent.ifBlank { process.ifBlank { "Shell" } } }
}

@Serializable
data class KeroSession(
    val id: String,
    val project: String,
    val name: String = "",
    val title: String = "",
    val dir: String = "",
    val agent: String = "",
    val status: String = "idle",
    val message: String = "",
    val unread: Boolean = false,
    val progress: Int = -1,
    val lead: String = "",
    val focus: String = "",
    val panes: List<KeroPane> = emptyList(),
) {
    val label: String get() = name.ifBlank { title.ifBlank { dir.substringAfterLast('/').ifBlank { "Session" } } }
    val needsAttention: Boolean get() = status == "waiting" || status == "error" || unread
}

@Serializable
data class KeroState(
    val projects: List<KeroProject> = emptyList(),
    val sessions: List<KeroSession> = emptyList(),
)

/** Control payloads mirrored from the pinned Kero protocol, not shell commands. */
@Serializable
data class CreateSessionRequest(
    val project: String = "",
    val dir: String = "",
    val after: String = "",
    val split: String = "",
    val direction: String = "",
    val cols: Int = 0,
    val rows: Int = 0,
)

@Serializable
internal data class IdRequest(val id: String)

@Serializable
internal data class RenameSessionRequest(val id: String, val name: String)

@Serializable
internal data class DirectoryRequest(val dir: String, val hidden: Boolean)

@Serializable
data class RemoteDirectory(
    val dir: String,
    val parent: String = "",
    val home: String = "",
    val entries: List<RemoteDirectoryEntry> = emptyList(),
) {
    fun child(name: String): String = dir.trimEnd('/') + "/" + name
}

@Serializable
data class RemoteDirectoryEntry(val name: String, val dir: Boolean = false, val repo: Boolean = false)

@Serializable
internal data class StateEvent(val event: String, val state: KeroState? = null)

@Serializable
internal data class ReadResult(val text: String)

@Serializable
internal data class ReadRequest(val id: String, val lines: Int = 150, val scrollback: Boolean = true)
