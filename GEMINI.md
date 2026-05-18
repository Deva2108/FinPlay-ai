# 🔧 FinPlay Runtime & Testing Instructions

## Objective

Ensure the backend runs correctly, all APIs respond, and fallback mechanisms behave safely when API keys are missing.

---

## 1. Build & Run Backend

Run from project root:

```bash
./mvnw -DskipTests compile
./mvnw spring-boot:run
```

Expected logs:

* "Started Application"
* "Tomcat started on port 8080"

If this appears → backend is running.

---

## 2. Health Check

Test server:

GET http://localhost:8080/actuator/health

Expected:

```json
{
  "status": "UP"
}
```

---

## 3. API Testing

### 3.1 Insight API (AI / fallback)

GET /api/insight?query=tesla

Expected:

* If GROQ/Gemini key present → structured AI JSON
* If missing →

```json
{
  "status": "SYNCING",
  "message": "Insight is being prepared"
}
```

---

### 3.2 Portfolio API

GET /api/portfolio

Expected:

* Portfolio data derived from internal transaction history and live market rates.
* Without data -> fallback syncing response

---

### 3.3 Forex API

GET /api/forex?from=USD&to=INR

Expected:

* Live rate OR cached fallback

---

### 3.4 Content API (YouTube)

GET /api/content

Expected:

* With API key → real videos
* Without → fallback mock content

---

## 4. System Behavior Rules

The system MUST:

* Never crash if API keys are missing
* Always return a valid JSON response
* Use "SYNCING" state when data is unavailable
* Never block user requests
* Always rely on Redis cache if available

---

## 5. Failure Handling

If API fails:

* Return cached data if present
* Otherwise return:

```json
{
  "status": "SYNCING",
  "source": "fallback"
}
```

---

## 6. Validation Checklist

System is considered correct if:

* Backend starts without errors
* Health endpoint returns UP
* All APIs respond (even fallback)
* No null or empty responses
*   Logs show graceful warnings (not crashes)
*   **Resilience4j** handles all external API calls with declarative circuit breakers and rate limiters for seamless fallbacks.
*   **Deep Caching Architecture:** Uses a multi-level strategy:
    *   **L1 (Caffeine):** 30s In-Memory cache for ultra-hot data (indices, forex).
    *   **L2 (Redis):** Distributed cache for consistent data across nodes.
    *   **Tiered TTLs:** 5m (Hot Quotes), 1h (News/Charts), 2h (AI Insights).
    *   **Persistent Fallback:** 24h "Last Close" snapshots for 100% off-hours availability.

---


## 7. Recommended Testing Tool

Use Postman or curl for testing endpoints.

---

## 8. Common Issues

Port already in use:

```bash
lsof -i :8080
kill -9 <pid>
```

Env not loaded:
Ensure variables are exported or available in runtime.

---

## 9. Next Phase Trigger

Once all endpoints respond correctly:

Proceed to frontend integration:

* Insights polling hook
* Vault page
* Portfolio UI
* Forex conversion display

---

## Important

This system is designed to simulate real-time behavior using:

* Scheduler-based hydration
* Redis caching
* Safe fallback states

Do NOT modify this architecture.
