# Jarvis AI Assistant - Architecture Documentation

## Overview

Jarvis is a production-ready personal AI assistant implementing **Claude Code SubAgent architecture** with clean separation of concerns and advanced AI capabilities. The system demonstrates modern AI application patterns with real-time reasoning, vector search, and comprehensive testing.

**Current Version**: 0.6.0 (Latest Release - 2025-08-23)  
**Architecture**: Clean Architecture + Domain-Driven Design  
**Technology Stack**: Spring Boot 3.5.4 + Kotlin 1.9.25 + PostgreSQL 16 + pgvector

## 🏗️ High-Level Architecture

### System Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web Browser   │◄──►│  Spring Boot App  │◄──►│   PostgreSQL    │
│                 │    │                  │    │   + pgvector    │
│ - Chat UI       │    │ - REST API       │    │                 │
│ - SSE Streams   │    │ - SubAgents      │    │ - Chat History  │
│ - Real-time     │    │ - AI Reasoning   │    │ - Vector Search │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   Anthropic API  │
                    │                  │
                    │ Claude 3.5       │
                    │ Sonnet           │
                    └──────────────────┘
```

### Claude Code SubAgent Pattern Implementation

The system follows **Claude Code principles** with AI-powered orchestration:

```
JarvisMainAgent (Orchestrator)
├── AgentDispatcher (AI Selection)
│   ├── Availability Check
│   ├── Agent Description Analysis
│   └── Confidence-based Selection
│
└── Specialized SubAgents
    └── ObsidianAgent (Vault Operations)
        ├── AI Query Parsing
        ├── Context Awareness  
        └── Tool Execution
```

## 🧩 Core Components

### 1. SubAgent Architecture

#### **SubAgent Interface (Contract)**
```kotlin
interface SubAgent {
    val name: String                    // Unique identifier
    val description: String             // For AI-based selection
    val tools: List<String>?           // Available capabilities
    
    suspend fun canHandle(query: String, chatHistory: List<ChatMessage>): Boolean
    suspend fun handle(query: String, chatHistory: List<ChatMessage>): String
    suspend fun isAvailable(): Boolean
}
```

#### **Key Principles**
- ✅ **Single Responsibility** - Each agent handles one domain
- ✅ **AI-First Selection** - No hardcoded routing patterns
- ✅ **Context Awareness** - Maintains conversation history
- ✅ **Graceful Degradation** - Fallback mechanisms for failures

### 2. JarvisMainAgent (Central Orchestrator)

**Location**: `src/main/kotlin/com/jarvis/agent/JarvisMainAgent.kt`

**Responsibilities**:
- **AI-based routing** - `knowledge_search` vs `dialogue` determination
- **Agent delegation** - Hands off to specialized SubAgents
- **Context management** - Maintains 10-message conversation window
- **Error recovery** - Graceful fallbacks and error handling

**Key Features**:
```kotlin
suspend fun processQuery(query: String, sessionId: String, chatHistory: List<ChatMessage>): String {
    // 1. Try to find suitable sub-agent
    val agentSelection = agentDispatcher.selectAgent(query, chatHistory)
    
    if (agentSelection != null) {
        // 2. Delegate to specialized agent
        return agentSelection.agent.handle(query, chatHistory)
    } else {
        // 3. Handle directly with knowledge search or dialogue
        val approach = determineApproach(query, chatHistory)
        return when (approach) {
            "knowledge_search" -> handleKnowledgeSearch(query, chatHistory)
            else -> handleDialogue(query, chatHistory)
        }
    }
}
```

### 3. AgentDispatcher (AI-Powered Selection)

**Location**: `src/main/kotlin/com/jarvis/agent/AgentDispatcher.kt`

**Core Logic**:
- **Automatic selection** using agent descriptions
- **Availability verification** before selection
- **Confidence-based fallbacks** when uncertain
- **No complex routing logic** - pure AI decision making

**Selection Process**:
1. Check agent availability (`isAvailable()`)
2. For single agent - verify `canHandle()`
3. For multiple agents - AI selection using descriptions
4. Return `AgentSelection` with confidence score

### 4. ObsidianAgent (Specialized SubAgent)

**Location**: `src/main/kotlin/com/jarvis/agent/ObsidianAgent.kt`

**Capabilities**:
- **Full CRUD operations** - Create, read, update, delete markdown files
- **AI query parsing** - No regex patterns, pure LLM understanding
- **Physical file management** - Real vault operations with transaction safety
- **Context awareness** - Remembers conversation between operations

**Tool Set**:
```kotlin
override val tools = listOf(
    "obsidian_create",     // Create new markdown notes
    "obsidian_read",       // Read existing notes
    "obsidian_update",     // Update note content
    "obsidian_delete",     // Delete notes
    "obsidian_search",     // Search vault content
    "obsidian_list"        // List notes and folders
)
```

### 5. Real-time AI Reasoning (ThinkingController)

**Location**: `src/main/kotlin/com/jarvis/controller/ThinkingController.kt`

**Features**:
- **Server-Sent Events (SSE)** for live reasoning display
- **Session-based streams** with 5-minute timeout
- **Thought categorization** - start, thinking, complete, error
- **Automatic cleanup** - Memory-efficient connection management

**Frontend Integration**:
```javascript
// Real-time thought streaming
const eventSource = new EventSource(`/api/thinking/stream/${sessionId}`);
eventSource.onmessage = function(event) {
    const thought = JSON.parse(event.data);
    displayThought(thought.type, thought.message);
};
```

### 6. Knowledge Management with Vector Search

#### **KnowledgeService Architecture**
**Location**: `src/main/kotlin/com/jarvis/service/KnowledgeService.kt`

**Capabilities**:
- **PostgreSQL + pgvector** - Semantic similarity search
- **ONNX embeddings** - all-MiniLM-L6-v2 (384 dimensions)
- **Query caching** - 777x performance improvement for repeated queries
- **Automatic cleanup** - Handles deleted files during sync

#### **Database Schema**
```sql
-- Chat Management
CREATE TABLE chat_sessions (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) REFERENCES chat_sessions(id),
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Knowledge Base with Vector Search
CREATE TABLE knowledge_files (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(500) UNIQUE NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(100) NOT NULL,
    source_id VARCHAR(255),
    file_hash VARCHAR(64),
    embedding VECTOR(384),  -- pgvector extension
    tags TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Vector similarity search index
CREATE INDEX idx_knowledge_files_embedding ON knowledge_files 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### 7. ObsidianVaultManager (File Operations)

**Location**: `src/main/kotlin/com/jarvis/service/knowledge/ObsidianVaultManager.kt`

**Features**:
- **Physical file manipulation** - Direct markdown file operations
- **YAML frontmatter** - Metadata extraction and preservation
- **WikiLink processing** - Internal link `[[link]]` handling
- **Tag extraction** - Automatic `#tag` detection
- **Concurrent safety** - Thread-safe operations with caching

## 🌐 API Architecture

### REST Endpoints

#### **Chat API**
```http
POST /api/chat
{
    "query": "Create a note about today's meeting",
    "sessionId": "user-session-123"
}

Response:
{
    "response": "✅ Note created: **Meeting Notes**",
    "sessionId": "user-session-123",
    "timestamp": [2025, 8, 23, 16, 30, 45, 123456789],
    "metadata": {
        "history_size": 3
    }
}
```

#### **Knowledge Management API**
```http
POST /api/knowledge/sync
{
    "vaultPath": "/path/to/obsidian-vault"
}

GET /api/knowledge/status
Response:
{
    "sources": {
        "obsidian": {
            "sourceId": "obsidian",
            "isActive": true,
            "itemCount": 150,
            "lastSync": "2025-08-23T16:30:45"
        }
    },
    "totalSources": 1
}
```

#### **Real-time Streaming**
```http
GET /api/thinking/stream/{sessionId}
Content-Type: text/event-stream

data: {"type":"start","message":"🎯 Analyzing query","timestamp":1692808245123}
data: {"type":"thinking","message":"🤖 Delegating to ObsidianAgent","timestamp":1692808245456}
data: {"type":"complete","message":"✅ Task completed","timestamp":1692808245789}
```

### WebSocket Alternative (SSE Benefits)
- **HTTP/2 compatible** - No additional protocols needed
- **Automatic reconnection** - Built-in browser support
- **Unidirectional** - Perfect for reasoning display
- **Firewall friendly** - Standard HTTP connections

## 🔄 Data Flow Architecture

### 1. Chat Processing Flow

```
User Input (Web UI)
        ↓
ChatController.processChat()
        ↓
JarvisService.chat()
        ↓
JarvisMainAgent.processQuery()
        ↓
┌─────────────────┐         ┌──────────────────┐
│ AgentDispatcher │ ──AI──► │ SubAgent         │
│ .selectAgent()  │         │ .handle()        │
└─────────────────┘         └──────────────────┘
        ↓                           ↓
┌─────────────────┐         ┌──────────────────┐
│ Knowledge       │         │ ObsidianAgent    │
│ Search          │         │ File Operations  │
└─────────────────┘         └──────────────────┘
        ↓                           ↓
Database Storage ◄──────────────────┘
```

### 2. Real-time Reasoning Flow

```
Agent Processing
        ↓
ThinkingController.sendThought()
        ↓
SSE Stream to Frontend
        ↓
JavaScript Event Handler
        ↓
UI Update (Thought Bubble)
```

### 3. Knowledge Sync Flow

```
Obsidian Vault Files
        ↓
ObsidianKnowledgeSource.syncData()
        ↓
MarkdownParser.parseFile()
        ↓
EmbeddingModel.embed()
        ↓
KnowledgeService.indexItems()
        ↓
PostgreSQL with pgvector
```

## 🧪 Testing Architecture

### Test Structure (63 Tests - 100% Pass Rate)

```
src/test/kotlin/
├── agent/                          # SubAgent Behavior Testing
│   ├── AgentDispatcherTest.kt     # AI selection logic
│   └── ObsidianAgentTest.kt       # Vault operations
├── controller/                     # REST API Testing
│   └── ChatControllerTest.kt      # Endpoint behavior
├── service/                        # Business Logic Testing  
│   ├── JarvisServiceTest.kt       # Chat orchestration
│   └── knowledge/                  # Vault management
├── integration/                    # Full System Testing
│   └── JarvisApplicationIntegrationTest.kt
└── config/                         # Test Configuration
    └── TestConfiguration.kt       # Mocked dependencies
```

### Testing Strategy

#### **Unit Tests (MockK Framework)**
- **Service layer isolation** - Mocked dependencies
- **Agent behavior verification** - AI decision simulation
- **Error handling coverage** - Exception scenarios

#### **Integration Tests (TestContainers)**
- **Real PostgreSQL** - Full database operations
- **Vector search testing** - Actual pgvector queries
- **End-to-end scenarios** - Complete user journeys

#### **Performance Testing**
- **Response time verification** - SLA compliance
- **Concurrent request handling** - Load simulation
- **Memory usage monitoring** - Resource optimization

## 📊 Performance Characteristics

### Response Time Analysis

| Query Type | First Request | Cached Request | Optimization |
|------------|---------------|----------------|--------------|
| Simple Chat | 2-3 seconds | 2-3 seconds | Context reuse |
| Knowledge Search | 20-30 seconds | 0.03 seconds | **777x faster** |
| Agent Operations | 3-5 seconds | 3-5 seconds | File caching |
| Vector Similarity | 5-10 seconds | 0.1 seconds | IVFFLAT index |

### Optimization Features

#### **Query Embedding Cache**
```kotlin
private val embeddingCache = ConcurrentHashMap<String, FloatArray>()

fun getEmbedding(text: String): FloatArray {
    val hash = text.hashCode().toString()
    return embeddingCache.computeIfAbsent(hash) { 
        embeddingModel.embed(text) 
    }
}
```

#### **PostgreSQL Vector Indexing**
```sql
-- IVFFLAT index for cosine similarity
CREATE INDEX idx_knowledge_files_embedding ON knowledge_files 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Optimized similarity query
SELECT file_path, content, 
       1 - (embedding <=> $1) AS similarity
FROM knowledge_files 
WHERE 1 - (embedding <=> $1) > 0.3
ORDER BY embedding <=> $1
LIMIT 5;
```

### Memory Management

#### **JVM Configuration**
```bash
# Development
JAVA_OPTS="-Xmx1g -Xms512m"

# Production  
JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
```

#### **Connection Pooling**
```yaml
spring.datasource.hikari:
  maximum-pool-size: 10
  minimum-idle: 5
  connection-timeout: 30000
  idle-timeout: 600000
```

## 🔐 Security Architecture

### Authentication & Authorization
- **Stateless sessions** - No server-side session storage
- **API key management** - Environment-based configuration
- **CORS configuration** - Controlled cross-origin access

### Data Protection
- **SQL injection prevention** - JPA parameterized queries
- **XSS protection** - Content sanitization
- **Input validation** - Request DTO validation
- **Secure headers** - Spring Security configuration

## 🚀 Deployment Architecture

### Docker Multi-stage Build

```dockerfile
# Build stage
FROM gradle:8-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle build -x test

# Runtime stage  
FROM openjdk:21-jre-slim
COPY --from=build /app/build/libs/*.jar app.jar
COPY --from=build /app/all-MiniLM-L6-v2.onnx /app/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Production Services

```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: jarvis
      POSTGRES_USER: jarvis
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - jarvis_postgres_data:/var/lib/postgresql/data
    
  jarvis:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - postgres
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - OBSIDIAN_VAULT_PATH=/app/obsidian-vault
    volumes:
      - ${OBSIDIAN_VAULT_PATH:-./obsidian-vault}:/app/obsidian-vault:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  jarvis_postgres_data:
```

### Health Monitoring

#### **Spring Actuator Endpoints**
```yaml
management:
  endpoints.web.exposure.include: health,metrics,info
  endpoint.health:
    show-details: always
    probes.enabled: true
```

#### **Custom Health Indicators**
```kotlin
@Component
class ObsidianAgentHealthIndicator : HealthIndicator {
    override fun health(): Health {
        return if (obsidianAgent.isAvailable()) {
            Health.up()
                .withDetail("vault_path", vaultPath)
                .withDetail("notes_count", getNotesCount())
                .build()
        } else {
            Health.down()
                .withDetail("error", "Vault not accessible")
                .build()
        }
    }
}
```

## 🔮 Evolutionary Architecture

### Current State (v0.6.0)
- ✅ **Claude Code SubAgent Pattern** - Production implementation
- ✅ **Real-time AI Reasoning** - SSE-powered thought streaming
- ✅ **Complete Obsidian Integration** - Full CRUD operations
- ✅ **Vector Knowledge Search** - 777x performance optimization
- ✅ **Comprehensive Testing** - 63 tests with 100% pass rate

### Planned Enhancements

#### **v0.7.0 - Voice Integration**
```kotlin
interface VoiceAgent : SubAgent {
    suspend fun transcribe(audioData: ByteArray): String
    suspend fun synthesize(text: String): ByteArray
}
```

#### **v0.8.0 - Multi-modal Content**
```kotlin
interface MultiModalAgent : SubAgent {
    suspend fun analyzeImage(imageData: ByteArray): String
    suspend fun processPDF(pdfData: ByteArray): String
}
```

#### **v0.9.0 - Distributed Architecture**
```kotlin
interface ClusterAgent : SubAgent {
    suspend fun distributeTask(task: Task): TaskResult
    suspend fun aggregateResults(results: List<TaskResult>): FinalResult
}
```

### Scalability Considerations

#### **Horizontal Scaling**
- **Stateless design** - No session affinity required
- **Database connection pooling** - Shared PostgreSQL instance
- **Redis caching** - Distributed embedding cache
- **Load balancer ready** - Health check endpoints

#### **Vertical Scaling**
- **Memory optimization** - Configurable JVM heap
- **CPU utilization** - Parallel processing with coroutines  
- **I/O optimization** - Async file operations
- **Database optimization** - Connection pooling and indexing

## 🎯 Architecture Principles

### 1. **Claude Code Alignment**
- ✅ **AI-First Decisions** - No hardcoded routing logic
- ✅ **Simple Orchestration** - Clean delegation patterns
- ✅ **Clear Agent Descriptions** - Self-describing capabilities
- ✅ **Context Awareness** - Conversation memory integration

### 2. **Clean Architecture**
- ✅ **Dependency Inversion** - Interfaces define contracts
- ✅ **Single Responsibility** - Focused component purposes
- ✅ **Open/Closed Principle** - Extensible without modification
- ✅ **Interface Segregation** - Minimal, focused interfaces

### 3. **Domain-Driven Design**
- ✅ **Bounded Contexts** - Clear domain boundaries
- ✅ **Ubiquitous Language** - Consistent terminology
- ✅ **Aggregate Roots** - Entity lifecycle management
- ✅ **Domain Services** - Business logic encapsulation

This architecture represents a mature, production-ready AI assistant system that successfully implements Claude Code principles while maintaining clean architecture patterns and comprehensive testing coverage.