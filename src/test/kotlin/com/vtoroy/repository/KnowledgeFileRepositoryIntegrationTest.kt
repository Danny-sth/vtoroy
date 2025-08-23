package com.vtoroy.repository

import com.vtoroy.entity.KnowledgeFile
import com.pgvector.PGvector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFails

@SpringBootTest
@Testcontainers
@Transactional
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.vtoroy.config.TestConfiguration::class)
class PgVectorSqlTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "shared_preload_libraries=vector")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.ai.anthropic.api-key") { "test-key" }
        }
    }

    @Autowired
    private lateinit var repository: KnowledgeFileRepository

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
    }

    @Test
    fun `test SQL vector similarity search - should work with our fixed queries`() {
        println("=== Testing PgVector SQL queries ===")
        
        // Create test embeddings with known similarity patterns
        val baseEmbedding = FloatArray(384) { 0.5f }  // Base vector
        val similarEmbedding = FloatArray(384) { 0.6f }  // Close to base (small distance)
        val differentEmbedding = FloatArray(384) { -0.5f } // Far from base (large distance)
        
        // Save test documents
        val doc1 = KnowledgeFile(
            source = "obsidian",
            sourceId = "similar-doc",
            filePath = "similar-doc.md",
            content = "Similar document content",
            embedding = PGvector(similarEmbedding),
            fileHash = "similar-hash"
        )
        
        val doc2 = KnowledgeFile(
            source = "obsidian",
            sourceId = "different-doc",
            filePath = "different-doc.md",
            content = "Different document content", 
            embedding = PGvector(differentEmbedding),
            fileHash = "different-hash"
        )
        
        val saved1 = repository.save(doc1)
        val saved2 = repository.save(doc2)
        println("Saved documents with IDs: ${saved1.id}, ${saved2.id}")
        
        // Test the SQL query that was failing
        val queryVector = PGvector(baseEmbedding)
        
        println("Executing findSimilarDocuments with queryVector...")
        try {
            val results = repository.findSimilarDocuments(queryVector.toString(), 2)
            
            println("SUCCESS: Found ${results.size} documents")
            assertEquals(2, results.size, "Should find both documents")
            
            results.forEachIndexed { index, doc ->
                println("  Result $index: ${doc.filePath} (ID: ${doc.id})")
                assertNotNull(doc.embedding, "Document should have embedding")
            }
            
            println("✅ Vector similarity SQL query works correctly!")
            
        } catch (e: Exception) {
            println("❌ SQL query failed: ${e.message}")
            println("Full error: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    @Test 
    fun `test SQL vector similarity with threshold - should work without CAST`() {
        println("=== Testing threshold query SQL ===")
        
        val baseEmbedding = FloatArray(384) { 0.5f }
        val closeEmbedding = FloatArray(384) { 0.6f }  // Small difference 
        val farEmbedding = FloatArray(384) { -0.5f }   // Large difference
        
        val closeDoc = KnowledgeFile(
            source = "obsidian",
            sourceId = "close-doc",
            filePath = "close-doc.md",
            content = "Close document",
            embedding = PGvector(closeEmbedding),
            fileHash = "close-hash"
        )
        
        val farDoc = KnowledgeFile(
            source = "obsidian",
            sourceId = "far-doc",
            filePath = "far-doc.md", 
            content = "Far document",
            embedding = PGvector(farEmbedding),
            fileHash = "far-hash"
        )
        
        repository.save(closeDoc)
        repository.save(farDoc)
        
        val queryVector = PGvector(baseEmbedding)
        
        println("Testing findSimilarDocumentsWithThreshold...")
        
        try {
            // Test with restrictive threshold
            val restrictiveResults = repository.findSimilarDocumentsWithThreshold(queryVector.toString(), 0.5f, 5)
            println("Restrictive threshold (0.5): ${restrictiveResults.size} documents")
            
            // Test with permissive threshold  
            val permissiveResults = repository.findSimilarDocumentsWithThreshold(queryVector.toString(), 2.0f, 5)
            println("Permissive threshold (2.0): ${permissiveResults.size} documents")
            
            assertTrue(permissiveResults.size >= restrictiveResults.size)
            println("✅ Threshold queries work correctly!")
            
        } catch (e: Exception) {
            println("❌ Threshold query failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}