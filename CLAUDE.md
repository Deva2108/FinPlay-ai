# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FinPlay** is a full-stack paper trading simulator with AI mentoring. Users build simulated portfolios, trade stocks (no real money), and receive AI-generated insights. The backend is Spring Boot (Java 21), the frontend is React + Vite, and the infrastructure uses PostgreSQL + Redis.

---

## Commands

### Backend (Spring Boot / Maven)

```bash
# Build (skip tests for speed)
./mvnw -DskipTests compile

# Run dev server (loads .env automatically via dotenv-java)
./mvnw spring-boot:run

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Package JAR
mvn clean package -DskipTests
```

Health check: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`

### Frontend (React / Vite)

```bash
cd frontend
npm install
npm run dev       # dev server on port 5173
npm run build
npm run preview
```

### Full Stack via Docker (recommended)

```bash
# All services: PostgreSQL (5444), Redis (6379), backend (8080), frontend (3000)
docker-compose up --build

# Only infra (run backend locally)
docker-compose up db redis
```

### Kill stale backend port

```bash
lsof -i :8080 && kill -9 <pid>
```

---

## Environment Setup

Copy `.env.example` to `.env`. The backend auto-loads `.env` via dotenv-java — no manual export needed.

| Variable | Required | Purpose |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | Yes | PostgreSQL credentials |
| `JWT_SECRET` / `JWT_EXPIRATION` | Yes | Auth token signing |
| `FINNHUB_API_KEY` | Yes | Real-time stock quotes |
| `NEWS_API_KEY` | Yes | Financial news feed |
| `GROQ_API_KEY` | Yes | Primary LLM (llama3-8b-8192) |
| `YOUTUBE_API_KEY` | No | Educational video content |
| `VITE_API_URL` | Frontend | Backend base URL |

---

## Architecture

### Backend Domain Modules

The backend is organized into domain packages under `src/main/java/com/example/stockPortfolio/`:

| Package | Responsibility |
|---|---|
| `UserManagement` | Registration, login, JWT auth |
| `Security` | `JwtRequestFilter`, `JwtUtils`, CORS config, Redis config |
| `HoldingsManagement` | User positions and transaction records |
| `TradingManagement` | Paper trade execution (`PaperTradingService`) |
| `MarketManagement` | Market quotes, news, forex — scheduler + gateway live here |
| `PortfolioManagement` | Portfolio analytics and aggregation |
| `AiManagement` | Groq/Gemini integration; insight generation + schema validation |
| `WatchlistManagement` | Saved/favorite symbols |
| `AlertManagement` | Price alert triggers |
| `VaultManagement` | Educational resources |
| `ContentManagement` | YouTube video fetching |
| `DecisionManagement` | Trade decision tracking for behavior analytics |
| `ExceptionManagement` | Global `@ControllerAdvice` error handling |

### Key Architectural Patterns

**Self-healing startup:** `StockPortfolioApplication` attempts PostgreSQL on startup. If unreachable, it automatically activates the `h2` Spring profile — no manual config needed. The H2 config lives in `application-h2.properties`.

**Scheduler-based data hydration (`MarketDataScheduler`):** Market data is pre-fetched in batches every 1 minute across 100+ symbols (batch size 10). AI insights are precomputed every 30 minutes. Requests never trigger live API calls — they always read from Redis cache or the precomputed store.

**`ExternalMarketDataGateway`:** Centralized multi-source fetching (Finnhub → Yahoo Finance → Alpha Vantage) with `MAX_ATTEMPTS=2`, `RETRY_DELAY_MS=150`, and symbol normalization for both US and Indian stocks. Aggregated failures return a `SYNCING` state, never an exception.

**Redis caching (30-min TTL):** `@Cacheable` annotations throughout service layer. Cache keys are `symbol + metricType`. On API failure, the system falls back to cached data before returning `SYNCING`.

**Universal fallback contract:** Every API response is wrapped in `GenericResponseDTO`. The system must never return `null`, never crash on missing API keys, and must return `{"status":"SYNCING","source":"fallback"}` when data is unavailable. Do not break this contract.

### Frontend

- **State management:** React Context API (no Redux). Separate contexts: `MarketContext`, `TradingContext`, `BehaviorContext`, `StockPanelContext`.
- **API layer:** All HTTP calls go through `frontend/src/services/api.js` (axios-based). Add new endpoints here.
- **Routing:** React Router v6 with page components in `src/pages/`.
- **Styling:** TailwindCSS + Framer Motion for animations.

### Data Flow

```
React UI (Vite :5173)
  → REST calls → Spring Boot (:8080)
      → Redis (cache read/write)
      → PostgreSQL (users, holdings, transactions)
      → ExternalMarketDataGateway → Finnhub / Yahoo / AlphaVantage
      → AiManagement → Groq (primary) / Gemini (fallback)
```

---

## Database

- **Production:** PostgreSQL 15 (Docker port 5444, app connects on 5432 inside compose network)
- **Fallback:** H2 in-memory, auto-activated if Postgres is unreachable
- `spring.jpa.hibernate.ddl-auto=update` — schema is managed automatically; do not add manual migrations unless changing this setting
- Swagger UI available at `/swagger-ui.html`; OpenAPI spec at `/v3/api-docs`
