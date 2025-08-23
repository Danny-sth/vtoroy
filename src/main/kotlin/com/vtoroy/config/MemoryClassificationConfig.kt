package com.vtoroy.config

import com.vtoroy.agent.memory.contract.ClassificationConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for memory classification system
 */
@Configuration
class MemoryClassificationConfig {
    
    @Bean
    fun classificationConfig(): ClassificationConfig {
        return ClassificationConfig(
            semanticWeight = 0.5f,
            structuralWeight = 0.3f,
            contextWeight = 0.2f,
            minimumConfidence = 0.1f,
            enableEnsemble = true
        )
    }
}