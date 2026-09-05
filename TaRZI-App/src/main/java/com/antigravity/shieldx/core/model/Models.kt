package com.antigravity.shieldx.core.model

/**
 * Categorization of content and domains across protection taxonomy.
 */
enum class Category(val displayName: String, val isSecurityThreat: Boolean = false) {
    // Adult & Explicit
    PORNOGRAPHY("Pornography"),
    NUDITY("Nudity"),
    SEXUAL_SERVICES("Sexual Services"),
    ADULT_DATING("Adult Dating"),
    CAM("Live Webcam / Cam Shows"),
    EXPLICIT_STREAMING("Adult Streaming"),
    ADULT_SOCIAL("Adult Social Networks"),
    ADULT_FORUM("Adult Forums"),
    ADULT_SEARCH("Adult Search Engines"),
    SEXUAL_HEALTH_EXPLICIT("Sexual Health Explicit"),
    NSFW_MEDIA("NSFW Media Leaks"),
    OTHER_EXPLICIT("Other Explicit Content"),

    // Extended Content & Threat Categories (Master Prompt Section 9)
    GAMBLING("Gambling & Betting"),
    DRUGS("Illegal Drugs & Paraphernalia"),
    MALWARE("Malware & Ransomware", isSecurityThreat = true),
    PHISHING("Phishing & Deceptive Sites", isSecurityThreat = true),
    SCAM("Fraud & Scam Sites", isSecurityThreat = true),
    VIOLENCE("Extreme Violence & Gore"),
    DATING("General Dating"),
    SOCIAL_MEDIA("Social Media Platforms"),
    SHORT_VIDEO("Short-form Video (Reels/TikTok/Shorts)"),
    STREAMING("Video & Audio Streaming"),
    PIRACY("Piracy & Torrent Portals"),
    SUSPICIOUS_DOMAINS("Newly Registered / Suspicious Domains", isSecurityThreat = true),
    TRACKING("Telemetry & Web Trackers"),
    ADVERTISING("Aggressive Advertising Networks"),
    CRYPTOMINING("Unauthorized Cryptominers", isSecurityThreat = true),

    // Baseline
    SAFE("Safe / Allowed Content"),
    UNKNOWN("Uncategorized Content");

    val isThreatOrExplicit: Boolean
        get() = this != SAFE && this != UNKNOWN
}

/**
 * Enforcement decisions emitted by the 10-step central policy engine.
 */
enum class PolicyDecision {
    ALLOW,
    BLOCK,
    RESTRICT,
    SAFESEARCH,
    REDIRECT,
    AUDIT_ONLY,
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
    SECURE_DEFAULT_FALLBACK,
    THREAT_INTELLIGENCE_MATCH,
    CATEGORY_POLICY_BLOCKED,
    APP_POLICY_BLOCKED,
    SCHEDULE_LOCKDOWN,
    TEMPORARY_OVERRIDE_ACTIVE
}

/**
 * Operational mode of Xblocker on the Android device.
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
 * Domain matching rule type.
 */
enum class MatchType {
    EXACT,
    SUBDOMAIN,
    REGEX,
    TLD
}
