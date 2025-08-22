package com.jarvis.agent

import com.jarvis.agent.contract.Agent
import com.jarvis.agent.contract.AgentResponse
import com.jarvis.agent.contract.AgentStatus
import com.jarvis.agent.contract.KnowledgeManageable
import com.jarvis.agent.contract.SourceStatus
import com.jarvis.agent.memory.HybridMemoryClassifier
import com.jarvis.agent.reasoning.ObsidianReasoningEngine
import com.jarvis.dto.*
import com.jarvis.entity.ChatMessage
import com.jarvis.service.knowledge.contract.KnowledgeItem
import com.jarvis.service.knowledge.ObsidianKnowledgeSource
import com.jarvis.service.knowledge.ObsidianVaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Specialized agent for Obsidian vault knowledge management
 * This agent owns all interactions with Obsidian vault
 * Uses ObsidianKnowledgeSource as its internal tool
 */
@Component
class ObsidianAgent(
    @Value("\${jarvis.obsidian.vault-path}")
    private val defaultVaultPath: String,
    private val memoryClassifier: HybridMemoryClassifier,
    private val vaultManager: ObsidianVaultManager,
    private val chatModel: AnthropicChatModel,
    private val reasoningEngine: ObsidianReasoningEngine
) : Agent, KnowledgeManageable {
    
    private val logger = KotlinLogging.logger {}
    private var lastSyncTime: Long? = null
    private var totalMemoriesFormed: Int = 0
    
    private val obsidianTool = ObsidianKnowledgeSource(defaultVaultPath)
    
    override val name = "ObsidianAgent"
    
    override val capabilities = """
        Управление Obsidian Vault:
        - Чтение заметок по пути
        - Поиск заметок с семантической релевантностью
        - Создание новых заметок с frontmatter и тегами
        - Обновление заметок (содержимое, заголовок, теги, метаданные)
        - Удаление заметок и папок
        - Перемещение/переименование заметок
        - Список заметок по папкам и тегам
        - Получение всех тегов vault
        - Поиск обратных ссылок
        - Создание и управление структурой папок
        
        Поддерживает Obsidian markdown формат с YAML frontmatter, wikilinks [[ссылка]], и hashtags #тег
    """.trimIndent()

    override fun canHandle(query: String, chatHistory: List<ChatMessage>): Boolean {
        // AI модель решает может ли ObsidianAgent обработать запрос
        return runBlocking {
            try {
                val systemPrompt = """
                Определи, может ли ObsidianAgent обработать этот запрос.
                
                ObsidianAgent специализируется на:
                - Работе с заметками и файлами в Obsidian vault
                - Создании, чтении, обновлении, удалении заметок
                - Поиске по заметкам и тегам
                - Управлении markdown файлами
                - Wikilinks и hashtags
                
                Отвечай ТОЛЬКО "true" или "false".
                """.trimIndent()
                
                val response = chatModel.call(Prompt(listOf(
                    SystemMessage(systemPrompt),
                    UserMessage(query)
                )))
                val canHandle = response.result.output.content.trim().lowercase() == "true"
                logger.debug { "ObsidianAgent.canHandle('$query'): $canHandle (AI decision)" }
                canHandle
            } catch (e: Exception) {
                logger.error(e) { "Error in AI canHandle decision, defaulting to false" }
                false
            }
        }
    }

    override suspend fun handle(query: String, chatHistory: List<ChatMessage>): AgentResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info { "ObsidianAgent processing query: '$query'" }
            
            // AI определяет нужен ли reasoning для сложных запросов
            val needsReasoning = isComplexQuery(query)
            
            val result = if (needsReasoning) {
                logger.info { "Using reasoning engine for complex query" }
                handleWithReasoning(query, chatHistory)
            } else {
                logger.debug { "Using simple parsing for direct query" }
                val simpleResult = handleWithSimpleParsing(query)
                
                // Если simple режим упал с ошибкой - пробуем reasoning
                if (isErrorResult(simpleResult)) {
                    logger.warn { "Simple parsing failed, falling back to reasoning: ${simpleResult.content}" }
                    val reasoningResult = handleWithReasoning(query, chatHistory)
                    // Добавляем информацию о fallback
                    reasoningResult.copy(
                        metadata = reasoningResult.metadata + mapOf(
                            "fallback_from" to "simple",
                            "simple_error" to simpleResult.content
                        )
                    )
                } else {
                    simpleResult
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info { "ObsidianAgent completed in ${processingTime}ms, result length: ${result.content.length}" }
            
            result.copy(processingTimeMs = processingTime)
            
        } catch (e: Exception) {
            logger.error(e) { "ObsidianAgent error processing query: '$query'" }
            val processingTime = System.currentTimeMillis() - startTime
            
            AgentResponse(
                content = "Ошибка при обработке запроса к Obsidian: ${e.message}",
                metadata = mapOf("error" to (e::class.simpleName ?: "Unknown")),
                confidence = 0.0,
                processingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Определяет является ли результат ошибкой
     */
    private fun isErrorResult(response: AgentResponse): Boolean {
        val errorKeywords = listOf(
            "не найден", "not found", "ошибка", "error", 
            "не указан", "не удалось", "failed", "cannot find"
        )
        
        val content = response.content.lowercase()
        return errorKeywords.any { keyword -> content.contains(keyword) }
    }
    
    /**
     * AI определяет нужен ли reasoning для запроса
     */
    private suspend fun isComplexQuery(query: String): Boolean {
        val prompt = """
        Определи, нужна ли цепочка действий (reasoning) для выполнения запроса.
        
        ПРОСТЫЕ запросы (one-shot):
        - создай заметку
        - прочитай заметку X  
        - найди заметки про Y
        - удали заметку X (если путь точный)
        - список заметок
        
        СЛОЖНЫЕ запросы (multi-step reasoning):
        - найди файл и удали его
        - если есть заметка X, то прочитай её
        - удали файл по неточному пути (нужен поиск)
        - переименуй/перемести файл
        - несколько действий в одном запросе
        - условная логика (если...то...)
        
        Отвечай ТОЛЬКО: "simple" или "complex"
        
        Запрос: $query
        """.trimIndent()
        
        return try {
            val response = chatModel.call(Prompt(listOf(
                SystemMessage("Ты классификатор сложности запросов."),
                UserMessage(prompt)
            )))
            
            val result = response.result.output.content.trim().lowercase()
            val isComplex = result.contains("complex")
            
            logger.debug { "Query complexity for '$query': $result -> isComplex=$isComplex" }
            isComplex
            
        } catch (e: Exception) {
            logger.error(e) { "Error determining query complexity, defaulting to simple" }
            false // fallback to simple
        }
    }
    
    /**
     * Обработка сложных запросов через reasoning
     */
    private suspend fun handleWithReasoning(query: String, chatHistory: List<ChatMessage> = emptyList()): AgentResponse {
        val reasoningContext = reasoningEngine.reason(query, chatHistory)
        
        return AgentResponse(
            content = reasoningContext.finalResult ?: "Задача не была завершена",
            metadata = mapOf(
                "mode" to "reasoning",
                "steps_count" to reasoningContext.steps.size,
                "reasoning_steps" to reasoningContext.steps.map { 
                    mapOf(
                        "thought" to it.thought,
                        "action" to it.action?.tool,
                        "observation" to it.observation
                    )
                },
                "vault_path" to defaultVaultPath
            ),
            confidence = if (reasoningContext.isCompleted) 0.9 else 0.3
        )
    }
    
    /**
     * Обработка простых запросов через old parsing
     */
    private suspend fun handleWithSimpleParsing(query: String): AgentResponse {
        val action = parseQuery(query)
        logger.debug { "Parsed action: ${action.type}, parameters: ${action.parameters}" }
        
        val result = when (action.type) {
            ObsidianAction.READ_NOTE -> handleReadNote(action)
            ObsidianAction.SEARCH_VAULT -> handleSearchVault(action)
            ObsidianAction.LIST_NOTES -> handleListNotes(action)
            ObsidianAction.GET_TAGS -> handleGetTags()
            ObsidianAction.GET_BACKLINKS -> handleGetBacklinks(action)
            ObsidianAction.CREATE_NOTE -> handleCreateNote(action)
            ObsidianAction.UPDATE_NOTE -> handleUpdateNote(action)
            ObsidianAction.DELETE_NOTE -> handleDeleteNote(action)
            ObsidianAction.MOVE_NOTE -> handleMoveNote(action)
            ObsidianAction.CREATE_FOLDER -> handleCreateFolder(action)
            ObsidianAction.LIST_FOLDERS -> handleListFolders(action)
            else -> "Операция не поддерживается: ${action.type}"
        }
        
        return AgentResponse(
            content = result,
            metadata = mapOf(
                "mode" to "simple",
                "action" to action.type,
                "parameters" to action.parameters,
                "vault_path" to defaultVaultPath
            ),
            confidence = calculateConfidence(query)
        )
    }

    override suspend fun getStatus(): AgentStatus {
        return try {
            logger.debug { "Checking ObsidianAgent status..." }
            // Проверяем доступность vault'а
            vaultManager.listFolders()
            logger.debug { "ObsidianAgent status: AVAILABLE" }
            AgentStatus.AVAILABLE
        } catch (e: Exception) {
            logger.error(e) { "ObsidianAgent status check failed" }
            AgentStatus.ERROR
        }
    }
    override suspend fun formMemories(config: Map<String, Any>): List<KnowledgeItem> {
        logger.info { "ObsidianAgent: Starting memory formation process" }
        
        val memories = obsidianTool.sync(config)
        
        // Agent processes and validates the memories
        val processedMemories = mutableListOf<KnowledgeItem>()
        for (memory in memories) {
            if (isWorthRemembering(memory.content)) {
                val processed = processMemory(memory)
                processedMemories.add(processed)
            }
        }
        
        totalMemoriesFormed += processedMemories.size
        lastSyncTime = System.currentTimeMillis()
        
        logger.info { "ObsidianAgent: Formed ${processedMemories.size} memories from ${memories.size} raw items" }
        return processedMemories
    }
    
    /**
     * Agent's high-level processing of raw memory into structured knowledge
     * Uses advanced ML-based classification system
     */
    private suspend fun processMemory(rawMemory: KnowledgeItem): KnowledgeItem {
        val enhancedMetadata = rawMemory.metadata?.toMutableMap() ?: mutableMapOf()
        
        // Use hybrid classifier for intelligent memory type detection
        val memoryType = memoryClassifier.classify(rawMemory.content, rawMemory.metadata)
        
        // Add agent's analysis  
        enhancedMetadata["processedBy"] = this::class.simpleName ?: "ObsidianAgent"
        enhancedMetadata["memoryType"] = memoryType.primary
        enhancedMetadata["memorySubType"] = memoryType.secondary ?: "unknown"
        enhancedMetadata["typeConfidence"] = memoryType.confidence
        enhancedMetadata["classificationAttributes"] = memoryType.attributes
        enhancedMetadata["importance"] = assessImportance(rawMemory.content, memoryType.primary)
        enhancedMetadata["processingTimestamp"] = System.currentTimeMillis()
        
        // Add semantic enrichment
        enhancedMetadata["contentAnalysis"] = analyzeContent(rawMemory.content)
        
        return rawMemory.copy(
            metadata = enhancedMetadata
        )
    }
    
    /**
     * Enhanced importance assessment based on content and type
     */
    private fun assessImportance(content: String, memoryType: String): String {
        var score = 0
        
        // Content-based scoring
        when {
            content.contains("IMPORTANT", ignoreCase = true) || 
            content.contains("URGENT", ignoreCase = true) || 
            content.contains("CRITICAL", ignoreCase = true) -> score += 3
            
            content.contains("TODO", ignoreCase = true) || 
            content.contains("FIXME", ignoreCase = true) -> score += 2
            
            content.length > 1000 -> score += 2
            content.length > 500 -> score += 1
        }
        
        // Type-based scoring
        when (memoryType) {
            "meeting" -> score += 2 // Meetings are generally important
            "project" -> score += 2 // Project docs are important
            "task" -> score += 1 // Tasks have moderate importance
            "code" -> score += 1 // Code snippets are useful
            "documentation" -> score += 1 // Docs are reference material
        }
        
        // Structure-based scoring
        val lines = content.lines()
        if (lines.any { it.trim().startsWith("#") }) score += 1 // Has headings
        if (lines.any { it.contains("http") }) score += 1 // Has references
        
        return when {
            score >= 5 -> "high"
            score >= 3 -> "medium"
            else -> "low"
        }
    }
    
    /**
     * Analyzes content structure and extracts key features
     */
    private fun analyzeContent(content: String): Map<String, Any> {
        val lines = content.lines()
        
        return mapOf(
            "wordCount" to content.split("\\s+".toRegex()).size,
            "lineCount" to lines.size,
            "hasHeadings" to lines.any { it.trim().startsWith("#") },
            "hasLinks" to (content.contains("http") || (content.contains("[") && content.contains("]"))),
            "hasCodeBlocks" to content.contains("```"),
            "hasLists" to lines.any { it.trim().startsWith("-") || it.trim().startsWith("*") },
            "hasCheckboxes" to lines.any { it.contains("[ ]") || it.contains("[x]") },
            "language" to detectLanguage(content),
            "extractedKeywords" to extractKeywords(content, 5)
        )
    }
    
    private fun detectLanguage(content: String): String {
        // Simple language detection based on common patterns
        return when {
            content.contains(Regex("[а-яё]", RegexOption.IGNORE_CASE)) -> "russian"
            content.contains(Regex("[a-z]", RegexOption.IGNORE_CASE)) -> "english"
            else -> "unknown"
        }
    }
    
    private fun extractKeywords(content: String, limit: Int): List<String> {
        // Simple keyword extraction (in production, use TF-IDF or more sophisticated methods)
        val stopWords = setOf("the", "is", "at", "which", "on", "and", "a", "an", "to", "in", "for", "of", "with", "by")
        
        return content
            .lowercase()
            .split(Regex("[^a-zа-яё]+"))
            .filter { it.length > 3 && !stopWords.contains(it) }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
    
    /**
     * Agent's criteria for what's worth remembering
     */
    private fun isWorthRemembering(content: String): Boolean {
        if (content.isBlank() || content.length < 20) return false
        
        // Skip empty templates
        val lines = content.lines()
        if (lines.all { it.startsWith("#") || it.isBlank() }) return false
        
        // Skip daily notes that are just task lists
        if (content.contains("## Tasks") && content.length < 100) return false
        
        return true
    }
    
    override fun canAccessSource(): Boolean {
        return obsidianTool.isAvailable()
    }
    
    override fun getSourceStatus(): SourceStatus {
        val toolStatus = obsidianTool.getStatus()
        val health = when {
            !toolStatus.isActive -> "vault_not_found"
            lastSyncTime == null -> "never_synced"
            else -> "healthy"
        }
        
        return SourceStatus(
            sourceType = "obsidian",
            isAccessible = toolStatus.isActive,
            lastSync = lastSyncTime,
            itemCount = totalMemoriesFormed,
            health = health
        )
    }
    
    private suspend fun parseQuery(query: String): ParsedQuery {
        logger.debug { "ObsidianAgent parsing query with AI model: '$query'" }
        
        val systemPrompt = """
        Ты эксперт по анализу запросов для работы с Obsidian vault. Точно определи операцию и извлеки ВСЕ необходимые параметры.
        
        ОПЕРАЦИИ:
        - READ_NOTE: чтение заметки (нужен path)
        - SEARCH_VAULT: поиск заметок (нужен query, опционально tags, folder)
        - LIST_NOTES: список заметок (опционально folder)
        - GET_TAGS: все теги vault (параметры не нужны)
        - GET_BACKLINKS: обратные ссылки (нужен path)
        - CREATE_NOTE: создание заметки (нужны path И title, опционально content, tags)
        - UPDATE_NOTE: обновление заметки (нужен path, опционально content, title, tags)
        - DELETE_NOTE: удаление заметки (нужен path)
        - MOVE_NOTE: перемещение заметки (нужны oldPath и newPath)
        - CREATE_FOLDER: создание папки (нужен folder)
        - LIST_FOLDERS: список папок (опционально access_query для вопросов о доступе)
        
        КРИТИЧЕСКИЕ ПРАВИЛА:
        1. Для CREATE_NOTE ОБЯЗАТЕЛЬНО нужны И path И title
        2. Если path не указан, создай его из title: "title.md"
        3. Если title не указан, извлеки из path: "file.md" → "file"
        4. Команды "list", "show", "список" в папке = LIST_NOTES, НЕ SEARCH_VAULT
        5. Для поиска в конкретной папке используй folder, а не query
        
        ПРИМЕРЫ ПРАВИЛЬНОГО РАЗБОРА:
        "создай заметку test.md" → {"action": "CREATE_NOTE", "parameters": {"path": "test.md", "title": "test"}}
        "создай заметку с названием Test" → {"action": "CREATE_NOTE", "parameters": {"path": "Test.md", "title": "Test"}}
        "show all notes in Projects" → {"action": "LIST_NOTES", "parameters": {"folder": "Projects"}}
        "найди заметки про AI" → {"action": "SEARCH_VAULT", "parameters": {"query": "AI"}}
        
        JSON ФОРМАТ (отвечай ТОЛЬКО JSON):
        {
          "action": "ACTION_NAME",
          "parameters": {
            "path": "путь к файлу",
            "title": "заголовок заметки", 
            "content": "содержимое",
            "tags": ["тег1", "тег2"],
            "folder": "имя папки",
            "query": "поисковый запрос",
            "oldPath": "старый путь",
            "newPath": "новый путь",
            "access_query": true
          }
        }
        """.trimIndent()
        
        val userPrompt = """
        Запрос пользователя: $query
        
        Определи операцию и извлеки параметры.
        """.trimIndent()
        
        return try {
            val prompt = Prompt(listOf(
                SystemMessage(systemPrompt),
                UserMessage(userPrompt)
            ))
            
            val response = chatModel.call(prompt)
            val jsonResponse = response.result.output.content.trim()
            
            logger.debug { "AI model response: $jsonResponse" }
            
            // Парсим JSON ответ
            parseAiResponse(jsonResponse)
            
        } catch (e: Exception) {
            logger.error(e) { "Error in AI-based query parsing, falling back to search" }
            // Fallback to search if AI parsing fails
            ParsedQuery(ObsidianAction.SEARCH_VAULT, mapOf("query" to query))
        }
    }
    
    private fun parseAiResponse(jsonResponse: String): ParsedQuery {
        try {
            // Простой JSON парсинг для извлечения action и parameters
            val actionMatch = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(jsonResponse)
            val actionName = actionMatch?.groupValues?.get(1) ?: "SEARCH_VAULT"
            
            val action = try {
                ObsidianAction.valueOf(actionName)
            } catch (e: IllegalArgumentException) {
                logger.warn { "Unknown action '$actionName', using SEARCH_VAULT" }
                ObsidianAction.SEARCH_VAULT
            }
            
            val parameters = mutableMapOf<String, Any?>()
            
            // Извлекаем параметры из JSON
            extractJsonParameter(jsonResponse, "path")?.let { parameters["path"] = it }
            extractJsonParameter(jsonResponse, "title")?.let { parameters["title"] = it }
            extractJsonParameter(jsonResponse, "content")?.let { parameters["content"] = it }
            extractJsonParameter(jsonResponse, "folder")?.let { parameters["folder"] = it }
            extractJsonParameter(jsonResponse, "query")?.let { parameters["query"] = it }
            extractJsonParameter(jsonResponse, "oldPath")?.let { parameters["oldPath"] = it }
            extractJsonParameter(jsonResponse, "newPath")?.let { parameters["newPath"] = it }
            
            // Обрабатываем теги как массив
            extractJsonArray(jsonResponse, "tags")?.let { tags ->
                if (tags.isNotEmpty()) parameters["tags"] = tags
            }
            
            // Специальные булевые параметры
            if (jsonResponse.contains("\"access_query\"\\s*:\\s*true".toRegex())) {
                parameters["access_query"] = true
            }
            
            logger.debug { "Parsed action: $action, parameters: $parameters" }
            return ParsedQuery(action, parameters)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse AI response: $jsonResponse" }
            return ParsedQuery(ObsidianAction.SEARCH_VAULT, mapOf("query" to jsonResponse))
        }
    }
    
    private fun extractJsonParameter(json: String, paramName: String): String? {
        val pattern = "\"$paramName\"\\s*:\\s*\"([^\"]*)\""
        return Regex(pattern).find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
    
    private fun extractJsonArray(json: String, paramName: String): Set<String>? {
        val pattern = "\"$paramName\"\\s*:\\s*\\[([^\\]]*)]"
        val match = Regex(pattern).find(json) ?: return null
        val arrayContent = match.groupValues[1]
        
        if (arrayContent.isBlank()) return emptySet()
        
        return arrayContent.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .toSet()
    }
    
    private suspend fun handleReadNote(action: ParsedQuery): String {
        val notePath = action.parameters["path"] as? String
            ?: return "Не указан путь к заметке"
        
        logger.debug { "Reading note: $notePath" }
        return when (val result = vaultManager.readNote(notePath)) {
            is ObsidianResult.Success<*> -> {
                val note = result.data as MarkdownNote
                formatNote(note)
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleSearchVault(action: ParsedQuery): String {
        val query = action.parameters["query"] as? String ?: ""
        val tags = action.parameters["tags"] as? Set<String>
        val folder = action.parameters["folder"] as? String
        
        logger.debug { "Searching vault: query='$query', tags=$tags, folder=$folder" }
        val searchRequest = VaultSearchRequest(
            query = query,
            folder = folder,
            tags = tags,
            limit = 10
        )
        
        return when (val result = vaultManager.searchNotes(searchRequest)) {
            is ObsidianResult.Success<*> -> {
                val results = result.data as List<SearchResult>
                logger.info { "Search found ${results.size} notes" }
                formatSearchResults(results)
            }
            is ObsidianResult.Error -> {
                logger.warn { "Search failed: ${result.message}" }
                result.message
            }
        }
    }
    
    private suspend fun handleCreateNote(action: ParsedQuery): String {
        // Умная логика восстановления недостающих параметров
        val pathParam = action.parameters["path"] as? String
        val titleParam = action.parameters["title"] as? String
        
        val (path, title) = when {
            pathParam != null && titleParam != null -> pathParam to titleParam
            pathParam != null && titleParam == null -> {
                // Извлекаем title из path: "test.md" -> "test"
                val extractedTitle = pathParam.substringBeforeLast(".md").substringAfterLast("/")
                pathParam to extractedTitle
            }
            pathParam == null && titleParam != null -> {
                // Создаем path из title: "My Note" -> "My Note.md"
                val generatedPath = if (titleParam.endsWith(".md")) titleParam else "$titleParam.md"
                generatedPath to titleParam
            }
            else -> return "Не указан ни путь, ни заголовок для новой заметки"
        }
        
        val content = action.parameters["content"] as? String ?: ""
        val tags = action.parameters["tags"] as? Set<String> ?: emptySet()
        
        logger.info { "Creating note: path='$path', title='$title', tags=$tags" }
        val request = CreateNoteRequest(
            path = path,
            title = title,
            content = content,
            tags = tags
        )
        
        return when (val result = vaultManager.createNote(request)) {
            is ObsidianResult.Success<*> -> {
                val note = result.data as MarkdownNote
                logger.info { "Note created successfully: ${note.path}" }
                "Заметка создана: **${note.title}** (${note.path})\n\nСодержимое:\n${note.content}"
            }
            is ObsidianResult.Error -> {
                logger.warn { "Failed to create note: ${result.message}" }
                result.message
            }
        }
    }
    
    private suspend fun handleUpdateNote(action: ParsedQuery): String {
        val path = action.parameters["path"] as? String
            ?: return "Не указан путь к заметке для обновления"
        
        val request = UpdateNoteRequest(
            path = path,
            content = action.parameters["content"] as? String,
            title = action.parameters["title"] as? String,
            tags = action.parameters["tags"] as? Set<String>
        )
        
        return when (val result = vaultManager.updateNote(request)) {
            is ObsidianResult.Success<*> -> {
                val note = result.data as MarkdownNote
                "Заметка обновлена: **${note.title}** (${note.path})"
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleDeleteNote(action: ParsedQuery): String {
        val path = action.parameters["path"] as? String
            ?: return "Не указан путь к заметке для удаления"
        
        return when (val result = vaultManager.deleteNote(path)) {
            is ObsidianResult.Success<*> -> "Заметка удалена: $path"
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleMoveNote(action: ParsedQuery): String {
        val oldPath = action.parameters["oldPath"] as? String
            ?: return "Не указан исходный путь заметки"
        val newPath = action.parameters["newPath"] as? String
            ?: return "Не указан новый путь заметки"
        
        val request = MoveNoteRequest(oldPath, newPath)
        
        return when (val result = vaultManager.moveNote(request)) {
            is ObsidianResult.Success<*> -> {
                "Заметка перемещена: $oldPath → $newPath"
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleListNotes(action: ParsedQuery): String {
        val folder = action.parameters["folder"] as? String
        
        return when (val result = vaultManager.listNotes(folder)) {
            is ObsidianResult.Success<*> -> {
                val notes = result.data as List<NoteInfo>
                formatNotesList(notes, folder)
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleGetTags(): String {
        return when (val result = vaultManager.getAllTags()) {
            is ObsidianResult.Success<*> -> {
                val tags = result.data as List<String>
                "Доступные теги в vault:\n${tags.joinToString(", ") { "#$it" }}"
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleGetBacklinks(action: ParsedQuery): String {
        val path = action.parameters["path"] as? String
            ?: return "Не указан путь к заметке для поиска обратных ссылок"
        
        return when (val result = vaultManager.getBacklinks(path)) {
            is ObsidianResult.Success<*> -> {
                val backlinks = result.data as List<String>
                if (backlinks.isEmpty()) {
                    "Обратные ссылки на '$path' не найдены"
                } else {
                    "Обратные ссылки на '$path':\n${backlinks.joinToString("\n") { "- $it" }}"
                }
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleCreateFolder(action: ParsedQuery): String {
        val folder = action.parameters["folder"] as? String
            ?: return "Не указано имя папки"
        
        return when (val result = vaultManager.createFolder(folder)) {
            is ObsidianResult.Success<*> -> "Папка создана: $folder"
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleListFolders(): String {
        return when (val result = vaultManager.listFolders()) {
            is ObsidianResult.Success<*> -> {
                val folders = result.data as List<String>
                if (folders.isEmpty()) {
                    "В vault нет папок"
                } else {
                    "Папки в vault:\n${folders.joinToString("\n") { "- $it" }}"
                }
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleListFolders(action: ParsedQuery): String {
        val isAccessQuery = action.parameters["access_query"] as? Boolean ?: false
        
        return if (isAccessQuery) {
            // Отвечаем на вопрос о доступе
            when (val result = vaultManager.listFolders()) {
                is ObsidianResult.Success<*> -> {
                    val folders = result.data as List<String>
                    """
                    ✅ Да, у меня есть ПОЛНЫЙ доступ к Obsidian vault!
                    
                    Мои возможности:
                    • Создавать новые заметки
                    • Читать существующие заметки  
                    • Искать заметки по содержимому и тегам
                    • Изменять и обновлять заметки
                    • Удалять заметки
                    • Управлять папками
                    • Работать с wikilinks [[ссылки]]
                    • Работать с тегами #тег
                    
                    Vault содержит ${folders.size} папок: ${folders.joinToString(", ")}
                    
                    Примеры команд:
                    - "создай заметку с названием 'Планы на день'"
                    - "прочитай заметку meeting.md" 
                    - "найди заметки с тегом #проект"
                    - "список всех заметок"
                    """.trimIndent()
                }
                is ObsidianResult.Error -> "❌ Не могу получить доступ к vault: ${result.message}"
            }
        } else {
            handleListFolders()
        }
    }
    
    private fun formatNote(note: MarkdownNote): String {
        val tagsStr = if (note.tags.isNotEmpty()) {
            "\n**Теги:** ${note.tags.joinToString(", ") { "#$it" }}"
        } else ""
        
        val linksStr = if (note.wikiLinks.isNotEmpty()) {
            "\n**Wikiссылки:** ${note.wikiLinks.joinToString(", ") { "[[${it}]]" }}"
        } else ""
        
        return """
            # ${note.title}
            
            **Путь:** ${note.path}
            **Изменено:** ${note.modifiedAt}
            **Размер:** ${note.size} байт$tagsStr$linksStr
            
            ---
            
            ${note.content}
        """.trimIndent()
    }
    
    private fun formatSearchResults(results: List<SearchResult>): String {
        if (results.isEmpty()) {
            return "По запросу ничего не найдено"
        }
        
        val resultsStr = results.joinToString("\n\n") { result ->
            val note = result.note
            val tagsStr = if (note.tags.isNotEmpty()) {
                " | Теги: ${note.tags.joinToString(", ") { "#$it" }}"
            } else ""
            
            val fragmentsStr = if (result.matchedFragments.isNotEmpty()) {
                "\n${result.matchedFragments.joinToString("\n") { "  $it" }}"
            } else ""
            
            "**${note.title}** (${note.path}) | Релевантность: ${"%.1f".format(result.relevanceScore)}$tagsStr$fragmentsStr"
        }
        
        return "Найдено ${results.size} заметок:\n\n$resultsStr"
    }
    
    private fun formatNotesList(notes: List<NoteInfo>, folder: String?): String {
        if (notes.isEmpty()) {
            return if (folder != null) "В папке '$folder' нет заметок" else "В vault нет заметок"
        }
        
        val header = if (folder != null) "Заметки в папке '$folder':" else "Все заметки в vault:"
        
        val notesStr = notes.joinToString("\n") { note ->
            val tagsStr = if (note.tags.isNotEmpty()) {
                " | ${note.tags.joinToString(", ") { "#$it" }}"
            } else ""
            
            "- **${note.title}** (${note.path}) | ${note.size} байт$tagsStr"
        }
        
        return "$header\n\n$notesStr\n\nВсего: ${notes.size} заметок"
    }
    
    private fun extractNotePath(query: String, prefix: String = ""): String? {
        val patterns = listOf(
            "\"([^\"]+)\"", // в кавычках
            "\\[\\[([^]]+)\\]\\]", // wikilink формат
            "'([^']+)'", // в одинарных кавычках
            "${prefix}\\s+(\\S+\\.md)", // префикс + имя.md
            "${prefix}\\s+(\\S+)" // префикс + имя
        )
        
        for (pattern in patterns) {
            val match = Regex(pattern).find(query)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
    
    private fun extractTitle(query: String): String? {
        return extractNotePath(query)?.substringBeforeLast(".md")
    }
    
    private fun extractContent(query: String): String? {
        val contentPattern = Regex("(содержимо[ем]|контент|content|текст)[:.]?\\s*(.+)", RegexOption.IGNORE_CASE)
        return contentPattern.find(query)?.groupValues?.get(2)?.trim()
    }
    
    private fun extractTags(query: String): Set<String>? {
        val tags = Regex("#(\\w+)").findAll(query)
            .map { it.groupValues[1] }
            .toSet()
        
        return if (tags.isNotEmpty()) tags else null
    }
    
    private fun extractFolder(query: String): String? {
        val patterns = listOf(
            "в папке\\s+\"([^\"]+)\"",
            "в папке\\s+'([^']+)'", 
            "в папке\\s+(\\S+)",
            "folder\\s+\"([^\"]+)\"",
            "folder\\s+'([^']+)'",
            "folder\\s+(\\S+)"
        )
        
        for (pattern in patterns) {
            val match = Regex(pattern, RegexOption.IGNORE_CASE).find(query)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
    
    private fun extractSearchQuery(query: String): String {
        return query.replace(Regex("\\b(найди|найти|поиск|search|find)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("в папке\\s+\\S+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("#\\w+"), "") // убираем теги
            .trim()
    }
    
    private fun calculateConfidence(query: String): Double {
        val queryLower = query.lowercase()
        
        return when {
            queryLower.contains("obsidian") || queryLower.contains("vault") -> 1.0
            queryLower.contains("заметк") || queryLower.contains("note") -> 0.9
            queryLower.contains("[[") && queryLower.contains("]]") -> 0.8
            queryLower.contains("#") && queryLower.matches(Regex(".*#\\w+.*")) -> 0.7
            else -> 0.5
        }
    }
}

/**
 * Разобранный запрос с типом операции и параметрами
 */
private data class ParsedQuery(
    val type: ObsidianAction,
    val parameters: Map<String, Any?>
)