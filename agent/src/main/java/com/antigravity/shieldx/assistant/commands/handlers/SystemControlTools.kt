package com.antigravity.shieldx.assistant.commands.handlers

import android.content.Context
import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.ToolDefinition
import com.antigravity.shieldx.assistant.system.TarziAccessibilityService
import com.antigravity.shieldx.core.model.ConfirmationPolicy
import com.antigravity.shieldx.core.model.RiskLevel
import com.antigravity.shieldx.core.model.ToolResult
import kotlinx.coroutines.delay

/**
 * Tools that let Tarzi act on whatever is on screen, in any app.
 *
 * Everything here routes through [TarziAccessibilityService], so these are the
 * tools that make "open the first video" work while the user is in YouTube
 * rather than inside Tarzi.
 */
class SystemControlTools(
    private val context: Context,
    private val commandRegistry: CommandRegistry
) {

    /** Shared guard so every tool fails with the same actionable message. */
    private inline fun withScreen(
        toolName: String,
        block: (TarziAccessibilityService) -> ToolResult
    ): ToolResult {
        val service = TarziAccessibilityService.get()
            ?: return ToolResult(
                success = false,
                toolName = toolName,
                resultSummary = "I need screen control access first. Enable Tarzi under " +
                    "Settings, Accessibility, Installed apps."
            )
        return block(service)
    }

    fun registerAll() {
        registerNavigationTools()
        registerInteractionTools()
        registerScreenReadingTools()
    }

    // ==========================================================
    // Global navigation
    // ==========================================================

    private fun registerNavigationTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "GO_BACK",
                description = "Press the system back button",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    withScreen(req.toolName) {
                        if (it.pressBack()) ToolResult(true, req.toolName, "Went back")
                        else ToolResult(false, req.toolName, "Could not go back")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "GO_HOME",
                description = "Return to the device home screen",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    withScreen(req.toolName) {
                        if (it.pressHome()) ToolResult(true, req.toolName, "Back at the home screen")
                        else ToolResult(false, req.toolName, "Could not reach the home screen")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SHOW_RECENTS",
                description = "Open the recent apps switcher",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    withScreen(req.toolName) {
                        if (it.pressRecents()) ToolResult(true, req.toolName, "Showing recent apps")
                        else ToolResult(false, req.toolName, "Could not open recent apps")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "OPEN_NOTIFICATIONS",
                description = "Pull down the notification shade",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    withScreen(req.toolName) {
                        if (it.openNotifications()) ToolResult(true, req.toolName, "Notifications open")
                        else ToolResult(false, req.toolName, "Could not open notifications")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "SCROLL_SCREEN",
                description = "Scroll the current screen up or down",
                riskLevel = RiskLevel.L1_LOW_IMPACT,
                handler = { req ->
                    val direction = (req.parameters["direction"] as? String ?: "down").lowercase()
                    val amount = if (direction.contains("up")) -1 else 1
                    withScreen(req.toolName) {
                        if (it.scroll(amount)) ToolResult(true, req.toolName, "Scrolled $direction")
                        else ToolResult(false, req.toolName, "Could not scroll")
                    }
                }
            )
        )
    }

    // ==========================================================
    // Touch interaction
    // ==========================================================

    private fun registerInteractionTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "TAP_TEXT",
                description = "Tap the on-screen element matching the given text or label",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                handler = { req ->
                    val target = req.parameters["text"] as? String ?: ""
                    withScreen(req.toolName) {
                        if (it.tapByText(target)) {
                            ToolResult(true, req.toolName, "Tapped '$target'")
                        } else {
                            ToolResult(false, req.toolName, "I could not find '$target' on screen")
                        }
                    }
                }
            )
        )

        // The "open that first video" tool.
        commandRegistry.registerTool(
            ToolDefinition(
                name = "OPEN_ITEM",
                description = "Open the Nth item, result, or video on the current screen",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                timeoutMs = 8000L,
                handler = { req ->
                    // Callers speak in ordinals; the service indexes from zero.
                    val ordinal = ((req.parameters["ordinal"] as? Number)?.toInt() ?: 1)
                        .coerceAtLeast(1) - 1

                    withScreen(req.toolName) { service ->
                        val snapshot = service.captureScreen()
                        val opened = service.tapContentItem(ordinal)
                        if (opened) {
                            val label = snapshot.elements
                                .filter { it.isClickable && it.label.isNotBlank() }
                                .getOrNull(ordinal)?.label
                            val what = if (label.isNullOrBlank()) "item ${ordinal + 1}" else label
                            ToolResult(true, req.toolName, "Opening $what")
                        } else {
                            ToolResult(false, req.toolName, "I could not find item ${ordinal + 1} on screen")
                        }
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "TAP_INDEX",
                description = "Tap the element at a specific index from the last screen capture",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                handler = { req ->
                    val index = (req.parameters["index"] as? Number)?.toInt() ?: 0
                    withScreen(req.toolName) {
                        if (it.tapElementAt(index)) ToolResult(true, req.toolName, "Tapped element $index")
                        else ToolResult(false, req.toolName, "No element at index $index")
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "TYPE_TEXT",
                description = "Type text into the focused input field",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                confirmation = ConfirmationPolicy.CONFIGURABLE,
                handler = { req ->
                    val text = req.parameters["text"] as? String ?: ""
                    withScreen(req.toolName) {
                        if (it.typeText(text)) ToolResult(true, req.toolName, "Typed the text")
                        else ToolResult(false, req.toolName, "No text field is focused")
                    }
                }
            )
        )

        // Search inside whatever app is open: focus the field, type, submit.
        commandRegistry.registerTool(
            ToolDefinition(
                name = "SEARCH_IN_APP",
                description = "Search for something inside the app currently on screen",
                riskLevel = RiskLevel.L2_MODERATE_IMPACT,
                timeoutMs = 12000L,
                handler = { req ->
                    val query = req.parameters["query"] as? String ?: ""
                    if (query.isBlank()) {
                        return@ToolDefinition ToolResult(false, req.toolName, "Nothing to search for")
                    }

                    val service = TarziAccessibilityService.get()
                        ?: return@ToolDefinition ToolResult(
                            false,
                            req.toolName,
                            "I need screen control access first."
                        )

                    // Tap the search affordance, wait for the field, then type.
                    val opened = service.tapByText("search")
                    if (opened) delay(900)
                    val typed = service.typeText(query)
                    if (!typed) {
                        return@ToolDefinition ToolResult(
                            false,
                            req.toolName,
                            "I could not find a search field on this screen"
                        )
                    }
                    delay(600)
                    ToolResult(true, req.toolName, "Searching for $query")
                }
            )
        )
    }

    // ==========================================================
    // Screen reading
    // ==========================================================

    private fun registerScreenReadingTools() {
        commandRegistry.registerTool(
            ToolDefinition(
                name = "READ_SCREEN",
                description = "Read out what is currently visible on screen",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    withScreen(req.toolName) {
                        val text = it.readScreenText()
                        if (text.isBlank()) {
                            ToolResult(false, req.toolName, "I cannot read anything on this screen")
                        } else {
                            ToolResult(
                                success = true,
                                toolName = req.toolName,
                                resultSummary = text.take(600),
                                data = mapOf("fullText" to text)
                            )
                        }
                    }
                }
            )
        )

        commandRegistry.registerTool(
            ToolDefinition(
                name = "GET_FOREGROUND_APP",
                description = "Report which app is currently in the foreground",
                riskLevel = RiskLevel.L0_READ_ONLY,
                handler = { req ->
                    val pkg = TarziAccessibilityService.foregroundAppFlow.value
                    if (pkg.isBlank()) {
                        ToolResult(false, req.toolName, "I cannot tell which app is open")
                    } else {
                        val label = friendlyAppName(pkg)
                        ToolResult(
                            success = true,
                            toolName = req.toolName,
                            resultSummary = "You are in $label",
                            data = mapOf("package" to pkg)
                        )
                    }
                }
            )
        )
    }

    /** Resolve a package name to its user-visible app label. */
    private fun friendlyAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
