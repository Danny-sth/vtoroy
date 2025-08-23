package com.vtoroy.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class KnowledgeStatusResponse(
    val totalFiles: Long,
    val indexedFiles: Long,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val lastSync: LocalDateTime?,
    val status: KnowledgeStatus
)

enum class KnowledgeStatus {
    READY,
    SYNCING,
    ERROR,
    EMPTY
}