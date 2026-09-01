package com.antigravity.shieldx.assistant

import android.content.Context
import com.antigravity.shieldx.assistant.automation.AutomationEngine
import com.antigravity.shieldx.assistant.commands.CommandRegistry
import com.antigravity.shieldx.assistant.commands.handlers.SystemControlTools
import com.antigravity.shieldx.assistant.commands.handlers.ToolHandlers
import com.antigravity.shieldx.assistant.data.AutomationDao
import com.antigravity.shieldx.assistant.data.MemoryDao
import com.antigravity.shieldx.assistant.data.NetworkProfileDao
import com.antigravity.shieldx.assistant.executor.ConfirmationManager
import com.antigravity.shieldx.assistant.executor.DeterministicExecutor
import com.antigravity.shieldx.assistant.intent.IntentEngine
import com.antigravity.shieldx.assistant.memory.ContextEngine
import com.antigravity.shieldx.assistant.memory.MemoryManager
import com.antigravity.shieldx.assistant.planning.AssistantPlanner
import com.antigravity.shieldx.core.runtime.AuditSink
import com.antigravity.shieldx.core.runtime.ConfigStore
import com.antigravity.shieldx.core.runtime.EventBus
import com.antigravity.shieldx.core.runtime.MusicController
import com.antigravity.shieldx.core.runtime.ProtectionController
import com.antigravity.shieldx.core.runtime.ProtectionInsights

/**
 * Everything TaRZI-AI-Agent owns. Depends only on contracts (ProtectionController,
 * MusicController, ConfigStore, AuditSink, ProtectionInsights) and its own DAOs,
 * never on :app types.
 */
class AgentGraph(
    val context: Context,
    val configStore: ConfigStore,
    auditSink: AuditSink,
    protectionInsights: ProtectionInsights,
    memoryDao: MemoryDao,
    automationDao: AutomationDao,
    networkProfileDao: NetworkProfileDao,
    eventBus: EventBus,
    protectionController: ProtectionController,
    val musicController: MusicController
) {

    val commandRegistry = CommandRegistry()
    val confirmationManager = ConfirmationManager()
    val intentEngine = IntentEngine()
    val assistantPlanner = AssistantPlanner(intentEngine, commandRegistry)
    val deterministicExecutor = DeterministicExecutor(
        context = context,
        commandRegistry = commandRegistry,
        confirmationManager = confirmationManager,
        auditSink = auditSink,
        eventBus = eventBus
    )

    val memoryManager = MemoryManager(memoryDao)
    val contextEngine = ContextEngine()
    val automationEngine = AutomationEngine(automationDao, deterministicExecutor)

    val toolHandlers = ToolHandlers(
        context = context,
        commandRegistry = commandRegistry,
        protectionController = protectionController,
        musicController = musicController,
        memoryDao = memoryDao,
        networkProfileDao = networkProfileDao,
        protectionInsights = protectionInsights
    )

    /** Screen-control tools, backed by the accessibility service. */
    val systemControlTools = SystemControlTools(
        context = context,
        commandRegistry = commandRegistry
    )

    /** The in-app voice session, owned here rather than by :app's SecurityManager. */
    var voiceAssistantManager: com.antigravity.shieldx.assistant.voice.VoiceAssistantManager? = null

    /** The assistant's reasoning entry point, shared by chat, orb and voice loop. */
    fun newBrain(): TarziBrain = TarziBrain(this)
}
