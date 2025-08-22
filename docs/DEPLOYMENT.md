# Jarvis Deployment Guide

> **Версия:** 0.5.0 - ReAct Reasoning System  
> **Новые возможности:** Full CRUD Obsidian + AI-driven Decision Making

## Быстрый деплой на сервер

### 1. Настройка переменных окружения

```bash
# Установите ваш Anthropic API ключ
export ANTHROPIC_API_KEY="your-anthropic-api-key"
```

### 2. Запуск автоматического деплоя

```bash
# Деплой на сервер 90.156.230.18
./deploy.sh

# Или на другой сервер
./deploy.sh your-server-ip
```

Скрипт автоматически:
- ✅ Установит Docker и Docker Compose на сервере
- ✅ Создаст необходимые директории
- ✅ Скопирует код на сервер
- ✅ Соберет и запустит контейнеры
- ✅ Проверит работоспособность приложения

### 3. Проверка деплоя

После успешного деплоя приложение будет доступно по адресу:
- **API:** http://90.156.230.18:8080
- **Health Check:** http://90.156.230.18:8080/actuator/health
- **Knowledge Status:** http://90.156.230.18:8080/api/knowledge/status

## Ручной деплой (если нужен контроль)

### 1. Подключение к серверу

```bash
ssh root@90.156.230.18
```

### 2. Установка Docker (если не установлен)

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
systemctl start docker
systemctl enable docker

# Docker Compose
curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
```

### 3. Копирование проекта

```bash
# С локальной машины
scp -r /home/danny/IdeaProjects/jarvis root@90.156.230.18:/opt/jarvis
```

### 4. Настройка на сервере

```bash
ssh root@90.156.230.18
cd /opt/jarvis

# Создание .env файла
cat > .env << EOF
ANTHROPIC_API_KEY=your-api-key-here
DB_PASSWORD=secure-password-123
EOF

# Создание директорий
mkdir -p logs backups
```

### 5. Запуск приложения

```bash
# Сборка и запуск
docker-compose -f docker-compose.prod.yml up -d --build

# Проверка статуса
docker-compose -f docker-compose.prod.yml ps
docker-compose -f docker-compose.prod.yml logs -f jarvis-app
```

## Полезные команды на сервере

### Управление контейнерами

```bash
cd /opt/jarvis

# Просмотр логов
docker-compose -f docker-compose.prod.yml logs -f jarvis-app
docker-compose -f docker-compose.prod.yml logs -f postgres

# Перезапуск приложения
docker-compose -f docker-compose.prod.yml restart jarvis-app

# Остановка всех сервисов
docker-compose -f docker-compose.prod.yml down

# Полная пересборка
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d --build
```

### Мониторинг

```bash
# Проверка здоровья
curl http://localhost:8080/actuator/health

# Статус базы знаний
curl http://localhost:8080/api/knowledge/status

# Системные ресурсы
docker stats
htop
df -h
```

### Бэкап базы данных

```bash
# Создание бэкапа
docker exec jarvis-postgres-prod pg_dump -U jarvis jarvis > backups/jarvis-$(date +%Y%m%d).sql

# Восстановление из бэкапа
docker exec -i jarvis-postgres-prod psql -U jarvis jarvis < backups/jarvis-20250120.sql
```

## Тестирование API

### 1. Синхронизация знаний

```bash
curl -X POST http://90.156.230.18:8080/api/knowledge/sync \
  -H "Content-Type: application/json" \
  -d '{"vaultPath": "/app/obsidian-vault"}'
```

### 2. Тест чата

```bash
curl -X POST http://90.156.230.18:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Расскажи что знаешь о моем отпуске в Тайланде",
    "sessionId": "test-session-123"
  }'
```

### 3. Тестирование ReAct Reasoning System (v0.5.0)

#### Создание заметки с ReAct

```bash
curl -X POST http://90.156.230.18:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "создай заметку с названием daily-tasks.md и добавь туда список дел на сегодня",
    "sessionId": "react-test-create"
  }'
```

#### Поиск и анализ заметок с ReAct

```bash  
curl -X POST http://90.156.230.18:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "найди все заметки про проекты и покажи их названия",
    "sessionId": "react-test-search"
  }'
```

#### Удаление файла с ReAct

```bash
curl -X POST http://90.156.230.18:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "удали файл obsidian-vault/test456.md",
    "sessionId": "react-test-delete"
  }'
```

**Ожидаемый результат ReAct operations:**
- ✅ Многоступенчатые рассуждения в `metadata.reasoning_steps`
- ✅ Реальные физические операции с файлами
- ✅ Полные структурированные ответы (не обрезанные)
- ✅ Автоматический fallback при ошибках простых операций

## Troubleshooting

### Приложение не запускается

```bash
# Проверить логи
docker-compose -f docker-compose.prod.yml logs jarvis-app

# Проверить переменные окружения
docker-compose -f docker-compose.prod.yml config
```

### База данных недоступна

```bash
# Проверить статус PostgreSQL
docker-compose -f docker-compose.prod.yml logs postgres

# Подключение к БД
docker exec -it jarvis-postgres-prod psql -U jarvis
```

### Ошибки памяти

```bash
# Увеличить лимиты в docker-compose.prod.yml
deploy:
  resources:
    limits:
      memory: 2G
    reservations:
      memory: 1G
```

## Безопасность

1. **Firewall**: Откройте только нужные порты (8080, 22)
2. **SSL**: Добавьте HTTPS через Nginx (планы на потом)
3. **Updates**: Регулярно обновляйте систему
4. **Backup**: Настройте автоматические бэкапы

## Мониторинг производительности

### Метрики Spring Boot

- http://90.156.230.18:8080/actuator/metrics
- http://90.156.230.18:8080/actuator/prometheus (для Grafana)

### Система

```bash
# Использование ресурсов
docker stats

# Место на диске
du -sh /opt/jarvis/*
df -h

# Логи системы
journalctl -u docker
```