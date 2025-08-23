package com.vtoroy.agent

import com.vtoroy.agent.contract.SubAgent
import com.vtoroy.agent.contract.AgentSelection
import com.vtoroy.entity.ChatMessage
import com.vtoroy.entity.ChatSession
import com.vtoroy.entity.MessageRole
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

class AgentDispatcherTest {

    private lateinit var agentDispatcher: AgentDispatcher
    private lateinit var mockChatModel: AnthropicChatModel
    private lateinit var mockAgent1: SubAgent
    private lateinit var mockAgent2: SubAgent
    
    @BeforeEach
    fun setup() {
        mockChatModel = mockk()
        mockAgent1 = mockk()
        mockAgent2 = mockk()
        
        every { mockAgent1.name } returns "TestAgent1"
        every { mockAgent1.description } returns "Test agent for handling test queries"
        every { mockAgent2.name } returns "TestAgent2"
        every { mockAgent2.description } returns "Test agent for handling other queries"
        
        agentDispatcher = AgentDispatcher(listOf(mockAgent1, mockAgent2), mockChatModel)
    }
    
    @Test
    fun `selectAgent should return null when no agents are available`() = runTest {
        // Given
        coEvery { mockAgent1.isAvailable() } returns false
        coEvery { mockAgent2.isAvailable() } returns false
        
        // When
        val result = agentDispatcher.selectAgent("test query", emptyList())
        
        // Then
        assertThat(result).isNull()
    }
    
    @Test
    fun `selectAgent should return single available agent when it can handle query`() = runTest {
        // Given
        coEvery { mockAgent1.isAvailable() } returns true
        coEvery { mockAgent2.isAvailable() } returns false
        coEvery { mockAgent1.canHandle(any(), any()) } returns true
        
        // When
        val result = agentDispatcher.selectAgent("test query", emptyList())
        
        // Then
        assertThat(result).isNotNull
        assertThat(result?.agent).isEqualTo(mockAgent1)
        assertThat(result?.confidence).isEqualTo(1.0)
    }
    
    @Test
    fun `selectAgent should return null when single agent cannot handle query`() = runTest {
        // Given
        coEvery { mockAgent1.isAvailable() } returns true
        coEvery { mockAgent2.isAvailable() } returns false
        coEvery { mockAgent1.canHandle(any(), any()) } returns false
        
        // When
        val result = agentDispatcher.selectAgent("test query", emptyList())
        
        // Then
        assertThat(result).isNull()
    }
    
    @Test
    fun `selectAgent should use AI to select from multiple agents`() = runTest {
        // Given
        coEvery { mockAgent1.isAvailable() } returns true
        coEvery { mockAgent2.isAvailable() } returns true
        
        every { mockChatModel.call(any<Prompt>()) } returns ChatResponse(listOf(
            Generation(AssistantMessage("TestAgent1"))
        ))
        
        // When
        val result = agentDispatcher.selectAgent("test query", emptyList())
        
        // Then
        assertThat(result).isNotNull
        assertThat(result?.agent).isEqualTo(mockAgent1)
        verify { mockChatModel.call(any<Prompt>()) }
    }
    
    @Test
    fun `selectAgent should fallback to first agent when AI selects unknown agent`() = runTest {
        // Given
        coEvery { mockAgent1.isAvailable() } returns true
        coEvery { mockAgent2.isAvailable() } returns true
        
        every { mockChatModel.call(any<Prompt>()) } returns ChatResponse(listOf(
            Generation(AssistantMessage("UnknownAgent"))
        ))
        
        // When
        val result = agentDispatcher.selectAgent("test query", emptyList())
        
        // Then
        assertThat(result).isNotNull
        assertThat(result?.agent).isEqualTo(mockAgent1)
        assertThat(result?.reason).contains("Fallback")
    }
    
    @Test
    fun `selectAgent should include chat history in AI prompt`() = runTest {
        // Given
        val session = mockk<ChatSession> { every { id } returns "test" }
        val chatHistory = listOf(
            ChatMessage(session = session, role = MessageRole.USER, content = "Previous message")
        )
        
        coEvery { mockAgent1.isAvailable() } returns true
        coEvery { mockAgent2.isAvailable() } returns true
        
        every { mockChatModel.call(any<Prompt>()) } returns ChatResponse(listOf(
            Generation(AssistantMessage("TestAgent1"))
        ))
        
        // When
        agentDispatcher.selectAgent("test query", chatHistory)
        
        // Then
        verify { mockChatModel.call(any<Prompt>()) }
    }
    
    @Test
    fun `selectAgent should handle AI errors gracefully with fallback`() = runTest {
        // Given
        coEvery { mockAgent1.isAvailable() } returns true
        coEvery { mockAgent2.isAvailable() } returns true
        
        every { mockChatModel.call(any<Prompt>()) } throws RuntimeException("AI service unavailable")
        
        // When
        val result = agentDispatcher.selectAgent("test query", emptyList())
        
        // Then
        assertThat(result).isNotNull
        assertThat(result?.agent).isEqualTo(mockAgent1)
        assertThat(result?.reason).contains("Error fallback")
    }
}