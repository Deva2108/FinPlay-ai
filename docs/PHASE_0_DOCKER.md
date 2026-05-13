# Phase 0 — Docker-first setup

This phase makes FinPlay run cleanly on Windows (and Mac/Linux) via a single
`docker compose up --build` command. No machine-specific paths, no OS-specific
hacks; everything is portable.

---

## Files added / changed

| File | Status | Why |
|---|---|---|
| `Dockerfile` | rewritten | Multi-stage, layer-cached deps, non-root user, `HEALTHCHECK` |
| `frontend/Dockerfile` | rewritten | `npm ci`, node 20-alpine, custom `nginx.conf`, healthcheck |
| `frontend/nginx.conf` | new | SPA fallback (`try_files … /index.html`), gzip, cache headers |
| `docker-compose.yml` | rewritten | Healthchecks on every service, named network, persistent volumes, `depends_on` waits for **health** not just start |
| `.dockerignore` | new | Strips `target/`, `.git/`, logs, `.idea/`, `frontend/`, ~1 GB of cruft |
| `frontend/.dockerignore` | new | Strips `node_modules/`, `dist/`, `.env`, build logs |
| `src/main/resources/application-prod.properties` | new | Real prod profile so `SPRING_PROFILES_ACTIVE=prod` actually means something |
| `.env.example` | rewritten | Now includes every var the compose file expects |
| `.gitignore` | fixed | Removed stray pasted text on the `frontend/.env.local` line |

---

## Required `.env`

Copy `.env.example` → `.env` and fill in values:

```env
SPRING_PROFILES_ACTIVE=prod
POSTGRES_DB=stocksdb
DB_USERNAME=postgres
DB_PASSWORD=<strong>
JWT_SECRET=<openssl rand -base64 64>
JWT_EXPIRATION=86400000

FINNHUB_API_KEY=<your_finnhub_key>
NEWS_API_KEY=<your_newsapi_key>
GROQ_API_KEY=<your_groq_key>

# optional
ALPHA_VANTAGE_KEY=
GEMINI_API_KEY=
YOUTUBE_API_KEY=

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
VITE_API_URL=http://localhost:8080
```

> The compose file substitutes `${VAR}` from this file at startup. Any var with
> a `${VAR:-default}` fallback in the compose file is optional.

---

## Run on Windows

Prerequisites: **Docker Desktop for Windows** (WSL2 backend recommended), Git.

```powershell
# 1. Pull the latest code
git clone https://github.com/<you>/Stock-Portfolio-Monitoring-App.git
cd Stock-Portfolio-Monitoring-App

# 2. Configure env
copy .env.example .env
notepad .env        # fill in real keys

# 3. Build + start everything
docker compose up --build

# (Run in background instead)
docker compose up --build -d
docker compose logs -f backend
```

**First-time build:** ~3–5 min (Maven downloads, npm install, image layers).
**Subsequent builds:** ~30–60 s if only source changed (deps are cached).

### Expected output

You should see, in order:

```
finplay-db        ... healthy
finplay-redis     ... healthy
finplay-backend   ... Started StockPortfolioApplication in N.NNN seconds
finplay-backend   ... healthy           ← only after /actuator/health returns UP
finplay-frontend  ... healthy
```

### Verify

```powershell
# Backend health
curl http://localhost:8080/actuator/health
# → {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}

# Frontend
start http://localhost:3000
```

---

## Run on Mac / Linux

Identical:

```bash
cp .env.example .env
$EDITOR .env
docker compose up --build
```

---

## Common operations

```bash
# Stop & remove containers (volumes survive)
docker compose down

# Stop & wipe everything including DB data
docker compose down -v

# Rebuild only one service
docker compose up --build backend

# Tail logs
docker compose logs -f backend
docker compose logs -f frontend

# Shell into a container
docker compose exec backend sh
docker compose exec db psql -U $DB_USERNAME -d stocksdb
```

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `frontend` exits with 404 on refresh of `/dashboard` | Confirm `frontend/nginx.conf` was copied; rebuild with `--no-cache`. |
| `backend` health stays `starting` for >2 min | Check `docker compose logs backend` — usually a missing required env var (`JWT_SECRET`, `FINNHUB_API_KEY`, `GROQ_API_KEY`). |
| Port `5444` or `8080` already in use | Edit `docker-compose.yml` ports section — change host side e.g. `"15444:5432"`, `"18080:8080"`. |
| Postgres data wiped after `docker compose down -v` | That's intentional. Use `docker compose down` (no `-v`) to preserve `finplay-db-data`. |
| Image build fails on Windows with line-ending error | Run `git config --global core.autocrlf input` and re-clone. |
| `Error: ENOSPC: no space left on device` during npm build | `docker system prune -a` to clear old image layers. |

---

## What this phase does **not** do

These are tracked for later phases:

- **Race conditions in trade execution** → Phase 1/2.
- **JWT validation hardening, rate limiting** → Phase 5.
- **Sentry / structured logs** → Phase 6.
- **Deploy to Render + Vercel** → Phase 7.
- **Unit & integration tests** → Phase 8.
