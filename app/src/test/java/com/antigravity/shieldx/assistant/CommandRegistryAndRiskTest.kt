package com.antigravity.shieldx.assistant

import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.ToolDefinition
import com.antigravity.shieldx.core.model.ConfirmationPolicy
import com.antigravity.shieldx.core.model.RiskLevel
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.core.model.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CommandRegistryAndRiskTest {

    private lateinit var registry: CommandRegistry

    @Before
    fun setUp() {
        registry = CommandRegistry()
        registry.registerTool(
            ToolDefinition(
                name = "GET_BATTERY",
                description = "Get battery state",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { ToolResult(true, "GET_BATTERY", "Battery is 85%") }
            )
        )
        registry.registerTool(
            ToolDefinition(
                name = "PURCHASE",
                description = "Complete purchase",
                riskLevel = RiskLevel.L4_CRITICAL_ACTION,
                confirmation = ConfirmationPolicy.ALWAYS_REQUIRED,
                handler = { ToolResult(true, "PURCHASE", "Purchased item") }
            )
        )
    }

    @Test
    fun testToolRetrieval() {
        val tool = registry.getTool("GET_BATTERY")
        assertNotNull(tool)
        assertEquals(RiskLevel.L0_READ_ONLY, tool?.riskLevel)
    }

    @Test
    fun testRiskLevels() {
        val purchaseTool = registry.getTool("PURCHASE")
        assertNotNull(purchaseTool)
        assertEquals(RiskLevel.L4_CRITICAL_ACTION, purchaseTool?.riskLevel)
        assertEquals(ConfirmationPolicy.ALWAYS_REQUIRED, purchaseTool?.confirmation)
    }

    @Test
    fun testToolExecution() = runBlocking {
        val tool = registry.getTool("GET_BATTERY")!!
        val result = tool.handler(ToolRequest("GET_BATTERY"))
        assertTrue(result.success)
        assertEquals("Battery is 85%", result.resultSummary)
    }
}
