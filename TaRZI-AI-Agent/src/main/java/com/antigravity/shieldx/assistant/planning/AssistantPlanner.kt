package com.antigravity.shieldx.assistant.planning

import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.ToolDefinition
import com.antigravity.shieldx.assistant.intent.IntentEngine
import com.antigravity.shieldx.core.model.RiskLevel
import com.antigravity.shieldx.core.model.ToolRequest

data class PlanStep(
    val stepIndex: Int,
    val request: ToolRequest,
    val toolDefinition: ToolDefinition?,
    val riskLevel: RiskLevel,
    val requiresConfirmation: Boolean,
    val description: String
)

data class ExecutionPlan(
    val originalQuery: String,
    val steps: List<PlanStep>,
    val maxRiskLevel: RiskLevel,
    val requiresUserApprovalBeforeStart: Boolean
)

/**
 * Breaks complex, multi-step user commands into structured, auditable execution plans.
 */
class AssistantPlanner(
    private val intentEngine: IntentEngine,
    private val commandRegistry: CommandRegistry
) {

    fun createPlan(query: String): ExecutionPlan {
        val requests = intentEngine.parse(query)
        val steps = mutableListOf<PlanStep>()
        var highestRisk = RiskLevel.L0_READ_ONLY

        requests.forEachIndexed { index, req ->
            val toolDef = commandRegistry.getTool(req.toolName)
            val risk = toolDef?.riskLevel ?: RiskLevel.L0_READ_ONLY
            val reqConfirm = toolDef?.confirmation == com.antigravity.shieldx.core.model.ConfirmationPolicy.REQUIRED ||
                    toolDef?.confirmation == com.antigravity.shieldx.core.model.ConfirmationPolicy.ALWAYS_REQUIRED ||
                    risk == RiskLevel.L3_HIGH_SECURITY ||
                    risk == RiskLevel.L4_CRITICAL_ACTION

            if (risk > highestRisk) {
                highestRisk = risk
            }

            steps.add(
                PlanStep(
                    stepIndex = index + 1,
                    request = req,
                    toolDefinition = toolDef,
                    riskLevel = risk,
                    requiresConfirmation = reqConfirm,
                    description = toolDef?.description ?: "Execute ${req.toolName}"
                )
            )
        }

        return ExecutionPlan(
            originalQuery = query,
            steps = steps,
            maxRiskLevel = highestRisk,
            requiresUserApprovalBeforeStart = steps.any { it.requiresConfirmation }
        )
    }
}
