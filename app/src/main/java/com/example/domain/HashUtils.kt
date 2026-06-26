package com.example.domain

import java.security.MessageDigest

object HashUtils {
    // Salt/Pepper constraint to prevent standard offline lookups on SHA-256 digests
    private const val APP_PEPPER = "SmartMakhzanSecurityGuard_2026_#!"

    /**
     * Generates a protected cryptographic SHA-256 hash using static and dynamic salts.
     * Backwards-compatible fallback handles legacy inputs gracefully.
     */
    fun hashString(input: String, deviceSalt: String = ""): String {
        val dynamicSalt = if (deviceSalt.isNotEmpty()) deviceSalt.reversed() else "DefaultDeviceSalt2026#$"
        val saltedInput = input + APP_PEPPER + dynamicSalt
        val bytes = saltedInput.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
