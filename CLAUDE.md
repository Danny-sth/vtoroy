# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jarvis is a personal AI assistant built with Spring Boot 3.5.4 + Kotlin 1.9.25, featuring autonomous decision-making through Spring AI Routing Workflow Pattern. The system integrates with Anthropic Claude 3.5 Sonnet and includes a PostgreSQL knowledge base with vector search capabilities.

## Essential Commands

### Build and Test
```bash
# Build project
./gradlew build

# Run all tests (46 tests, must all pass)
./gradlew test

# Run tests with coverage report
./gradlew test jacocoTestReport

# Run specific test categories
./gradlew test --tests "*Test" --exclude-task integrationTest  # Unit tests only
./gradlew test --tests "*IntegrationTest"                      # Integration tests only
```

### Development Server
```bash
# Start PostgreSQL database
docker-compose up -d postgres

# Run application locally
./gradlew bootRun --args='--spring.profiles.active=local'

# Run with custom Obsidian vault path
OBSIDIAN_VAULT_PATH="/path/to/vault" ./gradlew bootRun --args='--spring.profiles.active=local'
```

### Database Operations
```bash
# Reset database (removes all data)
docker-compose down postgres -v
docker-compose up -d postgres

# View Flyway migration status
./gradlew flywayInfo

# Run migrations manually (if needed)
./gradlew flywayMigrate
```

### Production Deployment
```bash
# Build Docker image
docker build -t jarvis:latest .

# Deploy to server
./deploy.sh [server-ip]  # Defaults to configured server

# Production compose
docker-compose -f docker-compose.prod.yml up -d
```

## Architecture Overview

### Core Components

**Spring AI Routing Workflow**: Autonomous decision-making system that routes queries between general conversation and knowledge base search. The routing logic analyzes chat history to avoid redundant searches.

**Knowledge Service**: Manages Obsidian vault synchronization with pgvector-based semantic search using ONNX embeddings (all-MiniLM-L6-v2, 384 dimensions).

**Chat History**: Maintains conversational context with PostgreSQL storage, supporting up to 20 messages per session for context-aware responses.

**Web UI**: Static HTML/CSS/JS interface embedded in JAR, no Node.js dependencies.

### Key Service Classes

- `JarvisService`: Main chat orchestration with session management
- `RoutingWorkflow`: AI-powered query routing with chat history analysis  
- `KnowledgeService`: Obsidian vault sync and vector search
- `ChatController` & `KnowledgeController`: REST API endpoints

### Database Schema

- `chat_sessions`: User conversation sessions
- `chat_messages`: Message history with roles (USER, ASSISTANT, SYSTEM)
- `knowledge_files`: Obsidian documents with vector embeddings
- Uses pgvector extension with IVFFLAT indexes for similarity search

## Configuration

### Required Environment Variables
```bash
# Required for Claude API
export ANTHROPIC_API_KEY="your-api-key"

# Optional: Custom Obsidian vault location  
export OBSIDIAN_VAULT_PATH="/path/to/obsidian-vault"
```

### Application Profiles
- `local`: Development with localhost database
- `docker`: Docker environment configuration
- `test`: Test configuration with mocked services

### Key Configuration Files
- `application.yml`: Main configuration (Claude API, pgvector settings)
- `application-local.yml`: Local development overrides
- `application-docker.yml`: Docker-specific settings
- `application-test.yml`: Test configuration with mocks

## Testing Strategy

The project maintains 100% test pass rate (46/46 tests) with 80% code coverage:

### Test Structure
- **Unit Tests** (15): Service layer with MockK
- **Controller Tests** (20): MockMvc with @WebMvcTest
- **Integration Tests** (10): TestContainers with real PostgreSQL
- **Application Tests** (1): Full Spring Boot context

### Test Configuration
- `TestConfiguration.kt`: Provides mocked Anthropic API and embedding models
- `MockEmbeddingModel`: Generates deterministic 384D vectors for consistent testing
- TestContainers automatically spins up PostgreSQL with pgvector for integration tests

### Running Tests
Tests are designed to be deterministic and run without external dependencies. The MockEmbeddingModel ensures consistent vector generation for testing similarity searches.

## Development Workflow

### Adding New Features
1. Write tests first (TDD approach maintained)
2. Implement feature in appropriate service layer
3. Add controller endpoint if needed
4. Update configuration if required
5. Run full test suite to ensure no regressions

### Working with AI Components
- Claude API calls go through `AnthropicChatModel` (Spring AI)
- Embedding generation uses ONNX model with fallback to MockEmbeddingModel
- All AI interactions are tested with mocked responses

### Database Changes
- Create new Flyway migration in `src/main/resources/db/migration/`
- Follow naming convention: `V{number}__{description}.sql`
- Update corresponding entity classes and repositories
- Add integration tests for new schema

## Performance Characteristics

### Response Times
- Simple queries: 2-3 seconds
- History-based queries: 2-3 seconds (no search needed)
- Knowledge queries (first time): 20-30 seconds  
- Knowledge queries (cached): 0.03 seconds (777x faster)

### Optimization Features
- Query embedding cache with hash-based lookup
- PostgreSQL vector indexes (IVFFLAT with cosine distance)
- Context-aware routing eliminates unnecessary knowledge searches
- Chat history limits (20 messages max) for performance

## Web Interface

The web UI is served as static files embedded in the Spring Boot JAR:
- `src/main/resources/static/index.html`: Main interface
- `src/main/resources/static/css/style.css`: Jarvis-themed styling
- `src/main/resources/static/js/app.js`: Fetch API integration

Access at: http://localhost:8080 (when running locally)

## Deployment Notes

### Docker Configuration
- Multi-stage build with Java 21 runtime
- PostgreSQL 16 with pgvector extension  
- Volume mounts for Obsidian vault and ONNX model
- Health checks via Spring Actuator

### Production Considerations
- Requires external PostgreSQL with pgvector
- ONNX model file must be present in container
- Configure proper memory limits (1-2GB recommended)
- Monitor via `/actuator/health` and `/actuator/metrics`

### Obsidian Integration
- Supports markdown files with YAML frontmatter
- Processes internal links `[[link]]` and tags `#tag`  
- Sync via `/api/knowledge/sync` endpoint
- File watching disabled in current version (MVP)

## Common Issues & Solutions

### ONNX Model Missing
If the all-MiniLM-L6-v2.onnx model is not found, the system automatically falls back to MockEmbeddingModel for development/testing.

### pgvector Extension
Ensure PostgreSQL containers use `pgvector/pgvector:pg16` image, not standard PostgreSQL.

### Test Failures
All 46 tests should pass. If integration tests fail, check that TestContainers can access Docker daemon.

### Memory Issues
Increase JVM heap size in production: `-Xmx2g` or configure Docker memory limits.

## API Endpoints

### Chat API
- `POST /api/chat`: Send message with sessionId
- Supports conversation history and context-aware responses

### Knowledge API  
- `POST /api/knowledge/sync`: Sync Obsidian vault
- `GET /api/knowledge/status`: Check knowledge base status

### Health Monitoring
- `GET /actuator/health`: Application health
- `GET /actuator/metrics`: Performance metrics