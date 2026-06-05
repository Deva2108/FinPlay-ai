// ─── Auth service ────────────────────────────────────────────────────────────
// Auth-only responsibilities, extracted from api.js so the networking layer
// (axios instance, interceptors, dedupe, timeout, cold-start wake) stays free of
// session/token concerns. Behavior is byte-for-byte identical to the previous
// implementation that lived in api.js — same endpoints, same localStorage keys
// ('token', 'finplay_user'), same envelope unwrap, same swrClear() on logout.
//
// Dependency direction is one-way: auth.js → api.js. The networking layer never
// imports from here, so there is no import cycle.
import { api, API_ENDPOINTS, readApiEnvelope, swrClear } from './api';

// Per-request policy for the two non-idempotent auth POSTs.
//   _noRetry  — a retry could create a DUPLICATE account or a second login
//               round-trip, so these never auto-retry on a transient/timeout.
//   _noDedupe — never coalesced with any in-flight request (POSTs aren't
//               deduped anyway; set explicitly so intent is unambiguous).
//   timeout   — 30s (not the global 15s) so a Render cold start completes within
//               a SINGLE request, no retry needed. Market/sync keep the 15s
//               default. Global wake support stays intact.
const AUTH_REQUEST = { timeout: 30000, _noRetry: true, _noDedupe: true };

// Persist the auth result (token + user) returned inside the API envelope.
// Private to this module — only login/register use it.
function persistAuth(result) {
  const token = result?.data?.token;
  const user  = result?.data?.user;
  if (token) localStorage.setItem('token', token);
  if (user)  localStorage.setItem('finplay_user', JSON.stringify(user));
}

export const registerUser = async (data) => {
  const response = await api.post(API_ENDPOINTS.AUTH.REGISTER, data, AUTH_REQUEST);
  const result = readApiEnvelope(response);
  persistAuth(result);
  return result;
};

export const loginUser = async (data) => {
  localStorage.removeItem('token');
  localStorage.removeItem('finplay_user');
  const response = await api.post(API_ENDPOINTS.AUTH.LOGIN, data, AUTH_REQUEST);
  const result = readApiEnvelope(response);
  persistAuth(result);
  return result;
};

export const logoutUser = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('finplay_user');
  // Drop the per-session SWR cache so the next user on this browser starts
  // cold instead of inheriting this user's cached portfolio/market data.
  swrClear();
};

// Read the persisted user object ({ userId, name, email, admin, experiencePoints })
// written by persistAuth on login/register. Returns null when absent or corrupt —
// callers must tolerate null (e.g. a returning user on a fresh browser who has a
// token but no cached user object yet). Never throws.
export const getStoredUser = () => {
  try {
    const raw = localStorage.getItem('finplay_user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

// Decode a JWT's `exp` (seconds since epoch) WITHOUT a library or signature check.
// This is a client-side freshness hint only — the backend remains the source of
// truth for validity (it verifies the signature on every request). Returns null
// if the token is malformed or carries no numeric `exp`.
const decodeJwtExp = (token) => {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    // base64url → base64, then restore any stripped '=' padding for atob.
    const b64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const claims = JSON.parse(atob(padded));
    return typeof claims.exp === 'number' ? claims.exp : null;
  } catch {
    return null;
  }
};

// Route-guard predicate: is there a session we should treat as logged in?
//
// Stricter than the old raw-presence check (which let an expired or garbage
// token render the authenticated shell for ~350ms before a 401 bounced the user
// — the "appear logged in unexpectedly" / "incognito reaches shell" flash).
//
// Deliberately FAIL-OPEN when `exp` is unreadable: a token we can't introspect
// falls back to the previous presence behaviour, so we never lock out a valid
// session we simply couldn't parse. We only reject when we can POSITIVELY prove
// expiry. A 60s skew margin avoids bouncing a token that's about to refresh.
export const hasValidSession = () => {
  const token = localStorage.getItem('token');
  if (!token) return false;
  const exp = decodeJwtExp(token);
  if (exp == null) return true; // unreadable → defer to backend, treat as present
  return exp * 1000 > Date.now() - 60_000;
};
