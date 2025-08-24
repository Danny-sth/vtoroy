#!/bin/bash

# Vtoroy - Full Clean Rebuild Script
# Полная очистка Docker кеша и пересборка

set -e

echo "🧹 Full Docker cleanup and rebuild..."

# Остановка всех контейнеров
echo "⏹️  Stopping all containers..."
docker-compose -f docker-compose.local.yml down

# Полная очистка Docker (образы, кеш, volumes)
echo "🗑️  Complete Docker cleanup..."
docker system prune -a -f
docker volume prune -f

# Пересборка с нуля
echo "🔨 Building from scratch..."
docker-compose -f docker-compose.local.yml up --build -d

# Ожидание готовности
echo "⏳ Waiting for services to be ready..."
sleep 15

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

echo ""
echo "🎉 Vtoroy is ready after full rebuild!"
echo "   Web UI: http://localhost:8080"