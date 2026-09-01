package com.antigravity.shieldx.assistant.automation

import com.antigravity.shieldx.assistant.executor.DeterministicExecutor
import com.antigravity.shieldx.core.model.ToolRequest
import com.antigravity.shieldx.assistant.data.AutomationDao
import com.antigravity.shieldx.assistant.data.AutomationEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Executes routine automations when system triggers fire.
 */
class AutomationEngine(
    private val automationDao: AutomationDao,
    private val executor: DeterministicExecutor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val gson = Gson()
    val activeAutomationsFlow: Flow<List<AutomationEntity>> = automationDao.getActiveAutomationsFlow()

    suspend fun handleTrigger(triggerType: String, payload: String = "") = withContext(Dispatchers.IO) {
        val automations = automationDao.getAutomationsByTrigger(triggerType)

        for (auto in automations) {
            if (auto.triggerPayload.isNotEmpty() && !auto.triggerPayload.equals(payload, ignoreCase = true)) {
                continue
            }

            scope.launch {
                executeAutomationActions(auto)
            }
        }
    }

    private suspend fun executeAutomationActions(automation: AutomationEntity) {
        try {
            val listType = object : TypeToken<List<ToolRequest>>() {}.type
            val actions: List<ToolRequest> = gson.fromJson(automation.actionsJson, listType) ?: emptyList()

            for (action in actions) {
                // Execute actions as user-authorized routine
                executor.execute(action.copy(userConfirmed = true))
            }
        } catch (_: Exception) {}
    }

    suspend fun createAutomation(
        name: String,
        triggerType: String,
        triggerPayload: String,
        actions: List<ToolRequest>
    ) = withContext(Dispatchers.IO) {
        val entity = AutomationEntity(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            triggerType = triggerType,
            triggerPayload = triggerPayload,
            actionsJson = gson.toJson(actions),
            isEnabled = true
        )
        automationDao.insertOrUpdate(entity)
    }
}
