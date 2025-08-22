package com.jarvis.service.knowledge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.jarvis.service.knowledge.contract.KnowledgeItem
import com.jarvis.service.knowledge.contract.KnowledgeSource
import com.jarvis.service.knowledge.contract.SourceStatus
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
 * Internal tool for ObsidianAgent to work with Obsidian vault
 * NOT a Spring component - only ObsidianAgent can create and use this
 */
class ObsidianKnowledgeSource(
    private val defaultVaultPath: String
) : KnowledgeSource {
    
    private val logger = KotlinLogging.logger {}
    private val yamlMapper = ObjectMapper(YAMLFactory())
    
    override val sourceId = "obsidian"
    override val sourceName = "Obsidian Vault"
    
    override suspend fun sync(config: Map<String, Any>): List<KnowledgeItem> = withContext(Dispatchers.IO) {
        val vaultPath = config["vaultPath"] as? String ?: defaultVaultPath
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
    
    override fun isAvailable(): Boolean {
        return try {
            val path = Paths.get(defaultVaultPath)
            Files.exists(path) && Files.isDirectory(path)
        } catch (e: Exception) {
            logger.error(e) { "Failed to check Obsidian vault availability" }
            false
        }
    }
    
    override fun getStatus(): SourceStatus {
        return try {
            val path = Paths.get(defaultVaultPath)
            if (!Files.exists(path)) {
                return SourceStatus(
                    sourceId = sourceId,
                    isActive = false,
                    errorMessage = "Vault path does not exist"
                )
            }
            
            val fileCount = Files.walk(path)
                .filter { it.isRegularFile() && it.extension == "md" }
                .count()
                .toInt()

            SourceStatus(
                sourceId = sourceId,
                isActive = true,
                itemCount = fileCount
            )
        } catch (e: Exception) {
            SourceStatus(
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
            metadata = frontmatter?.plus(mapOf("path" to relativePath)),
            tags = tags,
            lastModified = Files.getLastModifiedTime(filePath).toMillis()
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