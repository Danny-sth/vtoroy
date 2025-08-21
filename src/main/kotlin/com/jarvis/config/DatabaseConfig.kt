package com.jarvis.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.pgvector.PGvector
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class DatabaseConfig(
    private val dataSource: DataSource
) {
    private val logger = KotlinLogging.logger {}
    
    @PostConstruct
    fun init() {
        // Register PGvector type
        try {
            dataSource.connection.use { connection ->
                PGvector.addVectorType(connection)
                logger.info { "PGvector type registered successfully" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register PGvector type" }
        }
    }
    
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            findAndRegisterModules()
        }
    }
}