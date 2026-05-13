# Phase 7 — Cloud deployment

End state: backend on Render, frontend on Vercel, Postgres on Neon, Redis on Upstash. Free tiers everywhere.

---

## 1 · Provision Postgres on Neon

1. https://console.neon.tech → New Project → region close to your Render region.
2. Copy the **pooled** JDBC URL — it looks like:
   `jdbc:postgresql://ep-xxx-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require`
3. Note the username (e.g. `neondb_owner`) and password.

> Use the **pooler** URL, not the direct one — Neon's connection pooler keeps you within the free-tier max-connections limit even under k6 load.

---

## 2 · Provision Redis on Upstash

1. https://console.upstash.com → Create Database → Global → free tier.
2. Reveal the **Endpoint** (host) and **Password**. Note the **port** (6379 by default).
3. Upstash supports plain Redis protocol over TLS. Spring's `spring.data.redis` understands it directly.

---

## 3 · Deploy backend to Render

### One-time setup

1. Push the repo to GitHub.
2. https://dashboard.render.com → **New** → **Web Service** → connect the repo.
3. Settings:
   - **Environment:** Docker
   - **Dockerfile path:** `./Dockerfile`
   - **Branch:** `main`
   - **Region:** same as Neon
   - **Plan:** Free (or Starter for ~$7/mo if you need always-on; free tier sleeps after 15 min idle)
   - **Health check path:** `/actuator/health`

### Environment variables (Render → Environment)

```
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=10000

# Neon
DB_URL=jdbc:postgresql://ep-xxx-pooler.<region>.aws.neon.tech/neondb?sslmode=require
DB_USERNAME=neondb_owner
DB_PASSWORD=<from neon>

# Upstash
REDIS_HOST=<endpoint>.upstash.io
REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=<password>
SPRING_DATA_REDIS_SSL_ENABLED=true

# Auth
JWT_SECRET=<openssl rand -base64 64>
JWT_EXPIRATION=86400000

# Market data
FINNHUB_API_KEY=...
NEWS_API_KEY=...
GROQ_API_KEY=...
ALPHA_VANTAGE_KEY=
GEMINI_API_KEY=
YOUTUBE_API_KEY=

# CORS — replace with your real Vercel URL once frontend is deployed
CORS_ALLOWED_ORIGINS=https://your-app.vercel.app

# Optional
SENTRY_DSN=
SWAGGER_ENABLED=false
FINPLAY_STARTING_BALANCE=100000
```

> **Important:** Render injects `PORT` automatically. Set `SERVER_PORT=$PORT` if Render's UI lets you reference env vars; otherwise hardcode the port Render assigns (typically `10000`). The Dockerfile already respects `SERVER_PORT`.

### One-time tweak for Upstash TLS

Add to `application-prod.properties` (already prepped — if the env var is unset Spring leaves SSL off, which is what local Docker wants):

```properties
spring.data.redis.password=${SPRING_DATA_REDIS_PASSWORD:}
spring.data.redis.ssl.enabled=${SPRING_DATA_REDIS_SSL_ENABLED:false}
```

### Verify

After Render finishes the first build (~6 min):

```bash
curl https://<render-name>.onrender.com/actuator/health
# → {"status":"UP", "components":{"db":{"status":"UP"},"redis":{"status":"UP"},...}}
```

---

## 4 · Deploy frontend to Vercel

1. https://vercel.com/new → import the repo.
2. **Root directory:** `frontend`
3. **Framework preset:** Vite — auto-detected.
4. Build command: `npm run build` · output: `dist`
5. Environment variables:

   ```
   VITE_API_URL = https://<render-name>.onrender.com
   VITE_SENTRY_DSN = (optional)
   ```

6. Deploy. Note the production URL (`https://<your>.vercel.app`).

7. **Go back to Render** and update `CORS_ALLOWED_ORIGINS` with that exact URL.

---

## 5 · Smoke test the cloud stack

```bash
BASE_URL=https://<render-name>.onrender.com bash scripts/smoke.sh
```

If everything's green: you're live.

---

## Cost summary

| Service | Plan | Limit | Cost |
|---|---|---|---|
| Render web | Free | sleeps after 15 min idle | $0 |
| Neon Postgres | Free | 0.5 GB · 1 project | $0 |
| Upstash Redis | Free | 256 MB · 10k cmds/day | $0 |
| Vercel | Hobby | 100 GB bandwidth/mo | $0 |
| Sentry | Developer | 5k events/mo | $0 |

---

## Common production-only gotchas

| Symptom | Fix |
|---|---|
| First request after idle is 30s slow | Render free dyno cold start. Upgrade to Starter ($7/mo) for always-on. |
| Backend up but `/api/auth/login` 500s | Bcrypt-12 + cold dyno = >1s on free tier. Either lower bcrypt strength to 10 in prod, or upgrade Render. |
| Frontend gets CORS error | `CORS_ALLOWED_ORIGINS` doesn't match your Vercel URL exactly. No trailing slash. |
| `spring.data.redis` connection refused | Upstash needs SSL on. Set `SPRING_DATA_REDIS_SSL_ENABLED=true`. |
| `SSL connection required` from Postgres | Append `?sslmode=require` to `DB_URL`. |
