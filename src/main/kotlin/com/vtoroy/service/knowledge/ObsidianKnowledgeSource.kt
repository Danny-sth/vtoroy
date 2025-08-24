package com.vtoroy.service.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.vtoroy.service.knowledge.contract.KnowledgeItem
import com.vtoroy.service.knowledge.contract.KnowledgeSource
import com.vtoroy.service.knowledge.contract.KnowledgeSourceStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Obsidian Knowledge Source - реализует интерфейс источника знаний для Obsidian
 * Аналог MCP Server для Obsidian в архитектуре Claude Code
 */
@Component
class ObsidianKnowledgeSource(
    @Value("\${vtoroy.obsidian.vault-path}")
    private val defaultVaultPath: String,
    private val vaultManager: ObsidianVaultManager
) : KnowledgeSource {
    
    private val logger = KotlinLogging.logger {}
    private val yamlMapper = ObjectMapper(YAMLFactory())
    
    override val sourceId = "obsidian"
    override val displayName = "Obsidian Vault"
    
    override suspend fun syncData(): List<KnowledgeItem> = withContext(Dispatchers.IO) {
        val vaultPath = defaultVaultPath
        val path = Paths.get(vaultPath)
        
        if (!Files.exists(path)) {
            throw IllegalArgumentException("Vault path does not exist: $path")
        }
        
        val items = mutableListOf<KnowledgeItem>()
        
        Files.walk(path)
            .filter { it.isRegularFile() && it.extension == "md" }
            .forEach { filePath ->
                try {
                    val item = processMarkdownFile(filePath, path)
                    items.add(item)
                } catch (e: Exception) {
                    logger.error(e) { "Error processing file: $filePath" }
                }
            }
        
        logger.info { "Synced ${items.size} files from Obsidian vault" }
        items
    }
    
    override suspend fun isAvailable(): Boolean {
        return try {
            val path = Paths.get(defaultVaultPath)
            Files.exists(path) && Files.isDirectory(path)
        } catch (e: Exception) {
            logger.error(e) { "Failed to check Obsidian vault availability" }
            false
        }
    }
    
    override suspend fun getStatus(): KnowledgeSourceStatus {
        return try {
            val path = Paths.get(defaultVaultPath)
            if (!Files.exists(path)) {
                return KnowledgeSourceStatus(
                    sourceId = sourceId,
                    isActive = false,
                    errorMessage = "Vault path does not exist"
                )
            }
            
            val fileCount = Files.walk(path)
                .filter { it.isRegularFile() && it.extension == "md" }
                .count()
                .toInt()

            KnowledgeSourceStatus(
                sourceId = sourceId,
                isActive = true,
                itemCount = fileCount
            )
        } catch (e: Exception) {
            KnowledgeSourceStatus(
                sourceId = sourceId,
                isActive = false,
                errorMessage = e.message
            )
        }
    }
    
    private fun processMarkdownFile(filePath: Path, basePath: Path): KnowledgeItem {
        val content = Files.readString(filePath)
        val relativePath = basePath.relativize(filePath).pathString
        
        // Extract frontmatter and content
        val (frontmatter, markdownContent) = extractFrontmatter(content)
        
        // Clean markdown content
        val cleanedContent = cleanMarkdown(markdownContent)
        
        // Extract tags from content and frontmatter
        val tags = extractTags(content, frontmatter)
        
        // Generate unique ID based on file path
        val id = generateFileId(relativePath)
        
        return KnowledgeItem(
            id = id,
            title = filePath.fileName.name.removeSuffix(".md"),
            content = cleanedContent,
            sourceId = sourceId,
            sourcePath = relativePath,
            lastModified = Files.getLastModifiedTime(filePath).toMillis(),
            metadata = (frontmatter ?: emptyMap()) + mapOf(
                "path" to relativePath,
                "tags" to tags
            )
        )
    }
    
    private fun extractFrontmatter(content: String): Pair<Map<String, Any>?, String> {
        if (!content.startsWith("---")) {
            return null to content
        }
        
        val lines = content.lines()
        val endIndex = lines.drop(1).indexOfFirst { it == "---" }
        
        if (endIndex == -1) {
            return null to content
        }
        
        val frontmatterContent = lines.subList(1, endIndex + 1).joinToString("\n")
        val markdownContent = lines.drop(endIndex + 2).joinToString("\n")
        
        return try {
            val frontmatter = yamlMapper.readValue(frontmatterContent, Map::class.java) as Map<String, Any>
            frontmatter to markdownContent
        } catch (e: Exception) {
            logger.warn { "Failed to parse frontmatter: ${e.message}" }
            null to content
        }
    }
    
    private fun cleanMarkdown(content: String): String {
        // Remove Obsidian-specific syntax while keeping content readable
        return content
            .replace(Regex("\\[\\[([^\\]]+)\\]\\]")) { match ->
                match.groupValues[1].split("|").last()
            }
            .replace(Regex("!\\[\\[([^\\]]+)\\]\\]"), "[$1]")
            .trim()
    }
    
    private fun extractTags(content: String, frontmatter: Map<String, Any>?): List<String> {
        val tags = mutableSetOf<String>()
        
        // Extract tags from frontmatter
        frontmatter?.get("tags")?.let { frontmatterTags ->
            when (frontmatterTags) {
                is List<*> -> tags.addAll(frontmatterTags.mapNotNull { it as? String })
                is String -> tags.add(frontmatterTags)
                else -> { /* ignore other types */ }
            }
        }
        
        // Extract hashtags from content
        val hashtagPattern = Regex("#([\\w-]+)")
        hashtagPattern.findAll(content).forEach { match ->
            tags.add(match.groupValues[1])
        }
        
        return tags.toList()
    }
    
    private fun generateFileId(path: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(path.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}