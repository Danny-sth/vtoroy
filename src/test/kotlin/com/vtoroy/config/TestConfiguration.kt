package com.vtoroy.config

import com.vtoroy.agent.ObsidianAgent
import com.vtoroy.service.VtoroyService
import io.mockk.mockk
import io.mockk.coEvery
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.Usage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

@TestConfiguration
@Profile("test")
class TestConfiguration {
    
    @Bean
    @Primary
    fun mockAnthropicChatModel(): AnthropicChatModel {
        val mockChatModel = mockk<AnthropicChatModel>()
        
        // Mock response for chat calls
        val mockResponse = ChatResponse(
            listOf(
                Generation(
                    "Hello! This is a mock response from Jarvis for testing purposes."
                )
            )
        )
        
        coEvery { mockChatModel.call(any<Prompt>()) } returns mockResponse
        
        return mockChatModel
    }
    
}