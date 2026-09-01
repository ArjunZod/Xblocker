package com.antigravity.shieldx.music

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * TaRZI-Music's own database, used when the music app runs standalone.
 *
 * The schema is exactly the play_history table Music has always defined, so a
 * device that previously kept this data inside the all-in-one app's
 * tarzi_unified.db is not migrated or touched — the standalone app simply
 * starts its own store.
 */
@Database(
    entities = [PlayHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "tarzi_music.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
