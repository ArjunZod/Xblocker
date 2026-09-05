package com.antigravity.shieldx.core.model

/**
 * Capabilities registered across Xblocker / ShieldX protection subsystems.
 */
enum class Capability {
    PROTECTION_MANAGEMENT,
    DEVICE_CONTROL,
    THREAT_INTELLIGENCE,
    AUDIT_LOGGING,
    APP_RESTRICTION
}

/**
 * Action Risk Levels.
 */
enum class RiskLevel {
    L0_READ_ONLY,        // Read-only queries (status, uptime, metrics)
    L1_LOW_IMPACT,       // Non-destructive UI navigation
    L2_MODERATE_IMPACT,  // Toggling category rule
    L3_HIGH_SECURITY,    // Modifying protection profile, suspending app, changing admin PIN
    L4_CRITICAL_ACTION   // Incurring factory reset, unregistering Device Owner
}

/**
 * Confirmation policy required before tool or action execution.
 */
enum class ConfirmationPolicy {
    NONE,
    CONFIGURABLE,
    REQUIRED,
    ALWAYS_REQUIRED
}

/**
 * Request passed to a deterministic ToolHandler or action executor.
 */
data class ToolRequest(
    val toolName: String,
    val parameters: Map<String, Any> = emptyMap(),
    val requestId: String = java.util.UUID.randomUUID().toString(),
    val userConfirmed: Boolean = false
)

/**
 * Result returned from a deterministic ToolHandler or action executor.
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
