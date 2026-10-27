package com.sulthonuladib.keroid.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real Android Keystore + AtomicFile, isolated from the app's saved connections. */
class CredentialVaultTest {
    private val directory = File(ApplicationProvider.getApplicationContext<Context>().noBackupFilesDir, "vault-test-${UUID.randomUUID()}").apply { mkdirs() }
    @After fun cleanup() { directory.deleteRecursively() }

    @Test fun encryptedLegacyRecordMigratesAndReloadsWithoutLosingKeyOrTrust() {
        val records = KeystoreLibraryRecords(directory)
        val legacy = SavedMachine(ConnectionProfile("Fixture", "127.0.0.1", "test", 2222, "SHA256:fixture"), "test-only-secret-material")
        records.write("machine.v1", wireJson.encodeToString(legacy))
        val inspector = object : IdentityInspector {
            override fun inspect(privateKey: String) = IdentityInfo("SHA256:public", "ssh-ed25519", true)
        }
        val vault = MigratingLibraryVault(records, inspector)
        val migrated = vault.load()
        assertTrue(migrated.identities.single().privateKey == legacy.privateKey)
        assertEquals("SHA256:fixture", migrated.pin(Endpoint.of("127.0.0.1", 2222)))
        assertFalse(File(directory, "machine.v1").exists())
        val bytes = File(directory, "library.v2").readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains(legacy.privateKey))
        assertEquals(12, bytes[0].toInt())
        val reloaded = vault.load()
        assertEquals(migrated.machines.single().id, reloaded.machines.single().id)
        assertTrue(reloaded.identities.single().privateKey == legacy.privateKey)
    }

    @Test fun tamperedLibraryIsRejectedNotReplacedWithEmptyStorage() {
        val records = KeystoreLibraryRecords(directory)
        val vault = MigratingLibraryVault(records, object : IdentityInspector {
            override fun inspect(privateKey: String) = IdentityInfo()
        })
        vault.save(ConnectionLibrary())
        val file = File(directory, "library.v2")
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertTrue(runCatching { vault.load() }.isFailure)
        assertTrue(file.readBytes().contentEquals(bytes))
    }
}
