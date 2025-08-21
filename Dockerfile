# Multi-stage build for Jarvis
FROM gradle:8.14-jdk21 as builder

WORKDIR /app

# Copy all source files
COPY . .

# Build application (gradle image already has gradle)
RUN gradle clean build -x test --no-daemon

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