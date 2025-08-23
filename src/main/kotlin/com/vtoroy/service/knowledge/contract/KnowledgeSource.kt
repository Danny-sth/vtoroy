package com.vtoroy.service.knowledge.contract

/**
 * Knowledge Source interface - аналог MCP Server в Claude Code
 * Каждый источник данных реализует этот интерфейс для синхронизации с векторной БД
 */
interface KnowledgeSource {
    /**
     * Уникальный ID источника (obsidian, notion, etc.)
     */
    val sourceId: String
    
    /**
     * Человеко-читаемое название источника
     */
    val displayName: String
    
    /**
     * Проверяет доступность источника
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Синхронизирует данные из источника
     * Возвращает список элементов знаний для индексации
     */
    suspend fun syncData(): List<KnowledgeItem>
    
    /**
     * Получает статус источника
     */
    suspend fun getStatus(): KnowledgeSourceStatus
}

/**
 * Элемент знаний из источника
 */
data class KnowledgeItem(
    val id: String,                    // Уникальный ID в рамках источника
    val title: String,                 // Заголовок/название
    val content: String,               // Текстовое содержимое
    val sourceId: String,              // ID источника
    val sourcePath: String,            // Путь в источнике (файл, URL, etc.)
    val lastModified: Long,            // Timestamp последнего изменения
    val metadata: Map<String, Any> = emptyMap()  // Дополнительные метаданные
)

/**
 * Статус источника знаний
 */
data class KnowledgeSourceStatus(
    val sourceId: String,
    val isActive: Boolean,
    val lastSync: Long? = null,
    val itemCount: Int = 0,
    val errorMessage: String? = null,
    val syncInProgress: Boolean = false
)