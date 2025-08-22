package com.jarvis.service.knowledge.contract

/**
 * Interface for pluggable knowledge sources
 * Each implementation represents a different knowledge provider (Obsidian, Notion, etc.)
 */
interface KnowledgeSource {
    /**
     * Unique identifier for this knowledge source
     */
    val sourceId: String
    
    /**
     * Human-readable name for this source
     */
    val sourceName: String
    
    /**
     * Sync knowledge from this source
     * @param config Configuration specific to this source (e.g., vault path, API key, etc.)
     * @return List of knowledge items ready to be processed
     */
    suspend fun sync(config: Map<String, Any>): List<KnowledgeItem>
    
    /**
     * Check if this source is available and properly configured
     */
    fun isAvailable(): Boolean
    
    /**
     * Get source-specific status information
     */
    fun getStatus(): SourceStatus
}

/**
 * Represents a single piece of knowledge from any source
 */
data class KnowledgeItem(
    val id: String,           // Unique ID within the source
    val title: String,         // Document title
    val content: String,       // Cleaned text content
    val metadata: Map<String, Any>? = null,  // Source-specific metadata
    val tags: List<String> = emptyList(),    // Tags/categories
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Status information for a knowledge source
 */
data class SourceStatus(
    val sourceId: String,
    val isActive: Boolean,
    val lastSyncTime: Long? = null,
    val itemCount: Int = 0,
    val errorMessage: String? = null
)