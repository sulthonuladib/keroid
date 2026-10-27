package com.sulthonuladib.keroid.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Separate encrypted machine and identity library; passphrases are NEVER persisted. */
@Singleton
class CredentialVault @Inject constructor(
    @ApplicationContext context: Context,
    inspector: IdentityInspector,
) : LibraryVault {
    private val delegate = MigratingLibraryVault(KeystoreLibraryRecords(context.noBackupFilesDir), inspector)
    @Synchronized override fun load() = delegate.load()
    @Synchronized override fun save(library: ConnectionLibrary) = delegate.save(library)
}

internal class KeystoreLibraryRecords(private val directory: File) : LibraryRecords {
    private fun record(name: String): AtomicFile {
        require(name == "machine.v1" || name == "library.v2")
        return AtomicFile(File(directory, name))
    }
    // Keep the v1 alias AND AAD byte-for-byte compatible with existing installs.
    private fun alias(name: String) = "keroid.$name"
    private fun key(name: String, create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias(name), null) as? SecretKey)?.let { return it }
        check(create) { "The saved connection key is unavailable." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias(name), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    override fun read(name: String): String? {
        val record = record(name)
        // AtomicFile may have only a backup after an interrupted legacy write.
        if (!record.baseFile.exists() && !File(record.baseFile.path + ".bak").exists()) return null
        require(record.baseFile.length() <= 16 * 1024 * 1024) { "Invalid saved library." }
        val bytes = record.readFully()
        require(bytes.size in 30..(16 * 1024 * 1024)) { "Invalid saved library." }
        val ivLength = bytes[0].toInt() and 255
        require(ivLength == 12) { "Invalid saved library." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(name, false), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivLength)))
        cipher.updateAAD(alias(name).toByteArray())
        val plaintext = cipher.doFinal(bytes.copyOfRange(1 + ivLength, bytes.size))
        return try { plaintext.toString(Charsets.UTF_8) } finally { plaintext.fill(0) }
    }

    override fun write(name: String, plaintext: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(name, true))
        cipher.updateAAD(alias(name).toByteArray())
        val bytes = plaintext.toByteArray()
        require(bytes.size <= 16 * 1024 * 1024 - 29) { "The connection library is full." }
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        val record = record(name)
        val stream = record.startWrite()
        try {
            stream.write(byteArrayOf(cipher.iv.size.toByte()))
            stream.write(cipher.iv)
            stream.write(encrypted)
            record.finishWrite(stream)
        } catch (e: Exception) {
            record.failWrite(stream)
            throw e
        }
    }

    override fun delete(name: String) {
        record(name).delete()
        // Keep the old Keystore entry: Android AtomicFile deletion does not report
        // failures. Removing its key first could make a surviving v1 unrecoverable.
    }
}
