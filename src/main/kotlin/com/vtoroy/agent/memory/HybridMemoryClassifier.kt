package com.vtoroy.agent.memory

import com.vtoroy.agent.memory.contract.ClassificationConfig
import com.vtoroy.agent.memory.contract.MemoryClassifier
import com.vtoroy.agent.memory.contract.MemoryType
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Hybrid memory classifier that combines multiple classification approaches
 * Uses ensemble voting to determine the best memory type
 */
@Component
class HybridMemoryClassifier(
    private val semanticClassifier: SemanticMemoryClassifier,
    private val structuralClassifier: StructuralMemoryClassifier,
    private val contextClassifier: ContextMemoryClassifier,
    private val config: ClassificationConfig = ClassificationConfig()
) : MemoryClassifier {
    
    private val logger = KotlinLogging.logger {}
    
    override suspend fun classify(content: String, metadata: Map<String, Any>?): MemoryType {
        logger.debug { "Classifying memory with ${content.length} characters" }
        
        if (!config.enableEnsemble) {
            // Fallback to semantic classification only
            return semanticClassifier.classify(content, metadata)
        }
        
        val candidates = listOf(
            semanticClassifier.classify(content, metadata),
            structuralClassifier.classify(content, metadata),
            contextClassifier.classify(content, metadata)
        )
        
        logger.debug { "Classification candidates: ${candidates.map { "${it.primary}(${it.confidence})" }}" }
        
        return ensemble(candidates)
    }
    
    override fun getSupportedTypes(): Set<String> {
        return setOf(
            semanticClassifier.getSupportedTypes(),
            structuralClassifier.getSupportedTypes(),
            contextClassifier.getSupportedTypes()
        ).flatten().toSet()
    }
    
    override fun getClassifierType(): String = "hybrid"
    
    private fun ensemble(candidates: List<MemoryType>): MemoryType {
        val weights = mapOf(
            "semantic" to config.semanticWeight,
            "structural" to config.structuralWeight,
            "context" to config.contextWeight
        )
        
        // Group by primary type and calculate weighted scores
        val typeScores = candidates
            .groupBy { it.primary }
            .mapValues { (_, types) ->
                types.sumOf { type ->
                    val classifierType = when (type.secondary) {
                        "semantic", "structural", "context" -> type.secondary!!
                        else -> "semantic" // fallback
                    }
                    (type.confidence * (weights[classifierType] ?: 0f)).toDouble()
                }
            }
        
        // Find best type
        val (bestType, bestScore) = typeScores.maxByOrNull { it.value }
            ?: return createUnknownType()
        
        val confidence = bestScore.toFloat().coerceAtMost(1f)
        
        if (confidence < config.minimumConfidence) {
            logger.debug { "Best confidence $confidence below threshold ${config.minimumConfidence}" }
            return createUnknownType()
        }
        
        // Get attributes from the best candidate of this type
        val bestCandidate = candidates.find { it.primary == bestType } ?: createUnknownType()
        val attributes = bestCandidate.attributes + mapOf(
            "ensembleScore" to bestScore,
            "candidateCount" to candidates.size,
            "weights" to weights
        )
        
        logger.debug { "Selected type: $bestType with confidence $confidence" }
        
        return MemoryType(
            primary = bestType,
            secondary = "ensemble",
            confidence = confidence,
            attributes = attributes
        )
    }
    
    private fun createUnknownType(): MemoryType {
        return MemoryType(
            primary = "unknown",
            secondary = "ensemble",
            confidence = 0f,
            attributes = mapOf("reason" to "insufficient_confidence")
        )
    }
}