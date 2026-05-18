// k6 — paper trading workload.
// Run:    k6 run load-tests/k6-trading.js
// Each VU: registers, then loops BUY → SELL on a small symbol set.
//
// Targets:
//   - p95 order latency < 800ms (incl. price fetch + DB write)
//   - error rate < 5% (503 'price syncing' tolerated under cold cache)
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SYMBOLS = ['AAPL', 'MSFT', 'GOOGL', 'NVDA', 'TSLA'];

const orderLatency = new Trend('order_latency_ms');
const orderSuccess = new Rate('order_success');

export const options = {
  scenarios: {
    sustained: {
      executor: 'constant-vus',
      vus: 25,
      duration: '90s',
    },
  },
  thresholds: {
    'http_req_failed':   ['rate<0.10'],   // tolerate 503 'syncing' bursts
    'order_latency_ms':  ['p(95)<800'],
    'order_success':     ['rate>0.85'],
  },
};

function register(vuId) {
  const email = `k6+t+${Date.now()}+${vuId}+${Math.floor(Math.random()*1e6)}@finplay.test`;
  const r = http.post(`${BASE}/api/auth/register`,
    JSON.stringify({ name: `k6 trader ${vuId}`, email, password: 'hunter2pass' }),
    { headers: { 'Content-Type': 'application/json' } });
  return r.status === 201 ? r.json('data.token') : null;
}

function getPid(token) {
  const r = http.get(`${BASE}/api/portfolios`, { headers: { Authorization: `Bearer ${token}` } });
  return r.status === 200 ? r.json('data.0.portfolioId') : null;
}

function placeOrder(token, pid, symbol, side, qty) {
  const t0 = Date.now();
  const r = http.post(`${BASE}/api/trading/paper/orders`,
    JSON.stringify({ portfolioId: pid, symbol, side, quantity: qty }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });
  orderLatency.add(Date.now() - t0);
  orderSuccess.add(r.status === 200);
  return r;
}

export default function () {
  const token = register(__VU);
  if (!token) { check(null, { 'register ok': () => false }); return; }
  const pid = getPid(token);
  if (!pid) { check(null, { 'pid ok': () => false }); return; }

  for (let i = 0; i < 5; i++) {
    const sym = SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)];
    group('BUY', () => {
      const r = placeOrder(token, pid, sym, 'BUY', 1);
      check(r, { 'buy 200/503': res => res.status === 200 || res.status === 503 });
    });
    sleep(0.5);
    group('SELL', () => {
      // SELL might 400 if BUY just 503'd — that's expected, count as success-ish
      const r = placeOrder(token, pid, sym, 'SELL', 1);
      check(r, { 'sell handled': res => [200, 400, 503].includes(res.status) });
    });
    sleep(0.5);
  }
}
