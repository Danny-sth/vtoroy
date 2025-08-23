package com.jarvis.agent

import com.jarvis.agent.contract.SubAgent
import com.jarvis.dto.*
import com.jarvis.entity.ChatMessage
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
 * Obsidian Sub-Agent - focused specialist for Obsidian vault operations
 * Follows Claude Code principles: single purpose, clear scope, direct execution
 */
@Component
class ObsidianAgent(
    @Value("\${jarvis.obsidian.vault-path}")
    private val defaultVaultPath: String,
    private val vaultManager: ObsidianVaultManager,
    private val chatModel: AnthropicChatModel
) : SubAgent {
    
    private val logger = KotlinLogging.logger {}
    
    // Sub-Agent configuration (Claude Code style)
    override val name = "obsidian-manager"
    
    override val description = """
        Expert at managing Obsidian vault operations: creating, reading, updating, deleting notes.
        Handles markdown files, wikilinks, tags, and vault organization.
        Use for any Obsidian-related tasks like "create note", "read file", "search notes".
    """.trimIndent()
    
    override val tools = listOf(
        "obsidian_read", "obsidian_create", "obsidian_search", 
        "obsidian_update", "obsidian_delete", "obsidian_list"
    )

    override suspend fun canHandle(query: String, chatHistory: List<ChatMessage>): Boolean {
        // AI-based decision (Claude Code principles - no hardcoded keywords!)
        val systemPrompt = """
        Определи, нужен ли Obsidian агент для этого запроса.
        
        Obsidian агент умеет:
        - Создавать/читать/обновлять заметки в markdown
        - Искать в vault по файлам
        - Работать с тегами и папками
        - Управлять структурой vault
        
        Отвечай только: true или false
        """.trimIndent()
        
        val contextMessages = if (chatHistory.isNotEmpty()) {
            "Контекст предыдущих сообщений:\n" + 
            chatHistory.takeLast(3).joinToString("\n") { "${it.role}: ${it.content}" } + "\n\n"
        } else ""
        
        val userPrompt = "${contextMessages}Запрос: $query"
        
        return try {
            val prompt = Prompt(listOf(
                SystemMessage(systemPrompt),
                UserMessage(userPrompt)
            ))
            
            val response = chatModel.call(prompt).result.output.content.trim().lowercase()
            val canHandle = response.contains("true")
            
            logger.debug { "ObsidianAgent.canHandle('$query'): $canHandle (AI decision: '$response')" }
            canHandle
            
        } catch (e: Exception) {
            logger.error(e) { "Error in AI-based canHandle, defaulting to false" }
            false
        }
    }

    override suspend fun handle(query: String, chatHistory: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        try {
            logger.info { "ObsidianAgent executing: '$query'" }
            
            // Получаем sessionId из metadata последнего (самого свежего) сообщения
            val lastMessage = chatHistory.lastOrNull()
            val sessionId = lastMessage?.metadata?.get("sessionId")?.asText()
            logger.debug { "ObsidianAgent sessionId: chatHistory.size=${chatHistory.size}, lastMessage=${lastMessage?.content?.take(50)}, metadata=${lastMessage?.metadata}, sessionId=$sessionId" }
            
            // Простое выполнение команды с отображением мыслей LLM
            return@withContext handleWithSimpleParsing(query, chatHistory, sessionId)
            
        } catch (e: Exception) {
            logger.error(e) { "ObsidianAgent error processing query: '$query'" }
            "❌ Ошибка при работе с Obsidian: ${e.message}"
        }
    }
    
    /**
     * Определяет является ли результат ошибкой
     */
    private fun isErrorResult(response: String): Boolean {
        val errorKeywords = listOf(
            "не найден", "not found", "ошибка", "error", 
            "не указан", "не удалось", "failed", "cannot find"
        )
        
        val content = response.lowercase()
        return errorKeywords.any { keyword -> content.contains(keyword) }
    }
    
    
    
    /**
     * Простая обработка команд (Claude Code style)
     */
    private suspend fun handleWithSimpleParsing(query: String, chatHistory: List<ChatMessage>, sessionId: String? = null): String {
        val action = parseQuery(query, chatHistory, sessionId)
        logger.debug { "Parsed action: ${action.type}, parameters: ${action.parameters}" }
        
        return when (action.type) {
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
            ObsidianAction.ASK_USER -> handleAskUser(action)
            else -> "❌ Операция не поддерживается: ${action.type}"
        }
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            logger.debug { "Checking ObsidianAgent availability..." }
            vaultManager.listFolders()
            true
        } catch (e: Exception) {
            logger.error(e) { "ObsidianAgent availability check failed" }
            false
        }
    }
    
    
    
    
    
    
    
    
    
    private suspend fun parseQuery(query: String, chatHistory: List<ChatMessage>, sessionId: String? = null): ParsedQuery {
        logger.debug { "ObsidianAgent parsing query with AI model: '$query'" }
        
        // Убрано сложное управление контекстом - Claude Code принципы
        // Простой промпт (Claude Code принципы)
        val systemPrompt = """
        Обработай запрос к Obsidian vault.
        
        ОПЕРАЦИИ:
        - READ_NOTE: чтение заметки (нужен path)
        - SEARCH_VAULT: поиск заметок (нужен query)
        - CREATE_NOTE: создание заметки (нужны path И title)
        - LIST_NOTES: список заметок
        - GET_TAGS: все теги vault
        - ASK_USER: когда нужна дополнительная информация
        
        ПРАВИЛА:
        1. Если нет имени/названия - используй ASK_USER
        2. НЕ придумывай данные
        
        Отвечай: JSON {"action": "...", "parameters": {...}}
        """.trimIndent()
        
        val contextMessages = if (chatHistory.isNotEmpty()) {
            "История диалога:\n" + 
            chatHistory.takeLast(5).joinToString("\n") { "${it.role}: ${it.content}" } + "\n\n"
        } else ""
        
        // Проверяем, является ли это ответом на вопрос из предыдущего сообщения
        val isResponseToQuestion = chatHistory.isNotEmpty() && 
            chatHistory.lastOrNull()?.role == com.jarvis.entity.MessageRole.ASSISTANT &&
            (chatHistory.lastOrNull()?.content?.contains("?") == true ||
             chatHistory.lastOrNull()?.content?.contains("укажите") == true ||
             chatHistory.lastOrNull()?.content?.contains("Пожалуйста") == true)
        
        val userPrompt = if (isResponseToQuestion) {
            """
            ${contextMessages}КОНТЕКСТ: Пользователь отвечает на мой предыдущий вопрос.
            Последний мой вопрос был: "${chatHistory.lastOrNull()?.content}"
            Ответ пользователя: "$query"
            
            ВАЖНО: 
            1. Интерпретируй "$query" как ответ на мой вопрос
            2. СОБЕРИ ВСЕ ПАРАМЕТРЫ из истории диалога (имена, пути, etc)
            3. Если у тебя есть ВСЕ нужные данные - ВЫПОЛНЯЙ операцию (например CREATE_NOTE)
            4. Если все еще чего-то не хватает - только тогда ASK_USER
            
            Проанализируй всю историю и определи что нужно сделать.
            """.trimIndent()
        } else {
            """
            ${contextMessages}Новый запрос пользователя: $query
            
            Определи операцию и извлеки параметры.
            """.trimIndent()
        }
        
        return try {
            // Отправляем промпт через SSE  
            sessionId?.let { 
                com.jarvis.controller.ThinkingController.sendThought(it, "🤔 Анализирую: '$query'", "obsidian_thinking")
                com.jarvis.controller.ThinkingController.sendThought(it, "💭 Промпт: ${userPrompt.take(100)}...", "obsidian_prompt") 
            }
            
            val prompt = Prompt(listOf(
                SystemMessage(systemPrompt),
                UserMessage(userPrompt)
            ))
            
            val response = chatModel.call(prompt)
            val fullResponse = response.result.output.content.trim()
            
            // Извлекаем рассуждение и JSON
            val jsonStartIndex = fullResponse.indexOf("{")
            val (reasoning, jsonPart) = if (jsonStartIndex > 0) {
                val reasoningPart = fullResponse.substring(0, jsonStartIndex).trim()
                val jsonPart = fullResponse.substring(jsonStartIndex).trim()
                reasoningPart to jsonPart
            } else {
                "" to fullResponse
            }
            
            // Отправляем рассуждение модели через SSE (если есть sessionId)
            if (reasoning.isNotEmpty() && sessionId != null) {
                com.jarvis.controller.ThinkingController.sendThought(sessionId, "💭 $reasoning", "obsidian_reasoning")
            }
            
            // Парсим JSON и отправляем действие
            val parsedAction = parseAiResponse(jsonPart)
            sessionId?.let { 
                val readableThought = when (parsedAction.type) {
                    ObsidianAction.CREATE_NOTE -> "📝 Создаю заметку: ${parsedAction.parameters["title"] ?: parsedAction.parameters["path"]}"
                    ObsidianAction.SEARCH_VAULT -> "🔍 Ищу в vault: ${parsedAction.parameters["query"]}"
                    ObsidianAction.READ_NOTE -> "📖 Читаю заметку: ${parsedAction.parameters["path"]}"
                    ObsidianAction.LIST_NOTES -> "📋 Получаю список заметок"
                    ObsidianAction.GET_TAGS -> "🏷️ Загружаю все теги"
                    ObsidianAction.ASK_USER -> "❓ Нужна дополнительная информация от пользователя"
                    else -> "❓ Выполняю действие: ${parsedAction.type}"
                }
                com.jarvis.controller.ThinkingController.sendThought(it, readableThought, "obsidian_action")
            }
            
            logger.debug { "AI model response: $fullResponse" }
            
            // Отправляем полное AI рассуждение пользователю через SSE
            sessionId?.let {
                com.jarvis.controller.ThinkingController.sendThought(it, "🤖 $fullResponse", "ai_full_response")
            }
            
            // Возвращаем уже распарсенное действие
            parsedAction
            
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
            extractJsonParameter(jsonResponse, "question")?.let { parameters["question"] = it }
            
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
                "✅ Заметка создана: **${note.title}**"
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
                "✅ Заметка обновлена: **${note.title}**"
            }
            is ObsidianResult.Error -> result.message
        }
    }
    
    private suspend fun handleDeleteNote(action: ParsedQuery): String {
        val path = action.parameters["path"] as? String
            ?: return "Не указан путь к заметке для удаления"
        
        return when (val result = vaultManager.deleteNote(path)) {
            is ObsidianResult.Success<*> -> "🗑️ Заметка удалена: $path"
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
                "📁 Заметка перемещена: $oldPath → $newPath"
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
            is ObsidianResult.Success<*> -> "📁 Папка создана: $folder"
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
    
    private fun handleAskUser(action: ParsedQuery): String {
        val question = action.parameters["question"] as? String ?: "Нужна дополнительная информация"
        return "❓ $question"
    }
}

/**
 * Разобранный запрос с типом операции и параметрами
 */
private data class ParsedQuery(
    val type: ObsidianAction,
    val parameters: Map<String, Any?>
)