package com.sulthonuladib.keroid.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ConnectionLibraryTest {
    private val inspector = object : IdentityInspector {
        override fun inspect(privateKey: String) = IdentityInfo("SHA256:public", "ssh-ed25519", false)
    }
    private fun legacy() = SavedMachine(ConnectionProfile("Dev", "DEVBOX.", "alice", 2222, "SHA256:host", "/opt/kero"), "test-only-key")

    @Test fun migratesLegacyKeyMachineAndEndpointTrustBeforeDeletingOldRecord() {
        val records = Records(mutableMapOf("machine.v1" to wireJson.encodeToString(legacy())))
        val result = MigratingLibraryVault(records, inspector).load()
        val machine = result.machines.single()
        assertEquals("Dev", machine.name)
        assertEquals("/opt/kero", machine.executable)
        assertEquals(2222, machine.port)
        assertEquals(result.identities.single().identity.id, machine.identityId)
        assertTrue(result.identities.single().privateKey == legacy().privateKey)
        assertEquals("SHA256:host", result.pin(Endpoint.of("devbox", 2222)))
        assertEquals(machine.id, result.selectedMachineId)
        assertEquals(listOf("write:library.v2", "delete:machine.v1"), records.operations)
        assertNull(records.values["machine.v1"])
        val reloaded = MigratingLibraryVault(records, inspector).load()
        assertEquals(machine.id, reloaded.machines.single().id)
    }

    @Test fun failedMigrationWriteLeavesLegacyRecoverable() {
        val records = Records(mutableMapOf("machine.v1" to wireJson.encodeToString(legacy())), failWrite = true)
        assertTrue(runCatching { MigratingLibraryVault(records, inspector).load() }.isFailure)
        assertNotNull(records.values["machine.v1"])
        assertNull(records.values["library.v2"])
        records.failWrite = false
        assertEquals("Dev", MigratingLibraryVault(records, inspector).load().machines.single().name)
    }

    @Test fun committedV2IsAuthoritativeAfterInterruptedMigrationCleanup() {
        val records = Records(mutableMapOf("machine.v1" to wireJson.encodeToString(legacy()), "library.v2" to wireJson.encodeToString(ConnectionLibrary())))
        assertTrue(MigratingLibraryVault(records, inspector).load().machines.isEmpty())
    }

    @Test fun corruptV2DoesNotFallBackToStaleLegacyTrust() {
        val records = Records(mutableMapOf("machine.v1" to wireJson.encodeToString(legacy()), "library.v2" to "corrupt"))
        assertTrue(runCatching { MigratingLibraryVault(records, inspector).load() }.isFailure)
        assertTrue(records.operations.isEmpty())
    }

    @Test fun unknownLegacyKeyMetadataDoesNotDiscardTheKey() {
        val failing = object : IdentityInspector { override fun inspect(privateKey: String): IdentityInfo = error("unsupported") }
        val records = Records(mutableMapOf("machine.v1" to wireJson.encodeToString(legacy())))
        val result = MigratingLibraryVault(records, failing).load()
        assertTrue(result.identities.single().privateKey == legacy().privateKey)
        assertTrue(result.identities.single().identity.requiresPassphrase)
    }

    @Test fun pinsAreEndpointBoundAndCannotBeSilentlyOverwritten() {
        val library = ConnectionLibrary().approve(Endpoint.of("DEVBOX.", 22), "SHA256:one")
        assertEquals("SHA256:one", library.pin(Endpoint.of("devbox", 22)))
        assertEquals("", library.pin(Endpoint.of("devbox", 2222)))
        assertTrue(runCatching { library.approve(Endpoint.of("devbox", 22), "SHA256:other") }.isFailure)
        assertEquals("SHA256:one", library.withoutMachine("any").pin(Endpoint.of("devbox", 22)))
    }
}

private class Records(val values: MutableMap<String, String>, var failWrite: Boolean = false) : LibraryRecords {
    val operations = mutableListOf<String>()
    override fun read(name: String) = values[name]
    override fun write(name: String, plaintext: String) {
        if (failWrite) error("disk full")
        operations += "write:$name"
        values[name] = plaintext
    }
    override fun delete(name: String) { operations += "delete:$name"; values.remove(name) }
}
