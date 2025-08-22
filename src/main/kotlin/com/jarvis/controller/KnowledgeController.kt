package com.jarvis.controller

import com.jarvis.dto.KnowledgeSyncRequest
import com.jarvis.dto.KnowledgeSyncSourceRequest
import com.jarvis.dto.KnowledgeSearchRequest
import com.jarvis.dto.KnowledgeStatusResponse
import com.jarvis.service.KnowledgeService
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = ["*"])
class KnowledgeController(
    private val knowledgeService: KnowledgeService
) {
    private val logger = KotlinLogging.logger {}
    
    @PostMapping("/sync")
    fun syncKnowledge(@RequestBody request: KnowledgeSyncRequest): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Starting knowledge sync for source: ${request.sourceId}, config: ${request.getEffectiveConfig()}" }
        
        return@runBlocking try {
            val result = knowledgeService.syncSource(request.sourceId, request.getEffectiveConfig())
            ResponseEntity.ok(mapOf(
                "message" to "Sync completed successfully",
                "filesProcessed" to result
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error syncing knowledge" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Sync failed",
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }
    
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<KnowledgeStatusResponse> {
        return try {
            val status = knowledgeService.getStatus()
            ResponseEntity.ok(status)
        } catch (e: Exception) {
            logger.error(e) { "Error getting knowledge status" }
            ResponseEntity.internalServerError().build()
        }
    }
    
    @PostMapping("/sync/source")
    fun syncSource(@Valid @RequestBody request: KnowledgeSyncSourceRequest): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Starting knowledge sync for source: ${request.sourceId}" }
        
        return@runBlocking try {
            val result = knowledgeService.syncSource(request.sourceId, request.config)
            ResponseEntity.ok(mapOf(
                "message" to "Sync completed successfully",
                "sourceId" to request.sourceId,
                "itemsProcessed" to result
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error syncing source ${request.sourceId}" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Sync failed",
                "message" to (e.message ?: "Unknown error"),
                "sourceId" to request.sourceId
            ))
        }
    }
    
    @GetMapping("/sources")
    fun getSources(): ResponseEntity<Map<String, Any>> {
        return try {
            val sources = knowledgeService.getAvailableSources()
            ResponseEntity.ok(mapOf(
                "sources" to sources,
                "totalSources" to sources.size
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting sources" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Failed to get sources",
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }
    
    @GetMapping("/sources/{sourceId}/status")
    fun getSourceStatus(@PathVariable sourceId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val status = knowledgeService.getSourceStatus(sourceId)
            ResponseEntity.ok(status)
        } catch (e: Exception) {
            logger.error(e) { "Error getting source status: $sourceId" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Failed to get source status",
                "message" to (e.message ?: "Unknown error"),
                "sourceId" to sourceId
            ))
        }
    }
    
    @GetMapping("/search")
    fun searchKnowledge(
        @RequestParam query: String,
        @RequestParam(defaultValue = "3") limit: Int,
        @RequestParam(required = false) source: String?
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Searching knowledge for query: '$query', limit: $limit, source: ${source ?: "all"}" }
        
        return@runBlocking try {
            val results = knowledgeService.searchKnowledge(query, limit, source)
            ResponseEntity.ok(mapOf(
                "query" to query,
                "source" to (source ?: "all"),
                "results" to results.map { file ->
                    mapOf(
                        "source" to file.source,
                        "filePath" to file.filePath,
                        "content" to file.content.take(200) + "...", // First 200 chars
                        "hasEmbedding" to (file.embedding != null)
                    )
                },
                "totalFound" to results.size
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error searching knowledge: ${e.message}" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Search failed",
                "message" to (e.message ?: "Unknown error"),
                "query" to query
            ))
        }
    }
    
    @PostMapping("/search")
    fun searchKnowledgePost(@Valid @RequestBody request: KnowledgeSearchRequest): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Searching knowledge for query: '${request.query}', limit: ${request.limit}, source: ${request.sourceFilter ?: "all"}" }
        
        return@runBlocking try {
            val results = knowledgeService.searchKnowledge(request.query, request.limit, request.sourceFilter)
            ResponseEntity.ok(mapOf(
                "query" to request.query,
                "source" to (request.sourceFilter ?: "all"),
                "results" to results.map { file ->
                    mapOf(
                        "source" to file.source,
                        "filePath" to file.filePath,
                        "content" to file.content.take(200) + "...", // First 200 chars
                        "hasEmbedding" to (file.embedding != null)
                    )
                },
                "totalFound" to results.size
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error searching knowledge: ${e.message}" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Search failed",
                "message" to (e.message ?: "Unknown error"),
                "query" to request.query
            ))
        }
    }
    
    @GetMapping("/test-anthropic")
    fun testAnthropicConnection(): ResponseEntity<Map<String, Any>> {
        logger.info { "Testing Anthropic API connection..." }
        
        return try {
            // Simple API test without complex logic
            ResponseEntity.ok(mapOf<String, String>(
                "status" to "API key configured",
                "keyPrefix" to (System.getenv("ANTHROPIC_API_KEY")?.take(20)?.plus("...") ?: "No key found")
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error testing Anthropic connection: ${e.message}" }
            ResponseEntity.internalServerError().body(mapOf(
                "error" to "Connection test failed",
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }
}