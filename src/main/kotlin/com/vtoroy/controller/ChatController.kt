package com.vtoroy.controller

import com.vtoroy.dto.ChatRequest
import com.vtoroy.dto.ChatResponse
import com.vtoroy.service.VtoroyService
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = ["*"])
class ChatController(
    private val vtoroyService: VtoroyService
) {
    private val logger = KotlinLogging.logger {}
    
    @PostMapping
    fun chat(@Valid @RequestBody request: ChatRequest): ResponseEntity<ChatResponse> = runBlocking {
        logger.debug { "Received chat request for session: ${request.sessionId}" }
        
        return@runBlocking try {
            val response = vtoroyService.chat(request.query, request.sessionId)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error(e) { "Error processing chat request" }
            ResponseEntity.internalServerError().body(
                ChatResponse(
                    response = "I encountered an error processing your request. Please try again.",
                    sessionId = request.sessionId,
                    metadata = mapOf("error" to (e.message ?: "Unknown error"))
                )
            )
        }
    }
}