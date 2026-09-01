package com.antigravity.shieldx.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antigravity.shieldx.core.model.*
import com.antigravity.shieldx.data.local.dao.*
import com.antigravity.shieldx.data.local.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromCategory(value: Category): String = value.name

    @TypeConverter
    fun toCategory(value: String): Category = runCatching { Category.valueOf(value) }.getOrDefault(Category.UNKNOWN)

    @TypeConverter
    fun fromPolicyDecision(value: PolicyDecision): String = value.name

    @TypeConverter
    fun toPolicyDecision(value: String): PolicyDecision = runCatching { PolicyDecision.valueOf(value) }.getOrDefault(PolicyDecision.ALLOW)

    @TypeConverter
    fun fromBlockReason(value: BlockReason): String = value.name

    @TypeConverter
    fun toBlockReason(value: String): BlockReason = runCatching { BlockReason.valueOf(value) }.getOrDefault(BlockReason.KNOWN_ADULT_DOMAIN)

    @TypeConverter
    fun fromDeviceMode(value: DeviceMode): String = value.name

    @TypeConverter
    fun toDeviceMode(value: String): DeviceMode = runCatching { DeviceMode.valueOf(value) }.getOrDefault(DeviceMode.NORMAL_CONSUMER)

    @TypeConverter
    fun fromTamperState(value: TamperState): String = value.name

    @TypeConverter
    fun toTamperState(value: String): TamperState = runCatching { TamperState.valueOf(value) }.getOrDefault(TamperState.NORMAL)

    @TypeConverter
    fun fromAppPolicy(value: AppPolicy): String = value.name

    @TypeConverter
    fun toAppPolicy(value: String): AppPolicy = runCatching { AppPolicy.valueOf(value) }.getOrDefault(AppPolicy.RESTRICTED)

    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name

    @TypeConverter
    fun toMatchType(value: String): MatchType = runCatching { MatchType.valueOf(value) }.getOrDefault(MatchType.SUBDOMAIN)
}

@Database(
    entities = [
        PolicyEntity::class,
        DomainRuleEntity::class,
        AppRuleEntity::class,
        ClassificationCacheEntity::class,
        BlockedEventEntity::class,
        TamperEventEntity::class,
        DeviceStateEntity::class,
        ModelDecisionEntity::class,
        PolicyVersionEntity::class,
        ConfigurationEntity::class,
        AuditEventEntity::class,
        UserPreferenceEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun policyDao(): PolicyDao
    abstract fun domainRuleDao(): DomainRuleDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun classificationCacheDao(): ClassificationCacheDao
    abstract fun blockedEventDao(): BlockedEventDao
    abstract fun tamperEventDao(): TamperEventDao
    abstract fun deviceStateDao(): DeviceStateDao
    abstract fun modelDecisionDao(): ModelDecisionDao
    abstract fun policyVersionDao(): PolicyVersionDao
    abstract fun configurationDao(): ConfigurationDao
    abstract fun auditEventDao(): AuditEventDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {

        /**
         * Music and the assistant became separate apps, each owning its own
         * database. Their tables are dropped here rather than left orphaned —
         * and dropping them explicitly is what keeps this an ordinary
         * migration, so protection policies, the blocklist, audit history and
         * saved settings all survive the split.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS play_history")
                db.execSQL("DROP TABLE IF EXISTS memory_items")
                db.execSQL("DROP TABLE IF EXISTS automations")
                db.execSQL("DROP TABLE IF EXISTS network_profiles")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tarzi_unified.db"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default baseline policy
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            database.policyDao().insertOrUpdate(PolicyEntity())
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
