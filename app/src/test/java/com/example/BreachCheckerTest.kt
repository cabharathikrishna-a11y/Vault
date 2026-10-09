package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Locale

class BreachCheckerTest {

    @Test
    fun testKAnonymitySha1PrefixAndSuffix() {
        // Test standard known vector: "password"
        // SHA-1 of "password" is 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        val testPassword = "password"
        val digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = digest.digest(testPassword.toByteArray(Charsets.UTF_8))
        val sha1 = hashBytes.joinToString("") { "%02X".format(it) }.uppercase(Locale.US)

        assertEquals(40, sha1.length)
        val prefix = sha1.substring(0, 5)
        val suffix = sha1.substring(5)

        assertEquals("5BAA6", prefix)
        assertEquals("1E4C9B93F3F0682250B6CF8331B7EE68FD8", suffix)

        // Verify only 5 characters are exposed for k-Anonymity
        assertEquals(5, prefix.length)
        assertEquals(35, suffix.length)
    }
}
