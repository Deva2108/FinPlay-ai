#!/bin/bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email": "valid@finplay.com", "password": "password123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)
docker-compose exec redis redis-cli FLUSHALL > /dev/null
echo "VIBE:"
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"
echo "INDEX:"
curl -s -w "\nTime: %{time_total}s\n" "http://localhost:8080/api/market/index-insight?symbol=%5ENSEI&value=20000&change=1" -H "Authorization: Bearer $TOKEN"
