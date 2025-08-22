package com.jarvis.agent.memory

import com.jarvis.agent.memory.contract.MemoryClassifier
import com.jarvis.agent.memory.contract.MemoryType
import mu.KotlinLogging
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.stereotype.Component
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Semantic memory classifier using embedding similarity
 * Uses pre-computed type vectors for classification
 */
@Component
class SemanticMemoryClassifier(
    private val embeddingModel: EmbeddingModel
) : MemoryClassifier {
    
    private val logger = KotlinLogging.logger {}
    
    // Pre-computed type vectors based on typical content examples
    // In production, these would be learned from training data
    private val typeExamples = mapOf(
        "meeting" to listOf(
            "Meeting agenda discussion participants action items",
            "Daily standup team progress blockers next steps",
            "Retrospective what went well improvements action points"
        ),
        "project" to listOf(
            "Project requirements specifications timeline deliverables",
            "Implementation plan architecture components modules",
            "Project status milestones progress roadmap goals"
        ),
        "task" to listOf(
            "TODO implement feature fix bug complete task",
            "Action item assign responsible deadline priority",
            "Checklist items complete pending review done"
        ),
        "note" to listOf(
            "Important information remember key points summary",
            "Research findings insights observations analysis",
            "Learning notes concepts ideas understanding knowledge"
        ),
        "code" to listOf(
            "Function method implementation algorithm logic code",
            "Class structure design pattern architecture component",
            "Bug fix issue solution implementation technical details"
        ),
        "research" to listOf(
            "Research study analysis findings conclusions references",
            "Investigation evidence data sources methodology results",
            "Academic paper literature review theoretical framework"
        ),
        "documentation" to listOf(
            "Documentation guide tutorial instructions how-to manual",
            "API reference specification parameters examples usage",
            "User guide setup configuration troubleshooting FAQ"
        )
    )
    
    // Lazy initialization of type vectors
    private val typeVectors by lazy {
        logger.info { "Computing semantic type vectors for ${typeExamples.size} types" }
        typeExamples.mapValues { (type, examples) ->
            try {
                val vectors = examples.map { embeddingModel.embed(it) }
                // Average the example vectors to create type prototype
                averageVectors(vectors)
            } catch (e: Exception) {
                logger.error(e) { "Failed to compute vector for type: $type" }
                FloatArray(384) { 0f } // Fallback empty vector
            }
        }
    }
    
    override suspend fun classify(content: String, metadata: Map<String, Any>?): MemoryType {
        if (content.isBlank()) {
            return MemoryType("unknown", "semantic", 0f, mapOf("reason" to "empty_content"))
        }
        
        return try {
            val contentVector = embeddingModel.embed(content)
            val similarities = typeVectors.mapValues { (_, typeVector) ->
                cosineSimilarity(contentVector, typeVector)
            }
            
            val (bestType, similarity) = similarities.maxByOrNull { it.value }
                ?: return MemoryType("unknown", "semantic", 0f, mapOf("reason" to "no_similarities"))
            
            val confidence = similarity.coerceIn(0f, 1f)
            val attributes = mapOf<String, Any>(
                "similarities" to similarities,
                "contentLength" to content.length,
                "vectorDimension" to contentVector.size
            )

            MemoryType(bestType, "semantic", confidence, attributes)
            
        } catch (e: Exception) {
            logger.error(e) { "Semantic classification failed for content length ${content.length}" }
            MemoryType("unknown", "semantic", 0f, mapOf("error" to (e.message ?: "unknown_error")))
        }
    }
    
    override fun getSupportedTypes(): Set<String> = typeExamples.keys
    
    override fun getClassifierType(): String = "semantic"
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i].pow(2)
            normB += b[i].pow(2)
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0f) 0f else dotProduct / denominator
    }
    
    private fun averageVectors(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(384) { 0f }
        
        val dimension = vectors.first().size
        val result = FloatArray(dimension) { 0f }
        
        for (vector in vectors) {
            for (i in vector.indices) {
                result[i] += vector[i]
            }
        }
        
        // Average
        for (i in result.indices) {
            result[i] = result[i] / vectors.size
        }
        
        return result
    }
}