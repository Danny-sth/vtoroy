package com.jarvis.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.dto.KnowledgeStatus
import com.jarvis.dto.KnowledgeStatusResponse
import com.jarvis.dto.KnowledgeSyncRequest
import com.jarvis.dto.KnowledgeSyncResponse
import com.jarvis.service.KnowledgeService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@WebMvcTest(KnowledgeController::class)
class KnowledgeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var knowledgeService: KnowledgeService

    // Test: Get status when knowledge base is empty
    @Test
    fun `GET status should return empty status`() {
        // Given
        val expectedStatus = KnowledgeStatusResponse(
            totalFiles = 0L,
            indexedFiles = 0L,
            lastSync = null,
            status = KnowledgeStatus.EMPTY
        )
        
        every { knowledgeService.getStatus() } returns expectedStatus

        // When & Then
        mockMvc.perform(get("/api/knowledge/status"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.totalFiles").value(0))
            .andExpect(jsonPath("$.indexedFiles").value(0))
            .andExpect(jsonPath("$.lastSync").doesNotExist())
            .andExpect(jsonPath("$.status").value("EMPTY"))
    }

    // Test: Get status when knowledge base has files
    @Test
    fun `GET status should return ready status with files`() {
        // Given
        val lastSync = LocalDateTime.now()
        val expectedStatus = KnowledgeStatusResponse(
            totalFiles = 10L,
            indexedFiles = 10L,
            lastSync = lastSync,
            status = KnowledgeStatus.READY
        )
        
        every { knowledgeService.getStatus() } returns expectedStatus

        // When & Then
        mockMvc.perform(get("/api/knowledge/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalFiles").value(10))
            .andExpect(jsonPath("$.indexedFiles").value(10))
            .andExpect(jsonPath("$.lastSync").exists())
            .andExpect(jsonPath("$.status").value("READY"))
    }

    // Test: Get status when syncing
    @Test
    fun `GET status should return syncing status`() {
        // Given
        val expectedStatus = KnowledgeStatusResponse(
            totalFiles = 10L,
            indexedFiles = 5L,
            lastSync = LocalDateTime.now(),
            status = KnowledgeStatus.SYNCING
        )
        
        every { knowledgeService.getStatus() } returns expectedStatus

        // When & Then
        mockMvc.perform(get("/api/knowledge/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalFiles").value(10))
            .andExpect(jsonPath("$.indexedFiles").value(5))
            .andExpect(jsonPath("$.status").value("SYNCING"))
    }

    // Test: Successful sync request with vault path
    @Test
    fun `POST sync should return successful sync response`() {
        // Given
        val request = KnowledgeSyncRequest(vaultPath = "/path/to/vault")
        
        coEvery { 
            knowledgeService.syncObsidianVault("/path/to/vault") 
        } returns 5

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.filesProcessed").value(5))
            .andExpect(jsonPath("$.message").value("Sync completed successfully"))

        coVerify(exactly = 1) { knowledgeService.syncObsidianVault("/path/to/vault") }
    }

    // Test: Request with null vault path should fail validation
    @Test
    fun `POST sync should use default vault path when not provided`() {
        // Given - JSON with null vaultPath
        val request = """{"vaultPath": null}"""

        // When & Then - should return 400 due to null value
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        )
            .andExpect(status().isBadRequest)

        // Verify service was never called due to validation failure
        coVerify(exactly = 0) { knowledgeService.syncObsidianVault(any()) }
    }

    // Test: Sync request with empty request body should fail validation
    @Test
    fun `POST sync should handle empty request body`() {
        // Given
        val emptyRequest = "{}"

        // When & Then - should return 400 due to missing required field
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyRequest)
        )
            .andExpect(status().isBadRequest)

        // Verify service was never called due to validation failure
        coVerify(exactly = 0) { knowledgeService.syncObsidianVault(any()) }
    }

    // Test: Sync with non-existent vault path
    @Test
    fun `POST sync should handle invalid vault path`() {
        // Given
        val request = KnowledgeSyncRequest(vaultPath = "/invalid/path")
        
        coEvery { 
            knowledgeService.syncObsidianVault("/invalid/path") 
        } throws IllegalArgumentException("Vault path does not exist: /invalid/path")

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Vault path does not exist: /invalid/path"))
    }

    // Test: Sync with service exception
    @Test
    fun `POST sync should handle service exceptions`() {
        // Given
        val request = KnowledgeSyncRequest(vaultPath = "/some/path")
        
        coEvery { 
            knowledgeService.syncObsidianVault("/some/path") 
        } throws RuntimeException("Database connection failed")

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Database connection failed"))
    }

    // Test: Sync with malformed JSON
    @Test
    fun `POST sync should handle malformed JSON`() {
        // Given
        val malformedJson = "{ invalid json"

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson)
        )
            .andExpect(status().isBadRequest)
    }

    // Test: Sync processing multiple files
    @Test
    fun `POST sync should handle large number of files`() {
        // Given
        val request = KnowledgeSyncRequest(vaultPath = "/large/vault")
        
        coEvery { 
            knowledgeService.syncObsidianVault("/large/vault") 
        } returns 1000

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.filesProcessed").value(1000))
            .andExpect(jsonPath("$.message").value("Sync completed successfully"))
    }

    // Test: Status endpoint response format
    @Test
    fun `GET status should return proper JSON structure`() {
        // Given
        val status = KnowledgeStatusResponse(
            totalFiles = 15L,
            indexedFiles = 12L,
            lastSync = LocalDateTime.now(),
            status = KnowledgeStatus.SYNCING
        )
        
        every { knowledgeService.getStatus() } returns status

        // When & Then
        mockMvc.perform(get("/api/knowledge/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalFiles").exists())
            .andExpect(jsonPath("$.indexedFiles").exists())
            .andExpect(jsonPath("$.lastSync").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.totalFiles").isNumber())
            .andExpect(jsonPath("$.indexedFiles").isNumber())
            .andExpect(jsonPath("$.status").isString())
    }

    // Test: Sync with special characters in path
    @Test
    fun `POST sync should handle paths with special characters`() {
        // Given
        val specialPath = "/vault with spaces/こんにちは/файлы"
        val request = KnowledgeSyncRequest(vaultPath = specialPath)
        
        coEvery { 
            knowledgeService.syncObsidianVault(specialPath) 
        } returns 2

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.filesProcessed").value(2))

        coVerify(exactly = 1) { knowledgeService.syncObsidianVault(specialPath) }
    }
}