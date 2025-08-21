package com.jarvis.config

import mu.KotlinLogging
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.api.AnthropicApi
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.document.Document
import org.springframework.ai.embedding.EmbeddingRequest
import org.springframework.ai.embedding.EmbeddingResponse
import org.springframework.ai.embedding.Embedding
import org.springframework.ai.transformers.TransformersEmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.DefaultResourceLoader

@Configuration
class AiConfig {
    private val logger = KotlinLogging.logger {}
    
    @Value("\${spring.ai.anthropic.api-key}")
    private lateinit var anthropicApiKey: String
    
    @Bean
    fun anthropicApi(): AnthropicApi {
        logger.info { "Configuring Anthropic API with default settings" }
        // Используем стандартный конструктор - Spring AI сам применит настройки из application.yml
        return AnthropicApi(anthropicApiKey)
    }
    
    @Bean
    fun anthropicChatModel(anthropicApi: AnthropicApi): AnthropicChatModel {
        logger.info { "Creating AnthropicChatModel without function calling - using Routing Workflow instead" }
        return AnthropicChatModel(anthropicApi)
    }
    
    @Bean(name = ["customEmbeddingModel"])
    fun embeddingModel(): EmbeddingModel {
        // Use local ONNX model for embeddings
        // Model: sentence-transformers/all-MiniLM-L6-v2
        // Dimensions: 384
        val modelResource = DefaultResourceLoader().getResource("classpath:models/all-MiniLM-L6-v2.onnx")
        
        if (!modelResource.exists()) {
            logger.warn { "ONNX model not found at ${modelResource.description}, using mock embedding model for development" }
            return MockEmbeddingModel()
        }
        
        logger.info { "Loading ONNX embedding model from ${modelResource.description}" }
        val embeddingModel = TransformersEmbeddingModel()
        embeddingModel.setModelResource("classpath:models/all-MiniLM-L6-v2.onnx")
        // Use default tokenizer for all-MiniLM-L6-v2
        embeddingModel.afterPropertiesSet()
        return embeddingModel
    }
    
    // Function calling удален - используем Routing Workflow
    
    // Mock embedding model for development and testing
    private class MockEmbeddingModel : EmbeddingModel {
        override fun call(request: EmbeddingRequest): EmbeddingResponse {
            val embeddings = request.instructions.map { text ->
                // Return a deterministic 384-dimensional vector based on text hash
                val vector = generateDeterministicVector(text)
                Embedding(vector, 0)
            }
            return EmbeddingResponse(embeddings)
        }
        
        override fun embed(text: String): FloatArray {
            return generateDeterministicVector(text)
        }
        
        override fun embed(texts: MutableList<String>): MutableList<FloatArray> {
            return texts.map { embed(it) }.toMutableList()
        }
        
        override fun embed(document: Document): FloatArray {
            return embed(document.content)
        }
        
        override fun dimensions(): Int = 384
        
        private fun generateDeterministicVector(text: String): FloatArray {
            // Generate deterministic vector based on text hash for consistent test results
            val hash = text.hashCode()
            val random = java.util.Random(hash.toLong())
            return FloatArray(384) { random.nextFloat() * 2.0f - 1.0f } // Range [-1, 1]
        }
    }
}