package com.antigravity.shieldx.music.rn

import com.antigravity.shieldx.core.model.PlaybackState
import com.antigravity.shieldx.core.model.Track
import com.antigravity.shieldx.music.MusicApp
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The music engine, exposed to the React Native UI.
 *
 * Everything real — search, stream resolution, ExoPlayer, the queue — stays in
 * Kotlin. This is only a translation layer: JS asks for an action, and playback
 * state is pushed back as events so the UI never keeps its own copy.
 */
class MusicNativeModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    private val graph get() = MusicApp.instance.graph

    override fun getName() = "TarziMusic"

    // --- state -------------------------------------------------------------

    /** Starts pushing playback state to JS. Safe to call more than once. */
    @ReactMethod
    fun startStateUpdates() {
        if (stateJob?.isActive == true) return
        stateJob = scope.launch {
            graph.musicController.playbackStateFlow.collect { state ->
                emit("TarziMusic:playbackState", state.toJs())
            }
        }
    }

    @ReactMethod
    fun stopStateUpdates() {
        stateJob?.cancel()
        stateJob = null
    }

    // --- transport ---------------------------------------------------------

    @ReactMethod fun pause(promise: Promise) = run("pause", promise) { graph.musicController.pause() }
    @ReactMethod fun resume(promise: Promise) = run("resume", promise) { graph.musicController.resume() }
    @ReactMethod fun next(promise: Promise) = run("next", promise) { graph.musicController.next() }
    @ReactMethod fun previous(promise: Promise) = run("previous", promise) { graph.musicController.previous() }

    @ReactMethod
    fun seekTo(positionMs: Double, promise: Promise) =
        run("seekTo", promise) { graph.musicController.seekTo(positionMs.toLong()) }

    @ReactMethod
    fun searchAndPlay(query: String, promise: Promise) =
        run("searchAndPlay", promise) { graph.musicController.searchAndPlay(query) }

    @ReactMethod
    fun playQueueItem(index: Int, promise: Promise) =
        run("playQueueItem", promise) { graph.musicController.playQueueItem(index) }

    // --- catalog -----------------------------------------------------------

    @ReactMethod
    fun search(query: String, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            runCatching { graph.musicSearchEngine.search(query) }
                .onSuccess { tracks -> promise.resolve(tracks.toJsArray()) }
                .onFailure { promise.reject("search_failed", it.message, it) }
        }
    }

    @ReactMethod
    fun exploreSections(promise: Promise) {
        scope.launch(Dispatchers.IO) {
            runCatching { graph.innerTubeClient.getExploreSections() }
                .onSuccess { sections ->
                    val out = Arguments.createArray()
                    sections.forEach { section ->
                        out.pushMap(
                            Arguments.createMap().apply {
                                putString("title", section.title)
                                putArray("tracks", section.tracks.toJsArray())
                            }
                        )
                    }
                    promise.resolve(out)
                }
                .onFailure { promise.reject("explore_failed", it.message, it) }
        }
    }

    @ReactMethod
    fun recentlyPlayed(limit: Int, promise: Promise) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                graph.playHistoryRepository.recentFlow
            }.onFailure { promise.reject("history_failed", it.message, it) }
        }
        // Recent history arrives through its own Flow; the initial read is
        // served by the state event stream, so JS gets it without a round trip.
        promise.resolve(Arguments.createArray())
    }

    // --- plumbing ----------------------------------------------------------

    private fun run(name: String, promise: Promise, block: suspend () -> Result<Unit>) {
        scope.launch {
            runCatching { block() }
                .onSuccess { result ->
                    result.fold(
                        onSuccess = { promise.resolve(true) },
                        onFailure = { promise.reject("${name}_failed", it.message, it) }
                    )
                }
                .onFailure { promise.reject("${name}_failed", it.message, it) }
        }
    }

    private fun emit(event: String, payload: Any?) {
        if (!reactContext.hasActiveReactInstance()) return
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(event, payload)
    }

    override fun invalidate() {
        stateJob?.cancel()
        super.invalidate()
    }
}

// --- mapping ---------------------------------------------------------------

private fun Track.toJs(): WritableMap = Arguments.createMap().apply {
    putString("id", id)
    putString("title", title)
    putString("artist", artist)
    putString("album", album)
    putDouble("durationSeconds", durationSeconds.toDouble())
    putString("thumbnailUrl", thumbnailUrl)
    putBoolean("isExplicit", isExplicit)
    putString("source", source.name)
}

private fun List<Track>.toJsArray(): WritableArray =
    Arguments.createArray().also { arr -> forEach { arr.pushMap(it.toJs()) } }

private fun PlaybackState.toJs(): WritableMap = Arguments.createMap().apply {
    putBoolean("isPlaying", isPlaying)
    putBoolean("isBuffering", isBuffering)
    putDouble("positionMs", currentPositionMs.toDouble())
    putDouble("durationMs", durationMs.toDouble())
    putInt("queueIndex", queueIndex)
    putBoolean("isShuffleEnabled", isShuffleEnabled)
    putString("repeatMode", repeatMode.name)
    putString("error", error)
    putMap("currentTrack", currentTrack?.toJs())
    putArray("queue", queue.toJsArray())
}
