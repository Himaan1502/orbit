#!/bin/bash
set -e

echo "=== Running ORBIT Test Suite ==="

# Frontend tests
echo "--- Frontend Tests ---"
cd apps/web
npm run lint
npm run format:check
npm run type-check
npm run test
cd ../..

# Backend tests
echo "--- Backend Tests ---"
cd apps/api
./gradlew check
cd ../..

echo ""
echo "=== All Tests Passed ==="
