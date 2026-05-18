# FinPlay smoke test — PowerShell variant for Windows users without WSL/Git Bash.
# Usage:  pwsh -File scripts/smoke.ps1
#   or:   powershell -ExecutionPolicy Bypass -File scripts/smoke.ps1

$ErrorActionPreference = "Stop"
$Base = if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:8080" }
$pass = 0; $fail = 0

function Pass($msg) { Write-Host "[OK]  $msg" -ForegroundColor Green; $script:pass++ }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red;   $script:fail++ }

# 0. health
try {
  $h = Invoke-RestMethod "$Base/actuator/health"
  if ($h.status -eq "UP") { Pass "actuator/health UP" } else { Fail "health not UP: $($h.status)" }
} catch { Fail "health request error: $_" }

# 1. register
$ts    = [int][double]::Parse((Get-Date -UFormat %s))
$email = "qa+${ts}@finplay.test"
try {
  $reg = Invoke-WebRequest -Method POST "$Base/api/auth/register" `
    -ContentType "application/json" -Body (@{name="QA Bot"; email=$email; password="hunter2pass"} | ConvertTo-Json)
  if ($reg.StatusCode -eq 201) { Pass "register → 201" } else { Fail "register status $($reg.StatusCode)" }
} catch { Fail "register error: $_" }

# login
$login = Invoke-RestMethod -Method POST "$Base/api/auth/login" `
  -ContentType "application/json" -Body (@{email=$email; password="hunter2pass"} | ConvertTo-Json)
$token = $login.data.token
if ($token) { Pass "login → token captured (len=$($token.Length))" } else { Fail "no token"; exit 1 }

$headers = @{ Authorization = "Bearer $token" }

# 2. portfolio list
$pf = Invoke-RestMethod "$Base/api/portfolios" -Headers $headers
$pid = $pf.data[0].portfolioId
if ($pid) { Pass "portfolioId=$pid" } else { Fail "no auto-created portfolio"; exit 1 }

# 3. BUY
$body = @{ portfolioId=$pid; symbol="AAPL"; side="BUY"; quantity=1 } | ConvertTo-Json
try {
  $r = Invoke-WebRequest -Method POST "$Base/api/trading/paper/orders" -Headers $headers -ContentType "application/json" -Body $body
  if ($r.StatusCode -in 200,503) { Pass "BUY 1 AAPL → $($r.StatusCode)" } else { Fail "BUY status $($r.StatusCode)" }
} catch { if ($_.Exception.Response.StatusCode.value__ -in 200,503) { Pass "BUY → $($_.Exception.Response.StatusCode.value__)" } else { Fail "BUY error: $_" } }

# 4. negative qty (must 400)
$bad = @{ portfolioId=$pid; symbol="AAPL"; side="BUY"; quantity=-100 } | ConvertTo-Json
try {
  Invoke-WebRequest -Method POST "$Base/api/trading/paper/orders" -Headers $headers -ContentType "application/json" -Body $bad | Out-Null
  Fail "negative qty NOT blocked"
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 400) { Pass "negative qty exploit blocked (400)" } else { Fail "negative qty got $code, want 400" }
}

# 5. free-money endpoint (must 403)
$bal = @{ amount = 1000000 } | ConvertTo-Json
try {
  Invoke-WebRequest -Method POST "$Base/api/portfolios/$pid/balance" -Headers $headers -ContentType "application/json" -Body $bal | Out-Null
  Fail "free-money endpoint NOT blocked"
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 403) { Pass "free-money blocked (403 admin-only)" } else { Fail "free-money got $code, want 403" }
}

# 6. reset (now admin-only — non-admin users must NOT be able to call it)
try {
  Invoke-WebRequest -Method POST "$Base/api/portfolios/$pid/reset" -Headers $headers | Out-Null
  Fail "user-facing reset should be removed but returned 2xx"
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 404 -or $code -eq 405) { Pass "user-facing reset endpoint removed ($code)" }
  else { Fail "expected 404/405 for removed reset endpoint, got $code" }
}

# 6b. /api/admin/* must reject non-admins with 403
try {
  Invoke-WebRequest "$Base/api/admin/users" -Headers $headers | Out-Null
  Fail "non-admin reached /api/admin/users (got 2xx)"
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 403) { Pass "non-admin → /api/admin/users blocked (403)" }
  else { Fail "expected 403, got $code" }
}

# 7. no token
try {
  Invoke-WebRequest "$Base/api/portfolios" | Out-Null
  Fail "no-token NOT blocked"
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 401) { Pass "no-token → 401" } else { Fail "no-token got $code" }
}

# 8. bad token
try {
  Invoke-WebRequest "$Base/api/portfolios" -Headers @{Authorization="Bearer not.a.real.jwt"} | Out-Null
  Fail "bad-token NOT blocked"
} catch {
  $code = $_.Exception.Response.StatusCode.value__
  if ($code -eq 401) { Pass "bad-token → 401" } else { Fail "bad-token got $code" }
}

Write-Host ""
Write-Host "------------------------------------------------------------"
Write-Host "Results: $pass passed, $fail failed" -ForegroundColor Yellow
if ($fail -gt 0) { exit 1 }
