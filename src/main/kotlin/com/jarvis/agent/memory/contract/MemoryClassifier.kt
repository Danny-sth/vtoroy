package com.jarvis.agent.memory.contract

/**
 * Interface for memory classification systems
 * Supports multi-level classification with confidence scoring
 */
interface MemoryClassifier {
    suspend fun classify(content: String, metadata: Map<String, Any>?): MemoryType
    fun getSupportedTypes(): Set<String>
    fun getClassifierType(): String
}

/**
 * Represents a classified memory type with confidence and attributes
 */
data class MemoryType(
    val primary: String,
    val secondary: String? = null,
    val confidence: Float,
    val attributes: Map<String, Any> = emptyMap()
) {
    init {
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1, got $confidence" }
    }
}

/**
 * Configuration for memory classification
 */
data class ClassificationConfig(
    val semanticWeight: Float = 0.5f,
    val structuralWeight: Float = 0.3f,
    val contextWeight: Float = 0.2f,
    val minimumConfidence: Float = 0.1f,
    val enableEnsemble: Boolean = true
) {
    init {
        require(semanticWeight + structuralWeight + contextWeight == 1f) {
            "Weights must sum to 1.0"
        }
    }
}