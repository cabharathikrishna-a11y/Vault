package com.example

import com.example.data.crypto.VaultCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultCryptoTest {

    @Test
    fun testKeyDerivationAndEncryptionDecryption() {
        val passphrase = "SuperStrongFamilyPassphrase2026!"
        val salt = VaultCrypto.generateSalt()

        val key = VaultCrypto.deriveKey(passphrase, salt)
        val checkHash = VaultCrypto.generateKeyCheckHash(passphrase, salt)

        // Verify key check hash passes with correct passphrase
        assertTrue(VaultCrypto.verifyPassphrase(passphrase, salt, checkHash))
        // Verify key check hash fails with incorrect passphrase
        assertFalse(VaultCrypto.verifyPassphrase("WrongPassword123", salt, checkHash))

        // Encrypt & Decrypt plaintext
        val sensitiveData = "DocId: SAMPLE-DOC-ID, PIN: 9988, Secret: FamilyVaultLossless"
        val encrypted = VaultCrypto.encrypt(sensitiveData, key)
        val decrypted = VaultCrypto.decrypt(encrypted, key)

        assertEquals(sensitiveData, decrypted)
    }
}
