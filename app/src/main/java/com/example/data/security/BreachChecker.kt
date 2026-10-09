package com.example.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class BreachCheckResult(
    val checked: Boolean,
    val isPwned: Boolean,
    val breachCount: Long,
    val errorMessage: String? = null
)

object BreachChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun checkPassword(password: String): BreachCheckResult = withContext(Dispatchers.IO) {
        if (password.isBlank()) {
            return@withContext BreachCheckResult(checked = false, isPwned = false, breachCount = 0)
        }

        try {
            // 1. Compute SHA-1 hash of password (uppercase)
            val digest = MessageDigest.getInstance("SHA-1")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            val sha1Hash = hashBytes.joinToString("") { "%02X".format(it) }.uppercase(Locale.US)

            // 2. k-Anonymity: Send only the first 5 characters
            val prefix = sha1Hash.substring(0, 5)
            val suffix = sha1Hash.substring(5)

            // 3. Query HIBP Range API
            val request = Request.Builder()
                .url("https://api.pwnedpasswords.com/range/$prefix")
                .header("User-Agent", "FamilyVault-Android-Client")
                .header("Add-Padding", "true") // Adds randomized padding to prevent response size analysis
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext BreachCheckResult(
                        checked = false,
                        isPwned = false,
                        breachCount = 0,
                        errorMessage = "HIBP API error: ${response.code}"
                    )
                }

                val body = response.body?.string() ?: ""
                var breachCount: Long = 0
                var found = false

                // 4. Client-side local suffix search
                body.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.contains(":")) {
                        val parts = trimmed.split(":")
                        if (parts.size >= 2) {
                            val returnedSuffix = parts[0].trim().uppercase(Locale.US)
                            if (returnedSuffix == suffix) {
                                found = true
                                breachCount = parts[1].trim().toLongOrNull() ?: 1
                            }
                        }
                    }
                }

                BreachCheckResult(
                    checked = true,
                    isPwned = found,
                    breachCount = breachCount
                )
            }
        } catch (e: Exception) {
            BreachCheckResult(
                checked = false,
                isPwned = false,
                breachCount = 0,
                errorMessage = e.localizedMessage ?: "Failed to connect to breach database"
            )
        }
    }
}
