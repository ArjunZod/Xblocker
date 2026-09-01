package com.antigravity.shieldx.assistant.executor

import android.content.Context
import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.core.model.*
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.TaRZIEvent
import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.local.entities.AuditEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages explicit user confirmations for sensitive (L3/L4) actions.
 */
class ConfirmationManager {

    private val pendingConfirmations = ConcurrentHashMap<String, ToolRequest>()

    fun stageForConfirmation(request: ToolRequest): String {
        val id = request.requestId
        pendingConfirmations[id] = request
        return id
    }

    fun getPending(requestId: String): ToolRequest? {
        return pendingConfirmations[requestId]
    }

    fun confirm(requestId: String): ToolRequest? {
        val req = pendingConfirmations.remove(requestId)
        return req?.copy(userConfirmed = true)
    }

    fun cancel(requestId: String) {
        pendingConfirmations.remove(requestId)
    }
}

/**
 * Deterministic executor enforcing schema, permissions, risk gates, timeouts, and auditing.
 */
class DeterministicExecutor(
    private val context: Context,
    private val commandRegistry: CommandRegistry,
    private val confirmationManager: ConfirmationManager,
    private val database: AppDatabase,
    private val eventBus: EventBus
) {

    suspend fun execute(request: ToolRequest): ToolResult = withContext(Dispatchers.Default) {
        val toolDef = commandRegistry.getTool(request.toolName)
            ?: return@withContext ToolResult(
                success = false,
                toolName = request.toolName,
                resultSummary = "Tool '${request.toolName}' is not registered."
            )

        // 1. Permission Gate
        for (perm in toolDef.requiredPermissions) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                logAudit(request.toolName, "DENIED", "Missing Android permission: $perm")
                return@withContext ToolResult(
                    success = false,
                    toolName = request.toolName,
                    resultSummary = "Action requires permission: $perm",
                    riskLevel = toolDef.riskLevel
                )
            }
        }

        // 2. Risk & Confirmation Gate
        val requiresConfirmation = toolDef.confirmation == ConfirmationPolicy.REQUIRED ||
                toolDef.confirmation == ConfirmationPolicy.ALWAYS_REQUIRED ||
                toolDef.riskLevel == RiskLevel.L3_HIGH_SECURITY ||
                toolDef.riskLevel == RiskLevel.L4_CRITICAL_ACTION

        if (requiresConfirmation && !request.userConfirmed) {
            confirmationManager.stageForConfirmation(request)
            val prompt = generateConfirmationPrompt(request, toolDef)
            logAudit(request.toolName, "CONFIRMED_REQUIRED", "Staged for user approval: $prompt")
            return@withContext ToolResult(
                success = false,
                toolName = request.toolName,
                resultSummary = "Confirmation required before execution.",
                requiresConfirmation = true,
                confirmationPrompt = prompt,
                riskLevel = toolDef.riskLevel
            )
        }

        // 3. Execution with Timeout
        val result = try {
            withTimeoutOrNull(toolDef.timeoutMs) {
                toolDef.handler(request)
            } ?: ToolResult(
                success = false,
                toolName = request.toolName,
                resultSummary = "Execution timed out after ${toolDef.timeoutMs}ms"
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                toolName = request.toolName,
                resultSummary = "Execution error: ${e.message}"
            )
        }

        // 4. Audit & Event Dispatch
        logAudit(
            request.toolName,
            if (result.success) "SUCCESS" else "FAILED",
            result.resultSummary
        )

        eventBus.tryPublish(
            TaRZIEvent.AssistantCommandExecuted(
                toolName = request.toolName,
                success = result.success
            )
        )

        result
    }

    private suspend fun logAudit(action: String, status: String, details: String) {
        withContext(Dispatchers.IO) {
            database.auditEventDao().insert(
                AuditEventEntity(
                    timestamp = System.currentTimeMillis(),
                    category = "ASSISTANT",
                    action = action,
                    actor = "USER_ASSISTANT",
                    details = details,
                    status = status
                )
            )
        }
    }

    private fun generateConfirmationPrompt(request: ToolRequest, toolDef: com.antigravity.shieldx.assistant.commands.ToolDefinition): String {
        return when (request.toolName.uppercase()) {
            "SET_PROTECTION_PROFILE", "ENABLE_PROTECTION" -> "Are you sure you want to modify system protection settings?"
            "PURCHASE" -> "Confirm purchase of ${request.parameters["item"] ?: "item"} for ${request.parameters["price"] ?: "specified amount"}?"
            "MEMORY_DELETE" -> "Confirm deletion of memory for '${request.parameters["key"]}'?"
            "LOCK_DEVICE" -> "Lock this device immediately?"
            else -> "Execute sensitive action: ${toolDef.description}?"
        }
    }
}
