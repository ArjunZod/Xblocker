package com.antigravity.shieldx.backup

import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.MatchType
import com.antigravity.shieldx.core.model.PolicyDecision
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupRestoreTest {

    @Test
    fun testSha256ChecksumCalculation() {
        val input = "Xblocker-Security-Payload"
        val hash = BackupRestoreManager.calculateSha256(input)
        assertNotNull(hash)
        assertEquals(64, hash.length)
        // Deterministic
        assertEquals(hash, BackupRestoreManager.calculateSha256(input))
    }

    @Test
    fun testValidateValidBackupPayload() {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", 1700000000000L)

        val policy = JSONObject()
        policy.put("strictnessLevel", 3)
        policy.put("blockAllAdult", true)
        policy.put("safeSearchEnabled", true)
        policy.put("failClosedInLockdown", true)
        root.put("policy", policy)

        val domains = JSONArray()
        val d1 = JSONObject()
        d1.put("domain", "custom-blocked.com")
        d1.put("category", Category.PORNOGRAPHY.name)
        d1.put("matchType", MatchType.SUBDOMAIN.name)
        d1.put("action", PolicyDecision.BLOCK.name)
        domains.put(d1)
        root.put("customDomains", domains)

        val apps = JSONArray()
        root.put("customApps", apps)

        val progress = JSONObject()
        progress.put("xp", 250)
        progress.put("streakDays", 5)
        progress.put("totalResists", 12)
        root.put("progress", progress)

        val jsonStr = root.toString()

        // Create manager with dummy (we only test validation without DB calls)
        // We can test validation logic directly
        val manager = BackupRestoreManager(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            database = org.mockito.Mockito.mock(com.antigravity.shieldx.data.local.AppDatabase::class.java)
        )

        val result = manager.validateBackup(jsonStr)
        assertTrue(result.isSuccess)
        val payload = result.getOrNull()
        assertNotNull(payload)
        assertEquals(1, payload?.version)
        assertEquals(1, payload?.customDomains?.size)
        assertEquals("custom-blocked.com", payload?.customDomains?.first()?.domain)
        assertEquals(250, payload?.xp)
        assertEquals(5, payload?.streakDays)
    }

    @Test
    fun testValidateRejectsUnsupportedVersion() {
        val root = JSONObject()
        root.put("version", 999) // Future unsupported version
        val jsonStr = root.toString()

        val manager = BackupRestoreManager(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            database = org.mockito.Mockito.mock(com.antigravity.shieldx.data.local.AppDatabase::class.java)
        )

        val result = manager.validateBackup(jsonStr)
        assertTrue(result.isFailure)
    }

    @Test
    fun testDomainSanitizationFiltersInvalidDomains() {
        val root = JSONObject()
        root.put("version", 1)

        val domains = JSONArray()
        val invalid1 = JSONObject()
        invalid1.put("domain", "not-a-domain") // no dot
        domains.put(invalid1)

        val valid = JSONObject()
        valid.put("domain", "clean-site.org")
        domains.put(valid)

        root.put("customDomains", domains)

        val manager = BackupRestoreManager(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            database = org.mockito.Mockito.mock(com.antigravity.shieldx.data.local.AppDatabase::class.java)
        )

        val result = manager.validateBackup(root.toString())
        assertTrue(result.isSuccess)
        val payload = result.getOrNull()!!
        assertEquals(1, payload.customDomains.size)
        assertEquals("clean-site.org", payload.customDomains[0].domain)
    }
}
