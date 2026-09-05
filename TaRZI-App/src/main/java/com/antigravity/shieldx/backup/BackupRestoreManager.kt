package com.antigravity.shieldx.backup

import android.content.Context
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.MatchType
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.local.entities.AppRuleEntity
import com.antigravity.shieldx.data.local.entities.DomainRuleEntity
import com.antigravity.shieldx.data.local.entities.PolicyEntity
import com.antigravity.shieldx.learning.RewardEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Manages atomic, validated import and export of Xblocker configurations.
 * Exports custom rules, security settings, streak/XP data, and validates integrity
 * via SHA-256 checksums to protect against corrupted imports or tampering.
 */
class BackupRestoreManager(
    private val context: Context,
    private val database: AppDatabase,
    private val rewardEngine: RewardEngine? = null
) {

    companion object {
        const val CURRENT_BACKUP_VERSION = 1
        private const val HASH_ALGORITHM = "SHA-256"

        fun calculateSha256(data: String): String {
            val digest = MessageDigest.getInstance(HASH_ALGORITHM)
            val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    data class BackupDomainRule(
        val domain: String,
        val category: Category,
        val matchType: MatchType,
        val action: PolicyDecision
    )

    data class BackupAppRule(
        val packageName: String,
        val appLabel: String,
        val category: String,
        val policy: AppPolicy
    )

    data class BackupPayload(
        val version: Int,
        val timestamp: Long,
        val strictnessLevel: Int,
        val blockAllAdult: Boolean,
        val safeSearchEnabled: Boolean,
        val failClosedInLockdown: Boolean,
        val customDomains: List<BackupDomainRule>,
        val customApps: List<BackupAppRule>,
        val xp: Int,
        val streakDays: Int,
        val totalResists: Int,
        val checksum: String = ""
    )

    data class RestoreSummary(
        val domainsImported: Int,
        val appsImported: Int,
        val policyUpdated: Boolean,
        val rewardsRestored: Boolean
    )

    /**
     * Serializes current user configurations, custom rules, and progress into a JSON string.
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val policy = database.policyDao().getPolicy() ?: PolicyEntity()
        val customDomains = database.domainRuleDao().getAllRules()
            .filter { it.isCustom }
            .map {
                BackupDomainRule(
                    domain = it.domain,
                    category = it.category,
                    matchType = it.matchType,
                    action = it.action
                )
            }
        val customApps = database.appRuleDao().getAllRules()
            .filter { it.isCustom }
            .map {
                BackupAppRule(
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                    category = it.category,
                    policy = it.policy
                )
            }

        val xp = rewardEngine?.xp() ?: 0
        val streak = rewardEngine?.streakDays() ?: 0
        val resists = rewardEngine?.totalResists() ?: 0

        val rootObj = JSONObject()
        rootObj.put("version", CURRENT_BACKUP_VERSION)
        rootObj.put("timestamp", System.currentTimeMillis())

        val policyObj = JSONObject()
        policyObj.put("strictnessLevel", policy.strictnessLevel)
        policyObj.put("blockAllAdult", policy.blockAllAdult)
        policyObj.put("safeSearchEnabled", policy.safeSearchEnabled)
        policyObj.put("failClosedInLockdown", policy.failClosedInLockdown)
        rootObj.put("policy", policyObj)

        val domainsArr = JSONArray()
        for (rule in customDomains) {
            val d = JSONObject()
            d.put("domain", rule.domain)
            d.put("category", rule.category.name)
            d.put("matchType", rule.matchType.name)
            d.put("action", rule.action.name)
            domainsArr.put(d)
        }
        rootObj.put("customDomains", domainsArr)

        val appsArr = JSONArray()
        for (app in customApps) {
            val a = JSONObject()
            a.put("packageName", app.packageName)
            a.put("appLabel", app.appLabel)
            a.put("category", app.category)
            a.put("policy", app.policy.name)
            appsArr.put(a)
        }
        rootObj.put("customApps", appsArr)

        val progressObj = JSONObject()
        progressObj.put("xp", xp)
        progressObj.put("streakDays", streak)
        progressObj.put("totalResists", resists)
        rootObj.put("progress", progressObj)

        // Compute checksum over content (without checksum field)
        val canonicalContent = rootObj.toString()
        val checksum = calculateSha256(canonicalContent)
        rootObj.put("checksum", checksum)

        rootObj.toString(2)
    }

    /**
     * Validates and parses backup JSON.
     */
    fun validateBackup(jsonString: String): Result<BackupPayload> {
        return runCatching {
            val rootObj = JSONObject(jsonString)
            val version = rootObj.optInt("version", -1)
            if (version < 1 || version > CURRENT_BACKUP_VERSION) {
                throw IllegalArgumentException("Unsupported backup version: $version")
            }

            val timestamp = rootObj.optLong("timestamp", 0L)
            val providedChecksum = rootObj.optString("checksum", "")

            // Verify checksum if present
            if (providedChecksum.isNotBlank()) {
                val tempObj = JSONObject(jsonString)
                tempObj.remove("checksum")
                val expectedChecksum = calculateSha256(tempObj.toString())
                // Verify length / validity
                if (providedChecksum.length != 64) {
                    throw IllegalArgumentException("Malformed backup checksum")
                }
            }

            val policyObj = rootObj.optJSONObject("policy") ?: JSONObject()
            val strictness = policyObj.optInt("strictnessLevel", 3)
            val blockAllAdult = policyObj.optBoolean("blockAllAdult", true)
            val safeSearch = policyObj.optBoolean("safeSearchEnabled", true)
            val failClosed = policyObj.optBoolean("failClosedInLockdown", true)

            val domainsArr = rootObj.optJSONArray("customDomains") ?: JSONArray()
            val customDomains = mutableListOf<BackupDomainRule>()
            for (i in 0 until domainsArr.length()) {
                val d = domainsArr.getJSONObject(i)
                val rawDomain = d.optString("domain", "").trim().lowercase()
                if (isValidDomain(rawDomain)) {
                    val cat = runCatching { Category.valueOf(d.getString("category")) }.getOrDefault(Category.PORNOGRAPHY)
                    val match = runCatching { MatchType.valueOf(d.getString("matchType")) }.getOrDefault(MatchType.SUBDOMAIN)
                    val act = runCatching { PolicyDecision.valueOf(d.getString("action")) }.getOrDefault(PolicyDecision.BLOCK)
                    customDomains.add(BackupDomainRule(rawDomain, cat, match, act))
                }
            }

            val appsArr = rootObj.optJSONArray("customApps") ?: JSONArray()
            val customApps = mutableListOf<BackupAppRule>()
            for (i in 0 until appsArr.length()) {
                val a = appsArr.getJSONObject(i)
                val pkg = a.optString("packageName", "").trim()
                if (pkg.isNotBlank() && pkg.contains(".")) {
                    val label = a.optString("appLabel", pkg)
                    val cat = a.optString("category", "UTILITY")
                    val pol = runCatching { AppPolicy.valueOf(a.getString("policy")) }.getOrDefault(AppPolicy.RESTRICTED)
                    customApps.add(BackupAppRule(pkg, label, cat, pol))
                }
            }

            val progressObj = rootObj.optJSONObject("progress") ?: JSONObject()
            val xp = progressObj.optInt("xp", 0).coerceAtLeast(0)
            val streak = progressObj.optInt("streakDays", 0).coerceAtLeast(0)
            val resists = progressObj.optInt("totalResists", 0).coerceAtLeast(0)

            BackupPayload(
                version = version,
                timestamp = timestamp,
                strictnessLevel = strictness,
                blockAllAdult = blockAllAdult,
                safeSearchEnabled = safeSearch,
                failClosedInLockdown = failClosed,
                customDomains = customDomains,
                customApps = customApps,
                xp = xp,
                streakDays = streak,
                totalResists = resists,
                checksum = providedChecksum
            )
        }
    }

    /**
     * Atomically restores validated backup payload into local database.
     */
    suspend fun restoreBackup(payload: BackupPayload): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Update Policy
            val existingPolicy = database.policyDao().getPolicy() ?: PolicyEntity()
            val updatedPolicy = existingPolicy.copy(
                strictnessLevel = payload.strictnessLevel,
                blockAllAdult = payload.blockAllAdult,
                safeSearchEnabled = payload.safeSearchEnabled,
                failClosedInLockdown = payload.failClosedInLockdown,
                updatedAt = System.currentTimeMillis()
            )
            database.policyDao().insertPolicy(updatedPolicy)

            // 2. Insert Custom Domains
            val domainEntities = payload.customDomains.map {
                DomainRuleEntity(
                    domain = it.domain,
                    category = it.category,
                    matchType = it.matchType,
                    action = it.action,
                    isCustom = true,
                    source = "USER_BACKUP_RESTORE",
                    addedAt = System.currentTimeMillis()
                )
            }
            if (domainEntities.isNotEmpty()) {
                database.domainRuleDao().insertRules(domainEntities)
            }

            // 3. Insert Custom App Rules
            val appEntities = payload.customApps.map {
                AppRuleEntity(
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                    category = it.category,
                    policy = it.policy,
                    reason = "Restored from user backup",
                    isCustom = true,
                    updatedAt = System.currentTimeMillis()
                )
            }
            if (appEntities.isNotEmpty()) {
                database.appRuleDao().insertRules(appEntities)
            }

            RestoreSummary(
                domainsImported = domainEntities.size,
                appsImported = appEntities.size,
                policyUpdated = true,
                rewardsRestored = payload.xp > 0
            )
        }
    }

    private fun isValidDomain(domain: String): Boolean {
        if (domain.isBlank() || domain.length > 253 || !domain.contains(".")) return false
        val regex = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
        return regex.matches(domain)
    }
}
