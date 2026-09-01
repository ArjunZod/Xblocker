package com.antigravity.shieldx.data.repository

import android.content.Context
import com.antigravity.shieldx.core.model.*
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.local.entities.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class PolicyRepository(private val database: AppDatabase) {
    val currentPolicyFlow: Flow<PolicyEntity?> = database.policyDao().getPolicyFlow()

    suspend fun getPolicy(): PolicyEntity {
        return database.policyDao().getPolicy() ?: PolicyEntity().also {
            database.policyDao().insertOrUpdate(it)
        }
    }

    suspend fun updatePolicy(policy: PolicyEntity) = withContext(Dispatchers.IO) {
        database.policyDao().insertOrUpdate(policy.copy(updatedAt = System.currentTimeMillis()))
    }
}

class DomainRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    val allRulesFlow: Flow<List<DomainRuleEntity>> = database.domainRuleDao().getAllRulesFlow()

    suspend fun getAllRules(): List<DomainRuleEntity> = withContext(Dispatchers.IO) {
        database.domainRuleDao().getAllRules()
    }

    suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        database.domainRuleDao().getCount()
    }

    suspend fun addCustomRule(domain: String, category: Category, action: PolicyDecision) = withContext(Dispatchers.IO) {
        val rule = DomainRuleEntity(
            domain = domain.trim().lowercase(),
            category = category,
            matchType = MatchType.SUBDOMAIN,
            action = action,
            isCustom = true,
            source = "USER_CUSTOM",
            addedAt = System.currentTimeMillis()
        )
        database.domainRuleDao().insert(rule)
    }

    suspend fun removeRule(rule: DomainRuleEntity) = withContext(Dispatchers.IO) {
        database.domainRuleDao().delete(rule)
    }

    suspend fun populateDefaultBlocklistIfEmpty(): Int = withContext(Dispatchers.IO) {
        val currentCount = database.domainRuleDao().getCount()
        if (currentCount > 0) return@withContext currentCount

        try {
            val inputStream = context.resources.openRawResource(
                context.resources.getIdentifier("default_blocklist", "raw", context.packageName)
            )
            val reader = InputStreamReader(inputStream)
            val json = Gson().fromJson(reader, BlocklistJson::class.java)
            
            val entities = json.domains.map { item ->
                DomainRuleEntity(
                    domain = item.domain.trim().lowercase(),
                    category = runCatching { Category.valueOf(item.category) }.getOrDefault(Category.PORNOGRAPHY),
                    matchType = runCatching { MatchType.valueOf(item.matchType) }.getOrDefault(MatchType.SUBDOMAIN),
                    action = PolicyDecision.BLOCK,
                    isCustom = false,
                    source = "SYSTEM_SEED",
                    addedAt = System.currentTimeMillis()
                )
            }
            database.domainRuleDao().insertAll(entities)
            
            // Record version
            database.policyVersionDao().insert(
                PolicyVersionEntity(
                    version = json.version,
                    signature = json.signature,
                    rulesCount = entities.size,
                    source = "SEED_ASSET",
                    appliedAt = System.currentTimeMillis(),
                    isKnownGood = true
                )
            )
            entities.size
        } catch (e: Exception) {
            0
        }
    }

    private data class BlocklistJson(
        val version: Long,
        val signature: String,
        val timestamp: Long,
        val domains: List<BlocklistDomainItem>,
        val tlds: List<String>
    )

    private data class BlocklistDomainItem(
        val domain: String,
        val category: String,
        val matchType: String
    )
}

class AppPolicyRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    val allAppRulesFlow: Flow<List<AppRuleEntity>> = database.appRuleDao().getAllAppRulesFlow()

    suspend fun getRuleForPackage(packageName: String): AppRuleEntity? = withContext(Dispatchers.IO) {
        database.appRuleDao().getRuleForPackage(packageName)
    }

    suspend fun updateAppPolicy(packageName: String, appLabel: String, category: String, policy: AppPolicy, reason: String) = withContext(Dispatchers.IO) {
        val entity = AppRuleEntity(
            packageName = packageName,
            appLabel = appLabel,
            category = category,
            policy = policy,
            reason = reason,
            isCustom = true,
            updatedAt = System.currentTimeMillis()
        )
        database.appRuleDao().insert(entity)
    }

    suspend fun populateDefaultAppPoliciesIfEmpty(): Int = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.resources.openRawResource(
                context.resources.getIdentifier("default_app_policies", "raw", context.packageName)
            )
            val reader = InputStreamReader(inputStream)
            val json = Gson().fromJson(reader, AppPolicyJson::class.java)

            val entities = json.policies.map { p ->
                AppRuleEntity(
                    packageName = p.packageName,
                    appLabel = p.appLabel,
                    category = p.category,
                    policy = runCatching { AppPolicy.valueOf(p.policy) }.getOrDefault(AppPolicy.RESTRICTED),
                    reason = p.reason,
                    isCustom = false,
                    updatedAt = System.currentTimeMillis()
                )
            }
            database.appRuleDao().insertAll(entities)
            entities.size
        } catch (e: Exception) {
            0
        }
    }

    private data class AppPolicyJson(
        val version: Long,
        val policies: List<AppPolicyItem>
    )

    private data class AppPolicyItem(
        val packageName: String,
        val appLabel: String,
        val category: String,
        val policy: String,
        val reason: String
    )
}

class AuditRepository(private val database: AppDatabase) {
    val recentBlockedEventsFlow: Flow<List<BlockedEventEntity>> = database.blockedEventDao().getRecentEventsFlow(100)
    val recentTamperEventsFlow: Flow<List<TamperEventEntity>> = database.tamperEventDao().getRecentTamperEventsFlow(50)

    fun getBlockedTodayCountFlow(): Flow<Int> {
        val startOfDay = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000L)
        return database.blockedEventDao().getBlockedCountSinceFlow(startOfDay)
    }

    suspend fun logBlockedEvent(
        target: String,
        category: Category,
        reason: BlockReason,
        appPackageName: String? = null,
        deviceMode: DeviceMode = DeviceMode.NORMAL_CONSUMER,
        details: String = ""
    ) = withContext(Dispatchers.IO) {
        database.blockedEventDao().insert(
            BlockedEventEntity(
                timestamp = System.currentTimeMillis(),
                target = target,
                category = category,
                reason = reason,
                appPackageName = appPackageName,
                deviceMode = deviceMode,
                details = details
            )
        )
    }

    suspend fun logTamperEvent(
        eventType: String,
        severity: String,
        description: String,
        resultingState: TamperState
    ) = withContext(Dispatchers.IO) {
        database.tamperEventDao().insert(
            TamperEventEntity(
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                severity = severity,
                description = description,
                resultingState = resultingState
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.blockedEventDao().clearAll()
        database.tamperEventDao().clearAll()
    }
}

class ConfigRepository(private val database: AppDatabase) {
    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        database.configurationDao().getConfig(key)
    }

    fun getFlow(key: String): Flow<String?> = database.configurationDao().getConfigFlow(key)

    suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        database.configurationDao().setConfig(
            ConfigurationEntity(
                configKey = key,
                configValue = value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
