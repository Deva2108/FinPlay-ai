# Phase 3 — Local Windows test flow

Once `docker compose up --build` is healthy, run this end-to-end smoke test
in **Git Bash / PowerShell / WSL**. All commands are POSIX-portable.

The script `scripts/smoke.sh` automates the same flow.

---

## 0. Health probe

```bash
curl -fsS http://localhost:8080/actuator/health
# → {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}
```

If `status != UP`, stop here and check `docker compose logs backend`.

---

## 1. Register a user

```bash
TS=$(date +%s)
EMAIL="qa+${TS}@finplay.test"

curl -fsS -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"QA Bot\",\"email\":\"${EMAIL}\",\"password\":\"hunter2pass\"}"
# → 201 Created, body has data.token + data.user.userId
```

Capture the token:

```bash
TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"hunter2pass\"}" \
  | python -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
echo "Token: ${TOKEN:0:25}…"
```

---

## 2. List portfolios (the registration flow auto-creates one)

```bash
curl -fsS http://localhost:8080/api/portfolios \
  -H "Authorization: Bearer $TOKEN"
# → 200 OK, body.data[0].portfolioId, body.data[0].balance == 100000
```

```bash
PID=$(curl -fsS http://localhost:8080/api/portfolios \
  -H "Authorization: Bearer $TOKEN" \
  | python -c "import sys,json; print(json.load(sys.stdin)['data'][0]['portfolioId'])")
echo "Portfolio: $PID"
```

---

## 3. Place a paper trade

```bash
curl -fsS -X POST http://localhost:8080/api/trading/paper/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"portfolioId\":${PID},\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}"
# → 200 OK, body.data.status == "FILLED", price > 0
# OR
# → 503 if MarketGateway hasn't filled the cache yet (wait ~60s and retry)
```

Verify positions:

```bash
curl -fsS "http://localhost:8080/api/trading/paper/positions?portfolioId=${PID}" \
  -H "Authorization: Bearer $TOKEN"
# → 200 OK, body.data[0].symbol == "AAPL", quantity == 1
```

Verify balance dropped:

```bash
curl -fsS "http://localhost:8080/api/trading/paper/account?portfolioId=${PID}" \
  -H "Authorization: Bearer $TOKEN"
# → 200 OK, body.data.cash < 100000
```

---

## 4. Negative-quantity exploit attempt (should now FAIL)

```bash
curl -i -X POST http://localhost:8080/api/trading/paper/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"portfolioId\":${PID},\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":-100}"
# Expected: HTTP/1.1 400 Bad Request
# Body: { "success":false, "message":"quantity: quantity must be positive, ..." }
```

---

## 5. Free-money endpoint attempt (should now FAIL)

```bash
curl -i -X POST "http://localhost:8080/api/portfolios/${PID}/balance" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 1000000}'
# Expected: HTTP/1.1 403 Forbidden  (admin-only endpoint)
```

---

## 6. Portfolio reset (legitimate path)

```bash
curl -fsS -X POST "http://localhost:8080/api/portfolios/${PID}/reset" \
  -H "Authorization: Bearer $TOKEN"
# → 200 OK, body.data.balance == 100000 (initial), holdings cleared
```

---

## 7. Sell a holding back

```bash
# First buy something so we have stock to sell
curl -fsS -X POST http://localhost:8080/api/trading/paper/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"portfolioId\":${PID},\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":2}"

# Now sell
curl -fsS -X POST http://localhost:8080/api/trading/paper/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"portfolioId\":${PID},\"symbol\":\"AAPL\",\"side\":\"SELL\",\"quantity\":1}"
```

Verify orders ledger:

```bash
curl -fsS "http://localhost:8080/api/trading/paper/orders?portfolioId=${PID}" \
  -H "Authorization: Bearer $TOKEN"
# → 200 OK, body.data has 2+ entries (BUY then SELL), most recent first
```

---

## 8. Auth negative tests

```bash
# Missing token → 401
curl -i http://localhost:8080/api/portfolios
# Expected: HTTP/1.1 401 Unauthorized

# Tampered token → 401
curl -i -H "Authorization: Bearer not.a.real.jwt" http://localhost:8080/api/portfolios
# Expected: HTTP/1.1 401 Unauthorized
# Backend logs: "JWT malformed or unsupported" (warn level)
```

---

## Expected outcomes summary

| Step | Endpoint | Expected |
|---|---|---|
| 0  | `GET /actuator/health` | 200 + `{status: UP}` |
| 1  | `POST /api/auth/register` | 201 + token |
| 2  | `GET /api/portfolios` | 200 + portfolio with balance 100000 |
| 3  | `POST /api/trading/paper/orders` (BUY) | 200 / 503 if syncing |
| 4  | Same with `quantity: -100` | **400 Bad Request** |
| 5  | `POST /api/portfolios/{id}/balance` | **403 Forbidden** |
| 6  | `POST /api/portfolios/{id}/reset` | 200 + balance back to 100000 |
| 7  | BUY then SELL | both 200 |
| 8  | No token / bad token | 401 |
