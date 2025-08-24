#!/bin/bash

# Vtoroy - Rebuild and Restart Script
# Быстрая пересборка и перезапуск контейнеров
# 
# Этот скрипт:
# - Сохраняет базовые образы (gradle, eclipse-temurin) для ускорения сборки
# - Удаляет только образ приложения и dangling кеш
# - Для полной очистки используйте: docker system prune -a -f

set -e

# Загружаем переменные из .env файла
if [ -f "../.env" ]; then
    echo "📋 Loading environment variables from .env file..."
    set -a  # automatically export all variables
    source ../.env
    set +a  # stop automatically exporting
    echo "✅ Environment variables loaded"
else
    echo "⚠️  Warning: .env file not found, using system environment variables"
fi

echo "🔄 Rebuilding Vtoroy containers..."

# Остановка и удаление контейнеров
echo "⏹️  Stopping containers..."
docker-compose -f docker-compose.local.yml down

# Удаление только образа приложения (сохраняем базовые образы)
echo "🗑️  Removing application image..."
docker image rm scripts-vtoroy 2>/dev/null || true

# Пересборка и запуск
echo "🔨 Building and starting containers..."
docker-compose -f docker-compose.local.yml up --build -d

# Ожидание готовности
echo "⏳ Waiting for services to be ready..."
sleep 10

# Проверка статуса
echo "📊 Checking container status..."
docker-compose -f docker-compose.local.yml ps

# Проверка health
echo "🏥 Checking application health..."
for i in {1..30}; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "✅ Application is healthy!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Application health check failed after 30 attempts"
        exit 1
    fi
    echo "   Attempt $i/30 - waiting..."
    sleep 2
done

# Показать логи последних 10 строк
echo "📋 Recent logs:"
docker-compose -f docker-compose.local.yml logs --tail=10 vtoroy

echo ""
echo "🎉 Vtoroy is ready!"
echo "   Web UI: http://localhost:8080"
echo "   API: http://localhost:8080/api/*"
echo "   Health: http://localhost:8080/actuator/health"
echo ""
echo "Use 'docker-compose -f docker-compose.local.yml logs -f vtoroy' to follow logs"