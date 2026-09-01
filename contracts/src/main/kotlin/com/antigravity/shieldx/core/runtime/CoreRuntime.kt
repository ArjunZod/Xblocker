package com.antigravity.shieldx.core.runtime

import com.antigravity.shieldx.core.model.Capability
import com.antigravity.shieldx.core.model.PlaybackCommand
import com.antigravity.shieldx.core.model.PlaybackState
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.model.ToolResult
import com.antigravity.shieldx.core.model.Track
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Base TaRZI Subsystem Controller contract.
 */
interface TaRZIController {
    suspend fun execute(request: ToolRequest): ToolResult
    fun capabilities(): Set<Capability>
}

/**
 * Protection Subsystem Controller contract.
 */
interface ProtectionController : TaRZIController {
    suspend fun setProfile(profile: ProtectionProfile): Result<Unit>
    suspend fun blockExplicitContent(): Result<Unit>
    suspend fun getStatus(): Map<String, Any>
}

/**
 * Native Music Subsystem Controller contract (TaRZI Music Engine).
 */
interface MusicController : TaRZIController {
    val playbackStateFlow: StateFlow<PlaybackState>
    suspend fun play(command: PlaybackCommand): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun next(): Result<Unit>
    suspend fun previous(): Result<Unit>
    suspend fun searchAndPlay(query: String): Result<Unit>
    suspend fun seekTo(positionMs: Long): Result<Unit>

    // Queue editing. These mutate the queue and re-publish playback state, so
    // the UI never has to keep its own copy of the list.
    suspend fun playQueueItem(index: Int): Result<Unit>
    suspend fun removeQueueItem(index: Int): Result<Unit>
    suspend fun moveQueueItem(from: Int, to: Int): Result<Unit>
    suspend fun addToQueue(track: Track, playNext: Boolean = false): Result<Unit>
    suspend fun clearUpcoming(): Result<Unit>
    suspend fun stop(): Result<Unit>
    // Audio priority ducking for voice assistant turns
    suspend fun duck(isDucked: Boolean): Result<Unit>
}

/**
 * Typed EventBus for decoupled cross-subsystem messaging.
 */
sealed class TaRZIEvent {
    data class ProtectionStateChanged(val isEnabled: Boolean, val profileName: String) : TaRZIEvent()
    data class ContentBlocked(val target: String, val category: String, val reason: String) : TaRZIEvent()
    data class PlaybackStateChanged(val state: PlaybackState) : TaRZIEvent()
    data class TrackChanged(val trackTitle: String, val artist: String) : TaRZIEvent()
    data class AssistantCommandExecuted(val toolName: String, val success: Boolean) : TaRZIEvent()
    data class NetworkProfileChanged(val ssid: String, val profileName: String) : TaRZIEvent()
    data class TamperAlert(val severity: String, val description: String) : TaRZIEvent()
}

class EventBus {
    private val _events = MutableSharedFlow<TaRZIEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TaRZIEvent> = _events.asSharedFlow()

    suspend fun publish(event: TaRZIEvent) {
        _events.emit(event)
    }

    fun tryPublish(event: TaRZIEvent): Boolean {
        return _events.tryEmit(event)
    }
}

/**
 * Application-scoped service registry for accessing registered subsystem controllers.
 */
object ServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(serviceClass: Class<T>): T {
        return services[serviceClass] as? T
            ?: throw IllegalStateException("Service ${serviceClass.name} is not registered in ServiceRegistry")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(serviceClass: Class<T>): T? {
        return services[serviceClass] as? T
    }
}
