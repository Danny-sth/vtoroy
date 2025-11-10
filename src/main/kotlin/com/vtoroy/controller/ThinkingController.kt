package com.vtoroy.controller

import com.vtoroy.service.ThinkingService
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * SSE контроллер для real-time отображения мыслей Второго
 * Теперь делегирует работу ThinkingService для лучшей тестируемости
 */
@RestController
@RequestMapping("/api/thinking")
@CrossOrigin(origins = ["*"])
class ThinkingController(
    private val thinkingService: ThinkingService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Подключение к потоку мыслей для конкретной сессии
     */
    @GetMapping("/stream/{sessionId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamThoughts(@PathVariable sessionId: String): SseEmitter {
        logger.info { "Новое SSE подключение для сессии: $sessionId" }
        return thinkingService.createStream(sessionId)
    }

    /**
     * Получение информации о подключениях (для отладки)
     */
    @GetMapping("/status")
    fun getConnectionsStatus(): Map<String, Any> {
        return thinkingService.getConnectionsStatus()
    }

    companion object {
        /**
         * Backward compatibility: static методы теперь делегируют ThinkingService
         * TODO: Удалить после миграции всего кода на DI
         */
        private var thinkingServiceInstance: ThinkingService? = null

        fun setThinkingService(service: ThinkingService) {
            thinkingServiceInstance = service
        }

        @Deprecated("Use ThinkingService via DI instead", ReplaceWith("thinkingService.sendThought(sessionId, message, type)"))
        fun sendThought(sessionId: String, message: String, type: String = "thinking") {
            thinkingServiceInstance?.sendThought(sessionId, message, type)
        }

        @Deprecated("Use ThinkingService via DI instead", ReplaceWith("thinkingService.finishThinking(sessionId, finalMessage)"))
        fun finishThinking(sessionId: String, finalMessage: String) {
            thinkingServiceInstance?.finishThinking(sessionId, finalMessage)
        }
    }

    init {
        // Устанавливаем instance для backward compatibility
        setThinkingService(thinkingService)
    }
}
