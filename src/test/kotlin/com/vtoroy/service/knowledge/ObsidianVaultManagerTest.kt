package com.vtoroy.service.knowledge

import com.vtoroy.dto.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ObsidianVaultManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var vaultManager: ObsidianVaultManager
    private lateinit var mockMarkdownParser: MarkdownParser

    @BeforeEach
    fun setup() {
        mockMarkdownParser = mockk()
        vaultManager = ObsidianVaultManager(mockMarkdownParser, tempDir.toString())
        
        // Setup parser mock responses
        every { mockMarkdownParser.parseMarkdown(any(), any()) } returns ParsedMarkdown(
            title = "Test Note",
            content = "Test content",
            rawContent = "# Test Note\nTest content",
            frontmatter = mapOf("title" to "Test Note", "created" to "2025-08-22"),
            wikiLinks = listOf("link1", "link2"),
            tags = setOf("tag1", "tag2")
        )
        
        every { mockMarkdownParser.createMarkdownContent(any(), any()) } returns """---
title: Test Note
created: 2025-08-22
---

# Test Note
Test content"""
    }

    @Test
    fun `readNote should return note when file exists`() = runTest {
        // Given
        val notePath = "test-note.md"
        val noteFile = tempDir.resolve(notePath)
        noteFile.parent.createDirectories()
        noteFile.writeText("""---
title: Test Note
---

# Test Note
This is test content.""")

        // When
        val result = vaultManager.readNote(notePath)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val note = successResult.data as MarkdownNote
        assertThat(note.title).isEqualTo("Test Note")
        assertThat(note.path).isEqualTo(notePath)
        assertThat(note.tags).containsExactlyInAnyOrder("tag1", "tag2")
        assertThat(note.wikiLinks).containsExactlyInAnyOrder("link1", "link2")
    }

    @Test
    fun `readNote should return error when file does not exist`() = runTest {
        // Given
        val notePath = "non-existent.md"

        // When
        val result = vaultManager.readNote(notePath)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Note not found")
    }

    @Test
    fun `createNote should create new note successfully`() = runTest {
        // Given
        val request = CreateNoteRequest(
            path = "new-note.md",
            title = "New Note",
            content = "New note content",
            tags = setOf("new", "test"),
            frontmatter = mapOf("priority" to "high")
        )
        
        // Ensure temp directory exists
        tempDir.createDirectories()

        // When
        val result = vaultManager.createNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val note = successResult.data as MarkdownNote
        assertThat(note.title).isEqualTo("Test Note") // From mock
        assertThat(note.path).isEqualTo("new-note.md")
        
        verify { mockMarkdownParser.createMarkdownContent(eq("New note content"), any()) }
    }

    @Test
    fun `createNote should return error when note already exists`() = runTest {
        // Given
        val notePath = "existing-note.md"
        val noteFile = tempDir.resolve(notePath)
        noteFile.parent.createDirectories()
        noteFile.writeText("Existing content")
        
        val request = CreateNoteRequest(
            path = notePath,
            title = "New Note",
            content = "New content"
        )

        // When
        val result = vaultManager.createNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Note already exists")
    }

    @Test
    fun `updateNote should update existing note`() = runTest {
        // Given
        val notePath = "update-note.md"
        val noteFile = tempDir.resolve(notePath)
        noteFile.parent.createDirectories()
        noteFile.writeText("""---
title: Original Title
---

Original content""")
        
        val request = UpdateNoteRequest(
            path = notePath,
            content = "Updated content",
            title = "Updated Title",
            tags = setOf("updated")
        )

        // When
        val result = vaultManager.updateNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val note = successResult.data as MarkdownNote
        assertThat(note.title).isEqualTo("Test Note") // From mock
        
        verify { mockMarkdownParser.createMarkdownContent(eq("Updated content"), any()) }
    }

    @Test
    fun `updateNote should return error when note does not exist`() = runTest {
        // Given
        val request = UpdateNoteRequest(
            path = "non-existent.md",
            content = "Updated content"
        )

        // When
        val result = vaultManager.updateNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Note not found")
    }

    @Test
    fun `deleteNote should delete existing note`() = runTest {
        // Given
        val notePath = "delete-note.md"
        val noteFile = tempDir.resolve(notePath)
        noteFile.parent.createDirectories()
        noteFile.writeText("Content to delete")

        // When
        val result = vaultManager.deleteNote(notePath)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        assertThat(successResult.data).isEqualTo("Note deleted successfully")
        assertThat(noteFile).doesNotExist()
    }

    @Test
    fun `deleteNote should return error when note does not exist`() = runTest {
        // Given
        val notePath = "non-existent.md"

        // When
        val result = vaultManager.deleteNote(notePath)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Note not found")
    }

    @Test
    fun `moveNote should move note to new location`() = runTest {
        // Given
        val oldPath = "old-location.md"
        val newPath = "new-location.md"
        val oldFile = tempDir.resolve(oldPath)
        oldFile.parent.createDirectories()
        oldFile.writeText("Content to move")
        
        val request = MoveNoteRequest(oldPath, newPath)

        // When
        val result = vaultManager.moveNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val note = successResult.data as MarkdownNote
        assertThat(note.path).isEqualTo(newPath)
        assertThat(oldFile).doesNotExist()
        assertThat(tempDir.resolve(newPath)).exists()
    }

    @Test
    fun `moveNote should return error when source does not exist`() = runTest {
        // Given
        val request = MoveNoteRequest("non-existent.md", "new-location.md")

        // When
        val result = vaultManager.moveNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Source note not found")
    }

    @Test
    fun `moveNote should return error when destination exists`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val oldFile = vaultPath.resolve("source.md")
        val newFile = vaultPath.resolve("destination.md")
        
        oldFile.writeText("Source content")
        newFile.writeText("Destination content")
        
        val request = MoveNoteRequest("source.md", "destination.md")

        // When
        val result = vaultManager.moveNote(request)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Destination already exists")
    }

    @Test
    fun `listNotes should return all notes when no folder specified`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val note1 = vaultPath.resolve("note1.md")
        val note2 = vaultPath.resolve("note2.md")
        
        note1.writeText("Content 1")
        note2.writeText("Content 2")

        // When
        val result = vaultManager.listNotes()

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val notes = successResult.data as List<NoteInfo>
        assertThat(notes).hasSize(2)
        assertThat(notes.map { it.path }).containsExactlyInAnyOrder("note1.md", "note2.md")
    }

    @Test
    fun `listNotes should return notes in specified folder`() = runTest {
        // Given
        val vaultPath = tempDir
        val folderPath = vaultPath.resolve("projects")
        folderPath.createDirectories()
        
        val note1 = folderPath.resolve("project1.md")
        val note2 = folderPath.resolve("project2.md")
        val note3 = vaultPath.resolve("other.md")
        
        note1.writeText("Project 1")
        note2.writeText("Project 2") 
        note3.writeText("Other note")

        // When
        val result = vaultManager.listNotes("projects")

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val notes = successResult.data as List<NoteInfo>
        assertThat(notes).hasSize(2)
        assertThat(notes.map { it.path }).containsExactlyInAnyOrder("projects/project1.md", "projects/project2.md")
    }

    @Test
    fun `listNotes should return error for non-existent folder`() = runTest {
        // When
        val result = vaultManager.listNotes("non-existent-folder")

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Folder not found")
    }

    @Test
    fun `searchNotes should find matching notes`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val note1 = vaultPath.resolve("search-test1.md")
        val note2 = vaultPath.resolve("search-test2.md")
        val note3 = vaultPath.resolve("other.md")
        
        note1.writeText("This note contains search term")
        note2.writeText("Another note with search keyword")
        note3.writeText("Different content")
        
        // Mock parser responses for search files
        every { mockMarkdownParser.parseMarkdown(eq("This note contains search term"), eq("search-test1.md")) } returns ParsedMarkdown(
            title = "Search Test 1",
            content = "This note contains search term",
            rawContent = "This note contains search term",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = emptySet()
        )
        
        every { mockMarkdownParser.parseMarkdown(eq("Another note with search keyword"), eq("search-test2.md")) } returns ParsedMarkdown(
            title = "Search Test 2", 
            content = "Another note with search keyword",
            rawContent = "Another note with search keyword",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = emptySet()
        )
        
        every { mockMarkdownParser.parseMarkdown(eq("Different content"), eq("other.md")) } returns ParsedMarkdown(
            title = "Other Note",
            content = "Different content",
            rawContent = "Different content",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = emptySet()
        )
        
        val searchRequest = VaultSearchRequest(
            query = "search",
            limit = 10
        )

        // When
        val result = vaultManager.searchNotes(searchRequest)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val searchResults = successResult.data as List<SearchResult>
        assertThat(searchResults).hasSize(2)
        assertThat(searchResults.map { it.note.path }).containsExactlyInAnyOrder("search-test1.md", "search-test2.md")
        assertThat(searchResults.all { it.relevanceScore > 0 }).isTrue()
    }

    @Test
    fun `searchNotes should filter by tags when specified`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val note1 = vaultPath.resolve("tagged-note.md")
        note1.writeText("Content with tag")
        
        // Mock parser to return specific tags for the tagged note
        every { mockMarkdownParser.parseMarkdown(eq("Content with tag"), eq("tagged-note.md")) } returns ParsedMarkdown(
            title = "Tagged Note",
            content = "Content with tag",
            rawContent = "Content with tag",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = setOf("important")
        )
        
        val searchRequest = VaultSearchRequest(
            query = "content",
            tags = setOf("important"),
            limit = 10
        )

        // When
        val result = vaultManager.searchNotes(searchRequest)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val searchResults = successResult.data as List<SearchResult>
        assertThat(searchResults).hasSize(1)
        assertThat(searchResults[0].note.path).isEqualTo("tagged-note.md")
    }

    @Test
    fun `getAllTags should return all unique tags`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val note1 = vaultPath.resolve("note1.md")
        val note2 = vaultPath.resolve("note2.md")
        
        note1.writeText("Note with tags")
        note2.writeText("Another note")
        
        // Mock parser to return different tags for each note
        every { mockMarkdownParser.parseMarkdown(eq("Note with tags"), eq("note1.md")) } returns ParsedMarkdown(
            title = "Note 1",
            content = "Note with tags",
            rawContent = "Note with tags",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = setOf("tag1", "tag2")
        )
        
        every { mockMarkdownParser.parseMarkdown(eq("Another note"), eq("note2.md")) } returns ParsedMarkdown(
            title = "Note 2",
            content = "Another note",
            rawContent = "Another note", 
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = setOf("tag2", "tag3")
        )

        // When
        val result = vaultManager.getAllTags()

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val tags = successResult.data as List<String>
        assertThat(tags).containsExactlyInAnyOrder("tag1", "tag2", "tag3")
    }

    @Test
    fun `getBacklinks should find notes linking to target note`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val targetNote = vaultPath.resolve("target.md")
        val linkingNote = vaultPath.resolve("linking.md")
        val nonLinkingNote = vaultPath.resolve("other.md")
        
        targetNote.writeText("Target note content")
        linkingNote.writeText("This links to target")
        nonLinkingNote.writeText("No links here")
        
        // Mock parser to return wikilinks
        every { mockMarkdownParser.parseMarkdown(eq("Target note content"), eq("target.md")) } returns ParsedMarkdown(
            title = "Target",
            content = "Target note content",
            rawContent = "Target note content",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = emptySet()
        )
        
        every { mockMarkdownParser.parseMarkdown(eq("This links to target"), eq("linking.md")) } returns ParsedMarkdown(
            title = "Linking Note",
            content = "This links to target",
            rawContent = "This links to target",
            frontmatter = emptyMap(),
            wikiLinks = listOf("target"),
            tags = emptySet()
        )
        
        every { mockMarkdownParser.parseMarkdown(eq("No links here"), eq("other.md")) } returns ParsedMarkdown(
            title = "Other Note",
            content = "No links here",
            rawContent = "No links here",
            frontmatter = emptyMap(),
            wikiLinks = emptyList(),
            tags = emptySet()
        )

        // When
        val result = vaultManager.getBacklinks("target.md")

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val backlinks = successResult.data as List<String>
        assertThat(backlinks).containsExactly("linking.md")
    }

    @Test
    fun `createFolder should create new folder`() = runTest {
        // Given
        val folderPath = "new-folder"

        // When
        val result = vaultManager.createFolder(folderPath)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        assertThat(successResult.data).isEqualTo("Folder created successfully")
        assertThat(tempDir.resolve(folderPath)).exists()
    }

    @Test
    fun `createFolder should return error when folder already exists`() = runTest {
        // Given
        val folderPath = "existing-folder"
        val folder = tempDir.resolve(folderPath)
        folder.createDirectories()

        // When
        val result = vaultManager.createFolder(folderPath)

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Error::class.java)
        val errorResult = result as ObsidianResult.Error
        assertThat(errorResult.message).contains("Folder already exists")
    }

    @Test
    fun `listFolders should return all folders in vault`() = runTest {
        // Given
        val vaultPath = tempDir
        vaultPath.createDirectories()
        
        val folder1 = vaultPath.resolve("folder1")
        val folder2 = vaultPath.resolve("folder2")
        val nestedFolder = vaultPath.resolve("folder1/nested")
        
        folder1.createDirectories()
        folder2.createDirectories()
        nestedFolder.createDirectories()

        // When
        val result = vaultManager.listFolders()

        // Then
        assertThat(result).isInstanceOf(ObsidianResult.Success::class.java)
        val successResult = result as ObsidianResult.Success<*>
        val folders = successResult.data as List<String>
        assertThat(folders).containsExactlyInAnyOrder("folder1", "folder2", "folder1/nested")
    }
}