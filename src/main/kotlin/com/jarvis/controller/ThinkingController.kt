package com.jarvis.controller

import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * SSE контроллер для real-time отображения мыслей Джарвиса
 */
@RestController
@RequestMapping("/api/thinking")
@CrossOrigin(origins = ["*"])
class ThinkingController {
    
    private val logger = KotlinLogging.logger {}
    
    companion object {
        // Глобальное хранилище SSE соединений по sessionId
        private val emitters = ConcurrentHashMap<String, SseEmitter>()
        
        /**
         * Отправляет мысль всем подключенным клиентам для данной сессии
         */
        fun sendThought(sessionId: String, message: String, type: String = "thinking") {
            val emitter = emitters[sessionId]
            if (emitter != null) {
                try {
                    val data = mapOf(
                        "type" to type,
                        "message" to message,
                        "timestamp" to System.currentTimeMillis()
                    )
                    emitter.send(SseEmitter.event().data(data))
                } catch (e: Exception) {
                    // Соединение разорвано, убираем эмиттер
                    emitters.remove(sessionId)
                }
            }
        }
        
        /**
         * Завершает поток мыслей для сессии
         */
        fun finishThinking(sessionId: String, finalMessage: String) {
            val emitter = emitters[sessionId]
            if (emitter != null) {
                try {
                    val data = mapOf(
                        "type" to "complete",
                        "message" to finalMessage,
                        "timestamp" to System.currentTimeMillis()
                    )
                    emitter.send(SseEmitter.event().data(data))
                    emitter.complete()
                } catch (e: Exception) {
                    // Игнорируем ошибки при завершении
                } finally {
                    emitters.remove(sessionId)
                }
            }
        }
    }
    
    /**
     * Подключение к потоку мыслей для конкретной сессии
     */
    @GetMapping("/stream/{sessionId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamThoughts(@PathVariable sessionId: String): SseEmitter {
        logger.info { "Новое SSE подключение для сессии: $sessionId" }
        
        val emitter = SseEmitter(300000L) // 5 минут timeout
        
        emitter.onCompletion {
            logger.debug { "SSE соединение завершено для сессии: $sessionId" }
            emitters.remove(sessionId)
        }
        
        emitter.onError { throwable ->
            logger.error(throwable) { "Ошибка SSE для сессии: $sessionId" }
            emitters.remove(sessionId)
        }
        
        emitter.onTimeout {
            logger.warn { "SSE timeout для сессии: $sessionId" }
            emitters.remove(sessionId)
        }
        
        // Сохраняем эмиттер для данной сессии
        emitters[sessionId] = emitter
        
        // Отправляем первое сообщение о подключении
        try {
            val data = mapOf(
                "type" to "connected",
                "message" to "Подключен к потоку мыслей Джарвиса",
                "timestamp" to System.currentTimeMillis()
            )
            emitter.send(SseEmitter.event().data(data))
        } catch (e: Exception) {
            logger.error(e) { "Ошибка при отправке начального сообщения" }
        }
        
        return emitter
    }
    
    /**
     * Получение информации о подключениях (для отладки)
     */
    @GetMapping("/status")
    fun getConnectionsStatus(): Map<String, Any> {
        return mapOf(
            "activeConnections" to emitters.size,
            "sessions" to emitters.keys.toList()
        )
    }
}