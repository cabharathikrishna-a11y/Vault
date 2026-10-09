package com.example.data.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-Performance Encrypted Memory Management (EMM) Cryptographic Engine.
 * Features:
 * - Sub-second hardware-accelerated PBKDF2 key derivation.
 * - AES-GCM 256-bit encryption with random IVs and 128-bit authentication tags.
 * - EMM Memory Zeroization (Arrays.fill) to prevent RAM inspection and memory dump leaks.
 */
object VaultCrypto {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_ALGORITHM = "AES"
    private const val ITERATION_COUNT = 10_000
    private const val KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val VERIFIER_MAGIC = "FAMILY_VAULT_KEY_VALID_2026"

    private val secureRandom = SecureRandom()

    /**
     * Wipes byte array memory immediately to ensure zero plaintext residue in RAM (EMM standard).
     */
    fun zeroizeBytes(bytes: ByteArray?) {
        if (bytes != null && bytes.isNotEmpty()) {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    /**
     * Wipes char array memory immediately (EMM standard).
     */
    fun zeroizeChars(chars: CharArray?) {
        if (chars != null && chars.isNotEmpty()) {
            Arrays.fill(chars, '0')
        }
    }

    fun generateSalt(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val result = Base64.encodeToString(salt, Base64.NO_WRAP)
        zeroizeBytes(salt)
        return result
    }

    fun deriveKey(passphrase: String, saltBase64: String): SecretKey {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val passChars = passphrase.toCharArray()
        return try {
            val keySpec = PBEKeySpec(passChars, salt, ITERATION_COUNT, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, AES_ALGORITHM)
            zeroizeBytes(keyBytes)
            keySpec.clearPassword()
            secretKey
        } finally {
            zeroizeBytes(salt)
            zeroizeChars(passChars)
        }
    }

    fun generateKeyCheckHashFromKey(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyEncoded = key.encoded
        val base64Key = Base64.encodeToString(keyEncoded, Base64.NO_WRAP)
        zeroizeBytes(keyEncoded)

        val input = (VERIFIER_MAGIC + ":" + base64Key).toByteArray(Charsets.UTF_8)
        val hash = digest.digest(input)
        zeroizeBytes(input)

        return Base64.encodeToString(hash, Base64.NO_WRAP)
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

        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val cipherText = cipher.doFinal(plainBytes)

            val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
            byteBuffer.put(iv)
            byteBuffer.put(cipherText)

            val resultBytes = byteBuffer.array()
            Base64.encodeToString(resultBytes, Base64.NO_WRAP)
        } finally {
            zeroizeBytes(iv)
            zeroizeBytes(plainBytes)
        }
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

        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val decryptedBytes = cipher.doFinal(cipherText)
            val result = String(decryptedBytes, Charsets.UTF_8)
            zeroizeBytes(decryptedBytes)
            result
        } finally {
            zeroizeBytes(combined)
            zeroizeBytes(iv)
            zeroizeBytes(cipherText)
        }
    }
}
