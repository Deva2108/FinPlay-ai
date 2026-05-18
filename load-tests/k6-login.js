// k6 — login throughput.
// Run:    k6 run load-tests/k6-login.js
// Output: target ≥ 50 logins/s with p95 < 500ms.
//
// Generates one fresh user per VU at setup, then logs in repeatedly.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const loginTrend = new Trend('login_duration_ms');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_failed':   ['rate<0.02'],
    'http_req_duration': ['p(95)<500'],
    'login_duration_ms': ['p(95)<500'],
  },
};

// One shared user pool across VUs — registered upfront in setup().
export function setup() {
  const ts = Date.now();
  const users = [];
  for (let i = 0; i < 50; i++) {
    const email = `k6+${ts}+${i}@finplay.test`;
    const r = http.post(
      `${BASE}/api/auth/register`,
      JSON.stringify({ name: `k6 user ${i}`, email, password: 'hunter2pass' }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    if (r.status === 201) users.push({ email, password: 'hunter2pass' });
  }
  return { users };
}

export default function (data) {
  const u = data.users[Math.floor(Math.random() * data.users.length)];
  const t0 = Date.now();
  const r = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ email: u.email, password: u.password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  loginTrend.add(Date.now() - t0);
  check(r, {
    'login 200':       res => res.status === 200,
    'token returned':  res => !!(res.json('data.token')),
  });
  sleep(0.2);
}
