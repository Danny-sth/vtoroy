package com.jarvis.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.config.TestConfiguration
import com.jarvis.dto.ChatRequest
import com.jarvis.dto.KnowledgeSyncRequest
import com.jarvis.entity.KnowledgeFile
import com.jarvis.repository.ChatMessageRepository
import com.jarvis.repository.ChatSessionRepository
import com.jarvis.repository.KnowledgeFileRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@Transactional
class JarvisApplicationIntegrationTest {

    companion object {
        @Container
        val postgreSQLContainer = PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("jarvis_test")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgreSQLContainer::getUsername)
            registry.add("spring.datasource.password", postgreSQLContainer::getPassword)
            registry.add("spring.flyway.url", postgreSQLContainer::getJdbcUrl)
            registry.add("spring.flyway.user", postgreSQLContainer::getUsername)
            registry.add("spring.flyway.password", postgreSQLContainer::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var knowledgeFileRepository: KnowledgeFileRepository

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @BeforeEach
    fun cleanup() {
        chatMessageRepository.deleteAll()
        chatSessionRepository.deleteAll()
        knowledgeFileRepository.deleteAll()
    }

    // Test: Application context loads successfully
    @Test
    fun `application context should load`() {
        assertThat(mockMvc).isNotNull
        assertThat(knowledgeFileRepository).isNotNull
        assertThat(chatSessionRepository).isNotNull
        assertThat(chatMessageRepository).isNotNull
    }

    // Test: Health endpoint returns UP
    @Test
    fun `health endpoint should return UP`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    // Test: Knowledge status endpoint with empty database
    @Test
    fun `knowledge status should return EMPTY when database is empty`() {
        mockMvc.perform(get("/api/knowledge/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("EMPTY"))
            .andExpect(jsonPath("$.totalFiles").value(0))
            .andExpect(jsonPath("$.indexedFiles").value(0))
    }

    // Test: Chat endpoint with new session
    @Test
    fun `chat endpoint should create new session and save messages`() {
        // Given
        val request = ChatRequest(
            query = "Hello, integration test!",
            sessionId = "integration-test-session"
        )

        // When
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value("integration-test-session"))
            .andExpect(jsonPath("$.response").exists())

        // Then - Verify data persisted in database
        val sessions = chatSessionRepository.findAll()
        assertThat(sessions).hasSize(1)
        assertThat(sessions[0].id).isEqualTo("integration-test-session")

        val messages = chatMessageRepository.findAll()
        assertThat(messages).hasSize(2) // User message + assistant message
        assertThat(messages.map { it.content }).contains("Hello, integration test!")
    }

    // Test: Knowledge sync with temporary vault
    @Test
    fun `knowledge sync should process markdown files and save to database`() {
        // Given - Create temporary vault with markdown files
        val tempVault = createTempDirectory("test-vault")
        val file1 = tempVault.resolve("file1.md")
        val file2 = tempVault.resolve("file2.md")
        
        file1.writeText("""
            ---
            title: Test Document 1
            tags: [test, integration]
            ---
            # Document 1
            This is the first test document.
        """.trimIndent())
        
        file2.writeText("""
            # Document 2
            This is the second test document with [[internal link]].
        """.trimIndent())

        val request = KnowledgeSyncRequest(vaultPath = tempVault.toString())

        // When
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.filesProcessed").value(2))
            .andExpect(jsonPath("$.message").value("Sync completed successfully"))

        // Then - Verify files saved to database
        val knowledgeFiles = knowledgeFileRepository.findAll()
        assertThat(knowledgeFiles).hasSize(2)
        
        val file1Entity = knowledgeFiles.find { it.filePath.contains("file1.md") }
        assertThat(file1Entity).isNotNull
        assertThat(file1Entity!!.content).contains("Document 1")
        assertThat(file1Entity.content).contains("This is the first test document")
        assertThat(file1Entity.embedding).isNotNull
        
        val file2Entity = knowledgeFiles.find { it.filePath.contains("file2.md") }
        assertThat(file2Entity).isNotNull
        assertThat(file2Entity!!.content).contains("Document 2")
        assertThat(file2Entity.content).contains("internal link") // Wiki link cleaned
        assertThat(file2Entity.content).doesNotContain("[[") // No wiki link syntax

        // Cleanup
        Files.deleteIfExists(file1)
        Files.deleteIfExists(file2)
        Files.deleteIfExists(tempVault)
    }

    // Test: Knowledge status after adding files
    @Test
    fun `knowledge status should return READY after adding files`() {
        // Given - Add some test files to the database
        val testFile1 = KnowledgeFile(
            filePath = "/test/file1.md",
            content = "Test content 1",
            embedding = com.pgvector.PGvector(FloatArray(384) { 0.1f }),
            fileHash = "hash1"
        )
        
        val testFile2 = KnowledgeFile(
            filePath = "/test/file2.md",
            content = "Test content 2",
            embedding = com.pgvector.PGvector(FloatArray(384) { 0.2f }),
            fileHash = "hash2"
        )
        
        knowledgeFileRepository.saveAll(listOf(testFile1, testFile2))

        // When & Then
        mockMvc.perform(get("/api/knowledge/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.totalFiles").value(2))
            .andExpect(jsonPath("$.indexedFiles").value(2))
            .andExpect(jsonPath("$.lastSync").exists())
    }

    // Test: Multiple chat messages in same session
    @Test
    fun `multiple chat messages should maintain session context`() {
        // Given
        val sessionId = "multi-message-session"
        
        // First message
        val request1 = ChatRequest(
            query = "My name is Alice.",
            sessionId = sessionId
        )
        
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
        )
            .andExpect(status().isOk)

        // Second message
        val request2 = ChatRequest(
            query = "What is my name?",
            sessionId = sessionId
        )
        
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
        )
            .andExpect(status().isOk)

        // Then - Verify session has multiple messages
        val messages = chatMessageRepository.findAll()
        assertThat(messages).hasSize(4) // 2 user + 2 assistant messages
        
        val sessionMessages = messages.filter { it.session.id == sessionId }
        assertThat(sessionMessages).hasSize(4)
        assertThat(sessionMessages.map { it.content }).contains("My name is Alice.", "What is my name?")
    }

    // Test: Database schema validation
    @Test
    fun `database schema should support pgvector operations`() {
        // Given - Create a test file with embedding
        val testEmbedding = FloatArray(384) { it * 0.001f }
        val testFile = KnowledgeFile(
            filePath = "/test/vector-test.md",
            content = "Vector test content",
            embedding = com.pgvector.PGvector(testEmbedding),
            fileHash = "vector-hash"
        )
        
        // When - Save and retrieve
        val savedFile = knowledgeFileRepository.save(testFile)
        val retrievedFile = knowledgeFileRepository.findById(savedFile.id!!).orElse(null)
        
        // Then - Verify vector data integrity
        assertThat(retrievedFile).isNotNull
        assertThat(retrievedFile.embedding).isNotNull
        assertThat(retrievedFile.embedding!!.toArray()).hasSize(384)
        assertThat(retrievedFile.embedding!!.toArray()[0]).isEqualTo(0.0f, org.assertj.core.data.Offset.offset(0.001f))
        assertThat(retrievedFile.embedding!!.toArray()[100]).isEqualTo(0.1f, org.assertj.core.data.Offset.offset(0.001f))
    }

    // Test: Error handling for invalid sync path
    @Test
    fun `knowledge sync should return error for invalid path`() {
        // Given
        val request = KnowledgeSyncRequest(vaultPath = "/non/existent/path")

        // When & Then
        mockMvc.perform(
            post("/api/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Vault path does not exist: /non/existent/path"))
    }

    // Test: Concurrent chat sessions
    @Test
    fun `concurrent chat sessions should be handled independently`() {
        // Given
        val session1Id = "concurrent-session-1"
        val session2Id = "concurrent-session-2"
        
        val request1 = ChatRequest(query = "Session 1 message", sessionId = session1Id)
        val request2 = ChatRequest(query = "Session 2 message", sessionId = session2Id)

        // When - Send messages to different sessions
        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(session1Id))

        mockMvc.perform(
            post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(session2Id))

        // Then - Verify both sessions exist
        val sessions = chatSessionRepository.findAll()
        assertThat(sessions).hasSize(2)
        assertThat(sessions.map { it.id }).containsExactlyInAnyOrder(session1Id, session2Id)

        val messages = chatMessageRepository.findAll()
        assertThat(messages).hasSize(4) // 2 sessions * 2 messages each
    }
}