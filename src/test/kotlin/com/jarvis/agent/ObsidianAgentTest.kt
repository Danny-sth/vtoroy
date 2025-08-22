package com.jarvis.agent

import com.jarvis.agent.memory.HybridMemoryClassifier
import com.jarvis.agent.memory.contract.MemoryType
import com.jarvis.agent.reasoning.ObsidianReasoningEngine
import com.jarvis.service.knowledge.ObsidianVaultManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.prompt.Prompt
import java.nio.file.Path
import kotlin.io.path.writeText

class ObsidianAgentTest {

    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var obsidianAgent: ObsidianAgent
    private lateinit var mockMemoryClassifier: HybridMemoryClassifier
    private lateinit var mockVaultManager: ObsidianVaultManager
    private lateinit var mockChatModel: AnthropicChatModel
    private lateinit var mockReasoningEngine: ObsidianReasoningEngine
    
    @BeforeEach
    fun setup() {
        mockMemoryClassifier = mockk()
        mockVaultManager = mockk()
        mockChatModel = mockk()
        mockReasoningEngine = mockk()
        
        // Mock AI model for parseQuery
        every { mockChatModel.call(any<Prompt>()) } returns ChatResponse(listOf(
            Generation(AssistantMessage("""{"action": "SEARCH_VAULT", "parameters": {"query": "test"}}"""))
        ))
        
        obsidianAgent = ObsidianAgent(tempDir.toString(), mockMemoryClassifier, mockVaultManager, mockChatModel, mockReasoningEngine)
        
        // Setup smart mock responses based on content
        coEvery { mockMemoryClassifier.classify(any(), any()) } answers {
            val content = firstArg<String>()
            when {
                content.contains("## Meeting") -> MemoryType("meeting", "hybrid", 0.9f)
                content.contains("# Project") -> MemoryType("project", "hybrid", 0.9f)
                content.contains("TODO") || content.contains("- [ ]") -> MemoryType("task", "hybrid", 0.8f)
                else -> MemoryType("note", "hybrid", 0.7f)
            }
        }
    }
    
    @Test
    fun `formMemories should process markdown files from vault`() = runTest {
        // Given
        val testFile = tempDir.resolve("test.md")
        testFile.writeText("""
            ---
            title: Test Document
            tags: [test, example]
            ---
            # Test Document
            
            This is test content for the document.
            It contains [[internal links]] and #hashtags.
        """.trimIndent())
        
        // When
        val memories = obsidianAgent.formMemories(mapOf("vaultPath" to tempDir.toString()))
        
        // Then
        assertThat(memories).hasSize(1)
        val memory = memories.first()
        assertThat(memory.title).isEqualTo("test")
        assertThat(memory.content).contains("This is test content")
        assertThat(memory.content).contains("internal links") // Wiki links processed
        assertThat(memory.tags).contains("test", "example", "hashtags")
        assertThat(memory.metadata).containsKeys("processedBy", "memoryType", "importance")
    }
    
    @Test
    fun `formMemories should skip trivial content`() = runTest {
        // Given
        val emptyFile = tempDir.resolve("empty.md")
        emptyFile.writeText("# Empty")
        
        val taskFile = tempDir.resolve("tasks.md") 
        taskFile.writeText("## Tasks\n- [ ] Do something")
        
        // When
        val memories = obsidianAgent.formMemories(mapOf("vaultPath" to tempDir.toString()))
        
        // Then
        assertThat(memories).isEmpty() // Both files should be filtered out
    }
    
    @Test
    fun `canAccessSource should return true for existing directory`() {
        // When
        val canAccess = obsidianAgent.canAccessSource()
        
        // Then
        assertThat(canAccess).isTrue()
    }
    
    @Test
    fun `canAccessSource should return false for non-existing directory`() {
        // Given
        val nonExistentAgent = ObsidianAgent("/non/existent/path", mockMemoryClassifier, mockVaultManager, mockChatModel, mockReasoningEngine)
        
        // When
        val canAccess = nonExistentAgent.canAccessSource()
        
        // Then
        assertThat(canAccess).isFalse()
    }
    
    
    @Test
    fun `getSourceStatus should return healthy status when vault exists`() {
        // When
        val status = obsidianAgent.getSourceStatus()
        
        // Then
        assertThat(status.sourceType).isEqualTo("obsidian")
        assertThat(status.isAccessible).isTrue()
        assertThat(status.health).isEqualTo("never_synced")
    }
    
    @Test
    fun `formMemories should classify memory types correctly`() = runTest {
        // Given
        val meetingFile = tempDir.resolve("meeting.md")
        meetingFile.writeText("""
            # Daily Standup
            ## Meeting
            Discussion about project progress.
        """.trimIndent())
        
        val projectFile = tempDir.resolve("project.md")
        projectFile.writeText("""
            # Project Alpha
            Important project details here.
        """.trimIndent())
        
        // When
        val memories = obsidianAgent.formMemories(mapOf("vaultPath" to tempDir.toString()))
        
        // Then
        assertThat(memories).hasSize(2)
        
        val meetingMemory = memories.find { it.title == "meeting" }
        assertThat(meetingMemory?.metadata?.get("memoryType")).isEqualTo("meeting")
        
        val projectMemory = memories.find { it.title == "project" }  
        assertThat(projectMemory?.metadata?.get("memoryType")).isEqualTo("project")
    }
}