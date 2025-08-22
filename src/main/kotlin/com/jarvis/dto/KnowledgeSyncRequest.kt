package com.jarvis.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class KnowledgeSyncRequest(
    @JsonProperty("sourceId") 
    val sourceId: String = "obsidian",
    
    @JsonProperty("config")
    val config: Map<String, Any> = emptyMap(),
    
    // Backward compatibility for old API
    @JsonProperty("vaultPath")
    val vaultPath: String? = null
) {
    @JsonIgnore
    fun getEffectiveConfig(): Map<String, Any> {
        return if (vaultPath != null) {
            config + ("vaultPath" to vaultPath)
        } else {
            config
        }
    }
}