# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jarvis is a production-ready personal AI assistant built with **Clean Architecture** principles and implementing the **Claude Code SubAgent pattern**. The system features autonomous decision-making, real-time reasoning display, and complete Obsidian vault integration.

**Architecture**: Spring Boot 3.5.4 + Kotlin 1.9.25 + PostgreSQL 16 + pgvector  
**Version**: 0.6.0 (Latest Release - 2025-08-23)  
**Tests**: 63 test methods with 100% pass rate and 80% code coverage

## 🚀 Essential Commands

### Build and Development
```bash
# Build project
./gradlew build

# Run all tests (63 tests, must all pass)
./gradlew test

# Run with coverage report
./gradlew test jacocoTestReport

# Start with local database
docker-compose -f .scripts/docker-compose.local.yml up -d postgres
./gradlew bootRun --args='--spring.profiles.active=local'

# Custom Obsidian vault path
OBSIDIAN_VAULT_PATH="/path/to/vault" ./gradlew bootRun --args='--spring.profiles.active=local'
```

### Production Deployment
```bash
# Quick rebuild (organized scripts)
./.scripts/rebuild.sh

# Deploy to production server
./.scripts/deploy.sh [server-ip]

# Production services
docker-compose -f .scripts/docker-compose.prod.yml up -d

# Stop all services
./.scripts/stop.sh
```

### Database Operations
```bash
# Reset database (removes all data)
docker-compose -f .scripts/docker-compose.local.yml down postgres -v
docker-compose -f .scripts/docker-compose.local.yml up -d postgres

# Check migrations
./gradlew flywayInfo
```

## 🏗️ Claude Code Architecture Implementation

### Core Principle: SubAgent Pattern with AI-Powered Orchestration

**JarvisMainAgent** - Central orchestrator following Claude Code principles:
- ✅ **AI-based routing** - No hardcoded keywords, pure LLM decisions
- ✅ **Simple delegation** - Clean handoff to specialized SubAgents
- ✅ **Context-aware** - Maintains 10-message conversation history
- ✅ **Error handling** - Graceful fallbacks and error recovery

**AgentDispatcher** - AI-powered agent selection:
- ✅ **Automatic selection** - Uses agent descriptions for intelligent routing
- ✅ **Availability checking** - Real-time agent status verification  
- ✅ **Confidence scoring** - Fallback mechanisms when uncertain
- ✅ **No complex logic** - Simple, clean implementation

**ObsidianAgent** - Specialized SubAgent:
- ✅ **Clear description** for automatic selection
- ✅ **Tool availability** - Full CRUD operations
- ✅ **AI query parsing** - No regex patterns, pure LLM understanding
- ✅ **Context awareness** - Remembers conversation history

### SubAgent Interface (Contract)
```kotlin
interface SubAgent {
    val name: String                    // Agent identifier
    val description: String             // For AI-based selection
    val tools: List<String>?           // Available tool set
    suspend fun canHandle(query: String, chatHistory: List<ChatMessage>): Boolean
    suspend fun handle(query: String, chatHistory: List<ChatMessage>): String  
    suspend fun isAvailable(): Boolean  // Health check
}
```

### Real-time AI Reasoning (SSE)
**ThinkingController** - Shows AI's internal thoughts:
- ✅ **Server-Sent Events** for live reasoning display
- ✅ **Session-based streams** with automatic cleanup
- ✅ **Thought categorization** - start, thinking, complete, error
- ✅ **Frontend integration** - Real-time UI updates

## 📋 Project Structure v0.6.0 - Clean Architecture

```
jarvis/
├── .scripts/                       # 🔧 Build and Deploy Scripts
│   ├── rebuild.sh                  # Docker rebuild script  
│   ├── deploy.sh                   # Production deployment
│   └── docker-compose.*.yml       # Container configurations
├── docs/                          # 📚 Architecture Documentation
│   ├── ARCHITECTURE.md            # Detailed system design
│   ├── DEPLOYMENT.md              # Production deployment guide
│   └── CHANGELOG.md               # Version history
├── src/main/kotlin/com/jarvis/
│   ├── agent/                     # 🤖 SubAgent Domain Layer
│   │   ├── contract/             # 📋 Agent interfaces
│   │   │   ├── SubAgent.kt       # Core SubAgent interface
│   │   │   └── AgentSelection.kt  # Selection result wrapper
│   │   ├── JarvisMainAgent.kt    # Central orchestrator 
│   │   ├── AgentDispatcher.kt    # AI-powered agent selection
│   │   └── ObsidianAgent.kt      # Obsidian vault specialist
│   ├── service/                  # 💼 Business Logic Layer
│   │   ├── JarvisService.kt      # Main chat orchestration
│   │   ├── KnowledgeService.kt   # Vector search management
│   │   └── knowledge/           # Knowledge source implementations
│   │       ├── contract/        # Knowledge source interfaces
│   │       ├── ObsidianVaultManager.kt    # Vault operations
│   │       └── ObsidianKnowledgeSource.kt # Knowledge integration
│   ├── controller/              # 🌐 REST API Layer
│   │   ├── ChatController.kt    # Main chat endpoint
│   │   ├── ThinkingController.kt # SSE reasoning streams
│   │   └── KnowledgeController.kt # Knowledge management API
│   ├── repository/              # 💾 Data Access Layer
│   │   ├── ChatMessageRepository.kt
│   │   ├── ChatSessionRepository.kt
│   │   └── KnowledgeFileRepository.kt
│   ├── entity/                  # 📊 JPA Entities
│   ├── dto/                     # 📦 Data Transfer Objects
│   └── config/                  # ⚙️ Spring Configuration
├── src/main/resources/
│   ├── static/                  # 🌐 Web UI (embedded in JAR)
│   │   ├── index.html           # Main application interface
│   │   ├── css/style.css        # Jarvis-themed dark styling
│   │   └── js/app.js           # Frontend logic with SSE
│   └── db/migration/           # 🗃️ Flyway database migrations
└── src/test/kotlin/            # 🧪 Test Suite (63 tests, 100% pass)
    ├── agent/                  # Agent behavior testing
    ├── service/                # Business logic testing  
    ├── controller/             # API endpoint testing
    └── integration/            # Full system testing
```

## 🔌 API Architecture

### Chat API
- `POST /api/chat` - Main conversation endpoint with session management
- Supports context-aware responses and conversation history

### Knowledge Management API  
- `POST /api/knowledge/sync` - Sync Obsidian vault with vector database
- `GET /api/knowledge/status` - Knowledge base statistics and health

### Real-time Features
- `GET /api/thinking/stream/{sessionId}` - SSE reasoning thoughts
- `GET /api/system/logs/stream` - Live system log streaming

### Health & Monitoring
- `GET /actuator/health` - Application health checks
- `GET /actuator/metrics` - Performance metrics and statistics

## ⚙️ Configuration

### Required Environment Variables
```bash
# Required for Claude API
export ANTHROPIC_API_KEY="your-api-key"

# Optional: Custom Obsidian vault location  
export OBSIDIAN_VAULT_PATH="/path/to/obsidian-vault"
```

### Application Profiles
- **local**: Development with localhost PostgreSQL
- **docker**: Container environment with networking
- **test**: Mock services with TestContainers

### Key Configuration Options
```yaml
spring.ai.anthropic:
  model: claude-3-5-sonnet-20241022
  max-tokens: 4096
  temperature: 0.7

jarvis:
  obsidian.vault-path: ${OBSIDIAN_VAULT_PATH:./obsidian-vault}
  chat.max-history-size: 20
  vector-search.max-results: 5
```

## 🧪 Testing Strategy (63 Tests - 100% Pass Rate)

### Test Architecture
- **Unit Tests**: Service layer with MockK framework  
- **Controller Tests**: MockMvc with @WebMvcTest annotations
- **Integration Tests**: TestContainers with real PostgreSQL
- **Agent Tests**: AI-powered behavior and context testing

### Key Test Coverage
- **AgentDispatcherTest**: AI-based agent selection logic
- **ObsidianAgentTest**: Vault operations and availability
- **JarvisApplicationIntegrationTest**: Full system behavior
- **HybridMemoryClassifierTest**: ML classification accuracy

### Running Tests
```bash
# All tests (should always pass)
./gradlew test

# With coverage report
./gradlew test jacocoTestReport

# Integration tests only
./gradlew test --tests "*IntegrationTest"
```

## 📊 Performance Characteristics

### Response Times
- **Simple queries**: 2-3 seconds (direct processing)
- **Knowledge queries (cached)**: 0.03 seconds (777x faster than uncached)
- **Knowledge queries (first time)**: 20-30 seconds (vector search + AI processing)
- **Context-aware queries**: 2-3 seconds (uses existing chat history)

### Optimization Features
- ✅ **Query embedding cache** - Hash-based lookup for repeated queries
- ✅ **PostgreSQL vector indexes** - IVFFLAT with cosine distance  
- ✅ **Context-aware routing** - Eliminates unnecessary knowledge searches
- ✅ **Chat history limits** - 20 messages max for optimal performance

## 🌐 Web Interface

The complete web UI is embedded as static files in the Spring Boot JAR:
- **Dark theme** - Professional Jarvis-inspired design
- **Real-time chat** - SSE-powered reasoning display
- **Live system logs** - Streaming log viewer
- **Knowledge management** - Sync controls and status
- **Responsive design** - Works on desktop and mobile

Access at: `http://localhost:8080` (development) or configured production URL

## 🐳 Production Deployment

### Docker Architecture  
- **Multi-stage build** with Java 21 runtime
- **PostgreSQL 16** with pgvector extension
- **Volume mounts** for Obsidian vault and ONNX model
- **Health checks** via Spring Actuator endpoints

### Container Services
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    volumes:
      - jarvis_postgres_data:/var/lib/postgresql/data
      
  jarvis:
    build: .
    depends_on: [postgres]
    volumes:
      - ${OBSIDIAN_VAULT_PATH:-./obsidian-vault}:/app/obsidian-vault:ro
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
```

### Health Monitoring
- **Application health**: `/actuator/health`
- **Database connectivity**: Automatic PostgreSQL health checks  
- **Agent availability**: Real-time SubAgent status monitoring
- **Memory usage**: JVM metrics via Actuator

## 🚨 Important Development Guidelines

### Claude Code Principles (MANDATORY)
1. **AI-First Decisions** - Never use hardcoded patterns or keywords
2. **Simple Orchestration** - Keep agent logic clean and focused  
3. **Clear Descriptions** - SubAgent descriptions drive automatic selection
4. **Context Awareness** - Always pass chat history for context
5. **Error Recovery** - Implement graceful fallbacks and error handling

### Code Quality Standards
1. **Testing Required** - All new features must include tests
2. **No Regression** - All 63 tests must continue passing
3. **Clean Architecture** - Maintain separation of concerns
4. **Performance** - Monitor response times and optimize caching
5. **Documentation** - Update this file when adding major features

### Adding New SubAgents
1. Implement `SubAgent` interface
2. Add clear `description` for AI selection
3. Include in `AgentDispatcher` constructor
4. Write comprehensive tests
5. Update documentation

## 🔄 Recent Major Updates (v0.6.0)

### Complete Obsidian Integration
- ✅ **Full CRUD operations** - Create, read, update, delete markdown files
- ✅ **AI-powered parsing** - No regex patterns, pure LLM understanding  
- ✅ **Physical file management** - Real vault operations with transaction safety
- ✅ **Context awareness** - Remembers conversation history between operations

### Advanced AI Capabilities  
- ✅ **Multi-line response parsing** - Handles complex AI outputs
- ✅ **Anti-hallucination system** - Real tool observations prevent false claims
- ✅ **Error recovery** - Automatic fallback mechanisms
- ✅ **Context-aware routing** - Eliminates unnecessary knowledge searches

### Real-time Features
- ✅ **SSE reasoning display** - Live AI thought streaming
- ✅ **System log streaming** - Real-time debug information
- ✅ **Connection management** - Automatic cleanup and error handling

## 🔮 Architecture Evolution Path

The system has evolved from basic agent architecture (v0.3.0) through ReAct reasoning (v0.5.0) to the current complete Claude Code implementation (v0.6.0).

**Future Enhancement Areas**:
- **Voice Integration** - Whisper API for speech-to-text
- **Mobile PWA** - Progressive Web App for mobile access
- **Advanced Authentication** - User management and permissions
- **Multi-modal Content** - Image and document processing
- **Distributed Caching** - Redis for horizontal scaling

## 💡 Troubleshooting

### Common Issues
- **ONNX Model Missing**: System automatically falls back to MockEmbeddingModel
- **pgvector Extension**: Use `pgvector/pgvector:pg16` image, not standard PostgreSQL
- **Test Failures**: All 63 tests should pass - check Docker daemon access for TestContainers
- **Memory Issues**: Configure JVM heap size: `-Xmx2g` or Docker memory limits

### Health Checks
- **Application**: `curl http://localhost:8080/actuator/health`
- **Database**: Check PostgreSQL logs for connection issues
- **Obsidian Vault**: Verify path exists and is readable
- **AI Services**: Check Anthropic API key configuration

---

This documentation reflects the current production-ready state of Jarvis v0.6.0 with complete Claude Code architecture implementation, comprehensive testing, and advanced AI capabilities.