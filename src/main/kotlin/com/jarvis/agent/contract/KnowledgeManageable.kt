package com.jarvis.agent.contract

import com.jarvis.service.knowledge.contract.KnowledgeItem

/**
 * Interface for agents that can manage knowledge sources
 * Not all agents need this capability - only those responsible for forming persistent memories
 * 
 * Following the brain metaphor:
 * - Some agents form long-term memories (KnowledgeManageable)
 * - Others work with temporary information (e.g., web search agents)
 */
interface KnowledgeManageable {
    
    /**
     * Agent's capability to form memories from their specific source
     * Each agent decides what's worth remembering and how to structure it
     */
    suspend fun formMemories(config: Map<String, Any>): List<KnowledgeItem>
    
    /**
     * Check if the agent can currently access their knowledge source
     */
    fun canAccessSource(): Boolean
    
    /**
     * Get the agent's knowledge source status
     */
    fun getSourceStatus(): SourceStatus
}

/**
 * Status of agent's knowledge source
 */
data class SourceStatus(
    val sourceType: String,
    val isAccessible: Boolean,
    val lastSync: Long? = null,
    val itemCount: Int = 0,
    val health: String = "unknown"
)