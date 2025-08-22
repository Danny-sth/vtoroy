package com.jarvis

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [
    org.springframework.ai.autoconfigure.transformers.TransformersEmbeddingModelAutoConfiguration::class
])
class JarvisApplication

fun main(args: Array<String>) {
	// Test dependency caching optimization
	runApplication<JarvisApplication>(*args)
}
