package com.vtoroy.controller

import com.vtoroy.service.LoggingService
import mu.KotlinLogging
import org.springframework.boot.info.BuildProperties
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = ["*"])
class SystemController(
    private val loggingService: LoggingService,
    private val buildProperties: BuildProperties?
) {
    private val logger = KotlinLogging.logger {}
    private val logEmitters = ConcurrentHashMap<String, SseEmitter>()
    
    @GetMapping("/logs/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamLogs(): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val emitterId = System.currentTimeMillis().toString()
        
        logEmitters[emitterId] = emitter
        logger.info { "New log stream connection: $emitterId" }
        
        // Send initial connection message
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connected")
                    .data(mapOf(
                        "message" to "Подключено к логам Jarvis",
                        "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "level" to "INFO"
                    ))
            )
        } catch (e: Exception) {
            logger.warn { "Failed to send initial message: ${e.message}" }
        }
        
        // Start real-time log monitoring
        lateinit var logListener: (LoggingService.LogEntry) -> Unit
        logListener = { logEntry ->
            try {
                if (logEmitters.containsKey(emitterId)) {
                    emitter.send(
                        SseEmitter.event()
                            .name("log")
                            .data(mapOf(
                                "timestamp" to logEntry.timestamp,
                                "level" to logEntry.level,
                                "logger" to logEntry.logger,
                                "message" to logEntry.message
                            ))
                    )
                }
            } catch (e: Exception) {
                logger.debug { "Failed to send log to $emitterId: ${e.message}" }
                logEmitters.remove(emitterId)
                loggingService.removeLogListener(logListener)
            }
        }
        
        loggingService.addLogListener(logListener)
        
        emitter.onCompletion {
            logger.info { "Log stream completed: $emitterId" }
            logEmitters.remove(emitterId)
            loggingService.removeLogListener(logListener)
        }
        
        emitter.onTimeout {
            logger.info { "Log stream timeout: $emitterId" }
            logEmitters.remove(emitterId)
            loggingService.removeLogListener(logListener)
        }
        
        emitter.onError { throwable ->
            logger.warn { "Log stream error: $emitterId - ${throwable.message}" }
            logEmitters.remove(emitterId)
            loggingService.removeLogListener(logListener)
        }
        
        return emitter
    }
    
    @GetMapping("/logs/recent")
    fun getRecentLogs(@RequestParam(defaultValue = "100") lines: Int): ResponseEntity<Map<String, Any>> {
        return try {
            // Получаем логи из Logback appender
            val memoryLogs = loggingService.getRecentLogs(lines).map { logEntry ->
                mapOf(
                    "timestamp" to logEntry.timestamp,
                    "level" to logEntry.level,
                    "logger" to logEntry.logger,
                    "message" to logEntry.message
                )
            }
            
            // Также добавляем текущую активность
            val allLogs = memoryLogs.toMutableList()
            
            // Добавляем информацию о текущем состоянии
            allLogs.add(mapOf(
                "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                "level" to "INFO",
                "logger" to "SystemController",
                "message" to "Logs requested - showing last $lines entries"
            ))
            
            ResponseEntity.ok(mapOf(
                "logs" to allLogs.takeLast(lines),
                "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "lines" to allLogs.size
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting recent logs" }
            ResponseEntity.ok(mapOf(
                "logs" to listOf(
                    mapOf(
                        "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        "level" to "ERROR",
                        "logger" to "SystemController",
                        "message" to "Не удалось загрузить логи: ${e.message}"
                    )
                ),
                "error" to true
            ))
        }
    }
    
    @GetMapping("/version")
    fun getVersion(): ResponseEntity<Map<String, Any>> {
        return try {
            val version = buildProperties?.version ?: "unknown"
            val buildTime = buildProperties?.time
            
            ResponseEntity.ok(mapOf(
                "version" to version,
                "buildTime" to (buildTime?.toString() ?: "unknown"),
                "name" to (buildProperties?.name ?: "Jarvis")
            ))
        } catch (e: Exception) {
            logger.error(e) { "Error getting version info" }
            ResponseEntity.ok(mapOf(
                "version" to "v0.3.0", // fallback
                "buildTime" to "unknown",
                "name" to "Jarvis"
            ))
        }
    }
}