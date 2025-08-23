package com.vtoroy.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ChatRequest(
    @field:NotBlank(message = "Query cannot be blank")
    @field:Size(max = 10000, message = "Query is too long")
    val query: String,
    
    @field:NotBlank(message = "Session ID cannot be blank")
    @field:Size(max = 100, message = "Session ID is too long")
    val sessionId: String
)