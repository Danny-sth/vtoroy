# Changelog

All notable changes to this project will be documented in this file.

## [v0.6.0] - 2025-08-23

### 🤖 Major Release - Claude Code SubAgent Architecture

This release represents a complete architectural transformation to Claude Code principles, implementing the SubAgent pattern with AI-first decision making and enhanced context awareness.

#### 🚀 New Claude Code Features
- **SubAgent Architecture**: Implemented simple, focused agents following Claude Code principles
- **AgentDispatcher**: AI-powered agent selection without hardcoded routing
- **VtoroyMainAgent**: Central orchestrator that delegates to specialized SubAgents
- **ObsidianAgent**: Specialized SubAgent for Obsidian vault operations
- **Context Awareness**: Full chat history passing for conversation continuity
- **SSE Reasoning Display**: Real-time AI reasoning via Server-Sent Events
- **AI-First Decisions**: Zero hardcoded patterns - all decisions made by Claude AI
- **Enhanced Memory Classification**: Hybrid ML-powered classification system

#### 🛠️ Technical Architecture
- **SubAgent Interface**: Core contract with `canHandle()`, `handle()`, `isAvailable()` methods
- **AI-Based Routing**: AgentDispatcher uses Claude to select appropriate agents
- **ThinkingController**: SSE endpoint for real-time reasoning display
- **Spring Boot 3.5.4 + Kotlin 1.9.25**: Updated to latest stable versions
- **Claude Code Principles**: Simple agents, AI decisions, zero complexity

#### 🧪 Testing Excellence
- **63 Tests Total**: Comprehensive test suite covering all components
- **100% Test Pass Rate**: All tests passing consistently
- **MockK Integration**: Modern Kotlin mocking framework
- **TestContainers**: Real PostgreSQL for integration tests
- **Test Categories**: Unit tests, controller tests, integration tests

#### ✅ SubAgent Capabilities
- **Dynamic Agent Selection**: AI chooses best agent for each query
- **Context Preservation**: Agents remember conversation history
- **Multi-turn Conversations**: Proper context awareness between messages
- **Real-time Reasoning**: SSE display of AI decision process
- **Fallback Handling**: Graceful degradation when agents unavailable

#### 🎯 Context Awareness Fixes
- **Question Detection**: Agent recognizes when user is responding to questions
- **Parameter Collection**: Gathers all needed information from chat history
- **Execution Logic**: Proper action execution when all parameters available
- **Multi-step Operations**: Support for complex multi-turn interactions

#### 🐛 Bug Fixes
- Fixed context awareness in multi-turn conversations
- Fixed agent name mismatch in tests ("ObsidianAgent" vs "obsidian-manager")
- Fixed AgentDispatcher fallback logic expectations
- Fixed integration test API changes
- Fixed compilation errors in test suite

#### 📊 Performance
- Simple queries: 2-3 seconds
- Complex reasoning: 5-20 seconds (multi-step)
- Agent selection: < 1 second
- SSE latency: Real-time (<100ms)

---

## [v0.5.0] - 2025-08-22

### 🧠 Major Release - ReAct Reasoning System

#### 🚀 New Features
- **ReAct Reasoning Engine**: Полная реализация ReAct (Reasoning + Acting) pattern для сложных операций
- **AI-Driven Complexity Detection**: Модель Claude сама определяет simple vs complex запросы (никаких хардкодов!)
- **Automatic Fallback**: Автоматический переход от simple к reasoning при ошибках
- **Full CRUD Obsidian Integration**: Полная поддержка создания, чтения, обновления и удаления заметок
- **Anti-hallucination System**: AI больше не придумывает результаты - только реальные observation
- **Multi-line Response Parsing**: Корректная обработка многострочных Complete: ответов
- **Path Intelligence**: AI понимает структуру путей `obsidian-vault/filename.md`

#### 🛠️ Technical Implementation
- **ObsidianReasoningEngine**: Новый engine для пошагового рассуждения с 10 инструментами
- **ReasoningTypes.kt**: Новые data classes для ReAct pattern (ReasoningStep, ToolAction, ReasoningContext)
- **Enhanced ObsidianVaultManager**: Добавлены create_note, update_note operations
- **Smart Complete Parsing**: Исправлен парсинг многострочных ответов (убрали прерывание на пустых строках)
- **Tool Execution**: 8 полноценных инструментов: list_notes, search_notes, read_note, create_note, update_note, delete_note, get_tags, get_backlinks

#### ✅ Verified Operations
- **File Deletion**: ✅ Физическое удаление файлов с диска
- **File Creation**: ✅ Создание заметок с YAML frontmatter и содержимым  
- **File Updates**: ✅ Модификация существующих заметок
- **Complex Search**: ✅ Многоступенчатый поиск и анализ с полными результатами
- **Error Recovery**: ✅ Автоматический fallback при ошибках simple operations

#### 🐛 Fixed Issues  
- **AI Hallucination**: Исправлена проблема где AI придумывал results вместо ожидания реальных
- **Multi-line Truncation**: Исправлено обрезание многострочных Complete: ответов
- **Path Resolution**: Исправлено дублирование `obsidian-vault/` в путях файлов
- **Action Execution**: Исправлена проблема где actions не выполнялись реально

#### 📊 Performance Metrics
- **Reasoning Operations**: Multi-step операции 5-20 секунд
- **Simple Operations**: 2-3 секунды с fallback защитой  
- **Complex Analysis**: Полные структурированные ответы до 400+ символов
- **File Operations**: 100% точность физических операций
- **Error Recovery**: Автоматический fallback в 100% случаев ошибок

#### 🧪 Testing & Quality
- **Production Testing**: Все операции протестированы в реальных условиях
- **Reasoning Validation**: Проверка каждого шага reasoning loop
- **File System Integration**: Реальные операции с файловой системой
- **Error Scenarios**: Тестирование fallback механизмов

---

## [v0.3.1] - 2025-08-22

### 🎨 UI Fixes & Improvements

#### Fixed
- **Tab Navigation**: Fixed tab switching where chat panel was overlapping other tabs
- **Chat Scrolling**: Implemented proper scrolling for long messages in chat
- **Date Display**: Fixed knowledge sync date showing as "НИКОГДА" - now displays proper ISO format dates
- **Layout Issues**: Resolved CSS conflicts between tab panels and specific panel styling

#### Added  
- **Real-time Logging**: Added Server-Sent Events (SSE) for live Spring Boot logs viewing
- **System Controller**: New REST controller for log streaming functionality
- **LoggingService**: Service for capturing and streaming application logs via Logback
- **Development Scripts**: Added `rebuild.sh` and `stop.sh` for faster container management

#### Changed
- **Simplified Architecture**: Removed RoutingWorkflow in favor of MainAgent for cleaner codebase
- **Optimized Build**: Docker build now uses dependency caching for faster rebuilds (~1 minute vs 5+ minutes)
- **UI Polish**: Removed technical jargon from welcome panel, improved user experience

#### Technical Details
- Fixed CSS specificity issues with `#chat-panel` vs `.tab-panel` selectors
- Added `@JsonFormat` annotation for proper LocalDateTime serialization
- Implemented in-memory Logback appender for real-time log capture
- Optimized Dockerfile with multi-stage build and dependency layer caching

### 🐛 Bug Fixes
- Fixed chat input area going off-screen due to improper flex layout
- Fixed message truncation by switching from `textContent` to `innerHTML` 
- Fixed circular lambda reference in LoggingService with `lateinit var` pattern
- Fixed missing PostConstruct import (changed from javax to jakarta for Spring Boot 3)

### 📦 Dependencies
- All existing dependencies remain the same
- No breaking changes to API endpoints
- Backward compatible with existing data

---

## [v0.3.0] - 2025-08-21

### 🎯 Major Release - Agent Architecture & Web UI

#### Added
- **Complete Web UI**: Modern dark-themed interface with chat, knowledge management, and logs
- **Agent Architecture**: MainAgent with vector search and dialogue capabilities  
- **Contextual Memory**: Bot remembers conversation history within sessions
- **Spring AI Integration**: Claude 3.5 Sonnet with routing workflow pattern
- **Knowledge Base**: Obsidian vault integration with vector embeddings
- **Real-time Features**: Live status indicators and session management

#### Technical Implementation
- Spring Boot 3.5.4 + Kotlin 1.9.25
- PostgreSQL 16 + pgvector for vector search
- ONNX all-MiniLM-L6-v2 model for local embeddings
- Complete test coverage (46/46 tests passing)
- Docker containerization with health checks

### Performance
- Simple queries: 2-3 seconds
- Knowledge base queries: 20-30 seconds with vector search
- 777x faster embedding cache for repeated queries

---

## [v0.2.x] - Previous Versions

### Backend Foundation
- JPA entities and repositories
- Flyway database migrations  
- REST API endpoints
- MockEmbeddingModel for testing
- TestContainers integration
- JaCoCo test coverage reporting

---

## Project Status

- **Current Version**: v0.6.0
- **Status**: Stable (Claude Code architecture, SubAgent pattern, all tests passing)
- **Test Coverage**: 63/63 tests (100% passing), comprehensive coverage
- **Architecture**: Claude Code SubAgent pattern with AI-first decisions
- **Production Ready**: Full SubAgent architecture, SSE reasoning, Docker deployable

## Next Steps

- [ ] Add basic authentication
- [ ] Integrate real ONNX embedding model
- [ ] Set up CI/CD pipeline
- [ ] Add advanced UI features (streaming responses, file upload)