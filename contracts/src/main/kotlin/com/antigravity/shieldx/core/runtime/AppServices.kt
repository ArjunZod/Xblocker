package com.antigravity.shieldx.core.runtime

import kotlinx.coroutines.flow.Flow

/**
 * Services :app owns and provides downward to :ai-agent, so that
 * module never needs a compile-time reference back to :app.
 */

/** Key/value app configuration — API keys, toggles. */
interface ConfigStore {
    suspend fun get(key: String): String?
    fun getFlow(key: String): Flow<String?>
    suspend fun set(key: String, value: String)
}

/** Append-only audit trail. */
interface AuditSink {
    suspend fun record(
        category: String,
        action: String,
        actor: String,
        details: String,
        status: String
    )
}

data class BlockedEventSummary(
    val target: String,
    val category: String,
    val reason: String,
    val timestamp: Long
)

/** Read-only view of protection activity, for assistant tools that report it. */
interface ProtectionInsights {
    suspend fun blockedCountSince(sinceEpochMs: Long): Int
    suspend fun recentBlockedEvents(limit: Int): List<BlockedEventSummary>
}
