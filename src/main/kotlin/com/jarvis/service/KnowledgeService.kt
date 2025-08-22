package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.agent.contract.KnowledgeManageable
import com.jarvis.agent.contract.SourceStatus
import com.jarvis.service.knowledge.contract.KnowledgeItem
import com.jarvis.repository.KnowledgeFileRepository
import com.jarvis.dto.KnowledgeStatus
import com.jarvis.dto.KnowledgeStatusResponse
import com.jarvis.entity.KnowledgeFile
import com.pgvector.PGvector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing knowledge through specialized agents
 * Each agent manages its own knowledge source and forms memories
 */
@Service
class KnowledgeService(
    private val knowledgeFileRepository: KnowledgeFileRepository,
    @Qualifier("customEmbeddingModel") private val embeddingModel: EmbeddingModel,
    private val objectMapper: ObjectMapper,
    private val knowledgeAgents: List<KnowledgeManageable> // Auto-wired list of all knowledge agents
) {
    private val logger = KotlinLogging.logger {}
    
    // Cache for query embeddings - avoids recalculating identical queries
    private val queryEmbeddingCache = ConcurrentHashMap<String, FloatArray>()
    
    /**
     * Get all available knowledge agents and their status
     */
    fun getAvailableSources(): Map<String, SourceStatus> {
        return knowledgeAgents.associate { agent ->
            // Use agent class name as ID for now
            val agentId = agent::class.simpleName?.removeSuffix("Agent")?.lowercase() ?: "unknown"
            agentId to agent.getSourceStatus()
        }
    }
    
    /**
     * Sync knowledge through a specific agent
     * @param sourceId The ID of the agent/source to sync
     * @param config Agent-specific configuration
     * @return Number of items synced
     */
    @Transactional
    suspend fun syncSource(sourceId: String, config: Map<String, Any> = emptyMap()): Int = withContext(Dispatchers.IO) {
        val agent = knowledgeAgents.find { 
            val agentId = it::class.simpleName?.removeSuffix("Agent")?.lowercase() ?: "unknown"
            agentId == sourceId
        } ?: throw IllegalArgumentException("Unknown knowledge agent: $sourceId")
        
        logger.info { "Requesting agent ${agent::class.simpleName} to form memories" }
        
        if (!agent.canAccessSource()) {
            throw IllegalStateException("Agent ${agent::class.simpleName} cannot access its source")
        }
        
        val memories = agent.formMemories(config)
        var processedCount = 0
        
        memories.forEach { memory ->
            try {
                processKnowledgeItem(sourceId, memory)
                processedCount++
            } catch (e: Exception) {
                logger.error(e) { "Error processing memory ${memory.id} from agent ${agent::class.simpleName}" }
            }
        }
        
        logger.info { "Processed $processedCount memories from agent ${agent::class.simpleName}" }
        processedCount
    }
    
    /**
     * Sync Obsidian vault (backward compatibility)
     */
    
    private fun processKnowledgeItem(sourceId: String, item: KnowledgeItem) {
        val hash = calculateHash(item.content)
        
        // Check if item already exists and hasn't changed
        val existing = knowledgeFileRepository.findBySourceAndSourceId(sourceId, item.id)
        if (existing != null && existing.fileHash == hash) {
            logger.debug { "Item unchanged, skipping: ${item.id}" }
            return
        }
        
        // Generate embedding
        val embedding = generateEmbedding(item.content)
        
        // Prepare metadata with tags
        val metadata = item.metadata?.toMutableMap() ?: mutableMapOf()
        if (item.tags.isNotEmpty()) {
            metadata["tags"] = item.tags
        }
        
        // Create or update knowledge file
        val knowledgeFile = existing?.copy(
            content = item.content,
            embedding = PGvector(embedding),
            metadata = objectMapper.valueToTree(metadata),
            fileHash = hash,
            updatedAt = java.time.LocalDateTime.now()
        ) ?: KnowledgeFile(
            source = sourceId,
            sourceId = item.id,
            filePath = item.title, // Use title as file path for display
            content = item.content,
            embedding = PGvector(embedding),
            metadata = objectMapper.valueToTree(metadata),
            fileHash = hash
        )
        
        knowledgeFileRepository.save(knowledgeFile)
        logger.debug { "Processed item: ${item.id} from source: $sourceId" }
    }
    
    private fun generateEmbedding(text: String, useCache: Boolean = false): FloatArray {
        if (text.isBlank()) {
            return FloatArray(384) { 0f }
        }
        
        // Use cache only for queries (not for documents)
        if (useCache) {
            return queryEmbeddingCache.getOrPut(text) {
                logger.debug { "Generating cached embedding for query: '${text.take(50)}...'" }
                val startTime = System.currentTimeMillis()
                val result = embeddingModel.embed(text)
                val duration = System.currentTimeMillis() - startTime
                logger.info { "Generated embedding in ${duration}ms" }
                result
            }
        }
        
        return embeddingModel.embed(text)
    }
    
    private fun calculateHash(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get overall knowledge base status
     */
    fun getStatus(): KnowledgeStatusResponse {
        val totalFiles = knowledgeFileRepository.count()
        val indexedFiles = knowledgeFileRepository.findAll()
            .count { it.embedding != null }
        
        val lastFile = knowledgeFileRepository.findAll()
            .maxByOrNull { it.updatedAt }
        
        val status = when {
            totalFiles == 0L -> KnowledgeStatus.EMPTY
            indexedFiles < totalFiles -> KnowledgeStatus.SYNCING
            else -> KnowledgeStatus.READY
        }
        
        return KnowledgeStatusResponse(
            totalFiles = totalFiles,
            indexedFiles = indexedFiles.toLong(),
            lastSync = lastFile?.updatedAt,
            status = status
        )
    }
    
    /**
     * Get status for a specific agent
     */
    fun getSourceStatus(sourceId: String): Map<String, Any> {
        val agent = knowledgeAgents.find { 
            val agentId = it::class.simpleName?.removeSuffix("Agent")?.lowercase() ?: "unknown"
            agentId == sourceId
        } ?: throw IllegalArgumentException("Unknown knowledge agent: $sourceId")
        
        val itemCount = knowledgeFileRepository.countBySource(sourceId)
        val lastItem = knowledgeFileRepository.findBySource(sourceId)
            .maxByOrNull { it.updatedAt }
        
        return mapOf<String, Any>(
            "sourceId" to sourceId,
            "agentName" to (agent::class.simpleName ?: "Unknown"),
            "status" to agent.getSourceStatus(),
            "itemCount" to itemCount,
            "lastSync" to (lastItem?.updatedAt?.toString() ?: "Never")
        )
    }
    
    /**
     * Search knowledge across all sources or specific source
     */
    suspend fun searchKnowledge(
        query: String, 
        limit: Int = 5,
        sourceFilter: String? = null
    ): List<KnowledgeFile> = withContext(Dispatchers.IO) {
        logger.debug { "Starting knowledge search for: '$query' (source: ${sourceFilter ?: "all"})" }
        val startTime = System.currentTimeMillis()
        
        val queryEmbedding = generateEmbedding(query, useCache = true)
        val queryVector = PGvector(queryEmbedding)
        
        val results = if (sourceFilter != null) {
            knowledgeFileRepository.findSimilarDocumentsBySource(
                queryVector.toString(), 
                sourceFilter, 
                limit
            )
        } else {
            knowledgeFileRepository.findSimilarDocuments(queryVector.toString(), limit)
        }
        
        val duration = System.currentTimeMillis() - startTime
        logger.info { 
            "Knowledge search completed in ${duration}ms, found ${results.size} results" +
            if (sourceFilter != null) " from source: $sourceFilter" else ""
        }
        
        results
    }
}