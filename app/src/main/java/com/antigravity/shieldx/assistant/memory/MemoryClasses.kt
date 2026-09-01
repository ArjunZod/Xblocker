package com.antigravity.shieldx.assistant.memory

import com.antigravity.shieldx.data.local.AppDatabase
import com.antigravity.shieldx.data.local.entities.MemoryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages short-lived conversational and session context.
 */
class ContextEngine {
    private val turnContext = ConcurrentHashMap<String, Any>()

    fun set(key: String, value: Any) {
        turnContext[key] = value
    }

    fun get(key: String): Any? = turnContext[key]

    fun clearTurn() {
        turnContext.clear()
    }
}

/**
 * Manages persistent user-approved facts and preferences.
 */
class MemoryManager(private val database: AppDatabase) {

    val allMemoriesFlow: Flow<List<MemoryItemEntity>> = database.memoryDao().getAllMemoriesFlow()

    suspend fun saveFact(key: String, value: String, category: String = "PREFERENCE") = withContext(Dispatchers.IO) {
        database.memoryDao().insertOrUpdate(
            MemoryItemEntity(
                key = key.trim().lowercase(),
                value = value.trim(),
                source = "USER_EXPLICIT",
                consent = true,
                category = category
            )
        )
    }

    suspend fun recallFact(key: String): String? = withContext(Dispatchers.IO) {
        database.memoryDao().getMemory(key.trim().lowercase())?.value
    }

    suspend fun searchFacts(query: String): List<MemoryItemEntity> = withContext(Dispatchers.IO) {
        database.memoryDao().searchMemories(query)
    }

    suspend fun forgetFact(key: String): Boolean = withContext(Dispatchers.IO) {
        database.memoryDao().deleteByKey(key.trim().lowercase()) > 0
    }
}
