# Load tests (k6)

## Install k6

- **Mac:** `brew install k6`
- **Windows:** `winget install k6 --source winget` or `choco install k6`
- **Docker:** `docker pull grafana/k6`

## Run against local Docker stack

```bash
# 1. Start the app
docker compose up -d --build
# Wait until backend is healthy
docker compose ps

# 2. Smoke test first (Phase 3) — proves the API works at all
bash scripts/smoke.sh

# 3. Load tests
k6 run load-tests/k6-login.js
k6 run load-tests/k6-trading.js
k6 run load-tests/k6-portfolio-fetch.js
```

## Run via Docker (no local install)

```bash
docker run --rm -i --network=finplay_finplay-net -e BASE_URL=http://backend:8080 \
  -v $(pwd)/load-tests:/scripts \
  grafana/k6 run /scripts/k6-trading.js
```

## Targets (with the Phase 0/2 fixes)

| Test | Target | Reasoning |
|---|---|---|
| login | p95 < 500ms, error < 2%, 50 RPS sustained | bcrypt-12 ≈ 250ms; remainder is DB + JWT sign |
| trading | p95 < 800ms, success > 85% | Includes Redis quote read + DB write under pessimistic lock |
| portfolio fetch | p95 < 200ms, p99 < 400ms, error < 2% | All hits should be Redis-cached |

## Common failure modes

| Symptom | Likely cause |
|---|---|
| `order_success` rate < 50% | MarketDataScheduler hasn't pre-populated Redis. Wait 60s after `compose up` before running. |
| `http_req_failed` spikes at the start | bcrypt-12 contention while VUs ramp. Acceptable; check sustained phase. |
| p95 > 2s | Hikari pool exhausted. Bump `spring.datasource.hikari.maximum-pool-size` in `application-prod.properties`. |
