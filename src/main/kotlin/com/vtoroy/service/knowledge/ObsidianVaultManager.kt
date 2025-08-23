package com.vtoroy.service.knowledge

import com.vtoroy.dto.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

private val logger = KotlinLogging.logger {}

/**
 * Manager for Obsidian vault operations with full CRUD functionality
 */
@Service
class ObsidianVaultManager(
    private val markdownParser: MarkdownParser,
    vaultPath: String = "/app/obsidian-vault"
) {
    private val vaultPath: Path = Paths.get(vaultPath)
    private val noteCache = ConcurrentHashMap<String, MarkdownNote>()
    
    init {
        // Ensure vault directory exists
        if (!Files.exists(this.vaultPath)) {
            logger.warn { "Vault path does not exist: ${this.vaultPath}" }
        } else {
            logger.info { "Initialized Obsidian vault manager at: ${this.vaultPath}" }
        }
    }
    
    // ========== READ OPERATIONS ==========
    
    /**
     * Read a specific note by path
     */
    suspend fun readNote(notePath: String): ObsidianResult {
        return try {
            val fullPath = resolveNotePath(notePath)
            if (!Files.exists(fullPath)) {
                return ObsidianResult.Error("Note not found: $notePath")
            }
            
            val note = loadNote(fullPath)
            ObsidianResult.Success(note)
        } catch (e: Exception) {
            logger.error(e) { "Failed to read note: $notePath" }
            ObsidianResult.Error("Failed to read note: ${e.message}", e)
        }
    }
    
    /**
     * Search notes in vault
     */
    suspend fun searchNotes(request: VaultSearchRequest): ObsidianResult {
        return try {
            val allNotes = loadAllNotes()
            val results = allNotes
                .filter { note -> matchesSearch(note, request) }
                .map { note -> 
                    SearchResult(
                        note = note.toNoteInfo(),
                        relevanceScore = calculateRelevance(note, request.query),
                        matchedFragments = extractMatchedFragments(note, request.query)
                    )
                }
                .sortedByDescending { it.relevanceScore }
                .take(request.limit)
            
            ObsidianResult.Success(results)
        } catch (e: Exception) {
            logger.error(e) { "Failed to search notes: ${request.query}" }
            ObsidianResult.Error("Search failed: ${e.message}", e)
        }
    }
    
    /**
     * List notes in a folder (or all notes if folder is null)
     */
    suspend fun listNotes(folder: String? = null): ObsidianResult {
        return try {
            val searchPath = if (folder != null) {
                vaultPath.resolve(folder)
            } else {
                vaultPath
            }
            
            if (!Files.exists(searchPath)) {
                return ObsidianResult.Error("Folder not found: $folder")
            }
            
            val notes = Files.walk(searchPath)
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".md") }
                .map { loadNote(it).toNoteInfo() }
                .sorted { a, b -> a.title.compareTo(b.title) }
                .toList()
            
            ObsidianResult.Success(notes)
        } catch (e: Exception) {
            logger.error(e) { "Failed to list notes in folder: $folder" }
            ObsidianResult.Error("Failed to list notes: ${e.message}", e)
        }
    }
    
    /**
     * Get all unique tags in vault
     */
    suspend fun getAllTags(): ObsidianResult {
        return try {
            val allTags = loadAllNotes()
                .flatMap { it.tags }
                .distinct()
                .sorted()
            
            ObsidianResult.Success(allTags)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get all tags" }
            ObsidianResult.Error("Failed to get tags: ${e.message}", e)
        }
    }
    
    /**
     * Get backlinks for a note
     */
    suspend fun getBacklinks(notePath: String): ObsidianResult {
        return try {
            val noteTitle = notePath.substringAfterLast('/').substringBeforeLast('.')
            val allNotes = loadAllNotes()
            
            val backlinks = allNotes
                .filter { note -> note.wikiLinks.any { link -> 
                    link.equals(noteTitle, ignoreCase = true) || 
                    link.equals(notePath, ignoreCase = true)
                }}
                .map { it.path }
            
            ObsidianResult.Success(backlinks)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get backlinks for: $notePath" }
            ObsidianResult.Error("Failed to get backlinks: ${e.message}", e)
        }
    }
    
    // ========== WRITE OPERATIONS ==========
    
    /**
     * Create a new note
     */
    suspend fun createNote(request: CreateNoteRequest): ObsidianResult {
        return try {
            val fullPath = resolveNotePath(request.path)
            
            if (Files.exists(fullPath)) {
                return ObsidianResult.Error("Note already exists: ${request.path}")
            }
            
            // Ensure parent directories exist
            Files.createDirectories(fullPath.parent)
            
            // Create frontmatter with title and tags
            val frontmatter = request.frontmatter.toMutableMap()
            frontmatter["title"] = request.title
            if (request.tags.isNotEmpty()) {
                frontmatter["tags"] = request.tags.toList()
            }
            frontmatter["created"] = LocalDateTime.now().toString()
            
            // Generate markdown content
            val markdownContent = markdownParser.createMarkdownContent(
                content = request.content,
                frontmatter = frontmatter
            )
            
            // Write file
            Files.writeString(fullPath, markdownContent, StandardCharsets.UTF_8)
            
            // Load and cache the new note
            val note = loadNote(fullPath)
            noteCache[request.path] = note
            
            logger.info { "Created note: ${request.path}" }
            ObsidianResult.Success(note)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create note: ${request.path}" }
            ObsidianResult.Error("Failed to create note: ${e.message}", e)
        }
    }
    
    /**
     * Update an existing note
     */
    suspend fun updateNote(request: UpdateNoteRequest): ObsidianResult {
        return try {
            val fullPath = resolveNotePath(request.path)
            
            if (!Files.exists(fullPath)) {
                return ObsidianResult.Error("Note not found: ${request.path}")
            }
            
            // Load current note
            val currentNote = loadNote(fullPath)
            
            // Build updated frontmatter
            val updatedFrontmatter = currentNote.frontmatter.toMutableMap()
            request.title?.let { updatedFrontmatter["title"] = it }
            request.tags?.let { updatedFrontmatter["tags"] = it.toList() }
            request.frontmatter?.let { updatedFrontmatter.putAll(it) }
            updatedFrontmatter["modified"] = LocalDateTime.now().toString()
            
            // Use new content or keep existing
            val newContent = request.content ?: currentNote.content
            
            // Generate updated markdown
            val markdownContent = markdownParser.createMarkdownContent(
                content = newContent,
                frontmatter = updatedFrontmatter
            )
            
            // Write file
            Files.writeString(fullPath, markdownContent, StandardCharsets.UTF_8)
            
            // Reload and cache
            val updatedNote = loadNote(fullPath)
            noteCache[request.path] = updatedNote
            
            logger.info { "Updated note: ${request.path}" }
            ObsidianResult.Success(updatedNote)
        } catch (e: Exception) {
            logger.error(e) { "Failed to update note: ${request.path}" }
            ObsidianResult.Error("Failed to update note: ${e.message}", e)
        }
    }
    
    /**
     * Delete a note
     */
    suspend fun deleteNote(notePath: String): ObsidianResult {
        return try {
            val fullPath = resolveNotePath(notePath)
            
            if (!Files.exists(fullPath)) {
                return ObsidianResult.Error("Note not found: $notePath")
            }
            
            // Remove from cache
            noteCache.remove(notePath)
            
            // Delete file
            Files.delete(fullPath)
            
            logger.info { "Deleted note: $notePath" }
            ObsidianResult.Success("Note deleted successfully")
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete note: $notePath" }
            ObsidianResult.Error("Failed to delete note: ${e.message}", e)
        }
    }
    
    /**
     * Move/rename a note
     */
    suspend fun moveNote(request: MoveNoteRequest): ObsidianResult {
        return try {
            val oldPath = resolveNotePath(request.oldPath)
            val newPath = resolveNotePath(request.newPath)
            
            if (!Files.exists(oldPath)) {
                return ObsidianResult.Error("Source note not found: ${request.oldPath}")
            }
            
            if (Files.exists(newPath)) {
                return ObsidianResult.Error("Destination already exists: ${request.newPath}")
            }
            
            // Ensure parent directories exist
            Files.createDirectories(newPath.parent)
            
            // Move file
            Files.move(oldPath, newPath)
            
            // Update cache
            noteCache.remove(request.oldPath)
            val movedNote = loadNote(newPath)
            noteCache[request.newPath] = movedNote
            
            logger.info { "Moved note: ${request.oldPath} -> ${request.newPath}" }
            ObsidianResult.Success(movedNote)
        } catch (e: Exception) {
            logger.error(e) { "Failed to move note: ${request.oldPath} -> ${request.newPath}" }
            ObsidianResult.Error("Failed to move note: ${e.message}", e)
        }
    }
    
    // ========== FOLDER OPERATIONS ==========
    
    /**
     * Create a folder
     */
    suspend fun createFolder(folderPath: String): ObsidianResult {
        return try {
            val fullPath = vaultPath.resolve(folderPath)
            
            if (Files.exists(fullPath)) {
                return ObsidianResult.Error("Folder already exists: $folderPath")
            }
            
            Files.createDirectories(fullPath)
            
            logger.info { "Created folder: $folderPath" }
            ObsidianResult.Success("Folder created successfully")
        } catch (e: Exception) {
            logger.error(e) { "Failed to create folder: $folderPath" }
            ObsidianResult.Error("Failed to create folder: ${e.message}", e)
        }
    }
    
    /**
     * List all folders in vault
     */
    suspend fun listFolders(): ObsidianResult {
        return try {
            val folders = Files.walk(vaultPath)
                .filter { Files.isDirectory(it) && it != vaultPath }
                .map { vaultPath.relativize(it).toString() }
                .sorted()
                .toList()
            
            ObsidianResult.Success(folders)
        } catch (e: Exception) {
            logger.error(e) { "Failed to list folders" }
            ObsidianResult.Error("Failed to list folders: ${e.message}", e)
        }
    }
    
    // ========== PRIVATE HELPER METHODS ==========
    
    private fun resolveNotePath(notePath: String): Path {
        val cleanPath = notePath.removePrefix("/").let { 
            if (it.endsWith(".md")) it else "$it.md" 
        }
        return vaultPath.resolve(cleanPath)
    }
    
    private fun loadNote(path: Path): MarkdownNote {
        val relativePath = vaultPath.relativize(path).toString()
        
        // Check cache first
        noteCache[relativePath]?.let { cached ->
            // Simple freshness check - reload if file is newer
            val fileTime = Files.getLastModifiedTime(path).toInstant()
            val cacheTime = cached.modifiedAt.atZone(ZoneId.systemDefault()).toInstant()
            if (fileTime <= cacheTime) {
                return cached
            }
        }
        
        val rawContent = Files.readString(path, StandardCharsets.UTF_8)
        val parsed = markdownParser.parseMarkdown(rawContent, relativePath)
        val fileAttrs = Files.readAttributes(path, BasicFileAttributes::class.java)
        
        return MarkdownNote(
            path = relativePath,
            title = parsed.title,
            content = parsed.content,
            rawContent = parsed.rawContent,
            frontmatter = parsed.frontmatter,
            tags = parsed.tags,
            wikiLinks = parsed.wikiLinks,
            backlinks = emptyList(), // Will be computed separately
            createdAt = LocalDateTime.ofInstant(fileAttrs.creationTime().toInstant(), ZoneId.systemDefault()),
            modifiedAt = LocalDateTime.ofInstant(fileAttrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()),
            size = fileAttrs.size()
        ).also { noteCache[relativePath] = it }
    }
    
    private fun loadAllNotes(): List<MarkdownNote> {
        return try {
            Files.walk(vaultPath)
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".md") }
                .map { loadNote(it) }
                .toList()
        } catch (e: Exception) {
            logger.error(e) { "Failed to load all notes" }
            emptyList()
        }
    }
    
    private fun matchesSearch(note: MarkdownNote, request: VaultSearchRequest): Boolean {
        val query = request.query.lowercase()
        
        // If tags are specified, search by tags primarily
        val tagsMatch = request.tags?.let { requiredTags ->
            val matches = requiredTags.all { tag -> note.tags.contains(tag) }
            logger.debug { "Note ${note.path}: tags=${note.tags}, required=${requiredTags}, match=$matches" }
            matches
        } ?: true
        
        // Text search in title and content (skip if searching only by tags)
        val textMatch = if (request.tags?.isNotEmpty() == true && query.startsWith("#")) {
            // Tag search - skip text matching for queries like "#travel"
            true
        } else {
            note.title.lowercase().contains(query) ||
            note.content.lowercase().contains(query)
        }
        
        // Folder filter
        val folderMatch = request.folder?.let { folder ->
            note.path.startsWith(folder)
        } ?: true
        
        val result = textMatch && folderMatch && tagsMatch
        logger.debug { "Search match for ${note.path}: text=$textMatch, folder=$folderMatch, tags=$tagsMatch, result=$result" }
        return result
    }
    
    private fun calculateRelevance(note: MarkdownNote, query: String): Double {
        val queryLower = query.lowercase()
        var score = 0.0
        
        // Title match (highest weight)
        if (note.title.lowercase().contains(queryLower)) {
            score += 10.0
            if (note.title.lowercase() == queryLower) score += 5.0
        }
        
        // Content matches
        val contentLower = note.content.lowercase()
        val matches = Regex(Regex.escape(queryLower)).findAll(contentLower).count()
        score += matches * 1.0
        
        // Tag matches
        note.tags.forEach { tag ->
            if (tag.lowercase().contains(queryLower)) score += 3.0
        }
        
        // Boost for shorter notes (more focused)
        if (note.content.length < 1000) score += 1.0
        
        return score
    }
    
    private fun extractMatchedFragments(note: MarkdownNote, query: String): List<String> {
        val queryLower = query.lowercase()
        val content = note.content
        val fragments = mutableListOf<String>()
        
        val regex = Regex("(.{0,50}${Regex.escape(queryLower)}.{0,50})", RegexOption.IGNORE_CASE)
        regex.findAll(content).take(3).forEach { match ->
            fragments.add("...${match.value.trim()}...")
        }
        
        return fragments
    }
    
    private fun MarkdownNote.toNoteInfo(): NoteInfo = NoteInfo(
        path = path,
        title = title,
        tags = tags,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        size = size
    )
}