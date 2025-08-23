package com.vtoroy.service.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Utility for parsing Markdown files with frontmatter, wikilinks, and tags
 */
@Component
class MarkdownParser {
    
    private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        registerKotlinModule()
    }
    
    /**
     * Parse raw markdown content into structured data
     */
    fun parseMarkdown(rawContent: String, filePath: String): ParsedMarkdown {
        val (frontmatter, content) = extractFrontmatter(rawContent)
        val title = extractTitle(content, frontmatter, filePath)
        val wikiLinks = extractWikiLinks(content)
        val tags = extractTags(content, frontmatter)
        
        return ParsedMarkdown(
            title = title,
            content = content,
            rawContent = rawContent,
            frontmatter = frontmatter,
            wikiLinks = wikiLinks,
            tags = tags
        )
    }
    
    /**
     * Extract YAML frontmatter from markdown content
     */
    private fun extractFrontmatter(content: String): Pair<Map<String, Any>, String> {
        val frontmatterRegex = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n", RegexOption.DOT_MATCHES_ALL)
        val match = frontmatterRegex.find(content)
        
        return if (match != null) {
            val yamlContent = match.groupValues[1]
            val frontmatter = try {
                yamlMapper.readValue(yamlContent, Map::class.java) as Map<String, Any>
            } catch (e: Exception) {
                logger.warn(e) { "Failed to parse YAML frontmatter: $yamlContent" }
                emptyMap()
            }
            val contentWithoutFrontmatter = content.substring(match.range.last + 1)
            Pair(frontmatter, contentWithoutFrontmatter)
        } else {
            Pair(emptyMap(), content)
        }
    }
    
    /**
     * Extract title from content or frontmatter
     */
    private fun extractTitle(content: String, frontmatter: Map<String, Any>, filePath: String): String {
        // Try frontmatter first
        frontmatter["title"]?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        
        // Try first H1 heading
        val h1Regex = Regex("^#\\s+(.+)$", RegexOption.MULTILINE)
        h1Regex.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        
        // Fall back to filename without extension
        return filePath.substringAfterLast('/').substringBeforeLast('.')
    }
    
    /**
     * Extract Obsidian wikilinks [[link]] from content
     */
    private fun extractWikiLinks(content: String): List<String> {
        val wikiLinkRegex = Regex("\\[\\[([^\\]]+)\\]\\]")
        return wikiLinkRegex.findAll(content)
            .map { match ->
                val link = match.groupValues[1]
                // Handle aliases: [[link|alias]] -> link
                link.substringBefore('|').trim()
            }
            .distinct()
            .toList()
    }
    
    /**
     * Extract tags from content (#tag) and frontmatter
     */
    private fun extractTags(content: String, frontmatter: Map<String, Any>): Set<String> {
        val tags = mutableSetOf<String>()
        
        // Extract from frontmatter
        when (val frontmatterTags = frontmatter["tags"]) {
            is List<*> -> frontmatterTags.mapNotNull { it?.toString()?.trim() }.forEach { tags.add(it) }
            is String -> frontmatterTags.split(',', ';').map { it.trim() }.forEach { tags.add(it) }
        }
        
        // Extract from content (#tag)
        val hashTagRegex = Regex("#([a-zA-Z0-9_/-]+)")
        hashTagRegex.findAll(content).forEach { match ->
            tags.add(match.groupValues[1])
        }
        
        return tags.filter { it.isNotBlank() }.toSet()
    }
    
    /**
     * Generate frontmatter YAML from map
     */
    fun generateFrontmatter(frontmatter: Map<String, Any>): String {
        if (frontmatter.isEmpty()) return ""
        
        return try {
            val yaml = yamlMapper.writeValueAsString(frontmatter)
            "---\n$yaml---\n"
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate YAML frontmatter" }
            ""
        }
    }
    
    /**
     * Create markdown content with frontmatter
     */
    fun createMarkdownContent(
        content: String,
        frontmatter: Map<String, Any> = emptyMap()
    ): String {
        val frontmatterYaml = generateFrontmatter(frontmatter)
        return if (frontmatterYaml.isNotEmpty()) {
            "$frontmatterYaml\n$content"
        } else {
            content
        }
    }
}

/**
 * Result of markdown parsing
 */
data class ParsedMarkdown(
    val title: String,
    val content: String,
    val rawContent: String,
    val frontmatter: Map<String, Any>,
    val wikiLinks: List<String>,
    val tags: Set<String>
)