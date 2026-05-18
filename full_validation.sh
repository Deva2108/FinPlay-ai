#!/bin/bash
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"name": "Validation User", "email": "valid@finplay.com", "password": "password123"}' > /dev/null
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email": "valid@finplay.com", "password": "password123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

echo -e "\n--- Test 1: API Structure & Quality (Vibe) ---"
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"

echo -e "\n--- Test 3: Consistency Test ---"
for i in {1..3}; do
  curl -s http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"
  echo ""
done

echo -e "\n--- Test 6: Performance Test ---"
START=$(date +%s%N)
for i in {1..20}; do curl -s -o /dev/null http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"; done
END=$(date +%s%N)
echo "20 requests took $(((END - START) / 1000000)) ms"
