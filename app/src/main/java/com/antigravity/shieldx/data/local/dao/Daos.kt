package com.antigravity.shieldx.data.local.dao

import androidx.room.*
import com.antigravity.shieldx.core.model.AppPolicy
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import com.antigravity.shieldx.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policies WHERE id = :id LIMIT 1")
    fun getPolicyFlow(id: String = "default_policy"): Flow<PolicyEntity?>

    @Query("SELECT * FROM policies WHERE id = :id LIMIT 1")
    suspend fun getPolicy(id: String = "default_policy"): PolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(policy: PolicyEntity)
}

@Dao
interface DomainRuleDao {
    @Query("SELECT * FROM domain_rules")
    fun getAllRulesFlow(): Flow<List<DomainRuleEntity>>

    @Query("SELECT * FROM domain_rules")
    suspend fun getAllRules(): List<DomainRuleEntity>

    @Query("SELECT * FROM domain_rules WHERE domain = :domain LIMIT 1")
    suspend fun getRuleForDomain(domain: String): DomainRuleEntity?

    @Query("SELECT * FROM domain_rules WHERE action = :action")
    suspend fun getRulesByAction(action: PolicyDecision): List<DomainRuleEntity>

    @Query("SELECT COUNT(*) FROM domain_rules")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<DomainRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: DomainRuleEntity)

    @Delete
    suspend fun delete(rule: DomainRuleEntity)

    @Query("DELETE FROM domain_rules WHERE isCustom = 0")
    suspend fun clearSystemRules()

    @Query("DELETE FROM domain_rules")
    suspend fun clearAll()
}

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appLabel ASC")
    fun getAllAppRulesFlow(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getRuleForPackage(packageName: String): AppRuleEntity?

    @Query("SELECT * FROM app_rules WHERE policy = :policy")
    suspend fun getRulesByPolicy(policy: AppPolicy): List<AppRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<AppRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AppRuleEntity)

    @Delete
    suspend fun delete(rule: AppRuleEntity)
}

@Dao
interface ClassificationCacheDao {
    @Query("SELECT * FROM classification_cache WHERE contentHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): ClassificationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClassificationCacheEntity)

    @Query("DELETE FROM classification_cache WHERE (cachedAt + ttlMillis) < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM classification_cache")
    suspend fun clearAll()
}

@Dao
interface BlockedEventDao {
    @Query("SELECT * FROM blocked_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEventsFlow(limit: Int = 100): Flow<List<BlockedEventEntity>>

    @Query("SELECT * FROM blocked_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 100): List<BlockedEventEntity>

    @Query("SELECT COUNT(*) FROM blocked_events WHERE timestamp >= :sinceTimestamp")
    fun getBlockedCountSinceFlow(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_events WHERE timestamp >= :sinceTimestamp")
    suspend fun getBlockedCountSince(sinceTimestamp: Long): Int

    @Insert
    suspend fun insert(event: BlockedEventEntity)

    @Query("DELETE FROM blocked_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldEvents(beforeTimestamp: Long)

    @Query("DELETE FROM blocked_events")
    suspend fun clearAll()
}

@Dao
interface TamperEventDao {
    @Query("SELECT * FROM tamper_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTamperEventsFlow(limit: Int = 50): Flow<List<TamperEventEntity>>

    @Query("SELECT * FROM tamper_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEvent(): TamperEventEntity?

    @Insert
    suspend fun insert(event: TamperEventEntity)

    @Query("DELETE FROM tamper_events")
    suspend fun clearAll()
}

@Dao
interface DeviceStateDao {
    @Query("SELECT value FROM device_state WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM device_state WHERE `key` = :key LIMIT 1")
    fun getValueFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(state: DeviceStateEntity)
}

@Dao
interface ModelDecisionDao {
    @Query("SELECT * FROM model_decisions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentDecisions(limit: Int = 100): List<ModelDecisionEntity>

    @Insert
    suspend fun insert(decision: ModelDecisionEntity)
}

@Dao
interface PolicyVersionDao {
    @Query("SELECT * FROM policy_versions ORDER BY version DESC LIMIT 1")
    suspend fun getLatestVersion(): PolicyVersionEntity?

    @Query("SELECT * FROM policy_versions WHERE isKnownGood = 1 ORDER BY version DESC LIMIT 1")
    suspend fun getLatestKnownGood(): PolicyVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: PolicyVersionEntity)
}

@Dao
interface ConfigurationDao {
    @Query("SELECT configValue FROM configurations WHERE configKey = :key LIMIT 1")
    suspend fun getConfig(key: String): String?

    @Query("SELECT configValue FROM configurations WHERE configKey = :key LIMIT 1")
    fun getConfigFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfig(config: ConfigurationEntity)
}

// === TaRZI Super-App DAOs ===

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): MemoryItemEntity?

    @Query("SELECT * FROM memory_items WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: MemoryItemEntity)

    @Query("DELETE FROM memory_items WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    @Query("DELETE FROM memory_items")
    suspend fun clearAll()
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations WHERE isEnabled = 1")
    fun getActiveAutomationsFlow(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun getAllAutomationsFlow(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE triggerType = :triggerType AND isEnabled = 1")
    suspend fun getAutomationsByTrigger(triggerType: String): List<AutomationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(automation: AutomationEntity)

    @Delete
    suspend fun delete(automation: AutomationEntity)
}

@Dao
interface AuditEventDao {
    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAuditEventsFlow(limit: Int = 100): Flow<List<AuditEventEntity>>

    @Insert
    suspend fun insert(event: AuditEventEntity)

    @Query("DELETE FROM audit_events")
    suspend fun clearAll()
}

@Dao
interface UserPreferenceDao {
    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM user_preferences WHERE `key` = :key LIMIT 1")
    fun getFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(pref: UserPreferenceEntity)
}

@Dao
interface NetworkProfileDao {
    @Query("SELECT * FROM network_profiles WHERE ssid = :ssid LIMIT 1")
    suspend fun getProfileForSsid(ssid: String): NetworkProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: NetworkProfileEntity)
}
