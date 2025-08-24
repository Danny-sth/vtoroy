#!/bin/bash
set -e

# Vtoroy Deployment Script
# Usage: ./deploy.sh [server_ip]

SERVER_IP="${1:-90.156.230.18}"
SERVER_USER="root"
SERVER_PATH="/opt/vtoroy"
LOCAL_PATH="$(pwd)"

echo "🚀 Deploying Vtoroy to ${SERVER_IP}..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if required files exist
print_status "Checking required files..."
required_files=(
    "Dockerfile"
    "docker-compose.prod.yml"
    "build.gradle.kts"
    "src"
    "obsidian-vault"
)

for file in "${required_files[@]}"; do
    if [[ ! -e "$file" ]]; then
        print_error "Required file/directory not found: $file"
        exit 1
    fi
done

# Check if ANTHROPIC_API_KEY is set
if [[ -z "${ANTHROPIC_API_KEY}" ]]; then
    print_warning "ANTHROPIC_API_KEY not set in environment"
    read -p "Enter your Anthropic API key: " -s ANTHROPIC_API_KEY
    echo
    export ANTHROPIC_API_KEY
fi

# Create deployment package
print_status "Creating deployment package..."
DEPLOY_ARCHIVE="vtoroy-deploy-$(date +%Y%m%d-%H%M%S).tar.gz"

tar -czf "$DEPLOY_ARCHIVE" \
    --exclude='.git' \
    --exclude='.gradle' \
    --exclude='build' \
    --exclude='*.log' \
    --exclude='.idea' \
    --exclude='logs' \
    .

print_status "Created deployment archive: $DEPLOY_ARCHIVE"

# Deploy to server
print_status "Connecting to server and deploying..."

# Create server directory and stop existing containers
ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} << EOF
    set -e
    
    echo "Setting up server environment..."
    
    # Install Docker if not present
    if ! command -v docker &> /dev/null; then
        echo "Installing Docker..."
        curl -fsSL https://get.docker.com -o get-docker.sh
        sh get-docker.sh
        systemctl start docker
        systemctl enable docker
    fi
    
    # Install Docker Compose if not present
    if ! command -v docker-compose &> /dev/null; then
        echo "Installing Docker Compose..."
        curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-\$(uname -s)-\$(uname -m)" -o /usr/local/bin/docker-compose
        chmod +x /usr/local/bin/docker-compose
    fi
    
    # Create application directory
    mkdir -p ${SERVER_PATH}
    cd ${SERVER_PATH}
    
    # Stop existing containers if running
    if [ -f docker-compose.prod.yml ]; then
        echo "Stopping existing containers..."
        docker-compose -f docker-compose.prod.yml down || true
    fi
    
    # Create directories for volumes
    mkdir -p logs backups
    
    echo "Server prepared successfully!"
EOF

# Copy deployment archive to server
print_status "Copying files to server..."
scp -o StrictHostKeyChecking=no "$DEPLOY_ARCHIVE" ${SERVER_USER}@${SERVER_IP}:${SERVER_PATH}/

# Extract and start application on server
ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_IP} << EOF
    set -e
    cd ${SERVER_PATH}
    
    echo "Extracting deployment archive..."
    tar -xzf $DEPLOY_ARCHIVE --strip-components=0
    rm $DEPLOY_ARCHIVE
    
    # Set environment variables
    export ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}"
    export DB_PASSWORD="vtoroy_production_password_2025_$(date +%s)"
    
    # Save environment to .env file
    cat > .env << EOL
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
DB_PASSWORD=\${DB_PASSWORD}
EOL
    
    echo "Building and starting containers..."
    docker-compose -f docker-compose.prod.yml up -d --build
    
    echo "Waiting for services to start..."
    sleep 30
    
    # Check if containers are running
    docker-compose -f docker-compose.prod.yml ps
    
    echo "Checking application health..."
    for i in {1..10}; do
        if curl -f http://localhost:8080/actuator/health &>/dev/null; then
            echo "✅ Vtoroy is healthy and running!"
            break
        fi
        echo "Waiting for application to start... (\$i/10)"
        sleep 10
    done
    
    echo ""
    echo "🎉 Deployment completed successfully!"
    echo "🌐 Vtoroy is now running at: http://${SERVER_IP}:8080"
    echo "📊 Health check: http://${SERVER_IP}:8080/actuator/health"
    echo ""
    echo "Useful commands:"
    echo "  docker-compose -f docker-compose.prod.yml logs -f vtoroy-app"
    echo "  docker-compose -f docker-compose.prod.yml restart vtoroy-app"
    echo "  docker-compose -f docker-compose.prod.yml down"
EOF

# Clean up local deployment archive
rm "$DEPLOY_ARCHIVE"

print_status "Deployment script completed!"
print_status "Application should be available at: http://${SERVER_IP}:8080"

# Test the deployment
print_status "Testing deployment..."
sleep 5

if curl -f "http://${SERVER_IP}:8080/actuator/health" &>/dev/null; then
    print_status "✅ Deployment test passed! Vtoroy is responding."
else
    print_warning "⚠️ Deployment test failed. Check server logs."
fi

echo ""
echo "🔧 Quick test commands:"
echo "  curl http://${SERVER_IP}:8080/actuator/health"
echo "  curl -X GET http://${SERVER_IP}:8080/api/knowledge/status"
echo ""