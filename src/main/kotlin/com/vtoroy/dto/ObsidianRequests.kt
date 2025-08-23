package com.vtoroy.dto

import java.time.LocalDateTime

/**
 * Request to create a new note in Obsidian vault
 */
data class CreateNoteRequest(
    val path: String,
    val title: String,
    val content: String,
    val tags: Set<String> = emptySet(),
    val frontmatter: Map<String, Any> = emptyMap()
)

/**
 * Request to update an existing note
 */
data class UpdateNoteRequest(
    val path: String,
    val content: String? = null,
    val title: String? = null,
    val tags: Set<String>? = null,
    val frontmatter: Map<String, Any>? = null
)

/**
 * Request to move/rename a note
 */
data class MoveNoteRequest(
    val oldPath: String,
    val newPath: String
)

/**
 * Search request in vault
 */
data class VaultSearchRequest(
    val query: String,
    val folder: String? = null,
    val tags: Set<String>? = null,
    val limit: Int = 50
)

/**
 * Represents a markdown note from Obsidian vault
 */
data class MarkdownNote(
    val path: String,
    val title: String,
    val content: String,
    val rawContent: String,
    val frontmatter: Map<String, Any>,
    val tags: Set<String>,
    val wikiLinks: List<String>,
    val backlinks: List<String>,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val size: Long
)

/**
 * Brief note information for listings
 */
data class NoteInfo(
    val path: String,
    val title: String,
    val tags: Set<String>,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime,
    val size: Long
)

/**
 * Search result with relevance score
 */
data class SearchResult(
    val note: NoteInfo,
    val relevanceScore: Double,
    val matchedFragments: List<String>
)

/**
 * Obsidian action types for agent routing
 */
enum class ObsidianAction {
    // Read operations
    READ_NOTE,
    SEARCH_VAULT,
    LIST_NOTES,
    GET_TAGS,
    GET_BACKLINKS,
    
    // Write operations
    CREATE_NOTE,
    UPDATE_NOTE,
    DELETE_NOTE,
    MOVE_NOTE,
    
    // Folder operations
    CREATE_FOLDER,
    DELETE_FOLDER,
    LIST_FOLDERS,
    
    // Metadata operations
    UPDATE_FRONTMATTER,
    ADD_TAG,
    REMOVE_TAG,
    
    // Utility operations
    BACKUP_VAULT,
    VALIDATE_LINKS,
    
    // User interaction
    ASK_USER
}

/**
 * Result of Obsidian operations
 */
sealed class ObsidianResult {
    data class Success<T>(val data: T) : ObsidianResult()
    data class Error(val message: String, val cause: Throwable? = null) : ObsidianResult()
}