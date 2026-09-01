package com.antigravity.shieldx.music

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.antigravity.shieldx.core.model.*
import com.antigravity.shieldx.core.runtime.ContentPolicyGate
import com.antigravity.shieldx.core.runtime.MusicController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative TaRZI Music Engine coordinating:
 * - Multi-source search (YouTube Music, Spotify, Deezer)
 * - Lavalink v4 & NewPipe stream resolution
 * - Media3 foreground playback & MediaSession
 * - Queue manager & Library repository
 * - Synced lyrics & Policy enforcement
 * - Single source of truth for UI, Notifications, and Voice Assistant
 */
class MusicControllerImpl(
    private val context: Context,
    private val searchEngine: TaRziMusicSearchEngine,
    private val resolver: MusicIdentifierResolver,
    private val lyricsService: LyricsService,
    private val policyGate: ContentPolicyGate,
    private val playHistoryRepository: PlayHistoryRepository? = null,
    private val libraryRepository: MusicLibraryRepository? = null
) : MusicController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queueManager = MusicQueueManager()
    private val _playbackStateFlow = MutableStateFlow(PlaybackState())
    override val playbackStateFlow: StateFlow<PlaybackState> = _playbackStateFlow.asStateFlow()

    private var localPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var listenerAttachedTo: Player? = null

    companion object {
        private const val TAG = "TaRZIMusicEngine"
    }

    init {
        try {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}

        startPositionUpdater()
    }

    private fun getActivePlayer(): Player {
        return MusicPlaybackService.instance?.getPlayer() ?: localPlayer ?: run {
            ExoPlayer.Builder(context)
                .setHandleAudioBecomingNoisy(true)
                .build().also {
                    localPlayer = it
                    setupPlayerListener(it)
                }
        }
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        scope.launch { next() }
                    }
                    Player.STATE_BUFFERING -> {
                        _playbackStateFlow.value = _playbackStateFlow.value.copy(isBuffering = true)
                    }
                    Player.STATE_READY -> {
                        _playbackStateFlow.value = _playbackStateFlow.value.copy(
                            isBuffering = false,
                            isPlaying = player.isPlaying,
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                    else -> {}
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackStateFlow.value = _playbackStateFlow.value.copy(
                    isPlaying = isPlaying,
                    isBuffering = false
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Playback error: ${error.errorCodeName} - ${error.message}")
                _playbackStateFlow.value = _playbackStateFlow.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = "Playback failed: ${error.errorCodeName}"
                )
            }
        })
    }

    override fun capabilities(): Set<Capability> {
        return setOf(Capability.MEDIA_PLAYBACK, Capability.MEDIA_SEARCH)
    }

    override suspend fun execute(request: ToolRequest): ToolResult {
        return when (request.toolName.uppercase()) {
            "PLAY", "PLAY_TRACK", "SEARCH_AND_PLAY" -> {
                val query = (request.parameters["query"] ?: request.parameters["song"] ?: request.parameters["title"]) as? String
                if (!query.isNullOrBlank()) {
                    val res = searchAndPlay(query)
                    if (res.isSuccess) {
                        ToolResult(true, request.toolName, "Playing \"$query\"")
                    } else {
                        ToolResult(false, request.toolName, "Could not play \"$query\": ${res.exceptionOrNull()?.message}")
                    }
                } else {
                    resume()
                    ToolResult(true, request.toolName, "Resumed music playback")
                }
            }
            "PAUSE" -> {
                pause()
                ToolResult(true, request.toolName, "Paused music")
            }
            "RESUME" -> {
                resume()
                ToolResult(true, request.toolName, "Resumed music")
            }
            "NEXT", "SKIP" -> {
                next()
                ToolResult(true, request.toolName, "Skipped to next track")
            }
            "PREVIOUS" -> {
                previous()
                ToolResult(true, request.toolName, "Playing previous track")
            }
            "SHUFFLE" -> {
                val enabled = queueManager.toggleShuffle()
                _playbackStateFlow.value = _playbackStateFlow.value.copy(
                    isShuffleEnabled = enabled,
                    queue = queueManager.getQueue()
                )
                ToolResult(true, request.toolName, "Shuffle is now ${if (enabled) "on" else "off"}")
            }
            "REPEAT" -> {
                val mode = queueManager.toggleRepeat()
                _playbackStateFlow.value = _playbackStateFlow.value.copy(repeatMode = mode)
                ToolResult(true, request.toolName, "Repeat mode set to ${mode.name.lowercase()}")
            }
            "LIKE", "SAVE_TRACK" -> {
                val curr = _playbackStateFlow.value.currentTrack
                if (curr != null && libraryRepository != null) {
                    libraryRepository.toggleLike(curr)
                    ToolResult(true, request.toolName, "Saved \"${curr.title}\" to your Liked Songs")
                } else {
                    ToolResult(false, request.toolName, "No track is currently playing to save")
                }
            }
            "PLAY_LIKED" -> {
                val liked = libraryRepository?.likedTracksFlow?.let {
                    // Fetch top liked songs
                    val tracks = libraryRepository.createPlaylist("temp").tracks
                    tracks
                } ?: emptyList()
                if (liked.isNotEmpty()) {
                    play(PlaybackCommand.PlayPlaylist("Liked Songs", liked))
                    ToolResult(true, request.toolName, "Playing your Liked Songs")
                } else {
                    ToolResult(false, request.toolName, "Your Liked Songs playlist is empty")
                }
            }
            "WHAT_IS_PLAYING", "CURRENT_TRACK" -> {
                val curr = _playbackStateFlow.value.currentTrack
                if (curr != null) {
                    ToolResult(true, request.toolName, "Currently playing \"${curr.title}\" by ${curr.artist}")
                } else {
                    ToolResult(true, request.toolName, "No music is currently playing")
                }
            }
            else -> ToolResult(false, request.toolName, "Unknown music command: ${request.toolName}")
        }
    }

    override suspend fun play(command: PlaybackCommand): Result<Unit> = withContext(Dispatchers.Main) {
        when (command) {
            is PlaybackCommand.PlayTrack -> playTrackInternal(command.track)
            is PlaybackCommand.SearchAndPlay -> searchAndPlay(command.query)
            is PlaybackCommand.PlayPlaylist -> {
                queueManager.setQueue(command.tracks)
                val first = queueManager.getCurrentTrack()
                if (first != null) playTrackInternal(first)
            }
            PlaybackCommand.Pause -> pause()
            PlaybackCommand.Resume -> resume()
            PlaybackCommand.Next -> next()
            PlaybackCommand.Previous -> previous()
            is PlaybackCommand.SeekTo -> seekTo(command.positionMs)
            is PlaybackCommand.SetShuffle -> {
                val enabled = queueManager.toggleShuffle()
                _playbackStateFlow.value = _playbackStateFlow.value.copy(
                    isShuffleEnabled = enabled,
                    queue = queueManager.getQueue(),
                    queueIndex = queueManager.getCurrentIndex()
                )
            }
            is PlaybackCommand.SetRepeat -> {
                val mode = queueManager.toggleRepeat()
                _playbackStateFlow.value = _playbackStateFlow.value.copy(repeatMode = mode)
            }
        }
        Result.success(Unit)
    }

    override suspend fun searchAndPlay(query: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "SearchAndPlay requested for '$query'")
        val tracks = searchEngine.search(query)
        if (tracks.isEmpty()) {
            return@withContext Result.failure(Exception("No tracks found for \"$query\""))
        }

        // Apply explicit content protection policy
        val filteredTracks = tracks.filter { track ->
            if (track.isExplicit) {
                !policyGate.isExplicitContentBlocked()
            } else true
        }

        val trackToPlay = filteredTracks.firstOrNull()
            ?: return@withContext Result.failure(Exception("All matching tracks were filtered by policy"))

        withContext(Dispatchers.Main) {
            queueManager.setQueue(filteredTracks)
            playTrackInternal(trackToPlay)
        }
        Result.success(Unit)
    }

    override suspend fun pause(): Result<Unit> = withContext(Dispatchers.Main) {
        val player = getActivePlayer()
        player.pause()
        _playbackStateFlow.value = _playbackStateFlow.value.copy(isPlaying = false)
        Result.success(Unit)
    }

    override suspend fun resume(): Result<Unit> = withContext(Dispatchers.Main) {
        val player = getActivePlayer()
        player.play()
        _playbackStateFlow.value = _playbackStateFlow.value.copy(isPlaying = true)
        Result.success(Unit)
    }

    override suspend fun next(): Result<Unit> = withContext(Dispatchers.Main) {
        val nextTrack = queueManager.next()
        if (nextTrack != null) {
            playTrackInternal(nextTrack)
        } else {
            val curr = _playbackStateFlow.value.currentTrack
            if (curr != null) {
                scope.launch(Dispatchers.IO) {
                    val recommended = searchEngine.search("${curr.artist} radio").take(5)
                    if (recommended.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            queueManager.setQueue(recommended)
                            playTrackInternal(recommended.first())
                        }
                    }
                }
            }
        }
        Result.success(Unit)
    }

    override suspend fun previous(): Result<Unit> = withContext(Dispatchers.Main) {
        val prevTrack = queueManager.previous()
        if (prevTrack != null) {
            playTrackInternal(prevTrack)
        }
        Result.success(Unit)
    }

    override suspend fun seekTo(positionMs: Long): Result<Unit> = withContext(Dispatchers.Main) {
        val player = getActivePlayer()
        player.seekTo(positionMs)
        _playbackStateFlow.value = _playbackStateFlow.value.copy(currentPositionMs = positionMs)
        Result.success(Unit)
    }

    override suspend fun playQueueItem(index: Int): Result<Unit> = withContext(Dispatchers.Main) {
        val track = queueManager.jumpTo(index)
            ?: return@withContext Result.failure(IllegalArgumentException("No track at index $index"))
        playTrackInternal(track)
        Result.success(Unit)
    }

    override suspend fun removeQueueItem(index: Int): Result<Unit> = withContext(Dispatchers.Main) {
        val wasCurrent = index == queueManager.getCurrentIndex()
        if (!queueManager.removeAt(index)) {
            return@withContext Result.failure(IllegalArgumentException("No track at index $index"))
        }

        publishQueue()

        if (wasCurrent) {
            val nowCurrent = queueManager.getCurrentTrack()
            if (nowCurrent != null) {
                playTrackInternal(nowCurrent)
            } else {
                stop()
            }
        }
        Result.success(Unit)
    }

    override suspend fun moveQueueItem(from: Int, to: Int): Result<Unit> = withContext(Dispatchers.Main) {
        if (!queueManager.move(from, to)) {
            return@withContext Result.failure(IllegalArgumentException("Cannot move from $from to $to"))
        }
        publishQueue()
        Result.success(Unit)
    }

    override suspend fun addToQueue(track: Track, playNext: Boolean): Result<Unit> = withContext(Dispatchers.Main) {
        if (playNext) queueManager.playNext(track) else queueManager.addToQueue(track)
        publishQueue()
        Result.success(Unit)
    }

    override suspend fun clearUpcoming(): Result<Unit> = withContext(Dispatchers.Main) {
        queueManager.clearUpcoming()
        publishQueue()
        Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            getActivePlayer().stop()
        } catch (_: Exception) {}
        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            isPlaying = false,
            currentTrack = null,
            currentPositionMs = 0L
        )
        Result.success(Unit)
    }

    override suspend fun duck(isDucked: Boolean): Result<Unit> = withContext(Dispatchers.Main) {
        try {
            val player = getActivePlayer()
            player.volume = if (isDucked) 0.15f else 1.0f
        } catch (_: Exception) {}
        Result.success(Unit)
    }

    private fun publishQueue() {
        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            queue = queueManager.getQueue(),
            queueIndex = queueManager.getCurrentIndex()
        )
    }

    private suspend fun playTrackInternal(track: Track) {
        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            isBuffering = true,
            currentTrack = track,
            queue = queueManager.getQueue(),
            queueIndex = queueManager.getCurrentIndex(),
            error = null
        )

        // Resolve playable stream via Lavalink / NewPipe / InnerTube
        val resolution = withContext(Dispatchers.IO) {
            resolver.resolveStream(track)
        }

        if (resolution == null || resolution.second.isBlank()) {
            _playbackStateFlow.value = _playbackStateFlow.value.copy(
                isBuffering = false,
                isPlaying = false,
                error = "Could not play \"${track.title}\". Stream is unavailable."
            )
            return
        }

        val (resolvedTrack, streamUrl) = resolution

        // Record history
        playHistoryRepository?.let { repo ->
            scope.launch(Dispatchers.IO) { repo.record(resolvedTrack) }
        }

        MusicPlaybackService.instance?.let { service ->
            service.playTrack(resolvedTrack, streamUrl)
            service.getPlayer()?.let { player ->
                if (listenerAttachedTo !== player) {
                    setupPlayerListener(player)
                    listenerAttachedTo = player
                }
            }
        } ?: run {
            val player = getActivePlayer()
            val mediaItem = MediaItem.Builder()
                .setMediaId(resolvedTrack.id)
                .setUri(streamUrl)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }

        _playbackStateFlow.value = _playbackStateFlow.value.copy(
            currentTrack = resolvedTrack,
            durationMs = resolvedTrack.durationSeconds * 1000L,
            error = null
        )
    }

    private fun startPositionUpdater() {
        progressJob = scope.launch {
            while (isActive) {
                delay(500)
                val player = getActivePlayer()
                if (player.isPlaying) {
                    _playbackStateFlow.value = _playbackStateFlow.value.copy(
                        currentPositionMs = player.currentPosition,
                        durationMs = player.duration.coerceAtLeast(0L),
                        isPlaying = true
                    )
                }
            }
        }
    }
}
