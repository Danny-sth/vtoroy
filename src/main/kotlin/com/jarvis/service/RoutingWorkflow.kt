package com.jarvis.service

import com.jarvis.entity.ChatMessage
import com.jarvis.entity.MessageRole
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service

@Service
class RoutingWorkflow(
    private val chatModel: AnthropicChatModel,
    private val knowledgeService: KnowledgeService
) {
    private val logger = KotlinLogging.logger {}
    
    companion object {
        private const val ROUTING_PROMPT = """
        Analyze the user query and conversation history to determine the appropriate route:
        
        Routes:
        - "knowledge": Search in knowledge base for information about saved documents, notes, projects that are NOT in the current conversation
        - "general": Use for greetings, general questions, coding help, or when the answer is already in the conversation history
        
        Decision criteria:
        1. If the answer to the query is in the conversation history → "general"
        2. If asking about something from the current conversation → "general"  
        3. If need to search saved documents/notes → "knowledge"
        
        Examples:
        - "расскажи о проекте Jarvis" (no context) → knowledge
        - "что там с Thailand Vacation?" (no context) → knowledge
        - "Как меня зовут?" (name mentioned in history) → general
        - "привет" → general
        - "помоги с кодом" → general
        
        IMPORTANT: Respond with ONLY the route name (either "knowledge" or "general"). No explanation.
        
        {query}
        """
    }
    
    suspend fun route(query: String, chatHistory: List<ChatMessage> = emptyList()): String {
        logger.debug { "Routing query: '$query' with ${chatHistory.size} messages in history" }
        
        // Шаг 1: Определяем маршрут с учетом истории
        val routingResponse = determineRoute(query, chatHistory)
        val route = routingResponse.trim().lowercase()
        
        logger.info { "Query routed to: $route" }
        
        // Шаг 2: Выполняем соответствующую логику
        return when (route) {
            "knowledge" -> handleKnowledgeQuery(query, chatHistory)
            "general" -> handleGeneralQuery(query, chatHistory) 
            else -> {
                logger.warn { "Unknown route: $route, defaulting to general" }
                handleGeneralQuery(query, chatHistory)
            }
        }
    }
    
    private fun determineRoute(query: String, chatHistory: List<ChatMessage>): String {
        // Создаем контекст из истории для роутера
        val historyContext = if (chatHistory.isNotEmpty()) {
            val historyText = chatHistory.joinToString("\n") { msg ->
                "${msg.role}: ${msg.content}"
            }
            """
            
            Previous conversation context:
            $historyText
            
            Current query: $query
            
            IMPORTANT: If the answer to the current query can be found in the conversation history above, 
            route to "general" so the model can use the conversation context. 
            Only route to "knowledge" if you need to search for information that is NOT in the conversation history.
            """.trimIndent()
        } else {
            query
        }
        
        val routingPromptWithHistory = ROUTING_PROMPT.replace("{query}", historyContext)
        
        val prompt = Prompt(listOf(
            SystemMessage("You are a routing classifier. Follow instructions precisely."),
            UserMessage(routingPromptWithHistory)
        ))
        
        val response = chatModel.call(prompt)
        return response.result.output.content
    }
    
    private suspend fun handleKnowledgeQuery(query: String, chatHistory: List<ChatMessage>): String {
        logger.debug { "Handling knowledge query" }
        
        // Поиск в базе знаний
        val knowledgeFiles = knowledgeService.searchKnowledge(query, 5)
        
        if (knowledgeFiles.isEmpty()) {
            return "К сожалению, я не нашел информации по вашему запросу в базе знаний. Можете уточнить запрос или спросить что-то другое?"
        }
        
        // Создаем контекст из найденных документов
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
        
        // Добавляем историю чата в промпт
        val messages = mutableListOf<Message>()
        messages.add(SystemMessage("Ты - Джарвис, персональный AI ассистент. Используй только предоставленную информацию."))
        
        // Добавляем историю переписки
        chatHistory.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> messages.add(UserMessage(msg.content))
                MessageRole.ASSISTANT -> messages.add(AssistantMessage(msg.content))
                MessageRole.SYSTEM -> messages.add(SystemMessage(msg.content))
                MessageRole.FUNCTION -> {} // Пропускаем function messages для этой версии
            }
        }
        
        // Добавляем текущий запрос с контекстом из базы знаний
        messages.add(UserMessage(knowledgePrompt))
        
        val prompt = Prompt(messages)
        
        val response = chatModel.call(prompt)
        return response.result.output.content
    }
    
    private fun handleGeneralQuery(query: String, chatHistory: List<ChatMessage>): String {
        logger.debug { "Handling general query" }
        
        val systemPrompt = """
        Ты - Джарвис, персональный AI ассистент с автономным принятием решений.
        
        Твои возможности:
        - Ведение разговора
        - Помощь с общими вопросами
        - Техническая поддержка
        
        Правила поведения:
        1. Будь дружелюбным и профессиональным
        2. Отвечай конкретно на заданный вопрос
        3. Если нужна информация из базы знаний пользователя, предложи переформулировать запрос
        """
        
        // Создаем промпт с историей
        val messages = mutableListOf<Message>()
        messages.add(SystemMessage(systemPrompt))
        
        // Добавляем историю переписки
        chatHistory.forEach { msg ->
            when (msg.role) {
                MessageRole.USER -> messages.add(UserMessage(msg.content))
                MessageRole.ASSISTANT -> messages.add(AssistantMessage(msg.content))
                MessageRole.SYSTEM -> messages.add(SystemMessage(msg.content))
                MessageRole.FUNCTION -> {} // Пропускаем function messages для этой версии
            }
        }
        
        // Добавляем текущий запрос
        messages.add(UserMessage(query))
        
        val prompt = Prompt(messages)
        
        val response = chatModel.call(prompt)
        return response.result.output.content
    }
}