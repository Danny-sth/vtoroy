package com.vtoroy.dto

/**
 * ReAct (Reasoning + Acting) pattern data structures
 */

/**
 * Single reasoning step in ReAct loop
 */
data class ReasoningStep(
    val thought: String,
    val action: ToolAction?,
    val observation: String?,
    val stepNumber: Int
)

/**
 * Tool action to execute
 */
data class ToolAction(
    val tool: String,
    val parameters: Map<String, Any>
)

/**
 * Complete reasoning context
 */
data class ReasoningContext(
    val originalQuery: String,
    val steps: MutableList<ReasoningStep> = mutableListOf(),
    val isCompleted: Boolean = false,
    val finalResult: String? = null
) {
    fun addStep(step: ReasoningStep) {
        steps.add(step)
    }
    
    fun getContextString(): String = buildString {
        appendLine("Original Query: $originalQuery")
        appendLine()
        steps.forEach { step ->
            appendLine("Step ${step.stepNumber}:")
            appendLine("Thought: ${step.thought}")
            step.action?.let { 
                appendLine("Action: ${it.tool}(${it.parameters})")
            }
            step.observation?.let {
                appendLine("Observation: $it")
            }
            appendLine()
        }
    }
}

/**
 * Reasoning result
 */
sealed class ReasoningResult {
    data class Continue(val nextStep: ReasoningStep) : ReasoningResult()
    data class Complete(val result: String) : ReasoningResult()
    data class Error(val message: String) : ReasoningResult()
}