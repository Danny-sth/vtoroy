package com.vtoroy.service.ai

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * AiMetricsWrapper - оборачивает AnthropicChatModel для сбора метрик
 * Отслеживает latency, token usage, success/failure rates
 */
@Component
class AiMetricsWrapper(
    private val chatModel: AnthropicChatModel,
    private val meterRegistry: MeterRegistry
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Выполняет AI вызов с отслеживанием метрик
     */
    fun call(prompt: Prompt): ChatResponse {
        val startTime = System.nanoTime()
        var outcome = "success"

        try {
            val response = chatModel.call(prompt)

            // Отслеживаем успешный вызов
            val duration = System.nanoTime() - startTime
            recordMetrics(outcome, duration, prompt, response)

            return response

        } catch (e: Exception) {
            outcome = "failure"
            val duration = System.nanoTime() - startTime
            recordMetrics(outcome, duration, prompt, null)

            logger.error(e) { "AI call failed after ${TimeUnit.NANOSECONDS.toMillis(duration)}ms" }
            throw e
        }
    }

    /**
     * Записывает метрики в Micrometer registry
     */
    private fun recordMetrics(
        outcome: String,
        durationNanos: Long,
        prompt: Prompt,
        response: ChatResponse?
    ) {
        // Latency метрика
        meterRegistry.timer(
            "ai.request.duration",
            "outcome", outcome,
            "model", "anthropic"
        ).record(durationNanos, TimeUnit.NANOSECONDS)

        // Счетчик запросов
        meterRegistry.counter(
            "ai.request.count",
            "outcome", outcome,
            "model", "anthropic"
        ).increment()

        // Token usage метрики (approximate)
        if (response != null) {
            val inputTokens = estimateTokens(prompt.instructions.joinToString(" ") { it.content })
            val outputTokens = estimateTokens(response.result.output.content)

            meterRegistry.counter(
                "ai.tokens.input",
                "model", "anthropic"
            ).increment(inputTokens.toDouble())

            meterRegistry.counter(
                "ai.tokens.output",
                "model", "anthropic"
            ).increment(outputTokens.toDouble())

            // Cost estimation (Claude Sonnet pricing: ~$3/$15 per 1M tokens)
            val estimatedInputCost = (inputTokens / 1_000_000.0) * 3.0
            val estimatedOutputCost = (outputTokens / 1_000_000.0) * 15.0
            val totalCost = estimatedInputCost + estimatedOutputCost

            meterRegistry.counter(
                "ai.cost.estimated",
                "model", "anthropic"
            ).increment(totalCost)
        }
    }

    /**
     * Приблизительная оценка количества токенов
     * Простое приближение: 1 token ≈ 4 chars для английского, ~2 для русского
     */
    private fun estimateTokens(text: String): Int {
        // Считаем соотношение кириллицы к общему тексту
        val cyrillicChars = text.count { it in 'а'..'я' || it in 'А'..'Я' }
        val totalChars = text.length

        val avgCharsPerToken = if (cyrillicChars > totalChars / 2) {
            2.0 // Русский текст
        } else {
            4.0 // Английский текст
        }

        return (totalChars / avgCharsPerToken).toInt().coerceAtLeast(1)
    }

    /**
     * Получает текущую статистику AI вызовов
     */
    fun getStats(): Map<String, Any> {
        val successCount = meterRegistry.counter("ai.request.count", "outcome", "success", "model", "anthropic").count()
        val failureCount = meterRegistry.counter("ai.request.count", "outcome", "failure", "model", "anthropic").count()
        val totalCost = meterRegistry.counter("ai.cost.estimated", "model", "anthropic").count()

        return mapOf(
            "totalRequests" to (successCount + failureCount).toInt(),
            "successfulRequests" to successCount.toInt(),
            "failedRequests" to failureCount.toInt(),
            "successRate" to if (successCount + failureCount > 0) {
                (successCount / (successCount + failureCount) * 100).toInt()
            } else 0,
            "estimatedCostUsd" to String.format("%.4f", totalCost)
        )
    }
}
