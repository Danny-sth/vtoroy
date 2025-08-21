package com.jarvis.controller

import com.jarvis.dto.KnowledgeSyncRequest
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
    fun syncKnowledge(@Valid @RequestBody request: KnowledgeSyncRequest): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Starting knowledge sync from path: ${request.vaultPath}" }
        
        return@runBlocking try {
            val result = knowledgeService.syncObsidianVault(request.vaultPath)
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
    
    @GetMapping("/search")
    fun searchKnowledge(
        @RequestParam query: String,
        @RequestParam(defaultValue = "3") limit: Int
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Searching knowledge for query: '$query', limit: $limit" }
        
        return@runBlocking try {
            val results = knowledgeService.searchKnowledge(query, limit)
            ResponseEntity.ok(mapOf(
                "query" to query,
                "results" to results.map { file ->
                    mapOf(
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