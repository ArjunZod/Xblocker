package com.antigravity.shieldx.core.model

/**
 * Capabilities registered across TaRZI subsystems.
 */
enum class Capability {
    MEDIA_PLAYBACK,
    MEDIA_SEARCH,
    PROTECTION_MANAGEMENT,
    DEVICE_CONTROL,
    TELEPHONY_CALL,
    TELEPHONY_MESSAGE,
    PERSISTENT_MEMORY,
    ROUTINE_AUTOMATION,
    NETWORK_PROFILING,
    COMMERCE_DISCOVERY
}

/**
 * Assistant Action Risk Levels.
 */
enum class RiskLevel {
    L0_READ_ONLY,        // Read-only queries (battery, current track, memory read)
    L1_LOW_IMPACT,       // Play/pause, non-destructive UI navigation
    L2_MODERATE_IMPACT,  // Initiating call, drafting message, creating reminder
    L3_HIGH_SECURITY,    // Modifying protection profile, suspending app, changing admin PIN
    L4_CRITICAL_ACTION   // Incurring financial cost, deleting account/data, factory reset
}

/**
 * Confirmation policy required before tool execution.
 */
enum class ConfirmationPolicy {
    NONE,
    CONFIGURABLE,
    REQUIRED,
    ALWAYS_REQUIRED
}

/**
 * Request passed to a deterministic ToolHandler.
 */
data class ToolRequest(
    val toolName: String,
    val parameters: Map<String, Any> = emptyMap(),
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val userConfirmed: Boolean = false
)

/**
 * Result returned from a deterministic ToolHandler.
 */
data class ToolResult(
    val success: Boolean,
    val toolName: String,
    val resultSummary: String,
    val data: Map<String, Any> = emptyMap(),
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val riskLevel: RiskLevel = RiskLevel.L0_READ_ONLY
)

/**
 * Protection Profile setting.
 */
enum class ProtectionProfile {
    OFF,
    BALANCED,
    MAXIMUM,
    CUSTOM
}

enum class MusicSource {
    YOUTUBE_MUSIC,
    SPOTIFY,
    DEEZER,
    LAVALINK,
    CUSTOM_URL
}

/**
 * Normalized Media Track data model across all catalog sources.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String = "",
    val streamUrl: String = "",
    val isExplicit: Boolean = false,
    val source: MusicSource = MusicSource.YOUTUBE_MUSIC,
    val sourceId: String = id,
    val canonicalUrl: String = "",
    val isPlayable: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Playback command sent to Music Subsystem.
 */
sealed class PlaybackCommand {
    data class PlayTrack(val track: Track) : PlaybackCommand()
    data class SearchAndPlay(val query: String) : PlaybackCommand()
    data class PlayPlaylist(val playlistName: String, val tracks: List<Track>) : PlaybackCommand()
    object Pause : PlaybackCommand()
    object Resume : PlaybackCommand()
    object Next : PlaybackCommand()
    object Previous : PlaybackCommand()
    data class SeekTo(val positionMs: Long) : PlaybackCommand()
    data class SetShuffle(val enabled: Boolean) : PlaybackCommand()
    data class SetRepeat(val mode: RepeatMode) : PlaybackCommand()
}

enum class RepeatMode {
    OFF, ONE, ALL
}

/**
 * State emitted by the Music Subsystem.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val error: String? = null
)
