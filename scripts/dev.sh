#!/bin/bash
set -e

echo "=== Starting ORBIT Development Environment ==="

# Start infrastructure
docker compose up -d orbit-db orbit-redis

# Wait for services
echo "Waiting for infrastructure..."
sleep 3

# Start backend in background
echo "Starting backend..."
cd apps/api && ./gradlew bootRun &
BACKEND_PID=$!
cd ../..

# Start frontend
echo "Starting frontend..."
cd apps/web && npm run dev &
FRONTEND_PID=$!
cd ../..

echo ""
echo "=== ORBIT Development Environment Started ==="
echo "Frontend: http://localhost:3000"
echo "Backend:  http://localhost:8080"
echo "Press Ctrl+C to stop all services"

# Wait for interrupt
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; docker compose stop" INT TERM
wait
