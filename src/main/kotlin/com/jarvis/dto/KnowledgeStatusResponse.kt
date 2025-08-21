package com.jarvis.dto

import java.time.LocalDateTime

data class KnowledgeStatusResponse(
    val totalFiles: Long,
    val indexedFiles: Long,
    val lastSync: LocalDateTime?,
    val status: KnowledgeStatus
)

enum class KnowledgeStatus {
    READY,
    SYNCING,
    ERROR,
    EMPTY
}