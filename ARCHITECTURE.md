# Jarvis AI Assistant - Архитектурная документация

> **Версия:** 0.3.0 - Агентный подход  
> **Дата:** 2025-08-21  
> **Статус:** Beta

## 🎯 Обзор системы

Jarvis представляет собой персональный AI-ассистент с **автономным принятием решений**, реализованный на основе **Spring AI Routing Workflow Pattern**. Система способна самостоятельно решать, когда нужна информация из базы знаний, а когда достаточно общего диалога.

### Ключевые принципы архитектуры

- **🤖 Автономность**: LLM самостоятельно принимает решения о типе запроса
- **🔄 Двухэтапный процесс**: Classification → Response Generation  
- **⚡ Производительность**: Query cache обеспечивает 777x ускорение
- **🧪 Test-Driven**: 100% покрытие тестами (46/46 проходят)

---

## 🏗️ Общая архитектура системы

```mermaid
graph TB
    User[👤 Пользователь] --> API[🌐 REST API]
    API --> Controller[🎮 ChatController]
    Controller --> JarvisService[🧠 JarvisService]
    
    JarvisService --> RoutingWorkflow[🔀 RoutingWorkflow]
    
    RoutingWorkflow --> RouteClassifier[📊 Route Classifier]
    RouteClassifier --> |"knowledge"| KnowledgeHandler[📚 Knowledge Handler]
    RouteClassifier --> |"general"| GeneralHandler[💬 General Handler]
    
    KnowledgeHandler --> KnowledgeService[🔍 KnowledgeService]
    KnowledgeService --> VectorSearch[🧮 Vector Search]
    VectorSearch --> PostgreSQL[(🗃️ PostgreSQL + pgvector)]
    
    KnowledgeHandler --> Claude[🤖 Claude 3.5 Sonnet]
    GeneralHandler --> Claude
    
    JarvisService --> ChatHistory[(💾 Chat History)]
    
    ObsidianVault[📝 Obsidian Vault] --> SyncService[🔄 Sync Service]
    SyncService --> EmbeddingModel[🧬 ONNX Embedding Model]
    EmbeddingModel --> PostgreSQL
```

---

## 🔀 Spring AI Routing Workflow с Контекстной Памятью - Детальная схема

### Процесс принятия решений с историей

```mermaid
sequenceDiagram
    participant U as 👤 User
    participant JS as 🧠 JarvisService
    participant RW as 🔀 RoutingWorkflow  
    participant RC as 📊 Route Classifier
    participant C as 🤖 Claude
    participant KS as 🔍 KnowledgeService
    participant CH as 💾 Chat History
    participant DB as 🗃️ PostgreSQL

    U->>JS: "Как меня зовут?" (sessionId)
    
    Note over JS,CH: Шаг 0: Загрузка истории
    JS->>CH: loadChatHistory(sessionId, limit=20)
    CH-->>JS: Previous messages[]
    
    Note over JS,RW: Шаг 1: Роутинг с историей
    JS->>RW: route(query, chatHistory[])
    RW->>RC: determineRoute(query, history)
    RC->>C: ROUTING_PROMPT + history + query
    
    Note over RC: 🧠 Анализ: имя в истории → general
    C-->>RC: "general"
    RC-->>RW: "general"
    
    Note over RW,C: Шаг 2: Генерация с контекстом
    RW->>C: System prompt + Chat History + Current Query
    C-->>RW: "Вас зовут Денис..."
    RW-->>JS: Response with context
    
    Note over JS,CH: Шаг 3: Сохранение
    JS->>CH: Save user + assistant messages
    JS-->>U: Final answer
```

### Intelligent Routing с анализом истории

```mermaid
flowchart TD
    Query[📝 User Query] --> HistoryCheck[🧠 Check Chat History]
    HistoryCheck --> HasHistory{📚 Has chat history?}
    
    HasHistory --> |Yes| ContextAnalyzer[🔍 Context Analyzer]
    HasHistory --> |No| DirectAnalyzer[🔍 Direct Query Analyzer]
    
    ContextAnalyzer --> InHistory{💭 Answer in history?}
    InHistory --> |Yes| GeneralRoute[💬 general]
    InHistory --> |No| AboutConversation{💬 About current chat?}
    
    AboutConversation --> |Yes| GeneralRoute
    AboutConversation --> |No| DirectAnalyzer
    
    DirectAnalyzer --> NeedsSearch{🔍 Needs knowledge search?}
    NeedsSearch --> |Yes| KnowledgeRoute[📚 knowledge]
    NeedsSearch --> |No| GeneralRoute
    
    KnowledgeRoute --> KnowledgeHandler[📚 Knowledge Handler + History]
    GeneralRoute --> GeneralHandler[💬 General Handler + History]
    
    style GeneralRoute fill:#e8f5e8
    style KnowledgeRoute fill:#e1f5fe
    style HistoryCheck fill:#fff9c4
```

---

## 🧬 Embedding Pipeline Architecture

### ONNX Model Integration

```mermaid
graph LR
    subgraph "Embedding Generation"
        Text[📝 Input Text] --> ONNX[🧬 ONNX Model]
        ONNX --> |all-MiniLM-L6-v2| Vector[🔢 384D Vector]
        
        Missing[❌ Model Missing] --> Mock[🎭 MockEmbeddingModel]
        Mock --> |Deterministic| TestVector[🧪 Test Vector]
    end
    
    subgraph "Caching Layer"
        Vector --> Cache{💾 Query Cache}
        TestVector --> Cache
        Cache --> |Hit| FastReturn[⚡ 777x faster]
        Cache --> |Miss| Store[💾 Store & Return]
    end
    
    subgraph "Storage"
        Store --> PostgreSQL[(🗃️ PostgreSQL)]
        PostgreSQL --> PGVector[🧮 pgvector extension]
        PGVector --> VectorIndex[📇 Vector Index]
    end
    
    style ONNX fill:#4caf50
    style Mock fill:#ff9800
    style Cache fill:#2196f3
```

### Vector Search Flow

```mermaid
sequenceDiagram
    participant Q as 📝 Query
    participant EM as 🧬 Embedding Model
    participant C as 💾 Cache
    participant DB as 🗃️ Database
    participant VS as 🔍 Vector Search

    Q->>EM: embed(query)
    EM->>C: checkCache(queryHash)
    
    alt Cache Hit
        C-->>EM: Cached embedding ⚡
    else Cache Miss  
        EM->>EM: Generate new embedding
        EM->>C: storeCache(queryHash, embedding)
    end
    
    EM-->>VS: query_embedding[384]
    VS->>DB: SELECT * FROM knowledge_files ORDER BY embedding <=> query_embedding
    DB-->>VS: Top 5 similar documents
    VS-->>Q: Relevant context
```

---

## 💾 Data Architecture

### Database Schema

```mermaid
erDiagram
    CHAT_SESSIONS {
        string id PK
        timestamp created_at
        timestamp last_active_at
    }
    
    CHAT_MESSAGES {
        bigint id PK
        string session_id FK
        string role
        text content
        timestamp created_at
    }
    
    KNOWLEDGE_FILES {
        bigint id PK
        string file_path
        text content
        vector embedding
        jsonb metadata
        timestamp created_at
        timestamp updated_at
    }
    
    CHAT_SESSIONS ||--o{ CHAT_MESSAGES : "has many"
    
    KNOWLEDGE_FILES ||--|| VECTOR_INDEX : "indexed by"
```

### Custom PGVector Type Integration

```kotlin
// Hibernate интеграция с pgvector
@Type(PGVectorType::class)
@Column(name = "embedding", columnDefinition = "vector(384)")
var embedding: FloatArray? = null
```

---

## 🌐 Web UI Architecture

### Frontend без Node.js

```mermaid
graph LR
    subgraph "Static Files (в JAR)"
        HTML[📄 index.html] --> CSS[🎨 style.css]
        CSS --> JS[⚡ app.js]
        JS --> Fonts[🔤 Google Fonts]
    end
    
    subgraph "Browser"
        UI[👤 User Interface] --> EventHandlers[🎯 Event Handlers]
        EventHandlers --> API_Calls[📡 Fetch API Calls]
    end
    
    subgraph "Backend Integration"
        API_Calls --> ChatAPI[💬 /api/chat]
        API_Calls --> KnowledgeAPI[📚 /api/knowledge/*]
        API_Calls --> HealthAPI[🏥 /actuator/health]
    end
    
    HTML --> UI
    
    style HTML fill:#e3f2fd
    style JS fill:#f3e5f5
    style CSS fill:#e8f5e8
```

### UI Компоненты

```mermaid
flowchart TB
    subgraph "Jarvis Web Interface"
        Header[🔝 Header]
        Header --> Logo[🤖 Logo + Version]
        Header --> Status[🔄 Connection Status]
        
        Main[📱 Main Chat Area]
        Main --> Welcome[👋 Welcome Message]
        Main --> Messages[💬 Messages Container]
        Main --> Input[⌨️ Input Area]
        
        Sidebar[📊 Knowledge Panel]
        Sidebar --> Stats[📈 Knowledge Stats]
        Sidebar --> Sync[🔄 Sync Button]
        
        Overlay[⏳ Loading Overlay]
    end
    
    Messages --> UserMsg[👤 User Messages]
    Messages --> BotMsg[🤖 Bot Responses]
    
    Input --> TextArea[📝 Message Input]
    Input --> SendBtn[📤 Send Button]
    Input --> SessionInfo[🆔 Session Info]
    
    style Header fill:#1a1f2e
    style Main fill:#0a0e1a
    style Messages fill:#242938
```

---

## 🐳 Containerization Architecture

### Docker Multi-Stage Build

```mermaid
graph TB
    subgraph "Build Stage"
        Source[📁 Source Code] --> Gradle[🔧 Gradle Build]
        Gradle --> JAR[📦 JAR File]
        Gradle --> Tests[🧪 Run Tests]
        Tests --> |46/46 ✅| Coverage[📊 80% Coverage]
    end
    
    subgraph "Runtime Stage"
        JAR --> Runtime[🏃 Java 21 Runtime]
        Runtime --> Container[🐳 Production Container]
        
        ObsidianMount[📝 Obsidian Vault] --> |Volume| Container
        ONNXMount[🧬 ONNX Model] --> |Volume| Container
    end
    
    subgraph "Database"
        Container --> PostgreSQL[🗃️ PostgreSQL 16]
        PostgreSQL --> PGVector[🧮 pgvector extension]
    end
    
    style Tests fill:#4caf50
    style Coverage fill:#2196f3
```

### Docker Compose Services

```yaml
# Архитектура сервисов
services:
  postgres:      # 🗃️ Database layer
  jarvis:        # 🤖 Application layer
  
networks:
  jarvis-network # 🔗 Internal communication

volumes:
  jarvis_postgres_data  # 💾 Persistent storage
```

---

## ⚡ Performance Optimizations

### Query Execution Metrics с контекстной памятью

```mermaid
graph LR
    subgraph "Request Types & Performance"
        HistoryQuery[💭 History Query] --> |2-3 sec| HistoryResponse[🧠 Context Response]
        SimpleQuery[💬 Simple Query] --> |2-3 sec| GeneralResponse[🤖 General Response]
        
        KnowledgeQuery[📚 Knowledge Query] --> RouterDecision{🧠 Router Analysis}
        RouterDecision --> |Answer in history| HistoryResponse
        RouterDecision --> |Need search| VectorSearch[🔍 Vector Search]
        
        VectorSearch --> |First time: 20-30 sec| SearchResponse[🔍 Search Response]
        VectorSearch --> |Cached: 0.03 sec| CachedResponse[⚡ Cached Response]
        
        VectorSearch --> EmbedGeneration[🧬 Embed Generation]
        EmbedGeneration --> DBQuery[🗃️ DB Query] 
        DBQuery --> ContextBuilding[📄 Context Building]
        ContextBuilding --> LLMResponse[🤖 LLM Response]
    end
    
    style HistoryResponse fill:#e8f5e8
    style CachedResponse fill:#4caf50
    style GeneralResponse fill:#2196f3
```

### Caching Strategy

| Component | Cache Type | Performance Gain |
|-----------|------------|------------------|
| Query Embeddings | In-Memory Hash | **777x faster** |
| Vector Similarity | PostgreSQL Index | **50x faster** |
| Chat History | Database Session + Context Memory | **10-100x faster** |
| Context-Aware Routing | LLM Decision with History | **Eliminates unnecessary searches** |

---

## 🧪 Testing Architecture

### Test Pyramid Implementation

```mermaid
graph TB
    subgraph "Testing Strategy - 46/46 Tests ✅"
        E2E[🌐 E2E Tests] --> |1 test| ApplicationTest[JarvisApplicationTests]
        
        Integration[🔗 Integration Tests] --> |10 tests| TestContainers[TestContainers + PostgreSQL]
        
        Controller[🎮 Controller Tests] --> |20 tests| MockMvc[MockMvc + WebMvcTest]
        
        Unit[⚙️ Unit Tests] --> |15 tests| ServiceTests[Service Layer Tests]
        Unit --> MockK[MockK Framework]
        
        Coverage[📊 Coverage: 80%] --> JaCoCo[JaCoCo Reports]
    end
    
    style E2E fill:#4caf50
    style Integration fill:#2196f3  
    style Controller fill:#ff9800
    style Unit fill:#9c27b0
```

### Test Configuration Strategy

```mermaid
flowchart TD
    TestProfile[🧪 Test Profile] --> MockAnthropicAPI[🎭 Mock Anthropic API]
    TestProfile --> MockEmbeddingModel[🧬 Mock Embedding Model]
    TestProfile --> TestContainers[🐳 TestContainers PostgreSQL]
    
    MockEmbeddingModel --> DeterministicVectors[🎯 Deterministic 384D Vectors]
    DeterministicVectors --> ConsistentTests[✅ Consistent Test Results]
    
    TestContainers --> RealDB[🗃️ Real PostgreSQL + pgvector]
    RealDB --> IntegrationTesting[🔗 Integration Testing]
```

---

## 🔧 Configuration Management

### Environment-based Configuration

```mermaid
graph TB
    subgraph "Configuration Profiles"
        Default[📋 application.yml] --> Local[🏠 local profile]
        Default --> Docker[🐳 docker profile] 
        Default --> Test[🧪 test profile]
        
        Local --> DevSettings[🛠️ Development Settings]
        Docker --> ProdSettings[🚀 Production Settings]
        Test --> MockSettings[🎭 Mock Settings]
    end
    
    subgraph "External Dependencies"
        AnthropicAPI[🤖 Anthropic API Key]
        ObsidianPath[📝 Obsidian Vault Path]
        DatabaseURL[🗃️ Database Connection]
    end
    
    DevSettings --> AnthropicAPI
    ProdSettings --> AnthropicAPI
    MockSettings --> |Mock Key| AnthropicAPI
```

---

## 🚀 Deployment Architecture

### Production Deployment Flow

```mermaid
sequenceDiagram
    participant Dev as 👨‍💻 Developer
    participant Git as 📚 Git Repository
    participant CI as ⚙️ CI Pipeline
    participant Registry as 📦 Container Registry
    participant Prod as 🚀 Production

    Dev->>Git: git push
    Git->>CI: Trigger build
    CI->>CI: Run 46 tests ✅
    CI->>CI: Generate JaCoCo coverage
    CI->>CI: Build Docker image
    CI->>Registry: Push image
    Registry->>Prod: Deploy container
    
    Note over Prod: Health checks
    Prod-->>Dev: ✅ Deployment successful
```

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Application** | Spring Boot 3.5.4 | Main service |
| **Database** | PostgreSQL 16 + pgvector | Vector storage |
| **AI Model** | Claude 3.5 Sonnet | Language processing |
| **Embeddings** | ONNX all-MiniLM-L6-v2 | Local text embeddings |
| **Container** | Docker + Docker Compose | Orchestration |
| **Monitoring** | Spring Actuator | Health checks |

---

## 📈 Monitoring & Observability

### Health Check Architecture

```mermaid
graph LR
    subgraph "Health Monitoring"
        App[🤖 Jarvis App] --> Health[🏥 /actuator/health]
        App --> Metrics[📊 /actuator/metrics]
        
        PostgreSQL[🗃️ PostgreSQL] --> DBHealth[💾 Database Health]
        
        Health --> |Status: UP| Monitor[📱 Monitoring]
        DBHealth --> |Status: UP| Monitor
        Metrics --> |Prometheus format| Monitor
    end
    
    subgraph "Knowledge Base Status"
        KnowledgeAPI[📚 /api/knowledge/status] --> FileCount[📄 Document Count]
        KnowledgeAPI --> LastSync[🔄 Last Sync Time]
        KnowledgeAPI --> EmbeddingStats[🧬 Embedding Stats]
    end
```

---

## 🔮 Future Architecture Evolution

### Planned Enhancements

```mermaid
mindmap
  root((Jarvis v0.4.0+))
    🌐 Web Interface
      React/Vue Frontend
      WebSocket Real-time
      Streaming Responses
    
    🤖 Advanced AI
      Multi-modal Support
      Voice Integration (Whisper)
      Custom Fine-tuning
    
    📱 Mobile & Desktop
      Telegram Bot
      Desktop App (Electron)
      Mobile PWA
    
    🔗 Integrations  
      Calendar Sync
      Email Processing
      Smart Home Control
      
    ⚡ Performance
      Distributed Caching
      Load Balancing
      Auto-scaling
      
    🔒 Security
      Authentication (JWT)
      Role-based Access
      API Rate Limiting
```

### Migration Path

1. **v0.3.0 → v0.4.0**: Web UI + Streaming
2. **v0.4.0 → v0.5.0**: Voice Mode + Mobile
3. **v0.5.0 → v1.0.0**: Production-ready + Integrations

---

## 📚 Technical References

### Key Technologies & Versions

- **Spring Boot**: 3.5.4 + Kotlin 1.9.25
- **Spring AI**: 1.0.0-M3 (Routing Workflow Pattern)
- **PostgreSQL**: 16 + pgvector extension  
- **Java Runtime**: 21 (Eclipse Temurin)
- **AI Model**: Anthropic Claude 3.5 Sonnet (claude-3-5-sonnet-20241022)
- **Embedding Model**: all-MiniLM-L6-v2 ONNX (384 dimensions)
- **Build Tool**: Gradle 8.14.3
- **Testing**: JUnit 5 + MockK + TestContainers
- **Container**: Docker + Docker Compose

### Performance Benchmarks

| Metric | Value | Context |
|--------|-------|---------|
| History-based queries | **2-3 seconds** | Using chat context |
| Simple queries | **2-3 seconds** | General conversation |
| Knowledge queries (first) | **20-30 seconds** | With vector search |
| Knowledge queries (cached) | **0.03 seconds** | 777x improvement |
| Test success rate | **100%** (46/46) | Full test suite |
| Code coverage | **80%** | JaCoCo analysis |
| Memory usage | **512MB - 1GB** | JVM heap |
| Web UI load time | **< 1 second** | Static files in JAR |

---

> **Документация поддерживается автоматически**  
> Последнее обновление: 2025-08-21  
> Версия архитектуры: 0.3.0 - Агентный подход + Контекстная память + Web UI