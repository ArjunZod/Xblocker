package com.antigravity.shieldx.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The assistant's own store, used when TaRZI-AI-Agent runs standalone: what it
 * remembers, its routines, per-network profiles, and its tool audit trail.
 *
 * The all-in-one build keeps these tables inside the app's tarzi_unified.db
 * instead; the entity definitions are shared, so the schema is identical.
 */
@Entity(tableName = "agent_audit_events")
data class AgentAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val action: String,
    val actor: String = "USER",
    val details: String,
    val status: String = "SUCCESS"
)

@Dao
interface AgentAuditDao {
    @Insert
    suspend fun insert(event: AgentAuditEntity)

    @Query("SELECT * FROM agent_audit_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AgentAuditEntity>
}

@Database(
    entities = [
        MemoryItemEntity::class,
        AutomationEntity::class,
        NetworkProfileEntity::class,
        AgentAuditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AgentDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun automationDao(): AutomationDao
    abstract fun networkProfileDao(): NetworkProfileDao
    abstract fun auditDao(): AgentAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AgentDatabase? = null

        fun getInstance(context: Context): AgentDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "tarzi_agent.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
