package com.vtoroy.dto

import java.time.LocalDateTime

data class ChatResponse(
    val response: String,
    val sessionId: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val metadata: Map<String, Any>? = null
)