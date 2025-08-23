package com.jarvis.agent

import com.jarvis.agent.contract.SubAgent
import com.jarvis.controller.ThinkingController
import com.jarvis.entity.ChatMessage
import com.jarvis.service.KnowledgeService
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service

/**
 * Jarvis Main Agent - Simple dispatcher following Claude Code principles
 * Automatically selects appropriate sub-agents for tasks
 * Handles general conversations and knowledge search when no sub-agent matches
 */
@Service
class JarvisMainAgent(
    private val agentDispatcher: AgentDispatcher,
    private val knowledgeService: KnowledgeService,
    private val chatModel: AnthropicChatModel
) {
    
    private val logger = KotlinLogging.logger {}
    
    init {
        logger.info { "JarvisMainAgent initialized with AgentDispatcher" }
    }
    
    /**
     * Main entry point - processes user queries
     */
    suspend fun processQuery(query: String, sessionId: String, chatHistory: List<ChatMessage>): String {
        logger.info { "Processing query: '$query' for session: $sessionId" }
        
        return withContext(Dispatchers.IO) {
            try {
                // Send initial thought
                ThinkingController.sendThought(sessionId, "🎯 Анализирую запрос: «$query»", "start")
                
                // Try to find suitable sub-agent
                val agentSelection = agentDispatcher.selectAgent(query, chatHistory)
                
                if (agentSelection != null) {
                    // Delegate to sub-agent
                    ThinkingController.sendThought(sessionId, "🤖 Делегирую ${agentSelection.agent.name}", "delegate")
                    val result = agentSelection.agent.handle(query, chatHistory)
                    ThinkingController.finishThinking(sessionId, "✅ Выполнено!")
                    result
                } else {
                    // Handle directly - check if it's knowledge search or dialogue
                    val approach = determineApproach(query, chatHistory)
                    
                    when (approach) {
                        "knowledge_search" -> {
                            ThinkingController.sendThought(sessionId, "🔍 Ищу в базе знаний...", "search")
                            val result = handleKnowledgeSearch(query, chatHistory)
                            ThinkingController.finishThinking(sessionId, "✅ Поиск завершен!")
                            result
                        }
                        else -> {
                            ThinkingController.sendThought(sessionId, "💬 Отвечаю в диалоге...", "dialogue")
                            val result = handleDialogue(query, chatHistory)
                            ThinkingController.finishThinking(sessionId, "✅ Ответ готов!")
                            result
                        }
                    }
                }
                
            } catch (e: Exception) {
                logger.error(e) { "Error processing query: '$query'" }
                ThinkingController.finishThinking(sessionId, "❌ Произошла ошибка")
                "❌ Произошла ошибка при обработке запроса: ${e.message}"
            }
        }
    }
    
    /**
     * AI-based approach determination (Claude Code principles - no hardcoded keywords!)
     */
    private suspend fun determineApproach(query: String, chatHistory: List<ChatMessage>): String {
        val systemPrompt = """
        Определи подход для ответа на запрос пользователя:
        
        knowledge_search - если пользователь запрашивает информацию о чем-то конкретном, 
        что может быть в базе знаний (проекты, документация, заметки)
        
        dialogue - для обычного общения, вопросов общего характера, 
        просьб о помощи без конкретной информации
        
        Отвечай только: knowledge_search или dialogue
        """.trimIndent()
        
        val contextMessages = if (chatHistory.isNotEmpty()) {
            "Контекст:\n" + 
            chatHistory.takeLast(3).joinToString("\n") { "${it.role}: ${it.content}" } + "\n\n"
        } else ""
        
        val userPrompt = "${contextMessages}Запрос: $query"
        
        return try {
            val prompt = Prompt(listOf(
                SystemMessage(systemPrompt),
                UserMessage(userPrompt)
            ))
            
            val response = chatModel.call(prompt).result.output.content.trim().lowercase()
            val approach = if (response.contains("knowledge_search")) "knowledge_search" else "dialogue"
            
            logger.debug { "AI determined approach for '$query': $approach (response: '$response')" }
            approach
            
        } catch (e: Exception) {
            logger.error(e) { "Error in AI approach determination, defaulting to dialogue" }
            "dialogue"
        }
    }
    
    /**
     * Handle knowledge search using vector database
     */
    private suspend fun handleKnowledgeSearch(query: String, chatHistory: List<ChatMessage>): String {
        logger.debug { "Searching knowledge base for: '$query'" }
        
        val knowledgeFiles = knowledgeService.searchKnowledge(query, 5)
        
        if (knowledgeFiles.isEmpty()) {
            return "🤔 Не нашел информации по вашему запросу в базе знаний. Попробуйте переформулировать или спросите что-то другое."
        }
        
        val context = knowledgeFiles.joinToString("\n\n") { file ->
            "Документ: ${file.filePath}\n${file.content}"
        }
        
        val systemPrompt = """
        Ответь на вопрос пользователя, используя только предоставленную информацию.
        
        Контекст из базы знаний:
        $context
        
        Правила:
        - Отвечай кратко и по существу
        - Используй только информацию из контекста
        - Если информации недостаточно - честно скажи об этом
        """.trimIndent()
        
        val messages = buildMessagesWithHistory(chatHistory, query, systemPrompt)
        val response = chatModel.call(Prompt(messages))
        
        return response.result.output.content
    }
    
    /**
     * Handle general dialogue
     */
    private suspend fun handleDialogue(query: String, chatHistory: List<ChatMessage>): String {
        logger.debug { "Processing dialogue: '$query'" }
        
        val systemPrompt = """
        Ты - Джарвис, персональный AI ассистент.
        
        Твои возможности:
        - Дружелюбное общение с памятью о предыдущих сообщениях
        - Помощь с общими вопросами
        - Работа с Obsidian vault через специализированных агентов
        - Поиск в векторной базе знаний
        
        Правила:
        1. Будь дружелюбным и профессиональным
        2. Отвечай кратко и по существу
        3. Используй контекст предыдущих сообщений
        4. Если нужны операции с файлами/заметками - объясни что можешь помочь
        """.trimIndent()
        
        val messages = buildMessagesWithHistory(chatHistory, query, systemPrompt)
        val response = chatModel.call(Prompt(messages))
        
        return response.result.output.content
    }
    
    /**
     * Build message list including chat history
     */
    private fun buildMessagesWithHistory(
        chatHistory: List<ChatMessage>,
        currentQuery: String,
        systemPrompt: String = "Ты - Джарвис, персональный AI ассистент."
    ): List<Message> {
        val messages = mutableListOf<Message>()
        
        messages.add(SystemMessage(systemPrompt))
        
        // Add last 10 messages from history for context
        chatHistory.takeLast(10).forEach { msg ->
            when (msg.role) {
                com.jarvis.entity.MessageRole.USER -> messages.add(UserMessage(msg.content))
                com.jarvis.entity.MessageRole.ASSISTANT -> messages.add(AssistantMessage(msg.content))
                com.jarvis.entity.MessageRole.SYSTEM -> messages.add(SystemMessage(msg.content))
                com.jarvis.entity.MessageRole.FUNCTION -> {
                    // Skip function messages as they're not relevant for context
                }
            }
        }
        
        messages.add(UserMessage(currentQuery))
        
        return messages
    }
}