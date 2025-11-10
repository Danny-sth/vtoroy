package com.vtoroy.util

import kotlinx.coroutines.delay
import mu.KotlinLogging

/**
 * Utility для retry логики с exponential backoff
 */
object RetryUtil {

    private val logger = KotlinLogging.logger {}

    /**
     * Выполняет операцию с retry и exponential backoff
     *
     * @param maxAttempts максимальное количество попыток
     * @param initialDelay начальная задержка в мс
     * @param maxDelay максимальная задержка в мс
     * @param factor множитель для exponential backoff
     * @param block операция для выполнения
     * @return результат операции
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 2000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    logger.warn { "Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}, retrying in ${currentDelay}ms" }
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
            }
        }

        // Если все попытки неудачны, бросаем последнее исключение
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }

    /**
     * Выполняет операцию с retry только для определенных исключений
     */
    suspend fun <T> withRetryFor(
        maxAttempts: Int = 3,
        initialDelay: Long = 100,
        maxDelay: Long = 2000,
        factor: Double = 2.0,
        retryOn: List<Class<out Exception>> = emptyList(),
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                // Проверяем нужно ли retry для этого типа исключения
                val shouldRetry = retryOn.isEmpty() || retryOn.any { it.isInstance(e) }

                if (!shouldRetry) {
                    throw e
                }

                lastException = e
                if (attempt < maxAttempts - 1) {
                    logger.warn { "Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}, retrying in ${currentDelay}ms" }
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
            }
        }

        throw lastException ?: IllegalStateException("Retry failed without exception")
    }
}
