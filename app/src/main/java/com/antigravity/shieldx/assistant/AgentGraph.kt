package com.antigravity.shieldx.assistant

import android.content.Context
import com.antigravity.shieldx.assistant.automation.AutomationEngine
import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.handlers.SystemControlTools
import com.antigravity.shieldx.assistant.commands.handlers.ToolHandlers
import com.antigravity.shieldx.assistant.executor.ConfirmationManager
import com.antigravity.shieldx.assistant.executor.DeterministicExecutor
import com.antigravity.shieldx.assistant.intent.IntentEngine
import com.antigravity.shieldx.assistant.memory.ContextEngine
import com.antigravity.shieldx.assistant.memory.MemoryManager
import com.antigravity.shieldx.assistant.planning.AssistantPlanner
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.data.local.AppDatabase

/**
 * Everything TaRZI-AI-Agent owns. Depends only on the ProtectionController/
 * MusicController contracts, never on ProtectionGraph or MusicGraph directly.
 */
class AgentGraph(
    context: Context,
    database: AppDatabase,
    eventBus: EventBus,
    protectionController: ProtectionController,
    musicController: MusicController
) {

    val commandRegistry = CommandRegistry()
    val confirmationManager = ConfirmationManager()
    val intentEngine = IntentEngine()
    val assistantPlanner = AssistantPlanner(intentEngine, commandRegistry)
    val deterministicExecutor = DeterministicExecutor(
        context = context,
        commandRegistry = commandRegistry,
        confirmationManager = confirmationManager,
        database = database,
        eventBus = eventBus
    )

    val memoryManager = MemoryManager(database)
    val contextEngine = ContextEngine()
    val automationEngine = AutomationEngine(database, deterministicExecutor)

    val toolHandlers = ToolHandlers(
        context = context,
        commandRegistry = commandRegistry,
        protectionController = protectionController,
        musicController = musicController,
        database = database
    )

    /** Screen-control tools, backed by the accessibility service. */
    val systemControlTools = SystemControlTools(
        context = context,
        commandRegistry = commandRegistry
    )
}
