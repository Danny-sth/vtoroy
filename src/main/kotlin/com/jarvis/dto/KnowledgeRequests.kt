package com.jarvis.dto

import jakarta.validation.constraints.NotBlank

/**
 * Request for syncing knowledge from a specific source
 */
data class KnowledgeSyncSourceRequest(
    @field:NotBlank(message = "Source ID is required")
    val sourceId: String,
    
    val config: Map<String, Any> = emptyMap()
)

/**
 * Request for searching knowledge with optional source filter
 */
data class KnowledgeSearchRequest(
    @field:NotBlank(message = "Query is required")
    val query: String,
    
    val limit: Int = 5,
    
    val sourceFilter: String? = null
)