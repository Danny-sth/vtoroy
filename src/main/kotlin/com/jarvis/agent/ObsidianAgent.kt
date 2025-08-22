package com.jarvis.agent

import com.jarvis.agent.contract.KnowledgeManageable
import com.jarvis.agent.contract.SourceStatus
import com.jarvis.agent.memory.HybridMemoryClassifier
import com.jarvis.service.knowledge.contract.KnowledgeItem
import com.jarvis.service.knowledge.ObsidianKnowledgeSource
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Specialized agent for Obsidian vault knowledge management
 * This agent owns all interactions with Obsidian vault
 * Uses ObsidianKnowledgeSource as its internal tool
 */
@Component
class ObsidianAgent(
    @Value("\${jarvis.obsidian.vault-path}")
    private val defaultVaultPath: String,
    private val memoryClassifier: HybridMemoryClassifier
) : KnowledgeManageable {
    
    private val logger = KotlinLogging.logger {}
    private var lastSyncTime: Long? = null
    private var totalMemoriesFormed: Int = 0
    
    // Agent's private tool for working with Obsidian
    private val obsidianTool = ObsidianKnowledgeSource(defaultVaultPath)
    
    /**
     * Form memories from Obsidian vault using internal tool
     * The agent controls the process but delegates technical work to the tool
     */
    override suspend fun formMemories(config: Map<String, Any>): List<KnowledgeItem> {
        logger.info { "ObsidianAgent: Starting memory formation process" }
        
        val memories = obsidianTool.sync(config)
        
        // Agent processes and validates the memories
        val processedMemories = mutableListOf<KnowledgeItem>()
        for (memory in memories) {
            if (isWorthRemembering(memory.content)) {
                val processed = processMemory(memory)
                processedMemories.add(processed)
            }
        }
        
        totalMemoriesFormed += processedMemories.size
        lastSyncTime = System.currentTimeMillis()
        
        logger.info { "ObsidianAgent: Formed ${processedMemories.size} memories from ${memories.size} raw items" }
        return processedMemories
    }
    
    /**
     * Agent's high-level processing of raw memory into structured knowledge
     * Uses advanced ML-based classification system
     */
    private suspend fun processMemory(rawMemory: KnowledgeItem): KnowledgeItem {
        val enhancedMetadata = rawMemory.metadata?.toMutableMap() ?: mutableMapOf()
        
        // Use hybrid classifier for intelligent memory type detection
        val memoryType = memoryClassifier.classify(rawMemory.content, rawMemory.metadata)
        
        // Add agent's analysis  
        enhancedMetadata["processedBy"] = this::class.simpleName ?: "ObsidianAgent"
        enhancedMetadata["memoryType"] = memoryType.primary
        enhancedMetadata["memorySubType"] = memoryType.secondary ?: "unknown"
        enhancedMetadata["typeConfidence"] = memoryType.confidence
        enhancedMetadata["classificationAttributes"] = memoryType.attributes
        enhancedMetadata["importance"] = assessImportance(rawMemory.content, memoryType.primary)
        enhancedMetadata["processingTimestamp"] = System.currentTimeMillis()
        
        // Add semantic enrichment
        enhancedMetadata["contentAnalysis"] = analyzeContent(rawMemory.content)
        
        return rawMemory.copy(
            metadata = enhancedMetadata
        )
    }
    
    /**
     * Enhanced importance assessment based on content and type
     */
    private fun assessImportance(content: String, memoryType: String): String {
        var score = 0
        
        // Content-based scoring
        when {
            content.contains("IMPORTANT", ignoreCase = true) || 
            content.contains("URGENT", ignoreCase = true) || 
            content.contains("CRITICAL", ignoreCase = true) -> score += 3
            
            content.contains("TODO", ignoreCase = true) || 
            content.contains("FIXME", ignoreCase = true) -> score += 2
            
            content.length > 1000 -> score += 2
            content.length > 500 -> score += 1
        }
        
        // Type-based scoring
        when (memoryType) {
            "meeting" -> score += 2 // Meetings are generally important
            "project" -> score += 2 // Project docs are important
            "task" -> score += 1 // Tasks have moderate importance
            "code" -> score += 1 // Code snippets are useful
            "documentation" -> score += 1 // Docs are reference material
        }
        
        // Structure-based scoring
        val lines = content.lines()
        if (lines.any { it.trim().startsWith("#") }) score += 1 // Has headings
        if (lines.any { it.contains("http") }) score += 1 // Has references
        
        return when {
            score >= 5 -> "high"
            score >= 3 -> "medium"
            else -> "low"
        }
    }
    
    /**
     * Analyzes content structure and extracts key features
     */
    private fun analyzeContent(content: String): Map<String, Any> {
        val lines = content.lines()
        
        return mapOf(
            "wordCount" to content.split("\\s+".toRegex()).size,
            "lineCount" to lines.size,
            "hasHeadings" to lines.any { it.trim().startsWith("#") },
            "hasLinks" to (content.contains("http") || (content.contains("[") && content.contains("]"))),
            "hasCodeBlocks" to content.contains("```"),
            "hasLists" to lines.any { it.trim().startsWith("-") || it.trim().startsWith("*") },
            "hasCheckboxes" to lines.any { it.contains("[ ]") || it.contains("[x]") },
            "language" to detectLanguage(content),
            "extractedKeywords" to extractKeywords(content, 5)
        )
    }
    
    private fun detectLanguage(content: String): String {
        // Simple language detection based on common patterns
        return when {
            content.contains(Regex("[а-яё]", RegexOption.IGNORE_CASE)) -> "russian"
            content.contains(Regex("[a-z]", RegexOption.IGNORE_CASE)) -> "english"
            else -> "unknown"
        }
    }
    
    private fun extractKeywords(content: String, limit: Int): List<String> {
        // Simple keyword extraction (in production, use TF-IDF or more sophisticated methods)
        val stopWords = setOf("the", "is", "at", "which", "on", "and", "a", "an", "to", "in", "for", "of", "with", "by")
        
        return content
            .lowercase()
            .split(Regex("[^a-zа-яё]+"))
            .filter { it.length > 3 && !stopWords.contains(it) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
    
    /**
     * Agent's criteria for what's worth remembering
     */
    private fun isWorthRemembering(content: String): Boolean {
        if (content.isBlank() || content.length < 20) return false
        
        // Skip empty templates
        val lines = content.lines()
        if (lines.all { it.startsWith("#") || it.isBlank() }) return false
        
        // Skip daily notes that are just task lists
        if (content.contains("## Tasks") && content.length < 100) return false
        
        return true
    }
    
    override fun canAccessSource(): Boolean {
        return obsidianTool.isAvailable()
    }
    
    override fun getSourceStatus(): SourceStatus {
        val toolStatus = obsidianTool.getStatus()
        val health = when {
            !toolStatus.isActive -> "vault_not_found"
            lastSyncTime == null -> "never_synced"
            else -> "healthy"
        }
        
        return SourceStatus(
            sourceType = "obsidian",
            isAccessible = toolStatus.isActive,
            lastSync = lastSyncTime,
            itemCount = totalMemoriesFormed,
            health = health
        )
    }
}