package com.jarvis.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.agent.contract.KnowledgeManageable
import com.jarvis.dto.KnowledgeStatus
import com.jarvis.entity.KnowledgeFile
import com.jarvis.repository.KnowledgeFileRepository
import com.jarvis.service.knowledge.contract.KnowledgeItem
import com.pgvector.PGvector
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.ai.embedding.EmbeddingModel
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class KnowledgeServiceTest {

    @MockK
    private lateinit var knowledgeFileRepository: KnowledgeFileRepository

    @RelaxedMockK
    private lateinit var embeddingModel: EmbeddingModel

    @RelaxedMockK
    private lateinit var objectMapper: ObjectMapper


    private lateinit var knowledgeService: KnowledgeService

    private val testEmbedding = FloatArray(384) { 0.1f }

    // Test agent class that implements KnowledgeManageable
    private class TestObsidianAgent : KnowledgeManageable {
        override suspend fun formMemories(config: Map<String, Any>): List<KnowledgeItem> {
            return listOf(
                KnowledgeItem(
                    id = "test-1",
                    title = "Test Document",
                    content = "This is test content.",
                    tags = listOf("test"),
                    lastModified = System.currentTimeMillis()
                )
            )
        }

        override fun canAccessSource(): Boolean = true

        override fun getSourceStatus(): com.jarvis.agent.contract.SourceStatus {
            return com.jarvis.agent.contract.SourceStatus(
                sourceType = "obsidian",
                isAccessible = true,
                health = "healthy",
                itemCount = 5
            )
        }
    }

    // Test agent that cannot access source
    private class TestInaccessibleAgent : KnowledgeManageable {
        override suspend fun formMemories(config: Map<String, Any>): List<KnowledgeItem> {
            throw IllegalStateException("Cannot access source")
        }

        override fun canAccessSource(): Boolean = false

        override fun getSourceStatus(): com.jarvis.agent.contract.SourceStatus {
            return com.jarvis.agent.contract.SourceStatus(
                sourceType = "obsidian",
                isAccessible = false,
                health = "inaccessible"
            )
        }
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        
        // Initialize service with empty agents by default
        knowledgeService = KnowledgeService(
            knowledgeFileRepository = knowledgeFileRepository,
            embeddingModel = embeddingModel,
            objectMapper = objectMapper,
            knowledgeAgents = emptyList()
        )
    }

    // Test: Get status when database is empty
    @Test
    fun `getStatus should return EMPTY when no files exist`() {
        // Given
        every { knowledgeFileRepository.count() } returns 0L
        every { knowledgeFileRepository.findAll() } returns emptyList()

        // When
        val status = knowledgeService.getStatus()

        // Then
        assertThat(status.status).isEqualTo(KnowledgeStatus.EMPTY)
        assertThat(status.totalFiles).isEqualTo(0L)
        assertThat(status.indexedFiles).isEqualTo(0L)
        assertThat(status.lastSync).isNull()
    }

    // Test: Get status when files are ready
    @Test
    fun `getStatus should return READY when all files are indexed`() {
        // Given
        val now = LocalDateTime.now()
        val knowledgeFile = KnowledgeFile(
            id = 1L,
            source = "obsidian",
            sourceId = "test-file",
            filePath = "/test/file.md",
            content = "Test content",
            embedding = PGvector(testEmbedding),
            fileHash = "hash123",
            updatedAt = now
        )

        every { knowledgeFileRepository.count() } returns 1L
        every { knowledgeFileRepository.findAll() } returns listOf(knowledgeFile)

        // When
        val status = knowledgeService.getStatus()

        // Then
        assertThat(status.status).isEqualTo(KnowledgeStatus.READY)
        assertThat(status.totalFiles).isEqualTo(1L)
        assertThat(status.indexedFiles).isEqualTo(1L)
        assertThat(status.lastSync).isEqualTo(now)
    }

    // Test: Search knowledge
    @Test
    fun `searchKnowledge should return similar documents`() = runTest {
        // Given
        val query = "test query"
        val expectedFiles = listOf(
            KnowledgeFile(
                id = 1L,
                source = "obsidian",
                sourceId = "file1",
                filePath = "/test/file1.md",
                content = "Content 1",
                embedding = PGvector(testEmbedding),
                fileHash = "hash1"
            ),
            KnowledgeFile(
                id = 2L,
                source = "obsidian",
                sourceId = "file2",
                filePath = "/test/file2.md",
                content = "Content 2",
                embedding = PGvector(testEmbedding),
                fileHash = "hash2"
            )
        )

        every { embeddingModel.embed(query) } returns testEmbedding
        every { knowledgeFileRepository.findSimilarDocuments(any(), any()) } returns expectedFiles

        // When
        val result = knowledgeService.searchKnowledge(query, 5)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result).containsExactlyElementsOf(expectedFiles)
        verify { embeddingModel.embed(query) }
        verify { knowledgeFileRepository.findSimilarDocuments(any(), 5) }
    }

    // Test: Get available sources
    @Test
    fun `getAvailableSources should return all agents status`() {
        // Given
        val testAgent = TestObsidianAgent()
        
        // Create service with test agent
        val serviceWithAgent = KnowledgeService(
            knowledgeFileRepository = knowledgeFileRepository,
            embeddingModel = embeddingModel,
            objectMapper = objectMapper,
            knowledgeAgents = listOf(testAgent)
        )

        // When
        val sources = serviceWithAgent.getAvailableSources()

        // Then
        assertThat(sources).hasSize(1)
        assertThat(sources["testobsidian"]).isNotNull
        assertThat(sources["testobsidian"]?.isAccessible).isTrue()
    }

    // Test: Sync through agent
    @Test
    fun `syncSource should delegate to agent`() = runTest {
        // Given
        val testAgent = TestObsidianAgent()
        every { knowledgeFileRepository.findBySourceAndSourceId(any(), any()) } returns null
        every { knowledgeFileRepository.save(any()) } returns mockk<KnowledgeFile>()
        every { embeddingModel.embed(any<String>()) } returns testEmbedding

        // Create service with test agent
        val serviceWithAgent = KnowledgeService(
            knowledgeFileRepository = knowledgeFileRepository,
            embeddingModel = embeddingModel,
            objectMapper = objectMapper,
            knowledgeAgents = listOf(testAgent)
        )

        // When
        val result = serviceWithAgent.syncSource("testobsidian", mapOf("vaultPath" to "/test/path"))

        // Then
        assertThat(result).isEqualTo(1)
        verify { knowledgeFileRepository.save(any()) }
        verify { embeddingModel.embed("This is test content.") }
    }


    // Test: Agent not found
    @Test
    fun `syncSource should throw exception when agent not found`() {
        // Given
        knowledgeService = KnowledgeService(
            knowledgeFileRepository = knowledgeFileRepository,
            embeddingModel = embeddingModel,
            objectMapper = objectMapper,
            knowledgeAgents = emptyList()
        )

        // When & Then
        assertThatThrownBy {
            runTest { knowledgeService.syncSource("unknown", emptyMap()) }
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Unknown knowledge agent: unknown")
    }

    // Test: Agent cannot access source
    @Test
    fun `syncSource should throw exception when agent cannot access source`() {
        // Given
        val testAgent = TestInaccessibleAgent()
        
        // Create service with inaccessible agent
        val serviceWithAgent = KnowledgeService(
            knowledgeFileRepository = knowledgeFileRepository,
            embeddingModel = embeddingModel,
            objectMapper = objectMapper,
            knowledgeAgents = listOf(testAgent)
        )

        // When & Then
        assertThatThrownBy {
            runTest { serviceWithAgent.syncSource("testinaccessible", emptyMap()) }
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Agent TestInaccessibleAgent cannot access its source")
    }

    // Test: Get source status
    @Test
    fun `getSourceStatus should return agent and database info`() {
        // Given
        val testAgent = TestObsidianAgent()
        every { knowledgeFileRepository.countBySource("testobsidian") } returns 3L
        every { knowledgeFileRepository.findBySource("testobsidian") } returns emptyList()

        // Create service with test agent  
        val serviceWithAgent = KnowledgeService(
            knowledgeFileRepository = knowledgeFileRepository,
            embeddingModel = embeddingModel,
            objectMapper = objectMapper,
            knowledgeAgents = listOf(testAgent)
        )

        // When
        val status = serviceWithAgent.getSourceStatus("testobsidian")

        // Then
        assertThat(status["sourceId"]).isEqualTo("testobsidian")
        assertThat(status["agentName"]).isEqualTo("TestObsidianAgent")
        assertThat(status["itemCount"]).isEqualTo(3L)
        
        verify { knowledgeFileRepository.countBySource("testobsidian") }
        verify { knowledgeFileRepository.findBySource("testobsidian") }
    }
}