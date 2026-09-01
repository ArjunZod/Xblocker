package com.antigravity.shieldx.device

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class AdminRecoveryController(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "shieldx_admin_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences("shieldx_admin_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_PIN_HASH = "admin_pin_hash"
        private const val KEY_PIN_SALT = "admin_pin_salt"
        private const val KEY_IS_PIN_SET = "admin_is_pin_set"
    }

    fun isPinSet(): Boolean {
        return prefs.getBoolean(KEY_IS_PIN_SET, false)
    }

    fun setPin(pin: String): Boolean {
        if (pin.length < 4) return false

        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = Base64.getEncoder().encodeToString(saltBytes)

        val hash = hashPinWithSalt(pin, salt)

        return prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .putBoolean(KEY_IS_PIN_SET, true)
            .commit()
    }

    fun verifyPin(enteredPin: String): Boolean {
        // No PIN configured yet means nothing has been protected yet either -
        // deny rather than accept a well-known default anyone could guess.
        if (!isPinSet()) return false

        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return false

        val computedHash = hashPinWithSalt(enteredPin, storedSalt)
        return storedHash == computedHash
    }

    private fun hashPinWithSalt(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(combined)
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}
