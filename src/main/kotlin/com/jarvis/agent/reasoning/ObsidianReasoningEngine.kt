package com.jarvis.agent.reasoning

import com.jarvis.dto.*
import com.jarvis.entity.ChatMessage
import com.jarvis.service.knowledge.ObsidianVaultManager
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * ReAct reasoning engine for complex Obsidian operations
 */
@Component
class ObsidianReasoningEngine(
    private val chatModel: AnthropicChatModel,
    private val vaultManager: ObsidianVaultManager
) {
    
    companion object {
        private const val MAX_STEPS = 10
        
        private val REASONING_PROMPT = """
        Ты умный агент для работы с Obsidian vault. Используй ReAct pattern (Reasoning + Acting).
        
        КОНТЕКСТ РАЗГОВОРА:
        Учитывай предыдущие сообщения в чате для понимания контекста.
        Особенно важно помнить какие файлы были недавно созданы, прочитаны или изменены.
        
        ДОСТУПНЫЕ ИНСТРУМЕНТЫ:
        - list_notes(folder?) - список заметок в папке
        - search_notes(query, tags?, folder?) - поиск заметок
        - read_note(path) - прочитать заметку
        - create_note(path, title, content?, tags?) - создать заметку
        - update_note(path, title?, content?, tags?) - обновить заметку  
        - delete_note(path) - удалить заметку
        - get_tags() - все теги
        - get_backlinks(path) - обратные ссылки
        
        ВАЖНО О ПУТЯХ ФАЙЛОВ:
        - Vault находится в /app/obsidian-vault/
        - Если в запросе указан путь "obsidian-vault/filename.md" - это означает файл "filename.md" в корне vault
        - Всегда используй ТОЛЬКО относительные пути внутри vault (например: "test456.md", а НЕ "obsidian-vault/test456.md")
        - При поиске файла сначала попробуй точное имя, потом поиск по содержимому
        
        ФОРМАТ ОТВЕТА:
        Thought: [твои размышления о текущей ситуации]
        Action: [tool_name(parameters)] ИЛИ Complete: [финальный ответ]
        
        КРИТИЧЕСКИ ВАЖНО:
        - Отвечай ТОЛЬКО одним шагом за раз
        - НЕ придумывай результат действия (Observation)
        - НЕ пиши несколько шагов подряд
        - Жди реального результата от системы
        
        ПРАВИЛА:
        1. Думай пошагово
        2. Если файл не найден точно - поищи его через search_notes
        3. Убирай префикс "obsidian-vault/" из путей файлов
        4. Для поиска используй как полное имя, так и части имени
        5. Всегда проверяй результат действия
        6. Если задача выполнена - отвечай "Complete: [результат]"
        
        ПРИМЕРЫ:
        Query: "удали файл obsidian-vault/test456.md"
        Thought: Нужно удалить файл test456.md (убираю префикс obsidian-vault/)
        Action: delete_note(path="test456.md")
        
        Query: "найди заметки о проекте"  
        Thought: Поищу заметки содержащие слово "проект"
        Action: search_notes(query="проект")
        """.trimIndent()
    }
    
    suspend fun reason(query: String, chatHistory: List<ChatMessage> = emptyList()): ReasoningContext {
        val context = ReasoningContext(originalQuery = query)
        
        logger.info { "Starting reasoning for: '$query'" }
        
        var stepCount = 0
        while (!context.isCompleted && stepCount < MAX_STEPS) {
            stepCount++
            
            val result = processReasoningStep(context, stepCount, chatHistory)
            
            when (result) {
                is ReasoningResult.Continue -> {
                    context.addStep(result.nextStep)
                    // Выполним action если есть
                    result.nextStep.action?.let { action ->
                        val observation = executeAction(action)
                        context.steps.last().copy(observation = observation).also {
                            context.steps[context.steps.size - 1] = it
                        }
                    }
                }
                is ReasoningResult.Complete -> {
                    logger.info { "Reasoning completed in $stepCount steps" }
                    return context.copy(isCompleted = true, finalResult = result.result)
                }
                is ReasoningResult.Error -> {
                    logger.error { "Reasoning failed: ${result.message}" }
                    return context.copy(isCompleted = true, finalResult = "Ошибка: ${result.message}")
                }
            }
        }
        
        logger.warn { "Reasoning hit max steps limit ($MAX_STEPS)" }
        return context.copy(isCompleted = true, finalResult = "Задача слишком сложная, достигнут лимит шагов")
    }
    
    private suspend fun processReasoningStep(context: ReasoningContext, stepNumber: Int, chatHistory: List<ChatMessage>): ReasoningResult {
        val prompt = buildReasoningPrompt(context, chatHistory)
        
        return try {
            val response = chatModel.call(prompt)
            val content = response.result.output.content.trim()
            
            logger.debug { "Step $stepNumber AI response: $content" }
            
            parseReasoningResponse(content, stepNumber)
        } catch (e: Exception) {
            logger.error(e) { "Error in reasoning step $stepNumber" }
            ReasoningResult.Error("Ошибка обработки: ${e.message}")
        }
    }
    
    private fun buildReasoningPrompt(context: ReasoningContext, chatHistory: List<ChatMessage>): Prompt {
        val chatHistoryStr = if (chatHistory.isNotEmpty()) {
            val recentHistory = chatHistory.takeLast(6) // Last 3 exchanges
            val historyText = recentHistory.joinToString("\n") { msg ->
                "${msg.role}: ${msg.content}"
            }
            "\nПРЕДЫДУЩИЕ СООБЩЕНИЯ:\n$historyText\n"
        } else {
            ""
        }
        
        val contextStr = if (context.steps.isEmpty()) {
            "${chatHistoryStr}Query: ${context.originalQuery}"
        } else {
            "${chatHistoryStr}${context.getContextString()}Что делать дальше?"
        }
        
        return Prompt(listOf(
            SystemMessage(REASONING_PROMPT),
            UserMessage(contextStr)
        ))
    }
    
    private fun parseReasoningResponse(response: String, stepNumber: Int): ReasoningResult {
        val lines = response.lines()
        var thought = ""
        var actionLine = ""
        var foundCurrentStep = false
        
        // Найти текущий шаг (Step X:) и обработать только его
        val currentStepPrefix = "Step $stepNumber:"
        
        for (i in lines.indices) {
            val line = lines[i]
            
            // Ищем начало текущего шага
            if (line.startsWith(currentStepPrefix)) {
                foundCurrentStep = true
                continue
            }
            
            // Если нашли начало следующего шага - прекращаем обработку
            if (foundCurrentStep && line.startsWith("Step ") && !line.startsWith(currentStepPrefix)) {
                break
            }
            
            // Обрабатываем только строки текущего шага
            if (foundCurrentStep) {
                when {
                    line.startsWith("Thought:") -> thought = line.substringAfter("Thought:").trim()
                    line.startsWith("Action:") -> actionLine = line.substringAfter("Action:").trim()
                    line.startsWith("Complete:") -> {
                        // Захватываем многострочный результат после Complete:
                        val completeLines = mutableListOf<String>()
                        completeLines.add(line.substringAfter("Complete:").trim())
                        
                        // Добавляем все следующие строки до конца или до начала нового шага
                        for (j in (i + 1) until lines.size) {
                            val nextLine = lines[j]
                            if (nextLine.trim().startsWith("Step ")) {
                                break
                            }
                            // Добавляем и пустые строки для сохранения форматирования
                            completeLines.add(nextLine)
                        }
                        
                        val result = completeLines.joinToString("\n").trim()
                        logger.info { "Parsed Complete result: '$result'" }
                        return ReasoningResult.Complete(result)
                    }
                }
            }
        }
        
        // Если не нашли конкретный шаг, обрабатываем как обычно
        if (!foundCurrentStep) {
            for (i in lines.indices) {
                val line = lines[i]
                when {
                    line.startsWith("Thought:") -> thought = line.substringAfter("Thought:").trim()
                    line.startsWith("Action:") -> actionLine = line.substringAfter("Action:").trim()
                    line.startsWith("Complete:") -> {
                        // Захватываем многострочный результат после Complete:
                        val completeLines = mutableListOf<String>()
                        completeLines.add(line.substringAfter("Complete:").trim())
                        
                        // Добавляем все следующие строки до конца
                        for (j in (i + 1) until lines.size) {
                            val nextLine = lines[j]
                            if (nextLine.trim().startsWith("Step ")) {
                                break
                            }
                            // Добавляем и пустые строки для сохранения форматирования
                            completeLines.add(nextLine)
                        }
                        
                        val result = completeLines.joinToString("\n").trim()
                        logger.info { "Parsed Complete result: '$result'" }
                        return ReasoningResult.Complete(result)
                    }
                }
            }
        }
        
        val action = parseAction(actionLine)
        val step = ReasoningStep(thought, action, null, stepNumber)
        
        return ReasoningResult.Continue(step)
    }
    
    private fun parseAction(actionLine: String): ToolAction? {
        if (actionLine.isBlank()) return null
        
        // Парсим tool_name(parameters)
        val toolRegex = Regex("(\\w+)\\(([^)]*)\\)")
        val match = toolRegex.find(actionLine) ?: return null
        
        val toolName = match.groupValues[1]
        val paramsStr = match.groupValues[2]
        
        val parameters = parseParameters(paramsStr)
        
        return ToolAction(toolName, parameters)
    }
    
    private fun parseParameters(paramsStr: String): Map<String, Any> {
        if (paramsStr.isBlank()) return emptyMap()
        
        val params = mutableMapOf<String, Any>()
        
        // Простой парсинг: key="value", key2="value2"
        val paramRegex = Regex("(\\w+)=[\"']([^\"']*)[\"']")
        paramRegex.findAll(paramsStr).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            params[key] = value
        }
        
        return params
    }
    
    private suspend fun executeAction(action: ToolAction): String {
        logger.info { "Executing action: ${action.tool} with parameters: ${action.parameters}" }
        
        return try {
            when (action.tool) {
                "list_notes" -> {
                    val folder = action.parameters["folder"] as? String
                    when (val result = vaultManager.listNotes(folder)) {
                        is ObsidianResult.Success<*> -> {
                            val notes = result.data as List<NoteInfo>
                            "Найдено ${notes.size} заметок: ${notes.map { it.path }.joinToString(", ")}"
                        }
                        is ObsidianResult.Error -> "Ошибка: ${result.message}"
                    }
                }
                
                "search_notes" -> {
                    val query = action.parameters["query"] as? String ?: ""
                    val tags = (action.parameters["tags"] as? String)?.split(",")?.map { it.trim() }?.toSet()
                    val folder = action.parameters["folder"] as? String
                    
                    val request = VaultSearchRequest(query, folder, tags, 10)
                    when (val result = vaultManager.searchNotes(request)) {
                        is ObsidianResult.Success<*> -> {
                            val results = result.data as List<SearchResult>
                            if (results.isEmpty()) {
                                "Заметки не найдены"
                            } else {
                                "Найдено ${results.size} заметок: ${results.map { it.note.path }.joinToString(", ")}"
                            }
                        }
                        is ObsidianResult.Error -> "Ошибка поиска: ${result.message}"
                    }
                }
                
                "read_note" -> {
                    val path = action.parameters["path"] as? String ?: return "Не указан путь"
                    when (val result = vaultManager.readNote(path)) {
                        is ObsidianResult.Success<*> -> {
                            val note = result.data as MarkdownNote
                            "Заметка прочитана: ${note.title} (${note.content.take(100)}...)"
                        }
                        is ObsidianResult.Error -> "Ошибка чтения: ${result.message}"
                    }
                }
                
                "delete_note" -> {
                    val path = action.parameters["path"] as? String ?: return "Не указан путь"
                    logger.info { "Attempting to delete note at path: '$path'" }
                    when (val result = vaultManager.deleteNote(path)) {
                        is ObsidianResult.Success<*> -> {
                            logger.info { "Successfully deleted note: '$path'" }
                            "Заметка '$path' успешно удалена"
                        }
                        is ObsidianResult.Error -> {
                            logger.error { "Failed to delete note '$path': ${result.message}" }
                            "Ошибка удаления: ${result.message}"
                        }
                    }
                }
                
                "create_note" -> {
                    val path = action.parameters["path"] as? String ?: return "Не указан путь"
                    val title = action.parameters["title"] as? String ?: return "Не указан заголовок"
                    val content = action.parameters["content"] as? String ?: ""
                    val tags = (action.parameters["tags"] as? String)?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                    
                    val request = CreateNoteRequest(
                        path = path,
                        title = title,
                        content = content,
                        tags = tags,
                        frontmatter = emptyMap()
                    )
                    
                    when (val result = vaultManager.createNote(request)) {
                        is ObsidianResult.Success<*> -> "Заметка '$path' успешно создана"
                        is ObsidianResult.Error -> "Ошибка создания: ${result.message}"
                    }
                }
                
                "update_note" -> {
                    val path = action.parameters["path"] as? String ?: return "Не указан путь"
                    val title = action.parameters["title"] as? String
                    val content = action.parameters["content"] as? String
                    val tags = (action.parameters["tags"] as? String)?.split(",")?.map { it.trim() }?.toSet()
                    
                    val request = UpdateNoteRequest(
                        path = path,
                        title = title,
                        content = content,
                        tags = tags,
                        frontmatter = null
                    )
                    
                    when (val result = vaultManager.updateNote(request)) {
                        is ObsidianResult.Success<*> -> "Заметка '$path' успешно обновлена"
                        is ObsidianResult.Error -> "Ошибка обновления: ${result.message}"
                    }
                }
                
                "get_tags" -> {
                    when (val result = vaultManager.getAllTags()) {
                        is ObsidianResult.Success<*> -> {
                            val tags = result.data as List<String>
                            "Доступные теги: ${tags.joinToString(", ")}"
                        }
                        is ObsidianResult.Error -> "Ошибка получения тегов: ${result.message}"
                    }
                }
                
                else -> "Неизвестный инструмент: ${action.tool}"
            }
        } catch (e: Exception) {
            logger.error(e) { "Error executing action: ${action.tool}" }
            "Ошибка выполнения: ${e.message}"
        }
    }
}