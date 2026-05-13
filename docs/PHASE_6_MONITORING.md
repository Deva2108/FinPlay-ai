# Phase 6 — Free monitoring

Goal: errors visible within minutes, every log line traceable to an HTTP request, no paid SaaS.

## What's already wired up after Phases 0–5

- **`/actuator/health`** — Postgres + Redis health checked every 15s by Docker / Render's health probe.
- **Correlation IDs** — `RequestIdFilter` puts an 8-char `rid` into MDC on every request and echoes it as `X-Request-Id`. The prod logging pattern includes it: `[rid=ab12cd34]`.
- **Structured warnings** — `LoginRateLimitFilter` logs IP + path on every rate-limit hit; `JwtUtils` logs distinct messages for expired vs tampered vs malformed tokens; admin balance adjustments emit `log.warn`.

## Add Sentry (free tier)

1. Create a project at https://sentry.io → Java/Spring Boot → copy the DSN.
2. Add `SENTRY_DSN` to `.env` and to your Render env vars.
3. Add the dep to `pom.xml`:

   ```xml
   <dependency>
     <groupId>io.sentry</groupId>
     <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
     <version>7.14.0</version>
   </dependency>
   ```

4. Add to `application-prod.properties`:

   ```properties
   sentry.dsn=${SENTRY_DSN:}
   sentry.environment=${SENTRY_ENV:production}
   sentry.traces-sample-rate=0.1
   sentry.send-default-pii=false
   ```

   The `${SENTRY_DSN:}` form means: if no DSN is set, Sentry is silently disabled. So the variable is fully optional.

5. Add a `dist` tag from build version (optional). Done — every uncaught exception now reports.

## Frontend (Vercel) — Sentry browser SDK

```bash
cd frontend
npm install --save @sentry/react
```

In `frontend/src/main.jsx`:

```js
import * as Sentry from '@sentry/react';
if (import.meta.env.VITE_SENTRY_DSN) {
  Sentry.init({
    dsn: import.meta.env.VITE_SENTRY_DSN,
    environment: import.meta.env.MODE,
    tracesSampleRate: 0.1,
  });
}
```

Set `VITE_SENTRY_DSN` in Vercel env (per environment).

## Logs

- Render captures stdout for free. View at https://dashboard.render.com → service → Logs.
- Filter by `rid=…` to follow a single request across services.
- Set Render's log retention or stream to Logtail/Better Stack (free tier) if you need search.

## Optional: Uptime probe

Use https://betterstack.com/uptime free plan to ping `/actuator/health` every minute. PagerDuty-style alerts on free tier.
