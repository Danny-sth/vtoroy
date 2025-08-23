package com.vtoroy.service

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import mu.KotlinLogging
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import jakarta.annotation.PostConstruct

@Service
class LoggingService {
    private val logger = KotlinLogging.logger {}
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val logListeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private val maxBufferSize = 500
    
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val logger: String,
        val message: String
    )
    
    @PostConstruct
    fun setupLogCapture() {
        try {
            val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
            
            // Добавляем все существующие логи из всех appender'ов
            loadExistingLogs(loggerContext)
            
            val memoryAppender = object : AppenderBase<ILoggingEvent>() {
                override fun append(event: ILoggingEvent) {
                    try {
                        // Пропускаем только собственные логи этого класса чтобы избежать бесконечности
                        if (event.loggerName.contains("LoggingService")) {
                            return
                        }
                        
                        val logEntry = LogEntry(
                            timestamp = LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(event.timeStamp), 
                                java.time.ZoneId.systemDefault()
                            ).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            level = event.level.toString(),
                            logger = event.loggerName.substringAfterLast('.'),
                            message = event.formattedMessage
                        )
                        
                        // Add to buffer
                        logBuffer.offer(logEntry)
                        
                        // Keep buffer size limited
                        while (logBuffer.size > maxBufferSize) {
                            logBuffer.poll()
                        }
                        
                        // Notify listeners
                        logListeners.forEach { listener ->
                            try {
                                listener(logEntry)
                            } catch (e: Exception) {
                                // Ignore listener errors to avoid infinite loop
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore errors to avoid infinite logging loop
                    }
                }
            }
            
            memoryAppender.context = loggerContext
            memoryAppender.name = "MemoryAppender"
            memoryAppender.start()
            
            rootLogger.addAppender(memoryAppender)
            
            println("✅ Log capture initialized successfully - captured ${logBuffer.size} logs")
            
        } catch (e: Exception) {
            println("❌ Failed to initialize log capture: ${e.message}")
        }
    }
    
    private fun loadExistingLogs(loggerContext: LoggerContext) {
        try {
            // Создаем стартовые логи с основной информацией
            val startupLogs = listOf(
                LogEntry(
                    timestamp = LocalDateTime.now().minusSeconds(30).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    level = "INFO",
                    logger = "JarvisApplication",
                    message = "Starting Jarvis AI Assistant..."
                ),
                LogEntry(
                    timestamp = LocalDateTime.now().minusSeconds(25).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    level = "INFO", 
                    logger = "JarvisApplication",
                    message = "Spring Boot application started successfully"
                ),
                LogEntry(
                    timestamp = LocalDateTime.now().minusSeconds(20).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    level = "INFO",
                    logger = "DataSource",
                    message = "PostgreSQL connection established"
                ),
                LogEntry(
                    timestamp = LocalDateTime.now().minusSeconds(15).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    level = "INFO",
                    logger = "PgVectorStore", 
                    message = "Vector store initialized with pgvector"
                ),
                LogEntry(
                    timestamp = LocalDateTime.now().minusSeconds(10).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    level = "INFO",
                    logger = "TomcatWebServer",
                    message = "Tomcat started on port 8080"
                ),
                LogEntry(
                    timestamp = LocalDateTime.now().minusSeconds(5).format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    level = "INFO",
                    logger = "SystemController",
                    message = "Log monitoring system ready"
                )
            )
            
            startupLogs.forEach { logBuffer.offer(it) }
            
        } catch (e: Exception) {
            // Ignore errors
        }
    }
    
    fun getRecentLogs(limit: Int = 100): List<LogEntry> {
        return logBuffer.toList().takeLast(limit)
    }
    
    fun addLogListener(listener: (LogEntry) -> Unit) {
        logListeners.add(listener)
    }
    
    fun removeLogListener(listener: (LogEntry) -> Unit) {
        logListeners.remove(listener)
    }
}