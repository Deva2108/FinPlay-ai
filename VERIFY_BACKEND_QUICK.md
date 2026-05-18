# ⚡ Quick Backend Verification (5 minutes)

**Goal:** Verify backend is working before deploying deterministic engine

---

## 🚀 RUN THIS (In Order)

### Step 1: Check All Controllers Exist
```bash
cd /Users/devanshdubey/Stock-Portfolio-Monitoring-App

# Should show 16 controllers
find src/main/java -name "*Controller.java" | wc -l

# Expected output: 16
```

**Status:** ✅ or ❌?

---

### Step 2: Check Key Services Exist
```bash
# Should show all major services
find src/main/java -name "*Service.java" | wc -l

# Expected output: 25+
```

**Status:** ✅ or ❌?

---

### Step 3: Check Configuration Files
```bash
# Should show both properties files
ls -la src/main/resources/application*.properties

# Expected output:
# application.properties
# application-h2.properties
```

**Status:** ✅ or ❌?

---

### Step 4: Check Docker Setup
```bash
# Should show both files
ls -la | grep -E "Dockerfile|docker-compose"

# Expected output:
# Dockerfile
# docker-compose.yml
```

**Status:** ✅ or ❌?

---

### Step 5: Check .env File
```bash
# Should show .env exists and has content
wc -l .env
grep "FINNHUB\|GROQ\|JWT_SECRET" .env | wc -l

# Expected output: 3+ matches
```

**Status:** ✅ or ❌?

---

### Step 6: Start Docker Stack (Takes 2-3 min)
```bash
# Stop any existing
docker-compose down

# Start fresh
docker-compose up --build

# Wait for messages:
# "postgres-db | ready to accept connections"
# "redis-cache | Ready to accept connections"  
# "finplay-backend | Started StockPortfolioApplication"

# KEEP THIS RUNNING IN ANOTHER TERMINAL
```

**Status:** ✅ or ❌?

---

### Step 7: Test Health Endpoint (In New Terminal)
```bash
# While docker-compose is still running
curl http://localhost:8080/actuator/health

# Expected output:
# {"status":"UP"}
```

**Status:** ✅ or ❌?

---

### Step 8: Test Swagger UI
```bash
# Open in browser
http://localhost:8080/swagger-ui.html

# Should see:
# - All controllers listed
# - All endpoints documented
# - No errors
```

**Status:** ✅ or ❌?

---

### Step 9: Test a Real Endpoint
```bash
# Try the market quote endpoint
curl "http://localhost:8080/api/market/quote/RELIANCE" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# OR just test a public endpoint
curl http://localhost:8080/actuator/info

# Expected: 200 OK
```

**Status:** ✅ or ❌?

---

### Step 10: Check Logs for Errors
```bash
# In original docker-compose terminal, press Ctrl+C to see logs summary

# OR check logs in new terminal
docker-compose logs backend | tail -50

# Look for:
# ✅ No ERROR messages
# ✅ No EXCEPTION messages
# ✅ Started StockPortfolioApplication
# ✅ Scheduling tasks started
# ✅ Redis connected
```

**Status:** ✅ or ❌?

---

## 🎯 Quick Checklist

- [ ] 16 controllers found
- [ ] 25+ services found
- [ ] Both properties files exist
- [ ] Docker files present
- [ ] .env populated
- [ ] Docker stack starts
- [ ] Health endpoint returns UP
- [ ] Swagger UI accessible
- [ ] No errors in logs
- [ ] Redis connected

---

## ✅ If All ✅

**Your backend is READY!**

Next: Deploy deterministic engine

---

## ❌ If Any ❌

**Check the issue:**

### Problem: Controller/Service not found
- **Solution:** File may be missing. Check CODEBASE_VALIDATION_REPORT.md

### Problem: Docker won't start
- **Solution:** Check Docker running. Try `docker-compose down --volumes`

### Problem: Health endpoint fails
- **Solution:** Check backend logs: `docker-compose logs backend`

### Problem: Redis not connected
- **Solution:** Wait longer or check Redis is running: `docker ps`

### Problem: Swagger not accessible
- **Solution:** Backend may not be fully started. Wait 30 more seconds.

---

## 🚀 Summary

**If all checks pass:**

Your backend is SOLID.

Ready to deploy deterministic engine?

**YES** → Go to IMPLEMENTATION_CHECKLIST.md  
**NO** → Debug the failed check first

---

