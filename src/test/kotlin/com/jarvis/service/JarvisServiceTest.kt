package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.entity.ChatMessage
import com.jarvis.entity.ChatSession
import com.jarvis.entity.MessageRole
import com.jarvis.repository.ChatMessageRepository
import com.jarvis.repository.ChatSessionRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockKExtension::class)
class JarvisServiceTest {

    @RelaxedMockK
    private lateinit var chatModel: AnthropicChatModel

    @MockK
    private lateinit var knowledgeService: KnowledgeService

    @MockK
    private lateinit var chatSessionRepository: ChatSessionRepository

    @MockK
    private lateinit var chatMessageRepository: ChatMessageRepository

    @RelaxedMockK
    private lateinit var objectMapper: ObjectMapper

    @InjectMockKs
    private lateinit var jarvisService: JarvisService

    private val testSessionId = "test-session-123"
    private val testQuery = "Hello, Jarvis!"
    private val testResponse = "Hello! How can I help you today?"

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    // Test: Create new session when it doesn't exist
    @Test
    fun `chat should create new session when not exists`() = runTest {
        // Given
        val newSession = ChatSession(id = testSessionId)
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.empty()
        every { chatSessionRepository.save(any()) } returns newSession
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any(), any()) 
        } returns emptyList()
        
        val generation = mockk<Generation>(relaxed = true)
        every { generation.output.content } returns testResponse
        
        val aiResponse = mockk<ChatResponse>(relaxed = true)
        every { aiResponse.result } returns generation
        every { chatModel.call(any<org.springframework.ai.chat.prompt.Prompt>()) } returns aiResponse

        // When
        val response = jarvisService.chat(testQuery, testSessionId)

        // Then
        assertThat(response.response).isEqualTo(testResponse)
        assertThat(response.sessionId).isEqualTo(testSessionId)
        
        verify(exactly = 1) { chatSessionRepository.findById(testSessionId) }
        verify(atLeast = 1) { chatSessionRepository.save(match { it.id == testSessionId }) }
        verify(exactly = 2) { chatMessageRepository.save(any()) } // User and assistant messages
    }

    // Test: Use existing session
    @Test
    fun `chat should use existing session`() = runTest {
        // Given
        val existingSession = ChatSession(
            id = testSessionId,
            createdAt = LocalDateTime.now().minusHours(1)
        )
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.of(existingSession)
        every { chatSessionRepository.save(any()) } returns existingSession
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any(), any()) 
        } returns emptyList()
        
        val generation = mockk<Generation>(relaxed = true)
        every { generation.output.content } returns testResponse
        
        val aiResponse = mockk<ChatResponse>(relaxed = true)
        every { aiResponse.result } returns generation
        every { chatModel.call(any<org.springframework.ai.chat.prompt.Prompt>()) } returns aiResponse

        // When
        val response = jarvisService.chat(testQuery, testSessionId)

        // Then
        assertThat(response.response).isEqualTo(testResponse)
        
        verify(exactly = 1) { chatSessionRepository.findById(testSessionId) }
        verify(exactly = 0) { chatSessionRepository.save(match { it.id == testSessionId && it.createdAt == null }) }
        verify(exactly = 1) { chatSessionRepository.save(match { it.lastActiveAt != null }) }
    }

    // Test: Include conversation history in context
    @Test
    fun `chat should include conversation history`() = runTest {
        // Given
        val session = ChatSession(id = testSessionId)
        val previousMessages = listOf(
            ChatMessage(
                id = 1L,
                session = session,
                role = MessageRole.USER,
                content = "Previous question"
            ),
            ChatMessage(
                id = 2L,
                session = session,
                role = MessageRole.ASSISTANT,
                content = "Previous answer"
            )
        )
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { chatSessionRepository.save(any()) } returns session
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                testSessionId, 
                PageRequest.of(0, 20)
            ) 
        } returns previousMessages.reversed()
        
        val generation = mockk<Generation>(relaxed = true)
        every { generation.output.content } returns testResponse
        
        val aiResponse = mockk<ChatResponse>(relaxed = true)
        every { aiResponse.result } returns generation
        
        // Capture the prompt to verify history is included
        val promptSlot = slot<org.springframework.ai.chat.prompt.Prompt>()
        every { chatModel.call(capture(promptSlot)) } returns aiResponse

        // When
        jarvisService.chat(testQuery, testSessionId)

        // Then
        val capturedMessages = promptSlot.captured.instructions
        assertThat(capturedMessages).hasSize(4) // System + 2 history + current query
        assertThat(capturedMessages[1]).isInstanceOf(UserMessage::class.java)
        assertThat((capturedMessages[1] as UserMessage).content).isEqualTo("Previous question")
        assertThat(capturedMessages[2]).isInstanceOf(AssistantMessage::class.java)
        assertThat((capturedMessages[2] as AssistantMessage).content).isEqualTo("Previous answer")
    }

    // Test: Save user and assistant messages
    @Test
    fun `chat should save both user and assistant messages`() = runTest {
        // Given
        val session = ChatSession(id = testSessionId)
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { chatSessionRepository.save(any()) } returns session
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any(), any()) 
        } returns emptyList()
        
        val generation = mockk<Generation>(relaxed = true)
        every { generation.output.content } returns testResponse
        
        val aiResponse = mockk<ChatResponse>(relaxed = true)
        every { aiResponse.result } returns generation
        every { chatModel.call(any<org.springframework.ai.chat.prompt.Prompt>()) } returns aiResponse

        // When
        jarvisService.chat(testQuery, testSessionId)

        // Then
        verify(exactly = 2) { chatMessageRepository.save(any()) }
        verify(exactly = 1) { 
            chatMessageRepository.save(match { 
                it.role == MessageRole.USER && it.content == testQuery 
            }) 
        }
        verify(exactly = 1) { 
            chatMessageRepository.save(match { 
                it.role == MessageRole.ASSISTANT && it.content == testResponse 
            }) 
        }
    }

    // Test: Search knowledge function

    // Test: Extract metadata from AI response
    @Test
    fun `chat should extract usage metadata from response`() = runTest {
        // Given
        val session = ChatSession(id = testSessionId)
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { chatSessionRepository.save(any()) } returns session
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any(), any()) 
        } returns emptyList()
        
        val generation = mockk<Generation>(relaxed = true)
        every { generation.output.content } returns testResponse
        
        val usage = mockk<org.springframework.ai.chat.metadata.Usage>(relaxed = true)
        every { usage.promptTokens } returns 100L
        every { usage.generationTokens } returns 50L
        every { usage.totalTokens } returns 150L
        
        val metadata = mockk<org.springframework.ai.chat.metadata.ChatResponseMetadata>(relaxed = true)
        every { metadata.usage } returns usage
        
        val aiResponse = mockk<ChatResponse>(relaxed = true)
        every { aiResponse.result } returns generation
        every { aiResponse.metadata } returns metadata
        every { chatModel.call(any<org.springframework.ai.chat.prompt.Prompt>()) } returns aiResponse

        // When
        val response = jarvisService.chat(testQuery, testSessionId)

        // Then
        assertThat(response.metadata).containsKey("usage")
        @Suppress("UNCHECKED_CAST")
        val usageMap = response.metadata?.get("usage") as Map<String, Any>
        assertThat(usageMap["promptTokens"]).isEqualTo(100L)
        assertThat(usageMap["generationTokens"]).isEqualTo(50L)
        assertThat(usageMap["totalTokens"]).isEqualTo(150L)
    }

    // Test: Limit conversation history size
    @Test
    fun `chat should limit conversation history to maxHistorySize`() = runTest {
        // Given
        val session = ChatSession(id = testSessionId)
        val manyMessages = (1..30).map { i ->
            ChatMessage(
                id = i.toLong(),
                session = session,
                role = if (i % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "Message $i"
            )
        }
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { chatSessionRepository.save(any()) } returns session
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                testSessionId,
                PageRequest.of(0, 20) // maxHistorySize = 20
            ) 
        } returns manyMessages.takeLast(20).reversed()
        
        val generation = mockk<Generation>(relaxed = true)
        every { generation.output.content } returns testResponse
        
        val aiResponse = mockk<ChatResponse>(relaxed = true)
        every { aiResponse.result } returns generation
        every { chatModel.call(any<org.springframework.ai.chat.prompt.Prompt>()) } returns aiResponse

        // When
        jarvisService.chat(testQuery, testSessionId)

        // Then
        verify(exactly = 1) { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                testSessionId,
                PageRequest.of(0, 20)
            ) 
        }
    }
}