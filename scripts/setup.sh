#!/bin/bash
set -e

echo "=== ORBIT Local Setup ==="
echo ""

# Check prerequisites
command -v java >/dev/null 2>&1 || { echo "Error: Java 21+ is required"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "Error: Node.js 20+ is required"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Error: Docker is required"; exit 1; }

echo "Prerequisites check passed."

# Copy env files if they don't exist
if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

# Start infrastructure
echo "Starting infrastructure services..."
docker compose up -d orbit-db orbit-redis

# Wait for PostgreSQL
echo "Waiting for PostgreSQL..."
until docker compose exec orbit-db pg_isready -U orbit_user -d orbit_db > /dev/null 2>&1; do
  sleep 1
done
echo "PostgreSQL is ready."

# Wait for Redis
echo "Waiting for Redis..."
until docker compose exec orbit-redis redis-cli -a redis_pass ping > /dev/null 2>&1; do
  sleep 1
done
echo "Redis is ready."

# Install frontend dependencies
echo "Installing frontend dependencies..."
cd apps/web && npm install && cd ../..

echo ""
echo "=== Setup Complete ==="
echo "Start backend:  cd apps/api && ./gradlew bootRun"
echo "Start frontend: cd apps/web && npm run dev"
echo "Or use Docker:  docker compose up"
