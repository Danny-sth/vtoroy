package com.vtoroy.agent.obsidian

import com.fasterxml.jackson.databind.ObjectMapper
import com.vtoroy.dto.ObsidianAction
import com.vtoroy.entity.ChatMessage
import com.vtoroy.entity.MessageRole
import com.vtoroy.service.ThinkingService
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

/**
 * ObsidianQueryParser - отвечает за парсинг запросов пользователя через AI
 * Извлекает действие и параметры из естественного языка
 */
@Component
class ObsidianQueryParser(
    private val chatModel: AnthropicChatModel,
    private val objectMapper: ObjectMapper,
    private val thinkingService: ThinkingService
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Парсит запрос пользователя и возвращает действие с параметрами
     */
    suspend fun parse(query: String, chatHistory: List<ChatMessage>, sessionId: String? = null): ParsedQuery {
        logger.debug { "Parsing query with AI: '$query'" }

        val systemPrompt = """
        Обработай запрос к Obsidian vault.

        ОПЕРАЦИИ:
        - READ_NOTE: чтение заметки (нужен path)
        - SEARCH_VAULT: поиск заметок (нужен query)
        - CREATE_NOTE: создание заметки (нужны path И title)
        - UPDATE_NOTE: обновление заметки (нужен path)
        - DELETE_NOTE: удаление заметки (нужен path)
        - LIST_NOTES: список заметок
        - GET_TAGS: все теги vault
        - ASK_USER: когда нужна дополнительная информация

        ПРАВИЛА:
        1. Если нет имени/названия - используй ASK_USER
        2. НЕ придумывай данные

        Отвечай JSON: {"action": "...", "parameters": {...}}
        """.trimIndent()

        val contextMessages = if (chatHistory.isNotEmpty()) {
            "История диалога:\n" +
            chatHistory.takeLast(5).joinToString("\n") { "${it.role}: ${it.content}" } + "\n\n"
        } else ""

        // Проверяем, является ли это ответом на вопрос
        val isResponseToQuestion = chatHistory.isNotEmpty() &&
            chatHistory.lastOrNull()?.role == MessageRole.ASSISTANT &&
            (chatHistory.lastOrNull()?.content?.contains("?") == true ||
             chatHistory.lastOrNull()?.content?.contains("укажите") == true ||
             chatHistory.lastOrNull()?.content?.contains("Пожалуйста") == true)

        val userPrompt = if (isResponseToQuestion) {
            """
            ${contextMessages}КОНТЕКСТ: Пользователь отвечает на мой предыдущий вопрос.
            Последний мой вопрос был: "${chatHistory.lastOrNull()?.content}"
            Ответ пользователя: "$query"

            ВАЖНО:
            1. Интерпретируй "$query" как ответ на мой вопрос
            2. СОБЕРИ ВСЕ ПАРАМЕТРЫ из истории диалога
            3. Если у тебя есть ВСЕ нужные данные - ВЫПОЛНЯЙ операцию
            4. Если все еще чего-то не хватает - только тогда ASK_USER

            Проанализируй всю историю и определи что нужно сделать.
            """.trimIndent()
        } else {
            """
            ${contextMessages}Новый запрос пользователя: $query

            Определи операцию и извлеки параметры.
            """.trimIndent()
        }

        return try {
            // Отправляем через SSE
            sessionId?.let {
                thinkingService.sendThought(it, "🤔 Анализирую: '$query'", "obsidian_thinking")
            }

            val prompt = Prompt(listOf(
                SystemMessage(systemPrompt),
                UserMessage(userPrompt)
            ))

            val response = chatModel.call(prompt)
            val fullResponse = response.result.output.content.trim()

            // Извлекаем рассуждение и JSON
            val jsonStartIndex = fullResponse.indexOf("{")
            val (reasoning, jsonPart) = if (jsonStartIndex > 0) {
                val reasoningPart = fullResponse.substring(0, jsonStartIndex).trim()
                val jsonPart = fullResponse.substring(jsonStartIndex).trim()
                reasoningPart to jsonPart
            } else {
                "" to fullResponse
            }

            // Отправляем рассуждение через SSE
            if (reasoning.isNotEmpty() && sessionId != null) {
                thinkingService.sendThought(sessionId, "💭 $reasoning", "obsidian_reasoning")
            }

            // Парсим JSON с Jackson (вместо regex!)
            val parsedAction = parseJsonWithJackson(jsonPart)

            // Отправляем действие через SSE
            sessionId?.let {
                val readableThought = formatActionForThinking(parsedAction)
                thinkingService.sendThought(it, readableThought, "obsidian_action")
            }

            logger.debug { "Parsed action: ${parsedAction.type}, parameters: ${parsedAction.parameters}" }
            parsedAction

        } catch (e: Exception) {
            logger.error(e) { "Error parsing query, falling back to search" }
            ParsedQuery(ObsidianAction.SEARCH_VAULT, mapOf("query" to query))
        }
    }

    /**
     * Парсит JSON ответ от AI используя Jackson ObjectMapper
     * Заменяет regex parsing для надежности
     */
    private fun parseJsonWithJackson(jsonResponse: String): ParsedQuery {
        try {
            val jsonNode = objectMapper.readTree(jsonResponse)

            // Извлекаем action
            val actionName = jsonNode.get("action")?.asText() ?: "SEARCH_VAULT"
            val action = try {
                ObsidianAction.valueOf(actionName)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Unknown action '$actionName', using SEARCH_VAULT" }
                ObsidianAction.SEARCH_VAULT
            }

            // Извлекаем parameters
            val parameters = mutableMapOf<String, Any?>()
            val paramsNode = jsonNode.get("parameters")

            if (paramsNode != null && paramsNode.isObject) {
                paramsNode.fields().forEach { (key, value) ->
                    parameters[key] = when {
                        value.isTextual -> value.asText()
                        value.isNumber -> value.asInt()
                        value.isBoolean -> value.asBoolean()
                        value.isArray -> value.map { it.asText() }.toSet()
                        else -> value.asText()
                    }
                }
            }

            return ParsedQuery(action, parameters)

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse JSON with Jackson: $jsonResponse" }
            // Fallback to search
            return ParsedQuery(ObsidianAction.SEARCH_VAULT, mapOf("query" to jsonResponse))
        }
    }

    /**
     * Форматирует действие для отображения в SSE
     */
    private fun formatActionForThinking(parsedAction: ParsedQuery): String {
        return when (parsedAction.type) {
            ObsidianAction.CREATE_NOTE -> "📝 Создаю заметку: ${parsedAction.parameters["title"] ?: parsedAction.parameters["path"]}"
            ObsidianAction.SEARCH_VAULT -> "🔍 Ищу в vault: ${parsedAction.parameters["query"]}"
            ObsidianAction.READ_NOTE -> "📖 Читаю заметку: ${parsedAction.parameters["path"]}"
            ObsidianAction.LIST_NOTES -> "📋 Получаю список заметок"
            ObsidianAction.GET_TAGS -> "🏷️ Загружаю все теги"
            ObsidianAction.ASK_USER -> "❓ Нужна дополнительная информация от пользователя"
            else -> "❓ Выполняю действие: ${parsedAction.type}"
        }
    }
}

/**
 * Результат парсинга запроса
 */
data class ParsedQuery(
    val type: ObsidianAction,
    val parameters: Map<String, Any?>
)
