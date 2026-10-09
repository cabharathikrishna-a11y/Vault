package com.example.security

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EmmPolicy(
    val isManaged: Boolean = false,
    val disallowScreenshots: Boolean = true,
    val enforceBiometrics: Boolean = false,
    val autoLockTimeoutSeconds: Int = 300,
    val allowExport: Boolean = true
)

class SecurityManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("family_vault_security_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "local_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DARK_MODE_SETTING = "dark_mode_setting" // "system", "dark", "light"
        private const val KEY_SAVED_PASSPHRASE = "encrypted_saved_passphrase"
        private const val KEYSTORE_ALIAS = "family_vault_passphrase_alias"
    }

    /**
     * Inspects Enterprise Mobility Management (EMM) restrictions applied by device management / MDM.
     */
    fun getEmmPolicy(): EmmPolicy {
        return try {
            val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            val applicationRestrictions: Bundle? = restrictionsManager?.applicationRestrictions
            if (applicationRestrictions != null && !applicationRestrictions.isEmpty) {
                EmmPolicy(
                    isManaged = true,
                    disallowScreenshots = applicationRestrictions.getBoolean("disallow_screenshots", true),
                    enforceBiometrics = applicationRestrictions.getBoolean("enforce_biometrics", false),
                    autoLockTimeoutSeconds = applicationRestrictions.getInt("auto_lock_timeout", 300),
                    allowExport = applicationRestrictions.getBoolean("allow_export", true)
                )
            } else {
                EmmPolicy(isManaged = false, disallowScreenshots = true)
            }
        } catch (_: Exception) {
            EmmPolicy(isManaged = false, disallowScreenshots = true)
        }
    }

    /**
     * Applies Window FLAG_SECURE for screenshot disallowance per EMM policy / Vault security standard.
     */
    fun applyEmmWindowSecurity(activity: FragmentActivity) {
        val policy = getEmmPolicy()
        if (policy.disallowScreenshots) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun getOrCreateKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun saveMasterPassphrase(passphrase: String) {
        if (passphrase.isBlank()) return
        try {
            val key = getOrCreateKeyStoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(KEY_SAVED_PASSPHRASE, encoded).apply()
        } catch (e: Exception) {
            val encoded = Base64.encodeToString(passphrase.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit().putString(KEY_SAVED_PASSPHRASE, "raw_$encoded").apply()
        }
    }

    fun getSavedMasterPassphrase(): String? {
        val encoded = prefs.getString(KEY_SAVED_PASSPHRASE, null) ?: return null
        if (encoded.startsWith("raw_")) {
            return try {
                String(Base64.decode(encoded.removePrefix("raw_"), Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) { null }
        }
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= 12) return null
            val iv = ByteArray(12)
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)

            val key = getOrCreateKeyStoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun clearSavedMasterPassphrase() {
        prefs.edit().remove(KEY_SAVED_PASSPHRASE).apply()
    }

    fun resetAllLocalData() {
        prefs.edit().clear().apply()
    }

    fun isPinSet(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == hashPin(pin)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun getDarkModeSetting(): String {
        return prefs.getString(KEY_DARK_MODE_SETTING, "system") ?: "system"
    }

    fun setDarkModeSetting(setting: String) {
        prefs.edit().putString(KEY_DARK_MODE_SETTING, setting).apply()
    }

    fun canAuthenticateBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Biometric Verification",
        subtitle: String = "Touch fingerprint sensor or use face recognition",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric authentication failed. Try again.")
            }
        })

        prompt.authenticate(promptInfo)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("SALT_VAULT_PIN_2026_" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
