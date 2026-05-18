#!/bin/bash

# FinPlay Production Setup Script

echo "🚀 Starting FinPlay Production Setup..."

# 1. Check for .env file
if [ ! -f .env ]; then
    echo "❌ Error: .env file not found. Please create one from .env.example"
    exit 1
fi

# 2. Start Infrastructure (Postgres + Redis)
echo "📦 Starting Database and Cache containers..."
docker-compose up db redis -d

# 3. Wait for DB to be ready
echo "⏳ Waiting for Postgres to wake up..."
sleep 5

# 4. Build Frontend
echo "🌐 Building Frontend assets..."
cd frontend
npm install
npm run build
cd ..

# 5. Build and Run Backend
echo "☕ Compiling and Starting Backend with PROD profile..."
./mvnw clean package -DskipTests
java -jar target/*.jar --spring.profiles.active=prod
