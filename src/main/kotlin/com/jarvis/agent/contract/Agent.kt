package com.jarvis.agent.contract

import com.jarvis.entity.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Базовый интерфейс для всех агентов в системе Jarvis
 */
interface Agent {
    /**
     * Уникальное имя агента
     */
    val name: String
    
    /**
     * Описание возможностей агента для координатора
     */
    val capabilities: String
    
    /**
     * Проверяет, может ли агент обработать запрос
     */
    fun canHandle(query: String, chatHistory: List<ChatMessage> = emptyList()): Boolean
    
    /**
     * Обрабатывает запрос пользователя
     */
    suspend fun handle(query: String, chatHistory: List<ChatMessage> = emptyList()): AgentResponse
    
    /**
     * Получает статус агента (доступен/недоступен/ошибка)
     */
    suspend fun getStatus(): AgentStatus
}

/**
 * Расширенный интерфейс для агентов поддерживающих потоковую обработку
 */
interface StreamingAgent : Agent {
    /**
     * Обрабатывает запрос с потоковым ответом
     */
    suspend fun handleStreaming(query: String, chatHistory: List<ChatMessage> = emptyList()): Flow<String>
}

/**
 * Ответ от агента
 */
data class AgentResponse(
    val content: String,
    val metadata: Map<String, Any> = emptyMap(),
    val confidence: Double = 1.0,
    val processingTimeMs: Long = 0L
)

/**
 * Статус агента
 */
enum class AgentStatus {
    AVAILABLE,
    BUSY, 
    UNAVAILABLE,
    ERROR
}