package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.agent.JarvisMainAgent
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
    private val jarvisMainAgent: JarvisMainAgent,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}
    
    @Value("\${jarvis.chat.max-history-size}")
    private var maxHistorySize: Int = 20
    
    @Value("\${jarvis.vector-search.max-results}")
    private var maxSearchResults: Int = 5
    
    @Transactional
    suspend fun chat(query: String, sessionId: String): ChatResponse {
        logger.debug { "Processing chat for session: $sessionId" }
        
        // Get or create session
        val session = getOrCreateSession(sessionId)
        
        // Save user message FIRST
        saveMessage(session, MessageRole.USER, query)
        
        // Load chat history for context (now includes the user message with sessionId)
        val chatHistory = loadChatHistory(sessionId)
        logger.debug { "Loaded ${chatHistory.size} messages from history" }
        
        try {
            // Обрабатываем запрос через JarvisMainAgent
            val responseContent = jarvisMainAgent.processQuery(query, sessionId, chatHistory)
            
            // Save assistant message
            saveMessage(session, MessageRole.ASSISTANT, responseContent)
            
            // Update session activity
            session.lastActiveAt = LocalDateTime.now()
            chatSessionRepository.save(session)
            
            return ChatResponse(
                response = responseContent,
                sessionId = sessionId,
                metadata = mapOf("history_size" to chatHistory.size)
            )
        } catch (e: Exception) {
            logger.error(e) { "Error in JarvisMainAgent: ${e.message}" }
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
        val metadata = objectMapper.createObjectNode().apply {
            put("sessionId", session.id)
        }
        
        val message = ChatMessage(
            session = session,
            role = role,
            content = content,
            metadata = metadata
        )
        chatMessageRepository.save(message)
    }
}