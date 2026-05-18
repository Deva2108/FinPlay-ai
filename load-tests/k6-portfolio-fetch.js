// k6 — portfolio/account read fan-out.
// Validates Redis caching keeps p95 < 200ms even at 100 concurrent readers.
// Run:  k6 run load-tests/k6-portfolio-fetch.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 100,
  duration: '60s',
  thresholds: {
    'http_req_failed':   ['rate<0.02'],
    'http_req_duration': ['p(95)<200', 'p(99)<400'],
  },
};

export function setup() {
  const ts = Date.now();
  const email = `k6+pf+${ts}@finplay.test`;
  http.post(`${BASE}/api/auth/register`,
    JSON.stringify({ name: 'pf', email, password: 'hunter2pass' }),
    { headers: { 'Content-Type': 'application/json' } });
  const r = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ email, password: 'hunter2pass' }),
    { headers: { 'Content-Type': 'application/json' } });
  const token = r.json('data.token');
  const pf = http.get(`${BASE}/api/portfolios`, { headers: { Authorization: `Bearer ${token}` } });
  const pid = pf.json('data.0.portfolioId');
  return { token, pid };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  const r1 = http.get(`${BASE}/api/trading/paper/account?portfolioId=${data.pid}`, { headers });
  check(r1, { 'account 200': r => r.status === 200 });
  const r2 = http.get(`${BASE}/api/trading/paper/positions?portfolioId=${data.pid}`, { headers });
  check(r2, { 'positions 200': r => r.status === 200 });
  sleep(0.3);
}
