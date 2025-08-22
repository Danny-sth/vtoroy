package com.jarvis.agent.memory

import com.jarvis.agent.memory.contract.ClassificationConfig
import com.jarvis.agent.memory.contract.MemoryType
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HybridMemoryClassifierTest {

    private lateinit var semanticClassifier: SemanticMemoryClassifier
    private lateinit var structuralClassifier: StructuralMemoryClassifier  
    private lateinit var contextClassifier: ContextMemoryClassifier
    private lateinit var hybridClassifier: HybridMemoryClassifier
    
    @BeforeEach
    fun setup() {
        semanticClassifier = mockk()
        structuralClassifier = mockk()
        contextClassifier = mockk()
        
        val config = ClassificationConfig(
            semanticWeight = 0.5f,
            structuralWeight = 0.3f,
            contextWeight = 0.2f,
            minimumConfidence = 0.1f,
            enableEnsemble = true
        )
        
        hybridClassifier = HybridMemoryClassifier(
            semanticClassifier,
            structuralClassifier,
            contextClassifier,
            config
        )
    }
    
    @Test
    fun `classify should combine results from all classifiers with ensemble voting`() = runTest {
        // Given
        val content = "## Meeting Notes\nDiscussed project timeline and deliverables."
        val metadata = mapOf("path" to "/meetings/standup-2024.md", "tags" to listOf("meeting"))
        
        coEvery { semanticClassifier.classify(content, metadata) } returns
                MemoryType("meeting", "semantic", 0.8f)
        
        coEvery { structuralClassifier.classify(content, metadata) } returns
                MemoryType("meeting", "structural", 0.9f)
            
        coEvery { contextClassifier.classify(content, metadata) } returns
                MemoryType("meeting", "context", 0.7f)
        
        // When
        val result = hybridClassifier.classify(content, metadata)
        
        // Then
        assertThat(result.primary).isEqualTo("meeting")
        assertThat(result.secondary).isEqualTo("ensemble")
        assertThat(result.confidence).isGreaterThan(0.7f) // High confidence from ensemble
        assertThat(result.attributes).containsKeys("ensembleScore", "candidateCount", "weights")
        
        coVerify { semanticClassifier.classify(content, metadata) }
        coVerify { structuralClassifier.classify(content, metadata) }
        coVerify { contextClassifier.classify(content, metadata) }
    }
    
    @Test
    fun `classify should return unknown when confidence below threshold`() = runTest {
        // Given
        val content = "Short text"
        val metadata = emptyMap<String, Any>()
        
        coEvery { semanticClassifier.classify(content, metadata) } returns
                MemoryType("note", "semantic", 0.05f)
        
        coEvery { structuralClassifier.classify(content, metadata) } returns
                MemoryType("unknown", "structural", 0.0f)
            
        coEvery { contextClassifier.classify(content, metadata) } returns
                MemoryType("unknown", "context", 0.0f)
        
        // When
        val result = hybridClassifier.classify(content, metadata)
        
        // Then
        assertThat(result.primary).isEqualTo("unknown")
        assertThat(result.confidence).isEqualTo(0f)
        assertThat(result.attributes["reason"]).isEqualTo("insufficient_confidence")
    }
    
    @Test
    fun `classify should handle conflicting classifier results`() = runTest {
        // Given
        val content = "TODO: Document the meeting results from project alpha"
        val metadata = mapOf("path" to "/tasks/project-alpha.md")
        
        coEvery { semanticClassifier.classify(content, metadata) } returns
                MemoryType("task", "semantic", 0.7f)
        
        coEvery { structuralClassifier.classify(content, metadata) } returns
                MemoryType("meeting", "structural", 0.6f)
            
        coEvery { contextClassifier.classify(content, metadata) } returns
                MemoryType("project", "context", 0.5f)
        
        // When
        val result = hybridClassifier.classify(content, metadata)
        
        // Then - Should pick "task" due to higher semantic weight and confidence
        assertThat(result.primary).isEqualTo("task")
        assertThat(result.secondary).isEqualTo("ensemble")
        assertThat(result.confidence).isGreaterThan(0.3f)
    }
    
    @Test
    fun `classify should fallback to semantic when ensemble disabled`() = runTest {
        // Given
        val config = ClassificationConfig(enableEnsemble = false)
        val hybridClassifier = HybridMemoryClassifier(
            semanticClassifier, structuralClassifier, contextClassifier, config
        )
        
        val content = "Test content"
        val metadata = emptyMap<String, Any>()
        
        coEvery { semanticClassifier.classify(content, metadata) } returns
                MemoryType("note", "semantic", 0.6f)
        
        // When
        val result = hybridClassifier.classify(content, metadata)
        
        // Then
        assertThat(result.primary).isEqualTo("note")
        assertThat(result.secondary).isEqualTo("semantic")
        
        // Should not call other classifiers
        coVerify(exactly = 0) { structuralClassifier.classify(any(), any()) }
        coVerify(exactly = 0) { contextClassifier.classify(any(), any()) }
    }
    
    @Test
    fun `getSupportedTypes should aggregate all classifier types`() {
        // Given
        every { semanticClassifier.getSupportedTypes() } returns setOf("meeting", "note", "task")
        every { structuralClassifier.getSupportedTypes() } returns setOf("meeting", "code", "documentation")  
        every { contextClassifier.getSupportedTypes() } returns setOf("project", "research")
        
        // When
        val supportedTypes = hybridClassifier.getSupportedTypes()
        
        // Then
        assertThat(supportedTypes).containsExactlyInAnyOrder(
            "meeting", "note", "task", "code", "documentation", "project", "research"
        )
    }
}