package com.antigravity.shieldx.assistant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persistence the assistant owns: what it remembers, the routines it runs, and
 * the per-network profiles it applies.
 *
 * The physical Room database still lives in :app (AppDatabase registers these
 * entities by name), so table definitions here are unchanged from when they
 * lived in app's Entities.kt/Daos.kt — same tables, same columns, no migration.
 */

@Entity(
    tableName = "memory_items",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["category"])
    ]
)
data class MemoryItemEntity(
    @PrimaryKey val key: String,
    val value: String,
    val source: String = "USER_EXPLICIT", // USER_EXPLICIT, INFERRED, SYSTEM
    val consent: Boolean = true,
    val category: String = "PREFERENCE", // PREFERENCE, FACT, ROUTINE, SENSITIVE
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automations",
    indices = [
        Index(value = ["triggerType"]),
        Index(value = ["isEnabled"])
    ]
)
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val triggerType: String, // WIFI_CONNECTED, TIME_SCHEDULE, BLUETOOTH, BATTERY, BOOT
    val triggerPayload: String, // e.g. "HOME_WIFI", "08:00", "HEADSET_CONNECTED"
    val conditionsJson: String = "[]",
    val actionsJson: String, // JSON array of serialized ToolRequests
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_profiles")
data class NetworkProfileEntity(
    @PrimaryKey val ssid: String,
    val profileName: String,
    val protectionLevel: String = "MAXIMUM",
    val isHomeNetwork: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

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
interface NetworkProfileDao {
    @Query("SELECT * FROM network_profiles WHERE ssid = :ssid LIMIT 1")
    suspend fun getProfileForSsid(ssid: String): NetworkProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: NetworkProfileEntity)
}
