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
docker-compose -f .scripts/docker-compose.local.yml up -d postgres

# Run application locally
./gradlew bootRun --args='--spring.profiles.active=local'

# Run with custom Obsidian vault path
OBSIDIAN_VAULT_PATH="/path/to/vault" ./gradlew bootRun --args='--spring.profiles.active=local'
```

### Database Operations
```bash
# Reset database (removes all data)
docker-compose -f .scripts/docker-compose.local.yml down postgres -v
docker-compose -f .scripts/docker-compose.local.yml up -d postgres

# View Flyway migration status
./gradlew flywayInfo

# Run migrations manually (if needed)
./gradlew flywayMigrate
```

### Production Deployment
```bash
# Quick rebuild with organized scripts
./.scripts/rebuild.sh

# Build Docker image
docker build -t jarvis:latest .

# Deploy to server
./.scripts/deploy.sh [server-ip]  # Defaults to configured server

# Production compose (from .scripts/)
docker-compose -f .scripts/docker-compose.prod.yml up -d

# Stop all services
./.scripts/stop.sh
```

## Architecture Overview

### Core Components

**Multi-Agent Architecture**: System now uses specialized agents for different knowledge sources with autonomous decision-making through Spring AI Routing Workflow Pattern.

**Knowledge Management**: Agent-based knowledge processing with ML-powered memory classification using hybrid ensemble voting from semantic, structural, and contextual classifiers.

**Vector Search**: PostgreSQL with pgvector extension for semantic search using ONNX embeddings (all-MiniLM-L6-v2, 384 dimensions).

**Chat History**: Conversational context with PostgreSQL storage, supporting up to 20 messages per session.

**Web UI**: Static HTML/CSS/JS interface embedded in JAR, no Node.js dependencies.

### Project Structure v0.4.0 - Clean Architecture

```
jarvis/
├── .scripts/                       # 🔧 Build and Deploy Scripts
│   ├── rebuild.sh                  # Docker rebuild script  
│   ├── clean-rebuild.sh           # Clean rebuild script
│   ├── deploy.sh                  # Production deployment
│   └── stop.sh                    # Stop services script
├── docs/                          # 📚 Documentation
│   ├── ARCHITECTURE.md            # Detailed architecture docs
│   ├── DEPLOYMENT.md              # Deployment guide
│   └── CHANGELOG.md               # Version history
├── src/main/kotlin/com/jarvis/
│   ├── agent/                     # 🤖 Multi-Agent Domain Layer
│   │   ├── contract/             # 📋 Agent interfaces  
│   │   ├── Agent.kt      # Base agent interface
│   │   └── KnowledgeManageable.kt  # Knowledge agent interface
│   ├── MainAgent.kt      # Orchestrator agent with routing logic
│   ├── ObsidianAgent.kt  # Obsidian vault specialist
│   ├── NotionAgent.kt    # Notion workspace specialist (stub)
│   └── memory/           # Memory classification layer
│       ├── contract/     # Memory classification interfaces
│       │   └── MemoryClassifier.kt     # ML classification system
│       ├── HybridMemoryClassifier.kt      # Ensemble voting
│       ├── SemanticMemoryClassifier.kt    # ML-based classification
│       ├── StructuralMemoryClassifier.kt  # Pattern matching
│       └── ContextMemoryClassifier.kt     # Metadata analysis
├── service/              # Business logic
│   ├── JarvisService.kt  # Main orchestration
│   ├── KnowledgeService.kt  # Multi-agent knowledge management
│   └── knowledge/        # Knowledge source layer
│       ├── contract/     # Knowledge source interfaces
│       │   └── KnowledgeSource.kt      # Pluggable knowledge sources
│       └── ObsidianKnowledgeSource.kt  # Obsidian implementation
├── repository/           # Data access layer (Spring Data JPA)
│   ├── ChatMessageRepository.kt
│   ├── ChatSessionRepository.kt
│   └── KnowledgeFileRepository.kt
├── controller/           # REST API layer
├── entity/              # JPA entities
├── dto/                 # Data transfer objects
└── config/              # Spring configuration
```

### Key Components

- **MainAgent**: Central orchestrator with routing workflow (dialogue/knowledge_search/delegate)
- **ObsidianAgent**: Specialized agent for Obsidian vault management with ML classification
- **HybridMemoryClassifier**: Ensemble ML system combining semantic, structural, and contextual analysis
- **KnowledgeService**: Multi-agent coordinator for knowledge source management
- **JarvisService**: Session management and chat orchestration

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
All 58 tests should pass. If integration tests fail, check that TestContainers can access Docker daemon.

## Recent Updates (v0.4.0)

### Architecture Improvements
- **Refactored to Clean Architecture**: Interfaces separated from implementations in `contract/` packages
- **Multi-Agent System**: Specialized agents for different knowledge sources
- **ML-Powered Classification**: Replaced hardcoded logic with intelligent memory classification
- **Layer-based Organization**: Each layer has its own `contract/` package for interfaces

### New Features
- **Hybrid Memory Classifier**: Ensemble voting from semantic, structural, and contextual classifiers
- **Enhanced Knowledge Management**: Agent-based architecture with pluggable knowledge sources
- **Improved Project Structure**: Clear separation of concerns with interfaces in dedicated packages

### Memory Classification System
- **SemanticMemoryClassifier**: Uses embeddings and cosine similarity for content analysis
- **StructuralMemoryClassifier**: Pattern matching for document structure (headings, lists, tasks)
- **ContextMemoryClassifier**: Metadata-based classification (file paths, timestamps, tags)
- **HybridMemoryClassifier**: Ensemble voting with configurable weights

### Supported Document Types
- `meeting`: Meeting notes, standups, discussions
- `project`: Project documentation, specifications
- `task`: TODO items, action items, checklists  
- `note`: General notes, observations
- `code`: Code snippets, technical documentation
- `documentation`: Reference materials, guides
- `research`: Research notes, analysis

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