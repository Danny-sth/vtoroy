package com.vtoroy.agent.memory

import com.vtoroy.agent.memory.contract.MemoryClassifier
import com.vtoroy.agent.memory.contract.MemoryType
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Context memory classifier using metadata and environmental context
 * Analyzes file paths, tags, timestamps, and other contextual information
 */
@Component
class ContextMemoryClassifier : MemoryClassifier {
    
    private val logger = KotlinLogging.logger {}
    
    // Path-based classification rules
    private val pathPatterns = mapOf(
        "meeting" to listOf(
            Regex("/meetings?/", RegexOption.IGNORE_CASE),
            Regex("/standup/", RegexOption.IGNORE_CASE),
            Regex("/retrospective/", RegexOption.IGNORE_CASE),
            Regex("meeting-\\d{4}-\\d{2}-\\d{2}", RegexOption.IGNORE_CASE)
        ),
        "project" to listOf(
            Regex("/projects?/", RegexOption.IGNORE_CASE),
            Regex("/specs?/", RegexOption.IGNORE_CASE),
            Regex("/requirements/", RegexOption.IGNORE_CASE),
            Regex("/roadmap/", RegexOption.IGNORE_CASE)
        ),
        "task" to listOf(
            Regex("/tasks?/", RegexOption.IGNORE_CASE),
            Regex("/todos?/", RegexOption.IGNORE_CASE),
            Regex("/backlog/", RegexOption.IGNORE_CASE)
        ),
        "code" to listOf(
            Regex("/code/", RegexOption.IGNORE_CASE),
            Regex("/src/", RegexOption.IGNORE_CASE),
            Regex("/implementation/", RegexOption.IGNORE_CASE),
            Regex("\\.(java|kt|js|py|cpp|cs|go|rs)$", RegexOption.IGNORE_CASE)
        ),
        "documentation" to listOf(
            Regex("/docs?/", RegexOption.IGNORE_CASE),
            Regex("/documentation/", RegexOption.IGNORE_CASE),
            Regex("/wiki/", RegexOption.IGNORE_CASE),
            Regex("/guides?/", RegexOption.IGNORE_CASE)
        ),
        "research" to listOf(
            Regex("/research/", RegexOption.IGNORE_CASE),
            Regex("/studies/", RegexOption.IGNORE_CASE),
            Regex("/analysis/", RegexOption.IGNORE_CASE),
            Regex("/papers?/", RegexOption.IGNORE_CASE)
        )
    )
    
    override suspend fun classify(content: String, metadata: Map<String, Any>?): MemoryType {
        if (metadata.isNullOrEmpty()) {
            return MemoryType("unknown", "context", 0f, mapOf("reason" to "no_metadata"))
        }
        
        val contextScores = mutableMapOf<String, Float>()
        val analysisDetails = mutableMapOf<String, Any>()
        
        // Analyze file path
        metadata["path"]?.toString()?.let { path ->
            val pathScore = analyzeFilePath(path)
            contextScores.putAll(pathScore)
            analysisDetails["pathAnalysis"] = pathScore
        }
        
        // Analyze tags
        metadata["tags"]?.let { tags ->
            val tagScore = analyzeTags(tags)
            tagScore.forEach { (type, score) ->
                contextScores[type] = (contextScores[type] ?: 0f) + score * 0.8f
            }
            analysisDetails["tagAnalysis"] = tagScore
        }
        
        // Analyze timestamps
        val temporalScore = analyzeTemporalContext(metadata)
        temporalScore.forEach { (type, score) ->
            contextScores[type] = (contextScores[type] ?: 0f) + score * 0.3f
        }
        analysisDetails["temporalAnalysis"] = temporalScore
        
        // Analyze source information
        metadata["source"]?.toString()?.let { source ->
            val sourceScore = analyzeSource(source)
            sourceScore.forEach { (type, score) ->
                contextScores[type] = (contextScores[type] ?: 0f) + score * 0.5f
            }
            analysisDetails["sourceAnalysis"] = sourceScore
        }
        
        // Find best type
        val (bestType, bestScore) = contextScores.maxByOrNull { it.value }
            ?: return MemoryType("unknown", "context", 0f, mapOf("reason" to "no_context_match"))
        
        val confidence = bestScore.coerceAtMost(1f)
        val attributes = mapOf(
            "contextScores" to contextScores,
            "analysisDetails" to analysisDetails,
            "metadataKeys" to metadata.keys.toList()
        )
        
        logger.debug { "Context classification: $bestType with confidence $confidence from ${contextScores.size} context signals" }
        
        return MemoryType(bestType, "context", confidence, attributes)
    }
    
    override fun getSupportedTypes(): Set<String> = pathPatterns.keys
    
    override fun getClassifierType(): String = "context"
    
    private fun analyzeFilePath(path: String): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        
        pathPatterns.forEach { (type, patterns) ->
            val matchCount = patterns.count { pattern ->
                pattern.containsMatchIn(path)
            }
            if (matchCount > 0) {
                scores[type] = (matchCount.toFloat() / patterns.size) * 0.9f
            }
        }
        
        return scores
    }
    
    private fun analyzeTags(tags: Any): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        val tagList = when (tags) {
            is List<*> -> tags.mapNotNull { it?.toString()?.lowercase() }
            is String -> listOf(tags.lowercase())
            else -> emptyList()
        }
        
        val tagMappings = mapOf(
            "meeting" to listOf("meeting", "standup", "retrospective", "sync", "review"),
            "project" to listOf("project", "epic", "milestone", "feature", "sprint"),
            "task" to listOf("task", "todo", "action", "bug", "issue", "ticket"),
            "code" to listOf("code", "implementation", "technical", "development", "programming"),
            "documentation" to listOf("docs", "documentation", "guide", "tutorial", "manual", "wiki"),
            "research" to listOf("research", "study", "analysis", "investigation", "findings"),
            "note" to listOf("note", "memo", "reminder", "observation", "insight")
        )
        
        tagMappings.forEach { (type, keywords) ->
            val matchingTags = tagList.intersect(keywords.toSet())
            if (matchingTags.isNotEmpty()) {
                scores[type] = (matchingTags.size.toFloat() / keywords.size).coerceAtMost(1f)
            }
        }
        
        return scores
    }
    
    private fun analyzeTemporalContext(metadata: Map<String, Any>): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        
        // Analyze creation/modification time
        listOf("created", "lastModified", "modified", "timestamp").forEach { key ->
            metadata[key]?.let { timestamp ->
                val instant = when (timestamp) {
                    is Long -> Instant.ofEpochMilli(timestamp)
                    is String -> try { Instant.parse(timestamp) } catch (e: Exception) { null }
                    else -> null
                }
                
                instant?.let {
                    val age = ChronoUnit.HOURS.between(it, Instant.now())
                    when {
                        age < 24 -> scores["task"] = (scores["task"] ?: 0f) + 0.3f // Recent items might be tasks
                        age < 168 -> scores["note"] = (scores["note"] ?: 0f) + 0.2f // Weekly notes
                        else -> scores["documentation"] = (scores["documentation"] ?: 0f) + 0.1f // Older items might be docs
                    }
                }
            }
        }
        
        return scores
    }
    
    private fun analyzeSource(source: String): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        
        val sourceMappings = mapOf(
            "obsidian" to mapOf("note" to 0.6f, "project" to 0.3f, "research" to 0.4f),
            "notion" to mapOf("project" to 0.7f, "task" to 0.5f, "documentation" to 0.4f),
            "github" to mapOf("code" to 0.8f, "documentation" to 0.6f, "project" to 0.4f),
            "jira" to mapOf("task" to 0.9f, "project" to 0.6f),
            "confluence" to mapOf("documentation" to 0.8f, "project" to 0.5f),
            "slack" to mapOf("meeting" to 0.6f, "task" to 0.4f, "note" to 0.3f)
        )
        
        sourceMappings[source.lowercase()]?.let { mappings ->
            scores.putAll(mappings)
        }
        
        return scores
    }
}