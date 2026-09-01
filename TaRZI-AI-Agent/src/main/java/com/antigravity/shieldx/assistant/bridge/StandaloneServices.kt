package com.antigravity.shieldx.assistant.bridge

import android.content.Context
import com.antigravity.shieldx.assistant.data.AgentAuditEntity
import com.antigravity.shieldx.assistant.data.AgentAuditDao
import com.antigravity.shieldx.core.model.Capability
import com.antigravity.shieldx.core.model.ProtectionProfile
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.model.ToolResult
import com.antigravity.shieldx.core.runtime.AuditSink
import com.antigravity.shieldx.core.runtime.BlockedEventSummary
import com.antigravity.shieldx.core.runtime.ConfigStore
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.core.runtime.ProtectionInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The :app-provided services, reimplemented for the standalone assistant.
 *
 * In the all-in-one build these are backed by the protection app's database and
 * policy engine. On its own the assistant keeps its own settings and audit
 * trail, and reports honestly that it cannot see or change protection.
 */

/** API keys and settings, kept in the assistant's own preferences. */
class PrefsConfigStore(context: Context) : ConfigStore {

    private val prefs = context.getSharedPreferences("tarzi_agent_config", Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0)

    override suspend fun get(key: String): String? =
        prefs.getString(key, null)?.takeIf { it.isNotBlank() }

    override fun getFlow(key: String): Flow<String?> =
        revision.map { prefs.getString(key, null)?.takeIf { v -> v.isNotBlank() } }

    override suspend fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        revision.value += 1
    }
}

/** Tool-execution audit trail, in the assistant's own database. */
class RoomAuditSink(private val dao: AgentAuditDao) : AuditSink {
    override suspend fun record(
        category: String,
        action: String,
        actor: String,
        details: String,
        status: String
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            AgentAuditEntity(
                category = category,
                action = action,
                actor = actor,
                details = details,
                status = status
            )
        )
    }
}

/**
 * Protection lives in a different app. Rather than pretending to know, these
 * report that the assistant can't see it — the assistant surfaces that to the
 * user instead of inventing a number.
 */
object UnavailableProtectionInsights : ProtectionInsights {
    override suspend fun blockedCountSince(sinceEpochMs: Long): Int = 0
    override suspend fun recentBlockedEvents(limit: Int): List<BlockedEventSummary> = emptyList()
}

object UnavailableProtectionController : ProtectionController {

    private const val MESSAGE =
        "Protection is managed by the TaRZI app, which isn't reachable from here. Open it to change protection settings."

    override suspend fun setProfile(profile: ProtectionProfile): Result<Unit> =
        Result.failure(UnsupportedOperationException(MESSAGE))

    override suspend fun blockExplicitContent(): Result<Unit> =
        Result.failure(UnsupportedOperationException(MESSAGE))

    override suspend fun getStatus(): Map<String, Any> = mapOf("available" to false)

    override suspend fun execute(request: ToolRequest): ToolResult = ToolResult(
        success = false,
        toolName = request.toolName,
        resultSummary = MESSAGE
    )

    override fun capabilities(): Set<Capability> = emptySet()
}
