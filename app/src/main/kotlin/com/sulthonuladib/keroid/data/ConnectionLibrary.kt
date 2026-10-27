package com.sulthonuladib.keroid.data

import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** Only public metadata may be passed to UI or saved state. */
@Serializable
data class Identity(
    val id: String,
    val name: String,
    val fingerprint: String = "",
    val algorithm: String = "",
    val requiresPassphrase: Boolean = true,
)

@Serializable
data class IdentityInfo(
    val fingerprint: String = "",
    val algorithm: String = "",
    val requiresPassphrase: Boolean = true,
)

// Not a data class: generated toString/copy must not accidentally expose a key.
@Serializable
class StoredIdentity(val identity: Identity, val privateKey: String)

@Serializable
data class Machine(
    val id: String,
    val name: String,
    val host: String,
    val user: String,
    val identityId: String,
    val port: Int = 22,
    val executable: String = "",
    /** Defaults keep saved machines from before Tailscale reaching over SSH. */
    val transport: Transport = Transport.Ssh,
    /** Tailscale only; empty for SSH machines. */
    val endpoint: String = "",
) {
    /** SSH's host:port, which host-key trust is bound to; unused by Tailscale. */
    val sshEndpoint: Endpoint get() = Endpoint.of(host, port)

    val isTailscale: Boolean get() = transport == Transport.Tailscale

    /** What the machine list shows: a tailnet address, or an SSH destination. */
    val address: String
        get() = if (isTailscale) endpoint
        else "$user@${if (host.contains(':')) "[$host]" else host}${
            if (port == 22) "" else ":$port"
        }"

    fun profile(fingerprint: String = "") =
        ConnectionProfile(name, host, user, port, fingerprint, executable, transport, endpoint)
}

@Serializable
data class Endpoint(val host: String, val port: Int) {
    val label: String get() = "${if (host.contains(':')) "[$host]" else host}:$port"
    companion object {
        fun of(host: String, port: Int) = Endpoint(host.lowercase(Locale.ROOT).trimEnd('.'), port)
    }
}

@Serializable
data class HostPin(val endpoint: Endpoint, val fingerprint: String)

@Serializable
class ConnectionLibrary(
    val identities: List<StoredIdentity> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val pins: List<HostPin> = emptyList(),
    val selectedMachineId: String? = null,
) {
    fun pin(endpoint: Endpoint): String = pins.firstOrNull { it.endpoint == endpoint }?.fingerprint.orEmpty()
    fun withMachine(machine: Machine) = ConnectionLibrary(
        identities, machines.filterNot { it.id == machine.id } + machine, pins, machine.id,
    )
    fun withoutMachine(id: String) = ConnectionLibrary(
        identities, machines.filterNot { it.id == id }, pins,
        selectedMachineId?.takeUnless { it == id } ?: machines.firstOrNull { it.id != id }?.id,
    ) // Pins deliberately survive deleting a profile: re-adding must not bypass trust.
    fun withIdentity(identity: StoredIdentity) = ConnectionLibrary(
        identities.filterNot { it.identity.id == identity.identity.id } + identity, machines, pins, selectedMachineId,
    )
    fun withoutIdentity(id: String): ConnectionLibrary {
        require(machines.none { it.identityId == id }) { "This identity is used by a machine. Reassign or delete that machine first." }
        return ConnectionLibrary(identities.filterNot { it.identity.id == id }, machines, pins, selectedMachineId)
    }
    fun approve(endpoint: Endpoint, fingerprint: String): ConnectionLibrary {
        require(fingerprint.isNotBlank()) { "Invalid host fingerprint." }
        val existing = pin(endpoint)
        require(existing.isEmpty() || existing == fingerprint) { "Host key changed. Connection blocked." }
        return ConnectionLibrary(identities, machines, pins.filterNot { it.endpoint == endpoint } + HostPin(endpoint, fingerprint), selectedMachineId)
    }
    fun validate(): ConnectionLibrary {
        require(identities.map { it.identity.id }.distinct().size == identities.size)
        require(machines.map { it.id }.distinct().size == machines.size)
        require(pins.map { it.endpoint }.distinct().size == pins.size)
        // Tailscale machines carry no identity: kerod decides access itself.
        require(machines.all { m -> m.isTailscale || identities.any { it.identity.id == m.identityId } })
        return this
    }
}

@Serializable
class SavedMachine(val profile: ConnectionProfile, val privateKey: String)

interface LibraryVault {
    fun load(): ConnectionLibrary
    fun save(library: ConnectionLibrary)
}

/** Implementations encrypt/decrypt records, never log or back up their contents. */
interface LibraryRecords {
    fun read(name: String): String?
    fun write(name: String, plaintext: String)
    fun delete(name: String)
}

interface IdentityInspector { fun inspect(privateKey: String): IdentityInfo }

/** Pure, testable migration. A corrupt v2 never silently falls back to old trust. */
class MigratingLibraryVault(
    private val records: LibraryRecords,
    private val inspector: IdentityInspector,
) : LibraryVault {
    override fun load(): ConnectionLibrary {
        records.read("library.v2")?.let {
            val library = wireJson.decodeFromString<ConnectionLibrary>(it).validate()
            // A crash after committing v2 may leave v1; v2 is always authoritative.
            runCatching { records.delete("machine.v1") }
            return library
        }
        val legacy = records.read("machine.v1")?.let { wireJson.decodeFromString<SavedMachine>(it) }
            ?: return ConnectionLibrary()
        val info = runCatching { inspector.inspect(legacy.privateKey) }.getOrDefault(IdentityInfo())
        val identity = Identity(UUID.randomUUID().toString(), "${legacy.profile.name} key", info.fingerprint, info.algorithm, info.requiresPassphrase)
        val profile = legacy.profile
        val machine = Machine(UUID.randomUUID().toString(), profile.name, profile.host, profile.user, identity.id, profile.port, profile.executable)
        val library = ConnectionLibrary(
            listOf(StoredIdentity(identity, legacy.privateKey)), listOf(machine),
            if (profile.fingerprint.isBlank()) emptyList() else listOf(HostPin(machine.sshEndpoint, profile.fingerprint)), machine.id,
        )
        save(library) // Atomically commit the new encrypted record BEFORE removing v1.
        runCatching { records.delete("machine.v1") }
        return library
    }
    override fun save(library: ConnectionLibrary) {
        records.write("library.v2", wireJson.encodeToString(library.validate()))
    }
}
