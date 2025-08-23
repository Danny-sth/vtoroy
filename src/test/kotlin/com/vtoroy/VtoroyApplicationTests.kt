package com.vtoroy

import com.vtoroy.config.TestConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.annotation.Import
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfiguration::class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class VtoroyApplicationTests {

	companion object {
		@Container
		@JvmStatic
		val postgresContainer = PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg16")
				.asCompatibleSubstituteFor("postgres")
		)
			.apply {
				withDatabaseName("vtoroy_test")
				withUsername("test")
				withPassword("test")
				withCommand("postgres", "-c", "shared_preload_libraries=vector")
			}

		@DynamicPropertySource
		@JvmStatic
		fun properties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
			registry.add("spring.datasource.username") { postgresContainer.username }
			registry.add("spring.datasource.password") { postgresContainer.password }
		}
	}

	@Test
	fun contextLoads() {
	}

}
