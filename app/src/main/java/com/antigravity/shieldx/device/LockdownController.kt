package com.antigravity.shieldx.device

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Base64

/**
 * A deliberate, self-imposed lock on the protection toggle.
 *
 * The whole point is that the user does not get to talk themselves out of it in
 * a weak moment: the unlock secret is machine-generated, never chosen by the
 * user, shown to them exactly once, and never stored anywhere in plaintext -
 * only a salted PBKDF2 hash lives on the device (see [LockdownSecretUtil] for
 * the hashing itself). Forgetting the secret is the intended failure mode, not
 * a bug; it is what makes this a commitment device rather than a toggle with
 * extra steps.
 *
 * This gates the in-app "turn protection off" action. It is independent of, and
 * complements, the OS-level uninstall block that [RestrictionController]
 * applies when the device is enrolled as Device Owner - that stops the app
 * being removed; this stops protection being switched off while it stays
 * installed.
 */
class LockdownController(context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "tarzi_lockdown_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Encrypted storage should always be available on API 26+, but a
            // plain file that still requires the correct secret to unlock is a
            // safer failure than crashing and leaving lockdown unenforceable.
            context.getSharedPreferences("tarzi_lockdown_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_ACTIVE = "lockdown_active"
        private const val KEY_HASH = "lockdown_secret_hash"
        private const val KEY_SALT = "lockdown_secret_salt"
    }

    /** True while the user has committed to not being able to switch protection off. */
    fun isActive(): Boolean = prefs.getBoolean(KEY_ACTIVE, false)

    /**
     * Generate a fresh 100-character secret, store only its hash, and return the
     * plaintext exactly this once. Callers MUST display it immediately and must
     * never persist, log, or transmit it - there is no way to recover it later,
     * by design.
     */
    fun activate(): String {
        val secret = LockdownSecretUtil.generateSecret()
        val salt = LockdownSecretUtil.generateSalt()
        val hash = LockdownSecretUtil.deriveHash(secret, salt)

        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_HASH, hash)
            .putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
            .commit()

        return secret
    }

    /**
     * Verify [candidate] against the stored hash and, only if it matches, lift
     * the lock. Wrong guesses do not consume or weaken the secret.
     */
    fun deactivate(candidate: String): Boolean {
        if (!verify(candidate)) return false
        prefs.edit().putBoolean(KEY_ACTIVE, false).commit()
        return true
    }

    /** Check a candidate secret without changing lockdown state. */
    fun verify(candidate: String): Boolean {
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val salt = try {
            Base64.getDecoder().decode(saltB64)
        } catch (_: Exception) {
            return false
        }
        return LockdownSecretUtil.constantTimeEquals(storedHash, LockdownSecretUtil.deriveHash(candidate, salt))
    }
}
