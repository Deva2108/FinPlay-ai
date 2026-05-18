# Pre-Deployment Verification Checklist

## Code Changes Summary

### ✅ Backend Services - Implemented
All new deterministic services have been created and integrated:

**File: `src/main/java/com/example/stockPortfolio/AiManagement/service/CoreDataExtractor.java`**
- Extracts market data, news sentiment, portfolio exposure, volatility
- Uses cached data from MarketGateway, NewsApiService, HoldingService
- No external API calls

**File: `src/main/java/com/example/stockPortfolio/AiManagement/service/InsightPatternDetector.java`**
- 13 deterministic pattern detection rules
- Pattern enum with confidence scores
- Pure logic, no state management

**File: `src/main/java/com/example/stockPortfolio/AiManagement/service/InsightTextGenerator.java`**
- Generates 8-field RichInsightDTO for each pattern
- Custom text for each pattern type
- Includes: whatHappened, whyItMatters, globalImpact, indiaImpact, whatYouCanLearn, analogy, investorPerspective, action

**File: `src/main/java/com/example/stockPortfolio/AiManagement/service/DeterministicInsightService.java`**
- Orchestrates the 3-layer pipeline
- Implements caching with @Cacheable
- 30-minute TTL on insights
- Graceful fallback for errors

**File: `src/main/java/com/example/stockPortfolio/AiManagement/service/AiService.java`**
- Updated 13 methods to remove Groq dependencies:
  - generateRichInsight() ✅
  - getStructuredExplanation() ✅
  - generateMarketScenarios() ✅
  - generateVaultScenario() ✅
  - getArenaSummary() ✅
  - getExplanation() ✅
  - getOnboardingScenario() ✅
  - getOnboardingFeedback() ✅
  - getPortfolioMentorAdvice() ✅
  - getMentorExplanation() ✅
  - getMarketPulseInsights() ✅
  - getTutorialInsight() ✅
  - getBehavioralIdentity() ✅

**Verification:**
```
grep "groqGateway.generateContent" src/main/java/com/example/stockPortfolio/AiManagement/service/AiService.java
# Result: No matches found ✅
```

---

## Local Testing Steps

### 1. Verify Environment (.env)
```bash
cd /Users/devanshdubey/Stock-Portfolio-Monitoring-App
cat .env | grep -E "DB_|JWT_|FINNHUB|NEWS_API|GROQ"
```

Required variables:
- ✅ DB_USERNAME
- ✅ DB_PASSWORD
- ✅ JWT_SECRET
- ✅ JWT_EXPIRATION
- ✅ FINNHUB_API_KEY
- ✅ NEWS_API_KEY
- ✅ GROQ_API_KEY (optional after 1 week)

### 2. Build Docker Containers
```bash
docker-compose build --no-cache

# Expected output:
# Building finplay-backend... (using Dockerfile)
# Building finplay-frontend... (using frontend/Dockerfile)
# Success ✅
```

### 3. Start Services
```bash
docker-compose up -d

# Watch logs:
docker-compose logs -f backend

# Expected output:
# 2026-05-06 10:30:00,123 INFO  - Tomcat initialized with port(s): 8080 (http)
# 2026-05-06 10:30:05,456 INFO  - Started StockPortfolioApplication
# Server startup in X seconds
```

### 4. Verify Services Are Healthy
```bash
# Backend health check
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Database check
curl http://localhost:8080/actuator/health/db
# Expected: {"status":"UP","components":{"db":{"status":"UP"}}}

# Redis check
curl http://localhost:8080/actuator/health/redis
# Expected: {"status":"UP"}

# Full health report
curl http://localhost:8080/actuator/health/
# Expected: All components UP
```

### 5. Test Deterministic Insight Endpoint
```bash
# Test with a real stock symbol
curl -X GET "http://localhost:8080/api/stock/RELIANCE/insight" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Expected response:
# {
#   "explanation": "...",
#   "observation": "...",
#   "symbol": "RELIANCE",
#   "richInsight": {
#     "whatHappened": "...",
#     "whyItMatters": "...",
#     "globalImpact": "...",
#     "indiaImpact": "...",
#     "whatYouCanLearn": "...",
#     "analogy": "...",
#     "investorPerspective": "...",
#     "action": "...",
#     "confidence": 0.85-0.95
#   },
#   "source": "DETERMINISTIC_ENGINE"
# }

# Timing check: Should respond in <100ms
time curl -X GET "http://localhost:8080/api/stock/RELIANCE/insight" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" > /dev/null
```

### 6. Check Response Times
```bash
# Test caching behavior (first call loads data)
curl -w "Time: %{time_total}s\n" -X GET "http://localhost:8080/api/stock/TCS/insight" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Should be ~50-100ms first time

# Second call (cached)
curl -w "Time: %{time_total}s\n" -X GET "http://localhost:8080/api/stock/TCS/insight" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# Should be ~10-20ms (cache hit)
```

### 7. Frontend Verification
```bash
# Visit http://localhost:3000
# 1. Login with test account
# 2. Navigate to Dashboard
# 3. Check stock insights load quickly
# 4. Verify no errors in browser console
# 5. Test Vault educational content
# 6. Test Game mode insights
```

### 8. Database Verification
```bash
# Check if insights are being cached
docker exec postgres-db psql -U $DB_USERNAME -d stocksdb -c \
  "SELECT * FROM cache WHERE key LIKE '%insight%' LIMIT 5;"

# Check recent error logs
docker exec finplay-backend tail -50 /var/log/finplay.log
```

---

## Monitoring & Performance Metrics

### Key Metrics to Watch (First 24 Hours)

| Metric | Target | Alert Level |
|--------|--------|------------|
| Insight Response Time | <100ms | >500ms |
| Cache Hit Rate | >80% | <50% |
| Error Rate | <0.1% | >1% |
| Uptime | 100% | <99.5% |
| CPU Usage | <30% | >70% |
| Memory Usage | <500MB | >1GB |

### Log Checks

```bash
# Check for deterministic service logs
docker-compose logs backend | grep "DETERMINISTIC"

# Check for pattern detections
docker-compose logs backend | grep "Detected"

# Check for cache operations
docker-compose logs backend | grep "Cacheable"

# Check for errors
docker-compose logs backend | grep "ERROR"
```

---

## Troubleshooting

### Issue: Insight endpoint returns 500
```
Solution:
1. Check backend logs: docker-compose logs backend
2. Verify MarketGateway is returning cached data
3. Check if CoreDataExtractor can reach dependencies
4. Fallback should return STABLE pattern
```

### Issue: Insights are returning generic responses
```
Solution:
1. Verify data extraction is working: check logs for "✅ Extracted context"
2. Verify pattern detection rules are matching: check logs for "🎯 Detected"
3. Verify text generation is creating output
4. This is expected behavior if market data hasn't changed
```

### Issue: Response times are >1 second
```
Solution:
1. Check if cache is working: first call vs second call timing
2. Verify Redis is running: docker ps | grep redis
3. Check database query performance
4. Monitor MarketGateway response times
```

### Issue: "SYNCING" state returned
```
Solution:
1. This is normal fallback behavior
2. Market data is being fetched but not yet cached
3. Wait 1-2 minutes for scheduler to hydrate cache
4. Check MarketDataScheduler logs
```

---

## Deployment Steps (Production)

### Option 1: Railway Deployment

```bash
# 1. Ensure all code is committed
git add -A
git commit -m "feat: Deterministic Insight Engine - Groq removal"

# 2. Push to main branch
git push origin main

# 3. Railway auto-deploys on push
# Monitor at: https://railway.app/dashboard

# 4. Verify production health
curl https://YOUR_RAILWAY_DOMAIN/actuator/health

# 5. Run smoke tests against production
./scripts/smoke-tests.sh PROD
```

### Option 2: Manual Docker Deployment

```bash
# 1. Build and push to container registry
docker build -t finplay:v2.0.0 .
docker tag finplay:v2.0.0 YOUR_REGISTRY/finplay:v2.0.0
docker push YOUR_REGISTRY/finplay:v2.0.0

# 2. Update production deployment
kubectl set image deployment/finplay finplay=YOUR_REGISTRY/finplay:v2.0.0

# 3. Verify rollout
kubectl rollout status deployment/finplay

# 4. Monitor metrics
kubectl logs -f deployment/finplay
```

---

## Rollback Procedure (If Needed)

```bash
# Quick rollback to previous version
git revert HEAD
git push origin main

# Or manually revert AiService.java
git checkout HEAD~1 src/main/java/.../AiService.java
git commit -m "revert: Restore Groq for AiService"
git push origin main

# Redeploy
docker-compose up --build
```

---

## Sign-Off Checklist

Before marking as production-ready:

- [ ] All 13 AiService methods verified as Groq-free
- [ ] 4 new services created and integrated
- [ ] Docker compose builds successfully
- [ ] Health checks pass (DB, Redis, Backend)
- [ ] Insight endpoint returns <100ms
- [ ] Cache hit rate verified >80%
- [ ] Frontend loads insights without errors
- [ ] No console errors in browser
- [ ] All test accounts can access insights
- [ ] Educational content loads correctly
- [ ] Game mode scenarios work
- [ ] 24-hour monitoring completed
- [ ] Performance metrics within targets
- [ ] Zero error rate (<0.1%)

---

## Estimated Timeline

- Local testing: 20-30 minutes
- Production deployment: 5-10 minutes
- Post-deployment monitoring: 24 hours
- **Total: 1-2 hours to production + 24h observation**
