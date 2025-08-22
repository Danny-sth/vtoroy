package com.jarvis.agent.memory

import com.jarvis.agent.memory.contract.MemoryClassifier
import com.jarvis.agent.memory.contract.MemoryType
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Structural memory classifier using pattern matching
 * Analyzes document structure, formatting, and specific markers
 */
@Component
class StructuralMemoryClassifier : MemoryClassifier {
    
    private val logger = KotlinLogging.logger {}
    
    // Pattern definitions for different memory types
    private val typePatterns = mapOf(
        "meeting" to PatternSet(
            listOf(
                Regex("##\\s*(Meeting|Встреча|Совещание|Standup)", RegexOption.IGNORE_CASE),
                Regex("(Agenda|Повестка|Participants|Участники):", RegexOption.IGNORE_CASE),
                Regex("Action\\s+(items?|points?)", RegexOption.IGNORE_CASE),
                Regex("\\b(discussed|decided|agreed)\\b", RegexOption.IGNORE_CASE)
            ),
            weight = 1.0f
        ),
        
        "task" to PatternSet(
            listOf(
                Regex("- \\[ \\]|\\* \\[ \\]"), // Markdown checkboxes
                Regex("\\b(TODO|FIXME|HACK|NOTE)\\b", RegexOption.IGNORE_CASE),
                Regex("\\b(task|задача|выполнить|сделать)\\b", RegexOption.IGNORE_CASE),
                Regex("(due|deadline|срок)\\s*(date|дата)?:?\\s*\\d", RegexOption.IGNORE_CASE)
            ),
            weight = 1.2f
        ),
        
        "code" to PatternSet(
            listOf(
                Regex("```\\w*"), // Code blocks
                Regex("\\b(function|class|interface|enum|struct)\\s+\\w+"),
                Regex("\\b(import|from|include|require)\\s+"),
                Regex("\\w+\\(.*\\)\\s*[{:]"), // Function definitions
                Regex("\\b(var|let|const|def|public|private)\\b")
            ),
            weight = 1.3f
        ),
        
        "research" to PatternSet(
            listOf(
                Regex("(sources?|источники?|references?|ссылки):", RegexOption.IGNORE_CASE),
                Regex("\\[\\d+\\]|\\(\\d{4}\\)|doi:", RegexOption.IGNORE_CASE), // Citations
                Regex("\\b(study|research|analysis|анализ|исследование)\\b", RegexOption.IGNORE_CASE),
                Regex("\\b(hypothesis|conclusion|findings|выводы)\\b", RegexOption.IGNORE_CASE)
            ),
            weight = 1.0f
        ),
        
        "documentation" to PatternSet(
            listOf(
                Regex("##?\\s*(API|Usage|Installation|Установка)", RegexOption.IGNORE_CASE),
                Regex("\\b(example|пример|tutorial|guide)\\b", RegexOption.IGNORE_CASE),
                Regex("```\\s*(bash|shell|console|cmd)"), // Command examples
                Regex("\\$\\s+\\w+|>\\s+\\w+") // Command prompts
            ),
            weight = 1.0f
        ),
        
        "project" to PatternSet(
            listOf(
                Regex("##?\\s*(Project|Проект|Requirements|Требования)", RegexOption.IGNORE_CASE),
                Regex("\\b(milestone|roadmap|timeline|план|этап)\\b", RegexOption.IGNORE_CASE),
                Regex("\\b(deliverable|sprint|release|версия)\\b", RegexOption.IGNORE_CASE),
                Regex("##\\s*(Status|Progress|Статус|Прогресс)", RegexOption.IGNORE_CASE)
            ),
            weight = 1.0f
        ),
        
        "note" to PatternSet(
            listOf(
                Regex("^#\\s+"), // Simple heading
                Regex("\\b(note|заметка|important|важно)\\b", RegexOption.IGNORE_CASE),
                Regex("\\b(remember|помнить|keep in mind|учесть)\\b", RegexOption.IGNORE_CASE)
            ),
            weight = 0.8f // Lower weight as it's more generic
        )
    )
    
    private data class PatternSet(
        val patterns: List<Regex>,
        val weight: Float = 1.0f
    )
    
    override suspend fun classify(content: String, metadata: Map<String, Any>?): MemoryType {
        if (content.isBlank()) {
            return MemoryType("unknown", "structural", 0f, mapOf("reason" to "empty_content"))
        }
        
        val matches = typePatterns.mapValues { (type, patternSet) ->
            val matchCount = patternSet.patterns.count { pattern ->
                pattern.containsMatchIn(content)
            }
            
            MatchResult(
                matchCount = matchCount,
                totalPatterns = patternSet.patterns.size,
                weight = patternSet.weight,
                score = (matchCount.toFloat() / patternSet.patterns.size) * patternSet.weight
            )
        }
        
        val bestMatch = matches.maxByOrNull { it.value.score }
            ?: return MemoryType("unknown", "structural", 0f, mapOf("reason" to "no_patterns"))
        
        val (bestType, matchResult) = bestMatch
        val confidence = matchResult.score.coerceAtMost(1f)
        
        val attributes = mapOf(
            "matchedPatterns" to matchResult.matchCount,
            "totalPatterns" to matchResult.totalPatterns,
            "weight" to matchResult.weight,
            "allMatches" to matches.mapValues { it.value.matchCount },
            "contentLines" to content.lines().size,
            "analysisDetails" to analyzeStructure(content)
        )
        
        logger.debug { "Structural classification: $bestType (${matchResult.matchCount}/${matchResult.totalPatterns} patterns)" }
        
        return MemoryType(bestType, "structural", confidence, attributes)
    }
    
    override fun getSupportedTypes(): Set<String> = typePatterns.keys
    
    override fun getClassifierType(): String = "structural"
    
    private data class MatchResult(
        val matchCount: Int,
        val totalPatterns: Int,
        val weight: Float,
        val score: Float
    )
    
    private fun analyzeStructure(content: String): Map<String, Any> {
        val lines = content.lines()
        
        return mapOf(
            "hasHeadings" to lines.any { it.trim().startsWith("#") },
            "hasLists" to lines.any { it.trim().startsWith("-") || it.trim().startsWith("*") || it.trim().matches(Regex("\\d+\\..*")) },
            "hasCodeBlocks" to content.contains("```"),
            "hasLinks" to (content.contains("http") || (content.contains("[") && content.contains("]"))),
            "hasCheckboxes" to lines.any { it.contains("[ ]") || it.contains("[x]") },
            "lineCount" to lines.size,
            "avgLineLength" to if (lines.isNotEmpty()) lines.sumOf { it.length } / lines.size else 0,
            "hasYamlFrontmatter" to content.trimStart().startsWith("---")
        )
    }
}