package com.vtoroy.agent

import com.vtoroy.agent.contract.SubAgent
import com.vtoroy.agent.obsidian.ObsidianQueryParser
import com.vtoroy.agent.obsidian.VaultOperations
import com.vtoroy.entity.ChatMessage
import com.vtoroy.entity.MessageRole
import com.vtoroy.service.ThinkingService
import com.vtoroy.util.RetryUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * ObsidianAgent - упрощенный orchestrator для Obsidian vault операций
 * Делегирует парсинг и выполнение специализированным классам
 *
 * РЕФАКТОРИНГ: Разделен на 3 компонента:
 * 1. ObsidianQueryParser - парсинг запросов через AI
 * 2. VaultOperations - выполнение операций с vault
 * 3. ObsidianAgent - простая оркестрация (этот класс)
 */
@Component
class ObsidianAgent(
    @Value("\${vtoroy.obsidian.vault-path}")
    private val defaultVaultPath: String,
    private val queryParser: ObsidianQueryParser,
    private val vaultOperations: VaultOperations,
    private val chatModel: AnthropicChatModel,
    private val thinkingService: ThinkingService
) : SubAgent {

    private val logger = KotlinLogging.logger {}

    // Cache для canHandle() решений - оптимизация производительности
    private val canHandleCache = ConcurrentHashMap<Int, Boolean>()

    // SubAgent configuration
    override val name = "obsidian-manager"

    override val description = """
        Expert at managing Obsidian vault operations: creating, reading, updating, deleting notes.
        Handles markdown files, wikilinks, tags, and vault organization.
        Use for any Obsidian-related tasks like "create note", "read file", "search notes".
    """.trimIndent()

    override val tools = listOf(
        "obsidian_read", "obsidian_create", "obsidian_search",
        "obsidian_update", "obsidian_delete", "obsidian_list"
    )

    override suspend fun canHandle(query: String, chatHistory: List<ChatMessage>): Boolean {
        // Кеширование для избежания повторных AI вызовов
        val cacheKey = (query + chatHistory.takeLast(2).joinToString()).hashCode()

        return canHandleCache.computeIfAbsent(cacheKey) {
            runBlocking {
                canHandleWithAI(query, chatHistory)
            }
        }
    }

    /**
     * AI-based decision с retry logic
     */
    private suspend fun canHandleWithAI(query: String, chatHistory: List<ChatMessage>): Boolean {
        // Quick check - простые ключевые слова для fast path
        val queryLower = query.lowercase()
        if (queryLower.contains("obsidian") || queryLower.contains("vault")) {
            return true
        }

        // Проверяем есть ли потенциальные команды
        val hasCommands = queryLower.matches(Regex(".*\\b(создай|прочитай|найди|удали|заметк|note)\\b.*"))
        if (!hasCommands) {
            return false // Fast rejection
        }

        // AI вызов с retry logic только для неоднозначных случаев
        val systemPrompt = """
        Определи, нужен ли Obsidian агент для этого запроса.

        Obsidian агент умеет:
        - Создавать/читать/обновлять заметки в markdown
        - Искать в vault по файлам
        - Работать с тегами и папками
        - Управлять структурой vault

        Отвечай только: true или false
        """.trimIndent()

        val contextMessages = if (chatHistory.isNotEmpty()) {
            "Контекст:\n" +
            chatHistory.takeLast(3).joinToString("\n") { "${it.role}: ${it.content}" } + "\n\n"
        } else ""

        val userPrompt = "${contextMessages}Запрос: $query"

        return try {
            // Используем retry утилиту
            RetryUtil.withRetry(maxAttempts = 2) {
                val prompt = Prompt(listOf(
                    SystemMessage(systemPrompt),
                    UserMessage(userPrompt)
                ))

                val response = chatModel.call(prompt).result.output.content.trim().lowercase()
                val canHandle = response.contains("true")

                logger.debug { "ObsidianAgent.canHandle('$query'): $canHandle (AI: '$response')" }
                canHandle
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in AI-based canHandle, defaulting to false" }
            false
        }
    }

    override suspend fun handle(query: String, chatHistory: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        try {
            logger.info { "ObsidianAgent executing: '$query'" }

            // Извлекаем sessionId из метаданных
            val sessionId = chatHistory.lastOrNull()?.metadata?.get("sessionId")?.asText()
            logger.debug { "Session ID: $sessionId" }

            // Шаг 1: Парсим запрос (делегируем ObsidianQueryParser)
            val parsedQuery = queryParser.parse(query, chatHistory, sessionId)

            // Шаг 2: Выполняем операцию (делегируем VaultOperations)
            val result = vaultOperations.execute(parsedQuery)

            return@withContext result

        } catch (e: Exception) {
            logger.error(e) { "ObsidianAgent error: '$query'" }
            "❌ Ошибка при работе с Obsidian: ${e.message}"
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            logger.debug { "Checking ObsidianAgent availability..." }
            // Простая проверка - пытаемся вызвать listFolders
            vaultOperations.execute(
                com.vtoroy.agent.obsidian.ParsedQuery(
                    com.vtoroy.dto.ObsidianAction.LIST_FOLDERS,
                    emptyMap()
                )
            )
            true
        } catch (e: Exception) {
            logger.error(e) { "ObsidianAgent availability check failed" }
            false
        }
    }
}

private fun runBlocking(block: suspend () -> Boolean): Boolean {
    return kotlinx.coroutines.runBlocking {
        block()
    }
}
