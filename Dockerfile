# Multi-stage build for Jarvis
FROM gradle:8.14-jdk21 as builder

WORKDIR /app

# Copy only build files first for dependency caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle gradle

# Download dependencies (this layer will be cached unless build files change)
RUN gradle dependencies --no-daemon

# Copy source files (this will only invalidate cache when code changes)
COPY src src
COPY obsidian-vault obsidian-vault

# Build application
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy jar from builder stage (includes static frontend files)
COPY --from=builder /app/build/libs/*.jar app.jar

# Copy ONNX model
COPY --from=builder /app/src/main/resources/models /app/models

# Copy Obsidian vault
COPY obsidian-vault obsidian-vault

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "-Xmx512m", "-Xms256m", "app.jar"]