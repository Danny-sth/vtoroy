package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.jarvis.dto.KnowledgeStatus
import com.jarvis.dto.KnowledgeStatusResponse
import com.jarvis.entity.KnowledgeFile
import com.jarvis.repository.KnowledgeFileRepository
import com.pgvector.PGvector
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

@Service
class KnowledgeService(
    private val knowledgeFileRepository: KnowledgeFileRepository,
    @Qualifier("customEmbeddingModel") private val embeddingModel: EmbeddingModel,
    private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}
    
    // Кеш для query embeddings - избегаем пересчета одинаковых запросов
    private val queryEmbeddingCache = ConcurrentHashMap<String, FloatArray>()
    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val markdownParser: Parser
    
    @Value("\${jarvis.obsidian.vault-path}")
    private lateinit var defaultVaultPath: String
    
    init {
        val options = MutableDataSet()
        markdownParser = Parser.builder(options).build()
    }
    
    @Transactional
    suspend fun syncObsidianVault(vaultPath: String? = null): Int = withContext(Dispatchers.IO) {
        val path = Paths.get(vaultPath ?: defaultVaultPath)
        
        if (!Files.exists(path)) {
            throw IllegalArgumentException("Vault path does not exist: $path")
        }
        
        var processedCount = 0
        
        Files.walk(path)
            .filter { it.isRegularFile() && it.extension == "md" }
            .forEach { filePath ->
                try {
                    processMarkdownFile(filePath)
                    processedCount++
                } catch (e: Exception) {
                    logger.error(e) { "Error processing file: $filePath" }
                }
            }
        
        logger.info { "Synced $processedCount files from Obsidian vault" }
        processedCount
    }
    
    private fun processMarkdownFile(filePath: Path) {
        val content = Files.readString(filePath)
        val hash = calculateHash(content)
        val relativePath = filePath.pathString
        
        // Check if file already exists and hasn't changed
        val existing = knowledgeFileRepository.findByFilePath(relativePath)
        if (existing.isPresent && existing.get().fileHash == hash) {
            logger.debug { "File unchanged, skipping: $relativePath" }
            return
        }
        
        // Extract frontmatter and content
        val (frontmatter, markdownContent) = extractFrontmatter(content)
        
        // Clean markdown content
        val cleanedContent = cleanMarkdown(markdownContent)
        
        // Generate embedding
        val embedding = generateEmbedding(cleanedContent)
        
        // Create or update knowledge file
        val knowledgeFile = existing.orElse(null)?.copy(
            content = cleanedContent,
            embedding = PGvector(embedding),
            metadata = frontmatter?.let { objectMapper.valueToTree(it) },
            fileHash = hash
        ) ?: KnowledgeFile(
            filePath = relativePath,
            content = cleanedContent,
            embedding = PGvector(embedding),
            metadata = frontmatter?.let { objectMapper.valueToTree(it) },
            fileHash = hash
        )
        
        knowledgeFileRepository.save(knowledgeFile)
        logger.debug { "Processed file: $relativePath" }
    }
    
    private fun extractFrontmatter(content: String): Pair<Map<String, Any>?, String> {
        if (!content.startsWith("---")) {
            return null to content
        }
        
        val lines = content.lines()
        val endIndex = lines.drop(1).indexOfFirst { it == "---" }
        
        if (endIndex == -1) {
            return null to content
        }
        
        val frontmatterContent = lines.subList(1, endIndex + 1).joinToString("\n")
        val markdownContent = lines.drop(endIndex + 2).joinToString("\n")
        
        return try {
            val frontmatter = yamlMapper.readValue(frontmatterContent, Map::class.java) as Map<String, Any>
            frontmatter to markdownContent
        } catch (e: Exception) {
            logger.warn { "Failed to parse frontmatter: ${e.message}" }
            null to content
        }
    }
    
    private fun cleanMarkdown(content: String): String {
        // Remove Obsidian-specific syntax while keeping content readable
        return content
            .replace(Regex("\\[\\[([^\\]]+)\\]\\]")) { match ->
                match.groupValues[1].split("|").last()
            }
            .replace(Regex("!\\[\\[([^\\]]+)\\]\\]"), "[$1]")
            .trim()
    }
    
    private fun generateEmbedding(text: String, useCache: Boolean = false): FloatArray {
        if (text.isBlank()) {
            return FloatArray(384) { 0f }
        }
        
        // Используем кеш только для query (не для документов)
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
    
    suspend fun searchKnowledge(query: String, limit: Int = 5): List<KnowledgeFile> = withContext(Dispatchers.IO) {
        logger.debug { "Starting knowledge search for: '$query'" }
        val startTime = System.currentTimeMillis()
        
        val queryEmbedding = generateEmbedding(query, useCache = true) // Включаем кеш для запросов
        val queryVector = PGvector(queryEmbedding)
        
        val results = knowledgeFileRepository.findSimilarDocuments(queryVector.toString(), limit)
        
        val duration = System.currentTimeMillis() - startTime
        logger.info { "Knowledge search completed in ${duration}ms, found ${results.size} results" }
        
        results
    }
}