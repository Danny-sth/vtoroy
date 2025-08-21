package com.jarvis.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.dto.ChatRequest
import com.jarvis.dto.ChatResponse
import com.jarvis.service.JarvisService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@WebMvcTest(ChatController::class)
class ChatControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jarvisService: JarvisService

    // Test: Successful chat request
    @Test
    fun `POST chat should return successful response`() {
        // Given
        val request = ChatRequest(
            query = "Hello, Jarvis!",
            sessionId = "test-session"
        )
        
        val expectedResponse = ChatResponse(
            response = "Hello! How can I help you today?",
            sessionId = "test-session",
            timestamp = LocalDateTime.now(),
            metadata = mapOf("test" to "data")
        )
        
        coEvery { 
            jarvisService.chat(request.query, request.sessionId) 
        } returns expectedResponse

        // When & Then
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.response").value(expectedResponse.response))
            .andExpect(jsonPath("$.sessionId").value(expectedResponse.sessionId))
            .andExpect(jsonPath("$.metadata.test").value("data"))

        coVerify(exactly = 1) { jarvisService.chat(request.query, request.sessionId) }
    }

    // Test: Chat request with missing required fields
    @Test
    fun `POST chat should return bad request for invalid input`() {
        // Given
        val invalidRequest = """
            {
                "sessionId": "test-session"
            }
        """.trimIndent()

        // When & Then
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
        )
            .andExpect(status().isBadRequest)

        coVerify(exactly = 0) { jarvisService.chat(any(), any()) }
    }

    // Test: Chat request with empty query should return validation error
    @Test
    fun `POST chat should handle empty query`() {
        // Given
        val request = ChatRequest(
            query = "",
            sessionId = "test-session"
        )

        // When & Then - should return 400 due to @NotBlank validation
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)

        // Verify service was never called due to validation failure
        coVerify(exactly = 0) { jarvisService.chat(any(), any()) }
    }

    // Test: Chat request with missing sessionId should return validation error
    @Test
    fun `POST chat should handle null sessionId`() {
        // Given - JSON without sessionId field
        val request = """
            {
                "query": "Hello!"
            }
        """.trimIndent()

        // When & Then - should return 400 due to missing required field
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isBadRequest)

        // Verify service was never called due to validation failure
        coVerify(exactly = 0) { jarvisService.chat(any(), any()) }
    }

    // Test: Handle service exception gracefully
    @Test
    fun `POST chat should handle service exceptions`() {
        // Given
        val request = ChatRequest(
            query = "Cause an error",
            sessionId = "test-session"
        )
        
        coEvery { 
            jarvisService.chat(any(), any()) 
        } throws RuntimeException("Service error")

        val errorResponse = ChatResponse(
            response = "I encountered an error processing your request. Please try again.",
            sessionId = request.sessionId,
            timestamp = LocalDateTime.now(),
            metadata = mapOf("error" to "Service error")
        )
        
        coEvery { 
            jarvisService.chat(request.query, request.sessionId) 
        } returns errorResponse

        // When & Then
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.response").value(errorResponse.response))
            .andExpect(jsonPath("$.metadata.error").value("Service error"))
    }

    // Test: Validate response format
    @Test
    fun `POST chat should return proper response format`() {
        // Given
        val request = ChatRequest(
            query = "Test query",
            sessionId = "session-123"
        )
        
        val timestamp = LocalDateTime.now()
        val expectedResponse = ChatResponse(
            response = "Test response",
            sessionId = "session-123",
            timestamp = timestamp,
            metadata = mapOf(
                "usage" to mapOf(
                    "promptTokens" to 10,
                    "generationTokens" to 5,
                    "totalTokens" to 15
                )
            )
        )
        
        coEvery { 
            jarvisService.chat(request.query, request.sessionId) 
        } returns expectedResponse

        // When & Then
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.response").exists())
            .andExpect(jsonPath("$.sessionId").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.metadata").exists())
            .andExpect(jsonPath("$.metadata.usage.promptTokens").value(10))
            .andExpect(jsonPath("$.metadata.usage.generationTokens").value(5))
            .andExpect(jsonPath("$.metadata.usage.totalTokens").value(15))
    }

    // Test: Large message handling
    @Test
    fun `POST chat should handle large messages`() {
        // Given
        val largeQuery = "A".repeat(10000) // 10K characters
        val request = ChatRequest(
            query = largeQuery,
            sessionId = "test-session"
        )
        
        val expectedResponse = ChatResponse(
            response = "I received your long message.",
            sessionId = "test-session",
            timestamp = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        coEvery { 
            jarvisService.chat(largeQuery, request.sessionId) 
        } returns expectedResponse

        // When & Then
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.response").value(expectedResponse.response))
    }

    // Test: Concurrent session handling
    @Test
    fun `POST chat should handle different sessions independently`() {
        // Given
        val request1 = ChatRequest(query = "Hello", sessionId = "session-1")
        val request2 = ChatRequest(query = "Hi", sessionId = "session-2")
        
        val response1 = ChatResponse(
            response = "Response for session 1",
            sessionId = "session-1",
            timestamp = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        val response2 = ChatResponse(
            response = "Response for session 2",
            sessionId = "session-2",
            timestamp = LocalDateTime.now(),
            metadata = emptyMap()
        )
        
        coEvery { jarvisService.chat("Hello", "session-1") } returns response1
        coEvery { jarvisService.chat("Hi", "session-2") } returns response2

        // When & Then - Session 1
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.response").value("Response for session 1"))
            .andExpect(jsonPath("$.sessionId").value("session-1"))

        // When & Then - Session 2
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.response").value("Response for session 2"))
            .andExpect(jsonPath("$.sessionId").value("session-2"))

        coVerify(exactly = 1) { jarvisService.chat("Hello", "session-1") }
        coVerify(exactly = 1) { jarvisService.chat("Hi", "session-2") }
    }
}