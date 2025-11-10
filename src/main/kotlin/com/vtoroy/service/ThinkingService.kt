package com.vtoroy.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * ThinkingService - сервис для управления SSE потоками мыслей AI
 * Заменяет static методы ThinkingController для лучшей тестируемости и DI
 */
@Service
class ThinkingService {

    private val logger = KotlinLogging.logger {}

    // Хранилище SSE соединений по sessionId
    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    /**
     * Создает новое SSE подключение для сессии
     */
    fun createStream(sessionId: String, timeoutMs: Long = 300000L): SseEmitter {
        logger.info { "Creating SSE stream for session: $sessionId" }

        val emitter = SseEmitter(timeoutMs)

        emitter.onCompletion {
            logger.debug { "SSE stream completed for session: $sessionId" }
            emitters.remove(sessionId)
        }

        emitter.onError { throwable ->
            logger.error(throwable) { "SSE stream error for session: $sessionId" }
            emitters.remove(sessionId)
        }

        emitter.onTimeout {
            logger.warn { "SSE stream timeout for session: $sessionId" }
            emitters.remove(sessionId)
        }

        // Сохраняем эмиттер
        emitters[sessionId] = emitter

        // Отправляем первое сообщение о подключении
        try {
            val data = mapOf(
                "type" to "connected",
                "message" to "Подключен к потоку мыслей Второго",
                "timestamp" to System.currentTimeMillis()
            )
            emitter.send(SseEmitter.event().data(data))
        } catch (e: Exception) {
            logger.error(e) { "Error sending initial message" }
        }

        return emitter
    }

    /**
     * Отправляет мысль клиенту для данной сессии
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
                logger.warn(e) { "Failed to send thought to session: $sessionId, removing emitter" }
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
                logger.warn(e) { "Error finishing thinking for session: $sessionId" }
            } finally {
                emitters.remove(sessionId)
            }
        }
    }

    /**
     * Получает статус активных подключений
     */
    fun getConnectionsStatus(): Map<String, Any> {
        return mapOf(
            "activeConnections" to emitters.size,
            "sessions" to emitters.keys.toList()
        )
    }

    /**
     * Проверяет есть ли активное подключение для сессии
     */
    fun hasActiveStream(sessionId: String): Boolean {
        return emitters.containsKey(sessionId)
    }

    /**
     * Закрывает SSE подключение для сессии
     */
    fun closeStream(sessionId: String) {
        val emitter = emitters.remove(sessionId)
        emitter?.complete()
    }
}
