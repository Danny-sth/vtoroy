#!/bin/bash

# Jarvis - Stop Script
# Быстрая остановка всех контейнеров

echo "⏹️  Stopping Jarvis containers..."
docker-compose -f docker-compose.local.yml down

echo "🧹 Cleaning up..."
docker system prune -f > /dev/null 2>&1 || true

echo "✅ Jarvis stopped successfully!"