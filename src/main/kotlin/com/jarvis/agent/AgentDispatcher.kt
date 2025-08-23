package com.jarvis.agent

import com.jarvis.agent.contract.SubAgent
import com.jarvis.agent.contract.AgentSelection
import com.jarvis.entity.ChatMessage
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

/**
 * Agent Dispatcher - выбирает подходящего sub-agent для задачи
 * Работает по принципам Claude Code: простая автоматическая селекция
 */
@Component
class AgentDispatcher(
    private val subAgents: List<SubAgent>,
    private val chatModel: AnthropicChatModel
) {
    
    private val logger = KotlinLogging.logger {}
    
    init {
        logger.info { "AgentDispatcher initialized with ${subAgents.size} sub-agents: ${subAgents.map { it.name }}" }
    }
    
    /**
     * Автоматически выбирает лучшего агента для задачи
     * Использует AI для анализа описаний агентов
     */
    suspend fun selectAgent(query: String, chatHistory: List<ChatMessage> = emptyList()): AgentSelection? {
        logger.debug { "Selecting agent for query: '$query'" }
        
        // Фильтруем доступных агентов
        val availableAgents = subAgents.filter { 
            try {
                it.isAvailable()
            } catch (e: Exception) {
                logger.warn(e) { "Agent ${it.name} availability check failed" }
                false
            }
        }
        
        if (availableAgents.isEmpty()) {
            logger.warn { "No available sub-agents" }
            return null
        }
        
        // Если только один агент - проверяем может ли он обработать запрос
        if (availableAgents.size == 1) {
            val agent = availableAgents.first()
            logger.debug { "Single agent ${agent.name} - checking canHandle for: '$query'" }
            val canHandle = agent.canHandle(query, chatHistory)
            logger.debug { "Agent ${agent.name} canHandle result: $canHandle" }
            return if (canHandle) {
                logger.debug { "Selected single agent ${agent.name}" }
                AgentSelection(agent, 1.0, "Only available agent that can handle the query")
            } else {
                logger.debug { "Single agent ${agent.name} cannot handle query: '$query'" }
                null
            }
        }
        
        // Используем AI для выбора лучшего агента
        return selectAgentWithAI(query, availableAgents, chatHistory)
    }
    
    /**
     * AI-based agent selection как в Claude Code
     */
    private suspend fun selectAgentWithAI(
        query: String, 
        agents: List<SubAgent>,
        chatHistory: List<ChatMessage>
    ): AgentSelection? {
        
        val agentDescriptions = agents.map { agent ->
            "${agent.name}: ${agent.description}"
        }.joinToString("\n")
        
        val systemPrompt = """
        Выбери лучшего агента для выполнения запроса пользователя.
        
        Доступные агенты:
        $agentDescriptions
        
        Правила:
        1. Выбирай агента, чьё описание лучше всего соответствует запросу
        2. Если несколько агентов подходят - выбирай наиболее специализированного
        3. Отвечай ТОЛЬКО именем агента без объяснений
        
        Запрос: $query
        """.trimIndent()
        
        return try {
            val response = chatModel.call(Prompt(listOf(
                SystemMessage(systemPrompt),
                UserMessage("Выбери агента для: $query")
            )))
            
            val selectedAgentName = response.result.output.content.trim()
            val selectedAgent = agents.find { it.name == selectedAgentName }
            
            if (selectedAgent != null) {
                logger.info { "Selected agent: $selectedAgentName for query: '$query'" }
                AgentSelection(selectedAgent, 0.9, "AI selection")
            } else {
                logger.warn { "AI selected unknown agent: $selectedAgentName, falling back to first available" }
                AgentSelection(agents.first(), 0.5, "Fallback to first available")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "AI agent selection failed, using first available agent" }
            AgentSelection(agents.first(), 0.3, "Error fallback")
        }
    }
}