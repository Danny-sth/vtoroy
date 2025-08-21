package com.jarvis.dto

import jakarta.validation.constraints.NotBlank

data class KnowledgeSyncRequest(
    @field:NotBlank(message = "Vault path cannot be blank")
    val vaultPath: String
)