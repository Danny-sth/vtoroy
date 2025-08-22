package com.jarvis.agent

import com.jarvis.agent.contract.Agent
import com.jarvis.agent.contract.AgentResponse
import com.jarvis.agent.contract.AgentStatus
import com.jarvis.entity.ChatMessage
import com.jarvis.entity.MessageRole
import com.jarvis.service.KnowledgeService
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service

/**
 * Главный агент в системе Jarvis
 * Обрабатывает диалог, векторный поиск и координирует со специализированными агентами
 */
@Service
class MainAgent(
    private val chatModel: AnthropicChatModel,
    private val knowledgeService: KnowledgeService,
    private val allAgents: List<Agent>
) : Agent {
    
    private val specializedAgents = allAgents.filter { it != this }

    private val logger = KotlinLogging.logger {}

    init {
        logger.info { "MainAgent инициализирован с ${specializedAgents.size} специализированными агентами: ${specializedAgents.map { it.name }}" }
    }

    override val name = "MainAgent"
    override val capabilities = "Диалог с памятью, поиск в базе знаний, координация специализированных агентов"

    companion object {
        private const val ROUTING_PROMPT = """
        Проанализируй запрос пользователя и определи подход к обработке.
        
        ВАЖНО: Система Jarvis имеет полный доступ к Obsidian vault через специализированного агента!
        
        Доступные подходы:
        1. "dialogue" - обычный диалог, общие вопросы, приветствия
        2. "knowledge_search" - поиск в векторной базе знаний (документы, заметки, проекты)  
        3. "delegate" - делегировать специализированному агенту (Obsidian операции, внешние системы)
        
        Правила принятия решения:
        - Приветствие, общие вопросы, теоретические вопросы → "dialogue"
        - Поиск в сохраненных документах/заметках в базе знаний → "knowledge_search"
        - Любые операции с Obsidian (создание, чтение, поиск, изменение заметок) → "delegate"
        - Вопросы о доступе к Obsidian или возможностях системы → "delegate"
        
        Примеры:
        - "привет, как дела?" → "dialogue"
        - "что такое Python?" → "dialogue"
        - "расскажи о проекте Jarvis из моих заметок" → "knowledge_search"
        - "найди информацию о встрече в документах" → "knowledge_search"
        - "создай заметку test.md" → "delegate"
        - "есть ли у тебя доступ к Obsidian?" → "delegate"
        - "прочитай заметку meeting.md" → "delegate"
        - "найди заметки с тегом #проект" → "delegate"
        - "список всех заметок" → "delegate"
        
        ВАЖНО: Отвечай ТОЛЬКО названием подхода без объяснений.
        
        Запрос: {query}
        """
    }

    override fun canHandle(query: String, chatHistory: List<ChatMessage>): Boolean = true

    override suspend fun handle(query: String, chatHistory: List<ChatMessage>): AgentResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            logger.debug { "MainAgent обрабатывает запрос: '$query'" }
            
            // Определяем подход к обработке
            val approach = determineApproach(query, chatHistory)
            
            logger.info { "Выбран подход: $approach" }
            
            val response = when (approach) {
                "knowledge_search" -> handleKnowledgeSearch(query, chatHistory)
                "dialogue" -> handleDialogue(query, chatHistory)
                "delegate" -> delegateToSpecializedAgent(query, chatHistory)
                else -> handleDialogue(query, chatHistory) // fallback to dialogue
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            response.copy(
                metadata = response.metadata + mapOf(
                    "approach" to approach,
                    "agent" to name
                ),
                processingTimeMs = processingTime
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Ошибка в MainAgent при обработке запроса" }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            AgentResponse(
                content = "Извините, произошла ошибка при обработке запроса: ${e.message}",
                metadata = mapOf(
                    "error" to true,
                    "error_message" to (e.message ?: "Unknown error"),
                    "agent" to name
                ),
                confidence = 0.0,
                processingTimeMs = processingTime
            )
        }
    }

    override suspend fun getStatus(): AgentStatus {
        return try {
            // Простая проверка доступности основных сервисов
            val testPrompt = Prompt(listOf(SystemMessage("Test"), UserMessage("Test")))
            chatModel.call(testPrompt)
            AgentStatus.AVAILABLE
        } catch (e: Exception) {
            logger.error(e) { "MainAgent недоступен" }
            AgentStatus.UNAVAILABLE
        }
    }

    /**
     * Определяет подход к обработке запроса
     */
    private fun determineApproach(query: String, chatHistory: List<ChatMessage>): String {
        val historyContext = if (chatHistory.isNotEmpty()) {
            val historyText = chatHistory.joinToString("\n") { msg ->
                "${msg.role}: ${msg.content}"
            }
            """
            История диалога:
            $historyText
            
            Текущий запрос: $query
            """
        } else {
            query
        }
        
        val routingPrompt = ROUTING_PROMPT.replace("{query}", historyContext)
        
        val prompt = Prompt(listOf(
            SystemMessage("Ты классификатор запросов. Следуй инструкциям точно."),
            UserMessage(routingPrompt)
        ))
        
        return try {
            val response = chatModel.call(prompt)
            response.result.output.content.trim().lowercase()
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при определении подхода, используем dialogue" }
            "dialogue"
        }
    }

    /**
     * Обрабатывает поиск в базе знаний
     */
    private suspend fun handleKnowledgeSearch(query: String, chatHistory: List<ChatMessage>): AgentResponse {
        logger.debug { "Выполняем поиск в базе знаний" }
        
        val knowledgeFiles = knowledgeService.searchKnowledge(query, 5)
        
        if (knowledgeFiles.isEmpty()) {
            return AgentResponse(
                content = "К сожалению, я не нашел информации по вашему запросу в базе знаний. Можете уточнить запрос или спросить что-то другое?",
                metadata = mapOf("approach" to "knowledge_search", "results_count" to 0),
                confidence = 0.5
            )
        }
        
        val context = knowledgeFiles.joinToString("\n\n") { file ->
            "Документ: ${file.filePath}\n${file.content}"
        }
        
        val knowledgePrompt = """
        Используя предоставленную информацию из базы знаний, ответь на вопрос пользователя.
        
        Контекст из базы знаний:
        $context
        
        Вопрос: $query
        
        Инструкции:
        - Отвечай только на основе предоставленной информации
        - Если информации недостаточно, честно скажи об этом
        - Будь конкретным и полезным
        """
        
        val messages = buildMessagesWithHistory(chatHistory, knowledgePrompt)
        val prompt = Prompt(messages)
        
        val response = chatModel.call(prompt)
        
        return AgentResponse(
            content = response.result.output.content,
            metadata = mapOf(
                "approach" to "knowledge_search",
                "results_count" to knowledgeFiles.size,
                "sources" to knowledgeFiles.map { it.filePath }
            ),
            confidence = 0.9
        )
    }

    /**
     * Обрабатывает обычный диалог
     */
    private fun handleDialogue(query: String, chatHistory: List<ChatMessage>): AgentResponse {
        logger.debug { "Обрабатываем обычный диалог" }
        
        val systemPrompt = """
        Ты - Джарвис, персональный AI ассистент пользователя с автономным принятием решений.
        
        Твои возможности:
        - Ведение дружелюбного диалога с памятью
        - Помощь с общими вопросами
        - Техническая поддержка  
        - Объяснение концепций
        - ПОЛНЫЙ доступ к Obsidian vault (создание, чтение, поиск, изменение заметок)
        - Поиск в векторной базе знаний
        - Управление заметками с тегами и wikilinks
        
        ВАЖНО: У тебя есть специализированный агент для работы с Obsidian! 
        Ты можешь создавать, читать, искать и изменять заметки в Obsidian vault.
        
        Правила:
        1. Будь дружелюбным и профессиональным
        2. Используй контекст предыдущих сообщений
        3. Отвечай конкретно на заданные вопросы
        4. Если нужны операции с Obsidian, объясни что можешь помочь
        5. Если нужна информация из базы знаний, предложи переформулировать запрос
        """
        
        val messages = buildMessagesWithHistory(chatHistory, query, systemPrompt)
        val prompt = Prompt(messages)
        
        val response = chatModel.call(prompt)
        
        return AgentResponse(
            content = response.result.output.content,
            metadata = mapOf("approach" to "dialogue"),
            confidence = 0.8
        )
    }

    /**
     * Делегирует запрос подходящему специализированному агенту
     */
    private suspend fun delegateToSpecializedAgent(
        query: String, 
        chatHistory: List<ChatMessage>
    ): AgentResponse {
        // Находим агентов, которые могут обработать запрос
        val capableAgents = specializedAgents.filter { agent ->
            agent.getStatus() == AgentStatus.AVAILABLE && agent.canHandle(query, chatHistory)
        }
        
        return when {
            capableAgents.isEmpty() -> {
                logger.warn { "Нет доступных специализированных агентов для запроса, используем диалог" }
                handleDialogue(query, chatHistory).copy(
                    metadata = mapOf(
                        "approach" to "dialogue",
                        "fallback_reason" to "no_specialized_agents"
                    )
                )
            }
            capableAgents.size == 1 -> {
                val agent = capableAgents.first()
                logger.info { "Делегируем запрос агенту ${agent.name}" }
                val response = agent.handle(query, chatHistory)
                response.copy(
                    metadata = response.metadata + mapOf("delegated_to" to agent.name)
                )
            }
            else -> {
                // Если несколько агентов могут обработать - выбираем лучшего
                val agent = selectBestSpecializedAgent(query, capableAgents)
                logger.info { "Делегируем запрос агенту ${agent.name} (выбран из ${capableAgents.size} кандидатов)" }
                val response = agent.handle(query, chatHistory)
                response.copy(
                    metadata = response.metadata + mapOf(
                        "delegated_to" to agent.name,
                        "candidates_count" to capableAgents.size
                    )
                )
            }
        }
    }
    
    /**
     * Выбирает лучшего специализированного агента из кандидатов
     */
    private fun selectBestSpecializedAgent(query: String, candidates: List<Agent>): Agent {
        // Пока простая логика - берем первого
        // В будущем можно использовать AI для выбора лучшего
        return candidates.first()
    }

    /**
     * Формирует сообщения включая историю диалога
     */
    private fun buildMessagesWithHistory(
        chatHistory: List<ChatMessage>,
        currentQuery: String,
        systemPrompt: String = "Ты - Джарвис, персональный AI ассистент."
    ): List<Message> {
        val messages = mutableListOf<Message>()
        
        messages.add(SystemMessage(systemPrompt))
        
        // Добавляем историю диалога (последние 10 сообщений)
        chatHistory.takeLast(10).forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> messages.add(UserMessage(msg.content))
                MessageRole.ASSISTANT -> messages.add(AssistantMessage(msg.content))
                MessageRole.SYSTEM -> messages.add(SystemMessage(msg.content))
                MessageRole.FUNCTION -> {} // Пропускаем function messages
            }
        }
        
        messages.add(UserMessage(currentQuery))
        
        return messages
    }
}