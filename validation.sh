#!/bin/bash
curl -s -X POST http://localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"name": "Validation User", "email": "valid@finplay.com", "password": "password123"}' > /dev/null
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email": "valid@finplay.com", "password": "password123"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)

echo -e "\n--- Test 1: Dashboard Load Simulation ---"
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"
curl -s -w "\nTime: %{time_total}s\n" "http://localhost:8080/api/market/index-insight?symbol=^NSEI&value=20000&change=1" -H "Authorization: Bearer $TOKEN"

echo -e "\n--- Test 2: Cold Cache Test ---"
docker-compose exec redis redis-cli KEYS "insight:*" | xargs -I {} docker-compose exec redis redis-cli DEL {}
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"
curl -s -w "\nTime: %{time_total}s\n" "http://localhost:8080/api/market/index-insight?symbol=^NSEI&value=20000&change=1" -H "Authorization: Bearer $TOKEN"

echo -e "\n--- Test 4: Personalized Flow Test ---"
curl -s -X POST http://localhost:8080/api/portfolios -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"portfolioName": "Test Validation"}' > /dev/null
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/portfolios/1/mentor -H "Authorization: Bearer $TOKEN"
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/market/pulse?portfolioId=1 -H "Authorization: Bearer $TOKEN"

echo -e "\n--- Test 5: On-Demand Explain Test ---"
for i in {1..12}; do
  curl -s http://localhost:8080/api/explain -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"symbol": "AAPL", "trend": "up", "action": "buy"}' > /dev/null
done
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/explain -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"symbol": "AAPL", "trend": "up", "action": "buy"}'

echo -e "\n--- Test 6: Gemini Failure Simulation ---"
curl -s -w "\nTime: %{time_total}s\n" http://localhost:8080/api/explain -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"symbol": "TSLA", "trend": "down", "action": "sell", "behavior": "Cautious"}'

echo -e "\n--- Test 7: Performance Stress Check ---"
START=$(date +%s%N)
for i in {1..100}; do curl -s -o /dev/null http://localhost:8080/api/market/vibe -H "Authorization: Bearer $TOKEN"; done
END=$(date +%s%N)
echo "100 requests took $(((END - START) / 1000000)) ms"