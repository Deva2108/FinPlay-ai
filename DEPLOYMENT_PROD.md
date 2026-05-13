# 🚀 FinPlay Production Deployment Guide

This guide details the steps to deploy the **FinPlay** full-stack application to production using **Render** (Backend), **Vercel** (Frontend), **Neon** (Postgres), and **Upstash** (Redis).

---

## 1. Database Setup (Neon)

1. Sign up at [Neon.tech](https://neon.tech/).
2. Create a new project named `finplay-db`.
3. Copy the **Connection String** (it should look like `postgresql://user:password@host/neondb?sslmode=require`).

---

## 2. Cache Setup (Upstash)

1. Sign up at [Upstash.com](https://upstash.com/).
2. Create a **Redis** database named `finplay-cache`.
3. Scroll down to the **Configuration** section and copy:
   - **Endpoint** (This is your `REDIS_HOST`)
   - **Port** (This is your `REDIS_PORT`)
   - **Password** (This is your `REDIS_PASSWORD`)
4. Ensure **SSL/TLS** is enabled (Upstash provides this by default).

---

## 3. External API Keys

You will need the following API keys for full functionality:
- **Groq API**: [console.groq.com](https://console.groq.com/) (Primary AI Mentor)
- **Finnhub**: [finnhub.io](https://finnhub.io/) (Real-time price fallback)
- **Twelve Data**: [twelvedata.com](https://twelvedata.com/) (Global Market Quotes)
- **NewsAPI**: [newsapi.org](https://newsapi.org/) (Market News)

---

## 4. Backend Deployment (Render)

1. Log in to [Render.com](https://render.com/).
2. Click **New +** > **Web Service**.
3. Connect your GitHub repository.
4. Configure the service:
   - **Name**: `finplay-backend`
   - **Runtime**: `Docker` (Render will automatically use the root `Dockerfile`)
   - **Plan**: `Starter` (or higher if needed for 512MB+ RAM)

5. **Environment Variables**: Add the following in the Render Dashboard:

| Key | Value |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | *Your Neon Connection String (prepended with `jdbc:`) |
| `DB_USERNAME` | *Your Neon Username* |
| `DB_PASSWORD` | *Your Neon Password* |
| `REDIS_HOST` | *Your Upstash Endpoint* |
| `REDIS_PORT` | `6379` (usually) |
| `SPRING_DATA_REDIS_PASSWORD` | *Your Upstash Password* |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `true` |
| `JWT_SECRET` | *Run `openssl rand -base64 64` to generate one* |
| `GROQ_API_KEY` | *Your Groq Key* |
| `FINNHUB_API_KEY` | *Your Finnhub Key* |
| `TWELVEDATA_API_KEY` | *Your Twelve Data Key* |
| `NEWS_API_KEY` | *Your NewsAPI Key* |
| `ADMIN_EMAILS` | *Your email (e.g., `you@example.com`) to grant admin rights* |
| `CORS_ALLOWED_ORIGINS` | `https://your-app.vercel.app` (Your Vercel URL) |

*Note: For Neon, ensure the `DB_URL` starts with `jdbc:postgresql://`.*

---

## 5. Frontend Deployment (Vercel)

1. Log in to [Vercel.com](https://vercel.com/).
2. Click **Add New** > **Project**.
3. Import your GitHub repository.
4. Configure the project:
   - **Framework Preset**: `Vite`
   - **Root Directory**: `frontend`
5. **Environment Variables**: Add the following:

| Key | Value |
| :--- | :--- |
| `VITE_API_URL` | `https://finplay-backend.onrender.com` (Your Render URL) |

6. Click **Deploy**.

---

## 6. Post-Deployment Verification

1. **Check Backend Health**: Visit `https://your-backend.onrender.com/actuator/health`.
   - Ensure `"db": "UP"` and `"redis": "UP"`.
2. **Verify Frontend**: Open your Vercel URL.
   - Try to **Register** and **Login**.
   - Check if stock prices are loading in the **Market** section.
   - If you see "SYNCING", wait 1-2 minutes for the background scheduler to hydrate the cache.

---

## Security & Maintenance
- **API Limits**: Twelve Data free tier is 8 calls/min. The app handles this with a batch scheduler and caching.
- **Admin Access**: Only the emails listed in `ADMIN_EMAILS` can access the `/admin` features.
- **Logs**: Monitor Render logs for any `Internal Server Error` or `Connection Timeout`.
