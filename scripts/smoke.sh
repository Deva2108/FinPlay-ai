#!/usr/bin/env bash
# FinPlay smoke test — runs every critical path against a local docker stack.
# Usage:    bash scripts/smoke.sh
# Windows:  Use Git Bash or WSL.
#
# Exits non-zero if any required assertion fails. Prints colored pass/fail.

set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
RED=$'\033[0;31m'; GRN=$'\033[0;32m'; YLW=$'\033[1;33m'; NC=$'\033[0m'
PASS=0; FAIL=0

assert_eq()  { if [ "$1" = "$2" ]; then echo "${GRN}✓${NC} $3"; PASS=$((PASS+1)); else echo "${RED}✗${NC} $3 (want=$2 got=$1)"; FAIL=$((FAIL+1)); fi; }
assert_in()  { if echo "$2" | grep -q "$1"; then echo "${GRN}✓${NC} $3"; PASS=$((PASS+1)); else echo "${RED}✗${NC} $3 (no '$1' in response)"; FAIL=$((FAIL+1)); fi; }

# ---- 0. health ----
HEALTH=$(curl -fsS "$BASE/actuator/health" || true)
assert_in '"status":"UP"' "$HEALTH" "actuator/health UP"

# ---- 1. register ----
TS=$(date +%s)
EMAIL="qa+${TS}@finplay.test"
REG=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"QA Bot\",\"email\":\"${EMAIL}\",\"password\":\"hunter2pass\"}")
assert_eq "$REG" "201" "POST /api/auth/register → 201"

# ---- login ----
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"hunter2pass\"}" \
  | python -c "import sys,json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
[ -n "$TOKEN" ] && echo "${GRN}✓${NC} login → token captured (len=${#TOKEN})" && PASS=$((PASS+1)) \
                || { echo "${RED}✗${NC} login failed"; FAIL=$((FAIL+1)); exit 1; }

AUTH="-H Authorization:Bearer\ $TOKEN"

# ---- 2. portfolio list ----
PFOLIO=$(curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE/api/portfolios")
PID=$(echo "$PFOLIO" | python -c "import sys,json; print(json.load(sys.stdin)['data'][0]['portfolioId'])" 2>/dev/null)
[ -n "$PID" ] && echo "${GRN}✓${NC} portfolioId=$PID" && PASS=$((PASS+1)) \
              || { echo "${RED}✗${NC} no portfolio created on register"; FAIL=$((FAIL+1)); exit 1; }

# ---- 3. BUY order (allow 200 OK or 503 if cache cold) ----
BUY=$(curl -s -o /tmp/buy.json -w "%{http_code}" -X POST "$BASE/api/trading/paper/orders" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"portfolioId\":${PID},\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1}")
if [ "$BUY" = "200" ] || [ "$BUY" = "503" ]; then
  echo "${GRN}✓${NC} BUY 1 AAPL → $BUY (200=filled, 503=cache cold)"; PASS=$((PASS+1))
else
  echo "${RED}✗${NC} BUY unexpected status $BUY"; cat /tmp/buy.json; FAIL=$((FAIL+1))
fi

# ---- 4. negative-quantity exploit (must fail with 400) ----
NEG=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/trading/paper/orders" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"portfolioId\":${PID},\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":-100}")
assert_eq "$NEG" "400" "negative quantity exploit blocked (400)"

# ---- 5. free-money endpoint (must fail with 403) ----
MONEY=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/portfolios/${PID}/balance" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"amount": 1000000}')
assert_eq "$MONEY" "403" "free-money endpoint blocked (403 admin-only)"

# ---- 6. reset (now admin-only — non-admin users must NOT be able to call it) ----
# This endpoint moved to /api/admin/users/{id}/reset and is gated by ROLE_ADMIN.
# The user-facing /api/portfolios/{id}/reset was removed entirely.
RESET=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/portfolios/${PID}/reset" \
  -H "Authorization: Bearer $TOKEN")
# 404 (path no longer exists) or 405 (method not allowed) both prove the endpoint is gone.
case "$RESET" in
  404|405) echo "${GRN}✓${NC} user-facing reset endpoint removed (got $RESET)"; PASS=$((PASS+1));;
  *)       echo "${RED}✗${NC} expected 404/405 for removed reset endpoint, got $RESET"; FAIL=$((FAIL+1));;
esac

# Non-admin users cannot hit /api/admin/* either — should be 403.
ADMIN_LOCKED=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/admin/users" \
  -H "Authorization: Bearer $TOKEN")
assert_eq "$ADMIN_LOCKED" "403" "non-admin → /api/admin/users blocked (403)"

# ---- 7. unauthenticated ----
NA=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/portfolios")
assert_eq "$NA" "401" "unauthenticated → 401"

# ---- 8. tampered token ----
BT=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer not.a.real.jwt" "$BASE/api/portfolios")
assert_eq "$BT" "401" "tampered token → 401"

echo
echo "------------------------------------------------------------"
echo "${YLW}Results:${NC} ${GRN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
[ "$FAIL" = "0" ] && exit 0 || exit 1
