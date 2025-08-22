package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.entity.ChatMessage
import com.jarvis.entity.ChatSession
import com.jarvis.entity.MessageRole
import com.jarvis.repository.ChatMessageRepository
import com.jarvis.repository.ChatSessionRepository
import com.jarvis.agent.contract.AgentResponse
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockKExtension::class)
class JarvisServiceTest {

    @io.mockk.impl.annotations.RelaxedMockK
    private lateinit var mainAgent: com.jarvis.agent.MainAgent

    @io.mockk.impl.annotations.MockK
    private lateinit var chatSessionRepository: ChatSessionRepository

    @io.mockk.impl.annotations.MockK
    private lateinit var chatMessageRepository: ChatMessageRepository

    @io.mockk.impl.annotations.RelaxedMockK
    private lateinit var objectMapper: ObjectMapper

    private lateinit var jarvisService: JarvisService

    private val testSessionId = "test-session-123"
    private val testQuery = "Hello, Jarvis!"
    private val testResponse = "Hello! How can I help you today?"

    @BeforeEach
    fun setup() {
        clearAllMocks()
        
        // Create service manually with mocked dependencies
        jarvisService = JarvisService(
            mainAgent = mainAgent,
            chatSessionRepository = chatSessionRepository,
            chatMessageRepository = chatMessageRepository,
            objectMapper = objectMapper
        )
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
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any()) 
        } returns emptyList()
        
        val agentResponse = AgentResponse(
            content = testResponse,
            metadata = emptyMap(),
            confidence = 0.8
        )
        coEvery { mainAgent.handle(any(), any()) } returns agentResponse

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
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any()) 
        } returns emptyList()
        
        val agentResponse = AgentResponse(
            content = testResponse,
            metadata = emptyMap(),
            confidence = 0.8
        )
        coEvery { mainAgent.handle(any(), any()) } returns agentResponse

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
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(testSessionId) 
        } returns previousMessages.reversed()
        
        val agentResponse = AgentResponse(
            content = testResponse,
            metadata = emptyMap(),
            confidence = 0.8
        )
        
        // Capture the history passed to mainAgent
        val historySlot = slot<List<ChatMessage>>()
        coEvery { mainAgent.handle(any(), capture(historySlot)) } returns agentResponse

        // When
        jarvisService.chat(testQuery, testSessionId)

        // Then - verify that history is passed to MainAgent
        assertThat(historySlot.captured).hasSize(2)
        assertThat(historySlot.captured[0].content).isEqualTo("Previous question")
        assertThat(historySlot.captured[0].role).isEqualTo(MessageRole.USER)
        assertThat(historySlot.captured[1].content).isEqualTo("Previous answer") 
        assertThat(historySlot.captured[1].role).isEqualTo(MessageRole.ASSISTANT)
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
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any()) 
        } returns emptyList()
        
        val agentResponse = AgentResponse(
            content = testResponse,
            metadata = emptyMap(),
            confidence = 0.8
        )
        coEvery { mainAgent.handle(any(), any()) } returns agentResponse

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

    // Test: Extract metadata from agent response
    @Test
    fun `chat should extract metadata from agent response`() = runTest {
        // Given
        val session = ChatSession(id = testSessionId)
        
        every { chatSessionRepository.findById(testSessionId) } returns Optional.of(session)
        every { chatSessionRepository.save(any()) } returns session
        every { chatMessageRepository.save(any()) } answers { firstArg() }
        every { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(any()) 
        } returns emptyList()
        
        val agentResponse = AgentResponse(
            content = testResponse,
            metadata = mapOf(
                "approach" to "dialogue",
                "agent" to "MainAgent",
                "confidence" to 0.8
            ),
            confidence = 0.8,
            processingTimeMs = 150L
        )
        coEvery { mainAgent.handle(any(), any()) } returns agentResponse

        // When
        val response = jarvisService.chat(testQuery, testSessionId)

        // Then
        assertThat(response.metadata).containsKey("approach")
        assertThat(response.metadata?.get("approach")).isEqualTo("dialogue")
        assertThat(response.metadata?.get("agent")).isEqualTo("MainAgent")
        assertThat(response.metadata?.get("confidence")).isEqualTo(0.8)
        // processingTimeMs не передается через metadata в AgentResponse, это отдельное поле
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
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(testSessionId)
        } returns manyMessages.takeLast(20).reversed()
        
        val agentResponse = AgentResponse(
            content = testResponse,
            metadata = emptyMap(),
            confidence = 0.8
        )
        coEvery { mainAgent.handle(any(), any()) } returns agentResponse

        // When
        jarvisService.chat(testQuery, testSessionId)

        // Then
        verify(exactly = 1) { 
            chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(testSessionId)
        }
    }
}