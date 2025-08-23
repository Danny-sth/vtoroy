package com.jarvis.agent

import com.jarvis.service.knowledge.ObsidianVaultManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.prompt.Prompt

class ObsidianAgentTest {

    private lateinit var obsidianAgent: ObsidianAgent
    private lateinit var mockVaultManager: ObsidianVaultManager
    private lateinit var mockChatModel: AnthropicChatModel
    
    @BeforeEach
    fun setup() {
        mockVaultManager = mockk()
        mockChatModel = mockk()
        
        obsidianAgent = ObsidianAgent("/test/vault", mockVaultManager, mockChatModel)
    }
    
    @Test
    fun `agent should have correct name and description`() {
        assertThat(obsidianAgent.name).isEqualTo("obsidian-manager")
        assertThat(obsidianAgent.description).contains("Obsidian")
        assertThat(obsidianAgent.description).contains("notes")
    }
    
    @Test
    fun `canHandle should return true for Obsidian queries`() = runTest {
        // Given
        every { mockChatModel.call(any<Prompt>()) } returns ChatResponse(listOf(
            Generation(AssistantMessage("true"))
        ))
        
        // When
        val result = obsidianAgent.canHandle("создай заметку")
        
        // Then
        assertThat(result).isTrue()
    }
    
    @Test
    fun `canHandle should return false for non-Obsidian queries`() = runTest {
        // Given
        every { mockChatModel.call(any<Prompt>()) } returns ChatResponse(listOf(
            Generation(AssistantMessage("false"))
        ))
        
        // When
        val result = obsidianAgent.canHandle("какая погода")
        
        // Then
        assertThat(result).isFalse()
    }
    
    @Test
    fun `isAvailable should check vault manager`() = runTest {
        // Given
        coEvery { mockVaultManager.listFolders() } returns mockk<com.jarvis.dto.ObsidianResult>()
        
        // When
        val result = obsidianAgent.isAvailable()
        
        // Then
        assertThat(result).isTrue()
        coVerify { mockVaultManager.listFolders() }
    }
    
    @Test
    fun `isAvailable should return false when vault not accessible`() = runTest {
        // Given
        coEvery { mockVaultManager.listFolders() } throws RuntimeException("Vault not found")
        
        // When
        val result = obsidianAgent.isAvailable()
        
        // Then
        assertThat(result).isFalse()
    }
}