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

// Persist the auth result (token + user) returned inside the API envelope.
// Private to this module — only login/register use it.
function persistAuth(result) {
  const token = result?.data?.token;
  const user  = result?.data?.user;
  if (token) localStorage.setItem('token', token);
  if (user)  localStorage.setItem('finplay_user', JSON.stringify(user));
}

export const registerUser = async (data) => {
  const response = await api.post(API_ENDPOINTS.AUTH.REGISTER, data);
  const result = readApiEnvelope(response);
  persistAuth(result);
  return result;
};

export const loginUser = async (data) => {
  localStorage.removeItem('token');
  localStorage.removeItem('finplay_user');
  const response = await api.post(API_ENDPOINTS.AUTH.LOGIN, data);
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
