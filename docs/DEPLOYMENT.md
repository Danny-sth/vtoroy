# Jarvis AI Assistant - Production Deployment Guide

> **Version**: 0.6.0 (Latest Release - 2025-08-23)  
> **Architecture**: Claude Code SubAgent Pattern  
> **New Features**: Complete Obsidian Integration + Real-time AI Reasoning

## 🚀 Quick Production Deployment

### Prerequisites

**System Requirements:**
- Linux server (Ubuntu 20.04+ recommended)
- Docker 20.10+ and Docker Compose 2.0+
- Minimum 2GB RAM, 10GB disk space
- PostgreSQL 16 with pgvector extension

**Required API Keys:**
- Anthropic API key for Claude 3.5 Sonnet
- Obsidian vault access (local or remote)

### 1. Environment Setup

```bash
# Set required environment variables
export ANTHROPIC_API_KEY="your-anthropic-api-key"
export OBSIDIAN_VAULT_PATH="/path/to/your/obsidian-vault"
export POSTGRES_PASSWORD="secure-database-password"
```

### 2. Automated Deployment Script

```bash
# Deploy to default server
./.scripts/deploy.sh

# Deploy to custom server  
./.scripts/deploy.sh your-server-ip

# Deploy with custom vault path
OBSIDIAN_VAULT_PATH="/custom/path" ./.scripts/deploy.sh
```

**The automated script will:**
- ✅ Install Docker and Docker Compose on target server
- ✅ Create necessary directories and permissions
- ✅ Transfer project files securely
- ✅ Build multi-stage Docker images
- ✅ Initialize PostgreSQL with pgvector extension
- ✅ Start all services with health checks
- ✅ Verify deployment with integration tests

### 3. Deployment Verification

After successful deployment, verify services:

```bash
# Application health check
curl http://your-server:8080/actuator/health

# SubAgent availability
curl http://your-server:8080/api/knowledge/status

# Real-time chat test
curl -X POST http://your-server:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "Hello, test my Obsidian integration", "sessionId": "deployment-test"}'
```

**Expected Response:**
```json
{
  "response": "✅ Hello! Your Jarvis system is running with complete Obsidian integration.",
  "sessionId": "deployment-test",
  "timestamp": [2025, 8, 23, 16, 30, 45, 123456789],
  "metadata": {
    "history_size": 1
  }
}
```

## 🏗️ Manual Deployment (Advanced)

### 1. Server Preparation

#### Install Docker and Dependencies

```bash
# Connect to target server
ssh root@your-server-ip

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
systemctl start docker
systemctl enable docker

# Install Docker Compose
curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Verify installations
docker --version
docker-compose --version
```

#### Create Project Structure

```bash
# Create application directory
mkdir -p /opt/jarvis/{logs,backups,config}
cd /opt/jarvis

# Set proper permissions
chown -R 1000:1000 /opt/jarvis
```

### 2. Project Transfer

```bash
# From local machine, transfer project
scp -r /home/danny/IdeaProjects/jarvis root@your-server:/opt/jarvis

# Or using git (if repository is available)
git clone https://github.com/your-username/jarvis.git /opt/jarvis
cd /opt/jarvis
```

### 3. Configuration Setup

#### Environment Variables

```bash
# Create production environment file
cat > /opt/jarvis/.env << EOF
# Anthropic API Configuration
ANTHROPIC_API_KEY=your-anthropic-api-key

# Database Configuration
POSTGRES_DB=jarvis
POSTGRES_USER=jarvis
POSTGRES_PASSWORD=secure-password-123

# Application Configuration
OBSIDIAN_VAULT_PATH=/opt/jarvis/obsidian-vault
SPRING_PROFILES_ACTIVE=docker

# JVM Configuration
JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC

# Logging
LOGGING_ROOT_LEVEL=INFO
LOGGING_FILE_PATH=/opt/jarvis/logs/jarvis.log
EOF
```

#### Docker Compose Production Configuration

```yaml
# /opt/jarvis/.scripts/docker-compose.prod.yml
version: '3.8'

services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - jarvis_postgres_data:/var/lib/postgresql/data
      - ./backups:/backups
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M

  jarvis:
    build: 
      context: ..
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
      - OBSIDIAN_VAULT_PATH=/app/obsidian-vault
      - SPRING_PROFILES_ACTIVE=docker
      - JAVA_OPTS=${JAVA_OPTS}
    volumes:
      - ${OBSIDIAN_VAULT_PATH:-./obsidian-vault}:/app/obsidian-vault:ro
      - ./logs:/app/logs
      - ./all-MiniLM-L6-v2.onnx:/app/all-MiniLM-L6-v2.onnx:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    deploy:
      resources:
        limits:
          memory: 2G
        reservations:
          memory: 1G
    restart: unless-stopped

volumes:
  jarvis_postgres_data:
    driver: local

networks:
  default:
    name: jarvis-network
```

### 4. Service Startup

```bash
cd /opt/jarvis

# Build and start all services
docker-compose -f .scripts/docker-compose.prod.yml up -d --build

# Monitor startup logs
docker-compose -f .scripts/docker-compose.prod.yml logs -f

# Verify all services are healthy
docker-compose -f .scripts/docker-compose.prod.yml ps
```

## 🔧 Production Operations

### Service Management

#### Start/Stop/Restart Services

```bash
cd /opt/jarvis

# Start services
docker-compose -f .scripts/docker-compose.prod.yml up -d

# Stop services  
docker-compose -f .scripts/docker-compose.prod.yml down

# Restart application only
docker-compose -f .scripts/docker-compose.prod.yml restart jarvis

# Restart database only
docker-compose -f .scripts/docker-compose.prod.yml restart postgres

# Full rebuild and restart
docker-compose -f .scripts/docker-compose.prod.yml down
docker-compose -f .scripts/docker-compose.prod.yml up -d --build
```

#### Update Application

```bash
# Pull latest changes
git pull origin main

# Rebuild and restart
docker-compose -f .scripts/docker-compose.prod.yml down
docker-compose -f .scripts/docker-compose.prod.yml up -d --build

# Verify update
curl http://localhost:8080/actuator/info
```

### Monitoring and Logging

#### Application Logs

```bash
# Real-time application logs
docker-compose -f .scripts/docker-compose.prod.yml logs -f jarvis

# Database logs
docker-compose -f .scripts/docker-compose.prod.yml logs -f postgres

# System resource usage
docker stats

# Container health status
docker-compose -f .scripts/docker-compose.prod.yml ps
```

#### Health Monitoring

```bash
# Application health endpoint
curl http://localhost:8080/actuator/health

# Detailed health information
curl http://localhost:8080/actuator/health | jq

# SubAgent availability
curl http://localhost:8080/api/knowledge/status | jq

# Performance metrics
curl http://localhost:8080/actuator/metrics
```

#### Log Management

```bash
# Rotate application logs (add to crontab)
0 2 * * * cd /opt/jarvis && docker-compose -f .scripts/docker-compose.prod.yml exec jarvis logrotate /etc/logrotate.conf

# Manual log cleanup
find /opt/jarvis/logs -name "*.log" -mtime +30 -delete

# Archive old logs
tar -czf backups/logs-$(date +%Y%m%d).tar.gz logs/*.log
```

### Database Operations

#### Backup and Restore

```bash
# Create database backup
docker exec jarvis-postgres pg_dump -U jarvis jarvis > backups/jarvis-$(date +%Y%m%d-%H%M).sql

# Compressed backup
docker exec jarvis-postgres pg_dump -U jarvis jarvis | gzip > backups/jarvis-$(date +%Y%m%d).sql.gz

# Restore from backup
docker exec -i jarvis-postgres psql -U jarvis jarvis < backups/jarvis-20250823.sql

# Verify restoration
docker exec jarvis-postgres psql -U jarvis jarvis -c "SELECT COUNT(*) FROM chat_sessions;"
```

#### Database Maintenance

```bash
# Connect to database
docker exec -it jarvis-postgres psql -U jarvis jarvis

# Check database size
SELECT pg_size_pretty(pg_database_size('jarvis'));

# Vacuum and analyze
VACUUM ANALYZE;

# Check vector index statistics
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch 
FROM pg_stat_user_indexes 
WHERE indexname LIKE '%embedding%';
```

### Performance Optimization

#### JVM Tuning

```bash
# Edit environment variables for production workloads
cat >> /opt/jarvis/.env << EOF
# Production JVM settings
JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication
EOF

# Restart to apply changes
docker-compose -f .scripts/docker-compose.prod.yml restart jarvis
```

#### Database Optimization

```sql
-- Connect to database and optimize
docker exec -it jarvis-postgres psql -U jarvis jarvis

-- Increase shared_buffers (25% of total RAM)
ALTER SYSTEM SET shared_buffers = '512MB';

-- Optimize for vector operations
ALTER SYSTEM SET effective_cache_size = '1GB';
ALTER SYSTEM SET random_page_cost = 1.1;

-- Apply settings
SELECT pg_reload_conf();
```

#### Resource Monitoring

```bash
# Monitor resource usage
htop
iotop
df -h

# Docker resource usage
docker stats --no-stream

# Network connections
ss -tuln | grep :8080
```

## 🧪 API Testing and Validation

### 1. Claude Code SubAgent Testing

#### Test Agent Selection

```bash
# Test AI-powered agent selection
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Create a note about my project meeting today",
    "sessionId": "agent-selection-test"
  }' | jq
```

#### Test Context Awareness

```bash
# First message
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Create a note with my name in the title",
    "sessionId": "context-test"
  }' | jq

# Follow-up message (should remember context)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Danny",
    "sessionId": "context-test"
  }' | jq
```

### 2. Real-time AI Reasoning Testing

#### SSE Stream Testing

```bash
# Test Server-Sent Events reasoning stream
curl -N -H "Accept: text/event-stream" \
  "http://localhost:8080/api/thinking/stream/test-session-123"

# Expected output:
# data: {"type":"connected","message":"Connected to AI reasoning stream","timestamp":1692808245123}
# data: {"type":"start","message":"🎯 Analyzing query","timestamp":1692808245456}
# data: {"type":"thinking","message":"🤖 Delegating to ObsidianAgent","timestamp":1692808245789}
# data: {"type":"complete","message":"✅ Task completed","timestamp":1692808246012}
```

### 3. Obsidian Integration Testing

#### Full CRUD Operations

```bash
# Create note
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Create a note called test-deployment.md with content: Deployment successful!",
    "sessionId": "crud-test-create"
  }' | jq

# Read note
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Show me the content of test-deployment.md",
    "sessionId": "crud-test-read"
  }' | jq

# Update note
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Add a line to test-deployment.md: All systems operational",
    "sessionId": "crud-test-update"
  }' | jq

# Delete note
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Delete the file test-deployment.md",
    "sessionId": "crud-test-delete"
  }' | jq
```

### 4. Knowledge Search Testing

#### Vector Search Performance

```bash
# Test knowledge search
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What do you know about machine learning?",
    "sessionId": "knowledge-search-test"
  }' | jq

# Sync knowledge base
curl -X POST http://localhost:8080/api/knowledge/sync \
  -H "Content-Type: application/json" \
  -d '{"vaultPath": "/app/obsidian-vault"}' | jq
```

## 🔐 Security Configuration

### 1. Firewall Setup

```bash
# Configure UFW firewall
ufw --force reset
ufw default deny incoming
ufw default allow outgoing

# Allow SSH
ufw allow 22/tcp

# Allow application port
ufw allow 8080/tcp

# Enable firewall
ufw --force enable
ufw status
```

### 2. SSL/HTTPS Setup (Optional)

#### Using Nginx Reverse Proxy

```bash
# Install Nginx
apt update && apt install nginx certbot python3-certbot-nginx

# Configure Nginx
cat > /etc/nginx/sites-available/jarvis << EOF
server {
    listen 80;
    server_name your-domain.com;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    
    location /api/thinking/stream/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        
        # SSE-specific headers
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
        proxy_buffering off;
        proxy_cache off;
    }
}
EOF

# Enable site
ln -s /etc/nginx/sites-available/jarvis /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx

# Generate SSL certificate
certbot --nginx -d your-domain.com
```

### 3. Environment Security

```bash
# Restrict file permissions
chmod 600 /opt/jarvis/.env
chown root:root /opt/jarvis/.env

# Create dedicated user
useradd -r -s /bin/false -d /opt/jarvis jarvis-user
chown -R jarvis-user:jarvis-user /opt/jarvis/logs
```

## 🚨 Troubleshooting

### Common Issues and Solutions

#### Application Won't Start

```bash
# Check logs for errors
docker-compose -f .scripts/docker-compose.prod.yml logs jarvis

# Common issues:
# 1. Missing API key
grep ANTHROPIC_API_KEY /opt/jarvis/.env

# 2. Database connection issues  
docker-compose -f .scripts/docker-compose.prod.yml logs postgres

# 3. Port conflicts
ss -tuln | grep :8080

# 4. Memory issues
free -h && docker stats --no-stream
```

#### Database Connection Errors

```bash
# Check PostgreSQL status
docker-compose -f .scripts/docker-compose.prod.yml exec postgres pg_isready -U jarvis

# Test connection manually
docker-compose -f .scripts/docker-compose.prod.yml exec postgres psql -U jarvis -d jarvis -c "SELECT 1;"

# Reset database if corrupted
docker-compose -f .scripts/docker-compose.prod.yml down postgres -v
docker-compose -f .scripts/docker-compose.prod.yml up -d postgres
```

#### Performance Issues

```bash
# Check resource usage
docker stats --no-stream
htop

# Analyze slow queries
docker exec jarvis-postgres psql -U jarvis jarvis -c "
SELECT query, mean_exec_time, calls 
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;"

# Check vector index usage
docker exec jarvis-postgres psql -U jarvis jarvis -c "
EXPLAIN ANALYZE SELECT * FROM knowledge_files 
ORDER BY embedding <=> '[0.1,0.2,...]' 
LIMIT 5;"
```

#### Memory Issues

```bash
# Check JVM memory usage
docker exec jarvis curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq

# Increase container memory limits
# Edit docker-compose.prod.yml:
deploy:
  resources:
    limits:
      memory: 4G
    reservations:
      memory: 2G
```

## 📊 Monitoring and Alerting

### Health Check Automation

```bash
# Create health check script
cat > /opt/jarvis/health-check.sh << 'EOF'
#!/bin/bash
set -e

# Check application health
if ! curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "$(date): Application health check failed" >> /opt/jarvis/logs/health.log
    exit 1
fi

# Check database connectivity
if ! docker exec jarvis-postgres pg_isready -U jarvis > /dev/null 2>&1; then
    echo "$(date): Database health check failed" >> /opt/jarvis/logs/health.log
    exit 1
fi

echo "$(date): All health checks passed" >> /opt/jarvis/logs/health.log
EOF

chmod +x /opt/jarvis/health-check.sh

# Add to crontab
echo "*/5 * * * * /opt/jarvis/health-check.sh" | crontab -
```

### Log Analysis

```bash
# Create log analysis script
cat > /opt/jarvis/analyze-logs.sh << 'EOF'
#!/bin/bash

echo "=== Application Error Summary ==="
docker-compose -f /opt/jarvis/.scripts/docker-compose.prod.yml logs jarvis | grep -i error | tail -10

echo "=== Database Connection Issues ==="
docker-compose -f /opt/jarvis/.scripts/docker-compose.prod.yml logs postgres | grep -i "connection\|error" | tail -5

echo "=== Performance Metrics ==="
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"
EOF

chmod +x /opt/jarvis/analyze-logs.sh
```

This deployment guide provides comprehensive instructions for deploying and maintaining Jarvis v0.6.0 in production environments with the complete Claude Code SubAgent architecture implementation.