package com.jarvis.agent

import com.jarvis.agent.contract.KnowledgeManageable
import com.jarvis.agent.contract.SourceStatus
import com.jarvis.service.knowledge.contract.KnowledgeItem
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Notion agent stub for future implementation
 * This agent would manage Notion workspace as knowledge source
 */
@Component
class NotionAgent : KnowledgeManageable {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun formMemories(config: Map<String, Any>): List<KnowledgeItem> {
        logger.info { "NotionAgent: Attempting to form memories (stub implementation)" }
        
        // Validate configuration
        val apiKey = config["apiKey"] as? String
            ?: throw IllegalArgumentException("Notion API key required")
        
        val workspaceId = config["workspaceId"] as? String
            ?: throw IllegalArgumentException("Notion workspace ID required")
        
        // Simulate API call
        delay(1000)
        
        logger.warn { "NotionAgent: Returning stub data - real implementation needed" }
        
        return listOf(
            KnowledgeItem(
                id = "notion-sample-1",
                title = "Sample Notion Page",
                content = "This is sample content from NotionAgent stub. " +
                         "Real implementation would fetch pages via Notion API.",
                metadata = mapOf(
                    "workspace" to workspaceId,
                    "processedBy" to "NotionAgent",
                    "isStub" to true
                ),
                tags = listOf("notion", "stub", "sample"),
                lastModified = System.currentTimeMillis()
            )
        )
    }
    
    override fun canAccessSource(): Boolean {
        logger.debug { "NotionAgent: Checking source access (always false for stub)" }
        return false // Stub implementation - never "available"
    }
    
    override fun getSourceStatus(): SourceStatus {
        return SourceStatus(
            sourceType = "notion",
            isAccessible = false,
            health = "stub_implementation"
        )
    }
}