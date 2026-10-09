package com.example.data.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultCrypto {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_ALGORITHM = "AES"
    // Recommended mobile iteration count for responsive execution:
    // 10,000 iterations provides strong cryptographic security with sub-second derivation
    private const val ITERATION_COUNT = 10_000
    private const val KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val VERIFIER_MAGIC = "FAMILY_VAULT_KEY_VALID_2026"

    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun deriveKey(passphrase: String, saltBase64: String): SecretKey {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }

    fun generateKeyCheckHashFromKey(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = (VERIFIER_MAGIC + ":" + Base64.encodeToString(key.encoded, Base64.NO_WRAP)).toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(digest.digest(input), Base64.NO_WRAP)
    }

    fun generateKeyCheckHash(passphrase: String, saltBase64: String): String {
        val key = deriveKey(passphrase, saltBase64)
        return generateKeyCheckHashFromKey(key)
    }

    fun verifyPassphrase(passphrase: String, saltBase64: String, expectedCheckHash: String): Boolean {
        val calculatedHash = generateKeyCheckHash(passphrase, saltBase64)
        return calculatedHash == expectedCheckHash
    }

    fun encrypt(plainText: String, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
        byteBuffer.put(iv)
        byteBuffer.put(cipherText)

        return Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String, key: SecretKey): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }

        val byteBuffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(GCM_IV_LENGTH)
        byteBuffer.get(iv)

        val cipherText = ByteArray(byteBuffer.remaining())
        byteBuffer.get(cipherText)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
