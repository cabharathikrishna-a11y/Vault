package com.example.generator

import java.security.SecureRandom
import kotlin.math.log2

data class PasswordGeneratorConfig(
    val length: Int = 18,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val avoidAmbiguous: Boolean = true
) {
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (includeUppercase) parts.add("A-Z")
        if (includeLowercase) parts.add("a-z")
        if (includeNumbers) parts.add("0-9")
        if (includeSymbols) parts.add("#$%")
        return "${length} chars (${parts.joinToString(", ")})"
    }
}

enum class PasswordStrength(val label: String, val score: Float) {
    WEAK("Weak", 0.25f),
    FAIR("Fair", 0.5f),
    STRONG("Strong", 0.75f),
    VERY_STRONG("Very Strong (Vault Grade)", 1.0f)
}

object PasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private const val AMBIGUOUS = "iloIO01"

    private val secureRandom = SecureRandom()

    fun generate(config: PasswordGeneratorConfig): String {
        var charPool = ""
        val requiredChars = mutableListOf<Char>()

        var upper = UPPERCASE
        var lower = LOWERCASE
        var num = NUMBERS
        var sym = SYMBOLS

        if (config.avoidAmbiguous) {
            upper = upper.filter { it !in AMBIGUOUS }
            lower = lower.filter { it !in AMBIGUOUS }
            num = num.filter { it !in AMBIGUOUS }
            sym = sym.filter { it !in AMBIGUOUS }
        }

        if (config.includeUppercase && upper.isNotEmpty()) {
            charPool += upper
            requiredChars.add(upper[secureRandom.nextInt(upper.length)])
        }
        if (config.includeLowercase && lower.isNotEmpty()) {
            charPool += lower
            requiredChars.add(lower[secureRandom.nextInt(lower.length)])
        }
        if (config.includeNumbers && num.isNotEmpty()) {
            charPool += num
            requiredChars.add(num[secureRandom.nextInt(num.length)])
        }
        if (config.includeSymbols && sym.isNotEmpty()) {
            charPool += sym
            requiredChars.add(sym[secureRandom.nextInt(sym.length)])
        }

        if (charPool.isEmpty()) {
            charPool = LOWERCASE
            requiredChars.add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
        }

        val passwordChars = ArrayList<Char>()
        passwordChars.addAll(requiredChars)

        while (passwordChars.size < config.length) {
            val randomChar = charPool[secureRandom.nextInt(charPool.length)]
            passwordChars.add(randomChar)
        }

        // Shuffle securely
        passwordChars.shuffle(secureRandom)
        return passwordChars.joinToString("")
    }

    fun calculateStrength(password: String): Pair<PasswordStrength, Double> {
        if (password.isEmpty()) return Pair(PasswordStrength.WEAK, 0.0)

        var poolSize = 0
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        if (poolSize == 0) poolSize = 10
        val entropyBits = password.length * log2(poolSize.toDouble())

        val strength = when {
            entropyBits < 40 -> PasswordStrength.WEAK
            entropyBits < 65 -> PasswordStrength.FAIR
            entropyBits < 90 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }

        return Pair(strength, entropyBits)
    }
}
