package com.vtoroy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [
    org.springframework.ai.autoconfigure.transformers.TransformersEmbeddingModelAutoConfiguration::class
])
class VtoroyApplication

fun main(args: Array<String>) {
	// Test dependency caching optimization
	runApplication<VtoroyApplication>(*args)
}
