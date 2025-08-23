package com.vtoroy.service

import com.vtoroy.service.knowledge.contract.KnowledgeSource
import com.vtoroy.service.knowledge.contract.KnowledgeSourceStatus
import com.vtoroy.service.knowledge.contract.KnowledgeItem
import com.vtoroy.repository.KnowledgeFileRepository
import com.vtoroy.entity.KnowledgeFile
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
 * Knowledge Service - управляет источниками знаний и синхронизацией с векторной БД
 * Аналог Claude Code MCP Server Manager
 */
@Service
class KnowledgeService(
    private val knowledgeFileRepository: KnowledgeFileRepository,
    @Qualifier("customEmbeddingModel") private val embeddingModel: EmbeddingModel,
    private val knowledgeSources: List<KnowledgeSource> // Auto-wired list of all knowledge sources
) {
    private val logger = KotlinLogging.logger {}
    
    // Cache for query embeddings - avoids recalculating identical queries
    private val queryEmbeddingCache = ConcurrentHashMap<String, FloatArray>()
    
    init {
        logger.info { "KnowledgeService initialized with ${knowledgeSources.size} sources: ${knowledgeSources.map { it.sourceId }}" }
    }
    
    /**
     * Получает статусы всех источников знаний
     */
    suspend fun getSourceStatuses(): Map<String, KnowledgeSourceStatus> {
        return knowledgeSources.associate { source ->
            try {
                source.sourceId to source.getStatus()
            } catch (e: Exception) {
                logger.error(e) { "Failed to get status for source ${source.sourceId}" }
                source.sourceId to KnowledgeSourceStatus(
                    sourceId = source.sourceId,
                    isActive = false,
                    errorMessage = e.message
                )
            }
        }
    }
    
    /**
     * Синхронизирует данные со всех доступных источников знаний
     * Аналог Claude Code MCP sync
     */
    suspend fun syncAllSources(): Int {
        val startTime = System.currentTimeMillis()
        
        val availableSources = knowledgeSources.filter { source ->
            try {
                source.isAvailable()
            } catch (e: Exception) {
                logger.warn(e) { "Source ${source.sourceId} availability check failed" }
                false
            }
        }
        
        if (availableSources.isEmpty()) {
            logger.warn { "No available knowledge sources found" }
            return 0
        }
        
        logger.info { "Starting sync with ${availableSources.size} sources" }
        
        var totalItemsIndexed = 0
        
        for (source in availableSources) {
            try {
                logger.info { "Syncing data from ${source.sourceId}" }
                val items = source.syncData()
                
                val indexedCount = indexKnowledgeItems(items)
                totalItemsIndexed += indexedCount
                
                logger.info { "Source ${source.sourceId} synced $indexedCount items" }
            } catch (e: Exception) {
                logger.error(e) { "Error syncing from source ${source.sourceId}" }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        logger.info { "Sync completed: $totalItemsIndexed items in ${duration}ms" }
        
        return totalItemsIndexed
    }
    
    /**
     * Индексирует элементы знаний в векторную БД
     */
    @Transactional
    private suspend fun indexKnowledgeItems(items: List<KnowledgeItem>): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        
        var indexedCount = 0
        
        for (item in items) {
            try {
                val checksum = generateChecksum(item.content)
                
                // Check if item already exists with same checksum
                val existingFile = knowledgeFileRepository.findBySource(item.sourceId)
                    .find { it.sourceId == item.id && it.fileHash == checksum }
                
                if (existingFile == null) {
                    // Generate embedding
                    val embedding = embeddingModel.embed(item.content)
                    
                    val knowledgeFile = KnowledgeFile(
                        source = item.sourceId,
                        sourceId = item.id,
                        filePath = item.sourcePath,
                        content = item.content,
                        fileHash = checksum,
                        embedding = PGvector(embedding)
                    )
                    
                    knowledgeFileRepository.save(knowledgeFile)
                    indexedCount++
                } else {
                    logger.debug { "Skipping existing item: ${item.sourcePath}" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to index item: ${item.sourcePath}" }
            }
        }
        
        indexedCount
    }
    
    /**
     * Поиск в векторной базе знаний
     */
    suspend fun searchKnowledge(query: String, limit: Int = 10): List<KnowledgeFile> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val embedding = queryEmbeddingCache.getOrPut(generateChecksum(query)) {
                embeddingModel.embed(query)
            }
            
            knowledgeFileRepository.findSimilarDocuments(PGvector(embedding).toString(), limit)
        } catch (e: Exception) {
            logger.error(e) { "Error during knowledge search for query: $query" }
            emptyList()
        }
    }
    
    /**
     * Generates MD5 checksum for content
     */
    private fun generateChecksum(content: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(content.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}