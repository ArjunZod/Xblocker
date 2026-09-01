package com.antigravity.shieldx.music

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.antigravity.shieldx.core.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * What has actually been played, so the Music screen can show it.
 *
 * Keyed by track id with REPLACE on conflict: replaying a song should move it
 * back to the top of "recently played" rather than adding a duplicate row.
 */
@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
) {
    fun toTrack(): Track = Track(
        id = trackId,
        title = title,
        artist = artist,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
        streamUrl = ""
    )
}

@Dao
interface PlayHistoryDao {

    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 30): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun mostPlayedFlow(limit: Int = 20): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE trackId = :trackId LIMIT 1")
    suspend fun find(trackId: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}

/**
 * Records plays. Called from the playback path rather than the UI, so history
 * reflects what the player actually started - not what a screen thought it
 * asked for.
 */
class PlayHistoryRepository(private val dao: PlayHistoryDao) {

    val recentFlow: Flow<List<PlayHistoryEntity>> = dao.recentFlow()
    val mostPlayedFlow: Flow<List<PlayHistoryEntity>> = dao.mostPlayedFlow()

    suspend fun record(track: Track) {
        if (track.id.isBlank()) return
        val existing = dao.find(track.id)
        dao.upsert(
            PlayHistoryEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                thumbnailUrl = track.thumbnailUrl,
                durationSeconds = track.durationSeconds,
                playCount = (existing?.playCount ?: 0) + 1,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear() = dao.clear()
}
