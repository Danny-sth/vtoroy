plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.4"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
	id("jacoco")
}

group = "com.jarvis"
version = "0.4.0"
description = "Personal AI Assistant with Autonomous Decision Making"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
	maven { url = uri("https://repo.spring.io/snapshot") }
}

extra["springAiVersion"] = "1.0.0-M3"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
	
	// Spring AI dependencies
	implementation(platform("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}"))
	implementation("org.springframework.ai:spring-ai-anthropic-spring-boot-starter")
	implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")
	implementation("org.springframework.ai:spring-ai-transformers-spring-boot-starter")
	
	// Database
	runtimeOnly("org.postgresql:postgresql")
	implementation("com.pgvector:pgvector:0.1.6")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	
	// Markdown processing
	implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
	
	// YAML parsing for frontmatter
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
	
	// File watching (for future use)
	implementation("io.methvin:directory-watcher:0.18.0")
	
	// Logging
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
	
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	
	// MockK for Kotlin mocking
	testImplementation("io.mockk:mockk:1.13.8")
	testImplementation("com.ninja-squad:springmockk:4.0.2")
	
	// AssertJ for fluent assertions
	testImplementation("org.assertj:assertj-core:3.24.2")
	
	// TestContainers for integration tests
	testImplementation("org.testcontainers:testcontainers:1.19.3")
	testImplementation("org.testcontainers:postgresql:1.19.3")
	testImplementation("org.testcontainers:junit-jupiter:1.19.3")
	
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

// JaCoCo test coverage
tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.jacocoTestCoverageVerification {
	violationRules {
		rule {
			limit {
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}

// Generate build-info  
springBoot {
	buildInfo()
}
