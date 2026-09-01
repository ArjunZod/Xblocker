package com.antigravity.shieldx.device

import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure secret-generation and hashing logic for [LockdownController], kept free
 * of Android framework types (Context, android.util.Base64, Keystore-backed
 * storage) so the cryptographic contract - random enough, hashed correctly,
 * constant-time compared - can be unit tested on the plain JVM without
 * Robolectric.
 */
object LockdownSecretUtil {

    const val SECRET_LENGTH = 100
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256

    private val SECRET_ALPHABET =
        ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz" +
            "0123456789" +
            "!@#\$%^&*()-_=+[]{}").toCharArray()

    /** A fresh, cryptographically random secret of [SECRET_LENGTH] characters. */
    fun generateSecret(random: SecureRandom = SecureRandom()): String =
        CharArray(SECRET_LENGTH) { SECRET_ALPHABET[random.nextInt(SECRET_ALPHABET.size)] }
            .concatToString()

    fun generateSalt(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(32).also { random.nextBytes(it) }

    /** PBKDF2-HMAC-SHA256 hash of [secret] under [salt], base64-encoded. */
    fun deriveHash(secret: String, salt: ByteArray): String {
        val spec: KeySpec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(derived)
    }

    /** Avoids leaking match length through early-exit string comparison. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
