package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.dto.ChatResponse
import com.jarvis.entity.ChatMessage
import com.jarvis.entity.ChatSession
import com.jarvis.entity.MessageRole
import com.jarvis.repository.ChatMessageRepository
import com.jarvis.repository.ChatSessionRepository
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class JarvisService(
    private val routingWorkflow: RoutingWorkflow,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}
    
    @Value("\${jarvis.chat.max-history-size}")
    private var maxHistorySize: Int = 20
    
    @Value("\${jarvis.vector-search.max-results}")
    private var maxSearchResults: Int = 5
    
    // Routing Workflow теперь обрабатывает всю логику
    
    @Transactional
    suspend fun chat(query: String, sessionId: String): ChatResponse {
        logger.debug { "Processing chat for session: $sessionId" }
        
        // Get or create session
        val session = getOrCreateSession(sessionId)
        
        // Load chat history for context
        val chatHistory = loadChatHistory(sessionId)
        logger.debug { "Loaded ${chatHistory.size} messages from history" }
        
        // Save user message
        saveMessage(session, MessageRole.USER, query)
        
        try {
            // Передаем историю чата в Routing Workflow
            val responseContent = routingWorkflow.route(query, chatHistory)
            
            // Save assistant message
            saveMessage(session, MessageRole.ASSISTANT, responseContent)
            
            // Update session activity
            session.lastActiveAt = LocalDateTime.now()
            chatSessionRepository.save(session)
            
            return ChatResponse(
                response = responseContent,
                sessionId = sessionId,
                metadata = mapOf("approach" to "routing_workflow", "history_size" to chatHistory.size)
            )
        } catch (e: Exception) {
            logger.error(e) { "Error in routing workflow: ${e.message}" }
            throw e
        }
    }
    
    private fun loadChatHistory(sessionId: String): List<ChatMessage> {
        val messages = chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
        // Берем последние N сообщений и переворачиваем в хронологический порядок
        return messages.take(maxHistorySize).reversed()
    }
    
    private fun getOrCreateSession(sessionId: String): ChatSession {
        return chatSessionRepository.findById(sessionId).orElseGet {
            logger.info { "Creating new session: $sessionId" }
            chatSessionRepository.save(ChatSession(id = sessionId))
        }
    }
    
    private fun saveMessage(session: ChatSession, role: MessageRole, content: String) {
        val message = ChatMessage(
            session = session,
            role = role,
            content = content
        )
        chatMessageRepository.save(message)
    }
    
    // Все методы перенесены в RoutingWorkflow
}