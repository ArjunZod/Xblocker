package com.antigravity.shieldx.core.model

/**
 * Categorization of content and domains according to adult protection taxonomy.
 */
enum class Category {
    PORNOGRAPHY,
    NUDITY,
    SEXUAL_SERVICES,
    ADULT_DATING,
    CAM,
    EXPLICIT_STREAMING,
    ADULT_SOCIAL,
    ADULT_FORUM,
    ADULT_SEARCH,
    SEXUAL_HEALTH_EXPLICIT,
    NSFW_MEDIA,
    OTHER_EXPLICIT,
    SAFE,
    UNKNOWN;

    val isExplicit: Boolean
        get() = this != SAFE && this != UNKNOWN
}

/**
 * Enforcement decisions emitted by policy engine.
 */
enum class PolicyDecision {
    ALLOW,
    BLOCK,
    RESTRICT,
    UNCERTAIN
}

/**
 * Specific reason code for an enforcement decision or block event.
 */
enum class BlockReason {
    KNOWN_ADULT_DOMAIN,
    ADULT_TLD_MATCH,
    WILDCARD_DOMAIN_MATCH,
    HOMOGLYPH_OBFUSCATION,
    URL_PATH_EXPLICIT,
    URL_QUERY_EXPLICIT,
    FILE_EXTENSION_EXPLICIT,
    PROHIBITED_PACKAGE,
    LOCAL_TEXT_EXPLICIT,
    LOCAL_IMAGE_EXPLICIT,
    LOCAL_VIDEO_EXPLICIT,
    AI_CONFIRMED_EXPLICIT,
    SAFESEARCH_ENFORCEMENT,
    TAMPER_LOCKDOWN,
    ADMIN_RESTRICTION,
    SECURE_DEFAULT_FALLBACK
}

/**
 * Operational mode of TaRZI on the Android device.
 */
enum class DeviceMode {
    NORMAL_CONSUMER,
    MANAGED_DEVICE_OWNER
}

/**
 * Deterministic tamper state machine levels.
 */
enum class TamperState {
    NORMAL,
    SUSPICIOUS,
    LOCKDOWN,
    RECOVERY_REQUIRED
}

/**
 * Application-level policy enforcement state.
 */
enum class AppPolicy {
    UNKNOWN,
    ALLOWED,
    RESTRICTED,
    BLOCKED,
    SYSTEM_EXEMPT
}

/**
 * Domain matching algorithm type.
 */
enum class MatchType {
    EXACT,
    SUBDOMAIN,
    SUFFIX,
    WILDCARD
}

// NOTE: Capability, RiskLevel, ConfirmationPolicy, ToolRequest, ToolResult,
// ProtectionProfile, MusicSource, Track, PlaybackCommand, RepeatMode, and
// PlaybackState moved to :contracts (com.antigravity.shieldx.core.model) —
// they're part of the MusicController/ProtectionController contract surface
// that :music and :ai-agent also need, so they can't live app-only.
