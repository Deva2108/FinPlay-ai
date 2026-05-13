# FinPlay — Launch Runbook

This is the index of everything produced during the production-readiness pass.
Everything below is repo-checked-in and OS-portable.

| Phase | What | Where |
|---|---|---|
| 0 | Docker-first setup | [`docs/PHASE_0_DOCKER.md`](PHASE_0_DOCKER.md) |
| 1+2 | Audit findings + critical fixes | summarized in chat; patches in `src/main/java/...` |
| 3 | Local Windows test flow | [`docs/PHASE_3_TESTING.md`](PHASE_3_TESTING.md) · `scripts/smoke.sh` · `scripts/smoke.ps1` |
| 4 | k6 load tests | [`load-tests/`](../load-tests/) |
| 5 | Light security hardening | rate limit filter + headers in `src/main/java/.../Security/` |
| 6 | Free monitoring | [`docs/PHASE_6_MONITORING.md`](PHASE_6_MONITORING.md) |
| 7 | Cloud deployment | [`docs/PHASE_7_DEPLOY.md`](PHASE_7_DEPLOY.md) |
| 8 | Tests (unit + integration) | `src/test/java/...` |

## Day-1 launch checklist

1. ✅ `cp .env.example .env` and fill in real keys.
2. ✅ `docker compose up --build` — wait for all four services to be healthy.
3. ✅ `bash scripts/smoke.sh` — every assertion green.
4. ✅ `mvn test` — every test green.
5. ✅ `k6 run load-tests/k6-trading.js` — error rate < 5%, p95 < 800ms.
6. ✅ Provision Neon + Upstash, copy URLs into Render env vars.
7. ✅ Deploy backend to Render, frontend to Vercel.
8. ✅ Re-run `BASE_URL=<render-url> bash scripts/smoke.sh` against prod.
9. ✅ Add Sentry DSN to backend (Render) and frontend (Vercel).

## What was fixed (high level)

- 🟥 Money-printing exploit via negative quantity (C1) — patched at controller and service.
- 🟥 Open `/api/portfolios/{id}/balance` endpoint (C2) — now admin-only, plus a safe user-facing `/reset`.
- 🟥 Zero-quantity bypass (C3) — patched.
- 🟧 Trading errors masked as HTTP 200 (H1) — controller try/catch removed; `GlobalExceptionHandler` does the job.
- 🟧 Silent JWT parse failures (H4) — distinct logs for expired/tampered/malformed.
- 🟧 JWT secret silent crash (H5) — startup-time validity check with helpful error message.
- 🟧 Prod profile actually exists (`application-prod.properties`).
- 🟧 H2 fallback can no longer engage in prod (`StockPortfolioApplication`).
- 🟧 Per-IP login rate limit (Phase 5).
- 🟧 Bcrypt cost bumped to 12, security headers added.
- 🟦 Request correlation IDs in every log line.
