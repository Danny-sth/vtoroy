package com.vtoroy.agent.obsidian

import com.vtoroy.dto.*
import com.vtoroy.service.knowledge.ObsidianVaultManager
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * VaultOperations - выполняет операции с Obsidian vault
 * Отделяет бизнес-логику работы с файлами от парсинга и форматирования
 */
@Component
class VaultOperations(
    private val vaultManager: ObsidianVaultManager
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Выполняет действие и возвращает результат
     */
    suspend fun execute(action: ParsedQuery): String {
        logger.debug { "Executing action: ${action.type}, parameters: ${action.parameters}" }

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
                // Извлекаем title из path
                val extractedTitle = pathParam.substringBeforeLast(".md").substringAfterLast("/")
                pathParam to extractedTitle
            }
            pathParam == null && titleParam != null -> {
                // Создаем path из title
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
            is ObsidianResult.Success<*> -> "📁 Заметка перемещена: $oldPath → $newPath"
            is ObsidianResult.Error -> result.message
        }
    }

    private suspend fun handleListNotes(action: ParsedQuery): String {
        val folder = action.parameters["folder"] as? String
        val isAccessQuery = action.parameters["access_query"] as? Boolean ?: false

        if (isAccessQuery) {
            return handleAccessQuery()
        }

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

    private suspend fun handleListFolders(action: ParsedQuery): String {
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

    private suspend fun handleAccessQuery(): String {
        return when (val result = vaultManager.listFolders()) {
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
    }

    private fun handleAskUser(action: ParsedQuery): String {
        val question = action.parameters["question"] as? String
            ?: "Нужна дополнительная информация"
        return "❓ $question"
    }

    // Formatting helpers

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
}
