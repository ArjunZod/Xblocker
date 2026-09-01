package com.antigravity.shieldx.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Locks in the cryptographic contract behind the self-lockout feature: the
 * secret must be genuinely random and long, the hash must be salted and
 * one-way, and comparison must not be foolable by a near-miss.
 */
class LockdownSecretUtilTest {

    @Test
    fun `generated secret has the promised length`() {
        val secret = LockdownSecretUtil.generateSecret()
        assertEquals(LockdownSecretUtil.SECRET_LENGTH, secret.length)
    }

    @Test
    fun `consecutive secrets are not repeats`() {
        val a = LockdownSecretUtil.generateSecret()
        val b = LockdownSecretUtil.generateSecret()
        assertNotEquals("two generated secrets collided - randomness is broken", a, b)
    }

    @Test
    fun `secret draws from a wide alphabet, not just a narrow default`() {
        // A weak generator (e.g. digits only) would make a 100-char secret
        // brute-forceable despite its length. Confirm real character diversity.
        val secret = LockdownSecretUtil.generateSecret()
        val distinctChars = secret.toSet().size
        assertTrue("expected varied characters, got only $distinctChars distinct", distinctChars > 20)
    }

    @Test
    fun `same secret and salt always derive the same hash`() {
        val salt = LockdownSecretUtil.generateSalt()
        val h1 = LockdownSecretUtil.deriveHash("test-secret-value", salt)
        val h2 = LockdownSecretUtil.deriveHash("test-secret-value", salt)
        assertEquals(h1, h2)
    }

    @Test
    fun `different secrets never collide on hash`() {
        val salt = LockdownSecretUtil.generateSalt()
        val h1 = LockdownSecretUtil.deriveHash("secret-one", salt)
        val h2 = LockdownSecretUtil.deriveHash("secret-two", salt)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `same secret under different salts produces different hashes`() {
        // This is what stops a stolen hash being matched against a rainbow table.
        val secret = "same-secret-every-time"
        val h1 = LockdownSecretUtil.deriveHash(secret, LockdownSecretUtil.generateSalt())
        val h2 = LockdownSecretUtil.deriveHash(secret, LockdownSecretUtil.generateSalt())
        assertNotEquals(h1, h2)
    }

    @Test
    fun `constant-time comparison agrees with equality for equal strings`() {
        assertTrue(LockdownSecretUtil.constantTimeEquals("abc123XYZ", "abc123XYZ"))
    }

    @Test
    fun `constant-time comparison rejects a near miss`() {
        val real = LockdownSecretUtil.generateSecret()
        val almostRight = real.dropLast(1) + if (real.last() == 'A') 'B' else 'A'
        assertFalse(LockdownSecretUtil.constantTimeEquals(real, almostRight))
    }

    @Test
    fun `constant-time comparison rejects different lengths without throwing`() {
        assertFalse(LockdownSecretUtil.constantTimeEquals("short", "much-longer-string"))
    }

    @Test
    fun `full round trip - correct secret verifies, wrong secret does not`() {
        val salt = LockdownSecretUtil.generateSalt(SecureRandom())
        val realSecret = LockdownSecretUtil.generateSecret(SecureRandom())
        val storedHash = LockdownSecretUtil.deriveHash(realSecret, salt)

        val correctAttempt = LockdownSecretUtil.deriveHash(realSecret, salt)
        val wrongAttempt = LockdownSecretUtil.deriveHash("totally-wrong-guess", salt)

        assertTrue(LockdownSecretUtil.constantTimeEquals(storedHash, correctAttempt))
        assertFalse(LockdownSecretUtil.constantTimeEquals(storedHash, wrongAttempt))
    }
}
