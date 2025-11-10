package com.vtoroy.config

import com.google.common.util.concurrent.RateLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.util.concurrent.ConcurrentHashMap

/**
 * RateLimitingInterceptor - защита API от злоупотреблений
 * Ограничивает количество запросов per session/IP
 */
@Component
class RateLimitingInterceptor : HandlerInterceptor {

    private val logger = KotlinLogging.logger {}

    // Rate limiters per session ID
    private val sessionLimiters = ConcurrentHashMap<String, RateLimiter>()

    // Global rate limiter for all requests
    private val globalLimiter = RateLimiter.create(100.0) // 100 requests/second globally

    // Per-session rate: 10 requests/second
    private val perSessionRate = 10.0

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // Пропускаем health check и actuator endpoints
        val path = request.requestURI
        if (path.startsWith("/actuator") || path == "/") {
            return true
        }

        // Проверяем global rate limit
        if (!globalLimiter.tryAcquire()) {
            logger.warn { "Global rate limit exceeded for ${request.remoteAddr}" }
            sendRateLimitError(response, "Global rate limit exceeded. Please try again later.")
            return false
        }

        // Проверяем per-session rate limit для API endpoints
        if (path.startsWith("/api/")) {
            val sessionId = extractSessionId(request)
            if (sessionId != null) {
                val limiter = sessionLimiters.computeIfAbsent(sessionId) {
                    RateLimiter.create(perSessionRate)
                }

                if (!limiter.tryAcquire()) {
                    logger.warn { "Session rate limit exceeded for session: $sessionId" }
                    sendRateLimitError(response, "Too many requests. Please slow down.")
                    return false
                }
            }
        }

        return true
    }

    /**
     * Извлекает session ID из запроса
     */
    private fun extractSessionId(request: HttpServletRequest): String? {
        // Пытаемся извлечь из заголовка
        val headerSessionId = request.getHeader("X-Session-Id")
        if (headerSessionId != null) {
            return headerSessionId
        }

        // Пытаемся извлечь из параметра query
        val querySessionId = request.getParameter("sessionId")
        if (querySessionId != null) {
            return querySessionId
        }

        // Fallback на IP адрес
        return request.remoteAddr
    }

    /**
     * Отправляет HTTP 429 Too Many Requests
     */
    private fun sendRateLimitError(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = "application/json"
        response.writer.write("""
            {
                "error": "rate_limit_exceeded",
                "message": "$message",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent())
    }

    /**
     * Очистка неактивных rate limiters (вызывается периодически)
     */
    fun cleanup() {
        // Удаляем старые limiters (можно добавить timestamp tracking)
        if (sessionLimiters.size > 1000) {
            logger.info { "Cleaning up rate limiters cache, current size: ${sessionLimiters.size}" }
            sessionLimiters.clear()
        }
    }
}
