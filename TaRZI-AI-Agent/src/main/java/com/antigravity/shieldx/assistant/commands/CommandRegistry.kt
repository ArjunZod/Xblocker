package com.antigravity.shieldx.assistant.commands

import com.antigravity.shieldx.core.model.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Definition of a versioned, auditable tool contract in TaRZI.
 */
data class ToolDefinition(
    val name: String,
    val version: Int = 1,
    val description: String,
    val requiredPermissions: Set<String> = emptySet(),
    val riskLevel: RiskLevel = RiskLevel.L0_READ_ONLY,
    val confirmation: ConfirmationPolicy = ConfirmationPolicy.NONE,
    val timeoutMs: Long = 5000L,
    val handler: suspend (ToolRequest) -> ToolResult
)

/**
 * Central registry of all tool capabilities across TaRZI subsystems.
 */
class CommandRegistry {

    private val tools = ConcurrentHashMap<String, ToolDefinition>()

    fun registerTool(tool: ToolDefinition) {
        tools[tool.name.uppercase()] = tool
    }

    fun getTool(name: String): ToolDefinition? {
        return tools[name.uppercase()]
    }

    fun getAllTools(): List<ToolDefinition> {
        return tools.values.toList()
    }
}
