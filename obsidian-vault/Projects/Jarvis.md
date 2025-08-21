---
title: Jarvis AI Assistant
status: active
start_date: 2025-01-20
tech_stack: [Kotlin, Spring Boot, PostgreSQL, Anthropic Claude]
priority: high
---

# Проект Jarvis

## Описание
Персональный AI ассистент с автономным принятием решений и интеграцией с Obsidian для управления базой знаний.

## Цели проекта
1. Создать умного ассистента, который понимает контекст
2. Интегрировать личную базу знаний из Obsidian
3. Автоматизировать рутинные задачи
4. Обеспечить приватность данных (self-hosted)

## Технический стек
- **Backend**: Spring Boot 3.3 + Kotlin
- **AI**: Anthropic Claude API (claude-3-5-sonnet)
- **Embeddings**: Локальная модель all-MiniLM-L6-v2
- **База данных**: PostgreSQL + pgvector
- **Контейнеризация**: Docker Compose

## Архитектура

### Компоненты
1. **REST API** - взаимодействие с клиентами
2. **Chat Service** - обработка диалогов
3. **Knowledge Service** - работа с базой знаний
4. **Vector Search** - семантический поиск
5. **Function Calling** - автономные действия AI

### Endpoints
- `POST /api/chat` - основной чат
- `POST /api/knowledge/sync` - синхронизация Obsidian
- `GET /api/knowledge/status` - статус базы знаний

## Функциональность MVP
- [x] Чат с контекстом сессии
- [x] Векторный поиск по заметкам
- [x] Function calling для автономности
- [x] Синхронизация Obsidian vault
- [ ] Веб интерфейс
- [ ] Telegram бот
- [ ] Голосовое управление

## Планы развития

### Фаза 2 - Интеграции
- Календарь (Google Calendar API)
- Email (Gmail API)
- Умный дом (Home Assistant)
- Задачи (Todoist/Notion)

### Фаза 3 - Автоматизация
- Автоматическое создание заметок
- Умные напоминания
- Анализ паттернов поведения
- Предиктивные предложения

## Заметки по разработке

### Проблемы и решения
1. **Размер embeddings**: Использовал локальную модель вместо OpenAI для экономии
2. **Производительность поиска**: Добавил IVFFLAT индекс для pgvector
3. **Контекст чата**: Ограничил историю 20 сообщениями

### Полезные команды
```bash
# Запуск PostgreSQL
docker-compose up -d

# Синхронизация vault
curl -X POST http://localhost:8080/api/knowledge/sync \
  -H "Content-Type: application/json" \
  -d '{"vaultPath": "./obsidian-vault"}'

# Тест чата
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "Что я делал сегодня?", "sessionId": "test-session"}'
```

## Связанные заметки
- [[Daily/2025-01-20|Сегодняшние задачи]]
- [[Ideas/AI Assistant Features|Идеи функций]]
- [[References/Spring AI Documentation|Spring AI Docs]]