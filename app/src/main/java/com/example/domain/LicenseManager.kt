package com.example.domain

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * LicenseManager isolates and protects all license validation, cryptographic hashing,
 * and device fingerprinting logic inside the Domain Layer, preventing architectural leakage.
 */
object LicenseManager {

    fun getSecureLimitVal(): Int {
        val mask1 = 0xE6F2
        val mask2 = 0xE696
        return mask1 xor mask2 // results in 100
    }

    fun getPrefixTemp(): String {
        return String(byteArrayOf(65, 67, 84, 45, 84, 45), Charsets.UTF_8) // "ACT-T-"
    }

    fun getPrefixPerm(): String {
        return String(byteArrayOf(65, 67, 84, 45, 80, 45), Charsets.UTF_8) // "ACT-P-"
    }

    fun decryptSalt(): String {
        val mask = 0x7F
        val obfuscatedSalt = byteArrayOf(
            50, 22, 5, 30, 17, 62, 19, 59, 30, 13,
            44, 26, 28, 10, 13, 26, 44, 30, 19, 11,
            77, 79, 77, 73, 32, 50, 30, 17, 12, 16,
            10, 13
        )
        val decrypted = ByteArray(obfuscatedSalt.size)
        for (i in obfuscatedSalt.indices) {
            decrypted[i] = (obfuscatedSalt[i].toInt() xor mask).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }

    fun verifyActivationCode(deviceId: String, enteredCode: String): Boolean {
        val cleanEntered = enteredCode.trim().uppercase()
        val parts = deviceId.split("-")
        val tempPart = if (parts.size >= 3) parts[1] else ""
        val permPart = if (parts.size >= 3) parts[2] else ""

        val tempPrefix = getPrefixTemp()
        val permPrefix = getPrefixPerm()

        if (cleanEntered.startsWith(tempPrefix)) {
            val enteredPayload = cleanEntered.substring(tempPrefix.length)
            val salt = decryptSalt()
            val combined = tempPart + salt
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            val shaResult = bytes.joinToString("") { "%02x".format(it) }.uppercase()
            return enteredPayload == shaResult.take(8)
        } else if (cleanEntered.startsWith(permPrefix)) {
            val enteredPayload = cleanEntered.substring(permPrefix.length)
            val salt = decryptSalt()
            val combined = permPart + salt
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            val shaResult = bytes.joinToString("") { "%02x".format(it) }.uppercase()
            return enteredPayload == shaResult.take(8)
        }
        return false
    }

    fun getOrGenerateUnifiedDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("makhzan_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("unified_device_id", "")

        if (deviceId.isNullOrBlank()) {
            val tempPart = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            val permPart = if (!androidId.isNullOrBlank()) {
                androidId.take(8).uppercase()
            } else {
                "A1B2C3D4"
            }
            deviceId = "MZ-$tempPart-$permPart"
            prefs.edit().putString("unified_device_id", deviceId).apply()
        }
        return deviceId
    }
}
