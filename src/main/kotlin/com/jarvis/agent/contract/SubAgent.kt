package com.jarvis.agent.contract

import com.jarvis.entity.ChatMessage

/**
 * Sub-Agent интерфейс по принципам Claude Code
 * Каждый sub-agent имеет четкую специализацию и короткий промпт
 */
interface SubAgent {
    /**
     * Уникальное имя агента (lowercase, например: obsidian-manager)
     */
    val name: String
    
    /**
     * Краткое описание когда использовать этого агента
     * Используется для автоматического выбора подходящего агента
     */
    val description: String
    
    /**
     * Список инструментов доступных агенту (опционально)
     * Если null - наследует все инструменты
     */
    val tools: List<String>? get() = null
    
    /**
     * Проверяет может ли агент обработать запрос
     * Используется системой выбора агентов
     */
    suspend fun canHandle(query: String, chatHistory: List<ChatMessage> = emptyList()): Boolean
    
    /**
     * Обрабатывает запрос пользователя
     * Возвращает прямой результат без метаданных
     */
    suspend fun handle(query: String, chatHistory: List<ChatMessage> = emptyList()): String
    
    /**
     * Проверяет доступность агента
     */
    suspend fun isAvailable(): Boolean = true
}

/**
 * Результат выбора агента диспетчером
 */
data class AgentSelection(
    val agent: SubAgent,
    val confidence: Double,
    val reason: String
)