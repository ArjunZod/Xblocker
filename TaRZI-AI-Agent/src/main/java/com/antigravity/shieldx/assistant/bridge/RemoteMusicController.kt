package com.antigravity.shieldx.assistant.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.antigravity.shieldx.core.model.Capability
import com.antigravity.shieldx.core.model.PlaybackCommand
import com.antigravity.shieldx.core.model.PlaybackState
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.model.ToolResult
import com.antigravity.shieldx.core.model.Track
import com.antigravity.shieldx.core.runtime.MusicController
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the separately-installed TaRZI Music app across the process boundary.
 *
 * Transport control and now-playing state go over Media3's MediaSession, which
 * is the platform's own cross-app media protocol — no custom AIDL needed.
 * Starting a *new* song by name can't be expressed as a transport command, so
 * that goes as an explicit intent the Music app advertises.
 *
 * Every call degrades quietly when the Music app isn't installed: the assistant
 * reports it rather than crashing.
 */
class RemoteMusicController(private val context: Context) : MusicController {

    private val _playbackStateFlow = MutableStateFlow(PlaybackState())
    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private var controller: MediaController? = null

    companion object {
        private const val TAG = "RemoteMusic"
        private const val MUSIC_PKG = "com.antigravity.tarzi.music"
        private const val MUSIC_PKG_DEBUG = "com.antigravity.tarzi.music.debug"
        private const val SERVICE = "com.antigravity.shieldx.music.MusicPlaybackService"
        private const val ACTION_PLAY_QUERY = "com.antigravity.tarzi.music.action.PLAY_QUERY"
    }

    /** The installed Music app, preferring a release build over a debug one. */
    private fun musicPackage(): String? {
        val pm = context.packageManager
        return listOf(MUSIC_PKG, MUSIC_PKG_DEBUG).firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }
    }

    fun connect() {
        val pkg = musicPackage() ?: run {
            Log.i(TAG, "TaRZI Music is not installed; music commands will report that")
            return
        }
        runCatching {
            val token = SessionToken(context, ComponentName(pkg, SERVICE))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                runCatching {
                    controller = future.get().also { attach(it) }
                    Log.i(TAG, "connected to $pkg")
                }.onFailure { Log.w(TAG, "controller connect failed: ${it.message}") }
            }, MoreExecutors.directExecutor())
        }.onFailure { Log.w(TAG, "session token failed: ${it.message}") }
    }

    private fun attach(c: MediaController) {
        publish(c)
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish(c)
            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = publish(c)
            override fun onPlaybackStateChanged(playbackState: Int) = publish(c)
        })
    }

    private fun publish(c: MediaController) {
        val md = c.mediaMetadata
        val title = md.title?.toString().orEmpty()
        _playbackStateFlow.value = PlaybackState(
            isPlaying = c.isPlaying,
            currentTrack = if (title.isBlank()) null else Track(
                id = c.currentMediaItem?.mediaId.orEmpty(),
                title = title,
                artist = md.artist?.toString().orEmpty()
            ),
            currentPositionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            isBuffering = c.playbackState == Player.STATE_BUFFERING
        )
    }

    private fun onMain(block: (MediaController) -> Unit): Result<Unit> {
        val c = controller ?: return Result.failure(
            IllegalStateException("TaRZI Music isn't running. Open it once, then try again.")
        )
        Handler(Looper.getMainLooper()).post { runCatching { block(c); publish(c) } }
        return Result.success(Unit)
    }

    override suspend fun searchAndPlay(query: String): Result<Unit> {
        val pkg = musicPackage()
            ?: return Result.failure(IllegalStateException("TaRZI Music isn't installed."))
        return runCatching {
            context.startActivity(
                Intent(ACTION_PLAY_QUERY).apply {
                    setPackage(pkg)
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            // The controller may not exist yet on a cold Music app.
            if (controller == null) connect()
        }
    }

    override suspend fun play(command: PlaybackCommand): Result<Unit> = when (command) {
        is PlaybackCommand.SearchAndPlay -> searchAndPlay(command.query)
        is PlaybackCommand.PlayTrack -> searchAndPlay("${command.track.title} ${command.track.artist}")
        PlaybackCommand.Pause -> pause()
        PlaybackCommand.Resume -> resume()
        PlaybackCommand.Next -> next()
        PlaybackCommand.Previous -> previous()
        is PlaybackCommand.SeekTo -> seekTo(command.positionMs)
        else -> Result.failure(UnsupportedOperationException("Not supported across apps."))
    }

    override suspend fun pause(): Result<Unit> = onMain { it.pause() }
    override suspend fun resume(): Result<Unit> = onMain { it.play() }
    override suspend fun next(): Result<Unit> = onMain { it.seekToNextMediaItem() }
    override suspend fun previous(): Result<Unit> = onMain { it.seekToPreviousMediaItem() }
    override suspend fun seekTo(positionMs: Long): Result<Unit> = onMain { it.seekTo(positionMs) }
    override suspend fun stop(): Result<Unit> = onMain { it.stop() }
    override suspend fun duck(isDucked: Boolean): Result<Unit> =
        onMain { it.volume = if (isDucked) 0.2f else 1.0f }

    // Queue editing needs the engine itself; not exposed across the boundary.
    override suspend fun playQueueItem(index: Int): Result<Unit> = unsupported()
    override suspend fun removeQueueItem(index: Int): Result<Unit> = unsupported()
    override suspend fun moveQueueItem(from: Int, to: Int): Result<Unit> = unsupported()
    override suspend fun addToQueue(track: Track, playNext: Boolean): Result<Unit> = unsupported()
    override suspend fun clearUpcoming(): Result<Unit> = unsupported()

    private fun unsupported(): Result<Unit> =
        Result.failure(UnsupportedOperationException("Open TaRZI Music to edit the queue."))

    override suspend fun execute(request: ToolRequest): ToolResult = ToolResult(
        success = false,
        toolName = request.toolName,
        resultSummary = "Handled by the music app."
    )

    override fun capabilities(): Set<Capability> =
        setOf(Capability.MEDIA_PLAYBACK, Capability.MEDIA_SEARCH)
}
