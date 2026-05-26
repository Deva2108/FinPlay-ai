import axios from 'axios';

// Backend Base URL from env or fallback
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

if (!import.meta.env.VITE_API_URL) {
  console.warn("VITE_API_URL is not defined. Falling back to http://localhost:8080");
}

// ─── Session-storage SWR cache ──────────────────────────────────────────────
// Zero-dependency micro-cache. Stores { data, ts } in sessionStorage keyed by
// `fp_swr_<key>`. Data is served instantly on next mount; the network call runs
// in the background and overwrites if fresher. On sessionStorage write failure
// (private browsing, full quota) we silently skip — the app still works.
const SWR_NS = 'fp_swr_';
export const swrRead = (key) => {
  try {
    const raw = sessionStorage.getItem(SWR_NS + key);
    if (!raw) return null;
    return JSON.parse(raw).data ?? null;
  } catch { return null; }
};
export const swrWrite = (key, data) => {
  try { sessionStorage.setItem(SWR_NS + key, JSON.stringify({ data, ts: Date.now() })); } catch {}
};
export const swrAge = (key) => {
  try {
    const raw = sessionStorage.getItem(SWR_NS + key);
    if (!raw) return null;
    const ts = JSON.parse(raw).ts;
    return ts ? Math.floor((Date.now() - ts) / 1000) : null;
  } catch { return null; }
};

export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: '/api/auth/login',
    REGISTER: '/api/auth/register',
  },
  USER: {
    PROFILE: (email) => `/api/user/profile/${email}`,
  },
  PORTFOLIO: {
    BASE: '/api/portfolios',
    BALANCE: (id) => `/api/portfolios/${id}/balance`,
    MENTOR: (id) => `/api/portfolios/${id}/mentor`,
    GROWTH: (id) => `/api/portfolios/${id}/growth`,
  },
  HOLDINGS: {
    BASE: '/api/holdings',
    TRANSACTIONS: '/api/holdings/transactions',
    DELETE: (id) => `/api/holdings/${id}`,
  },
  MARKET: {
    QUOTES: '/api/market/quotes',
    QUOTE: '/api/market/quote',
    NEWS: '/api/market/news',
    DETAILS: '/api/market/details',
    SEARCH: '/api/market/search',
    CHART: '/api/market/chart',
    INDICES: '/api/market/indices',
    INDEX_INSIGHT: '/api/market/index-insight',
    VIBE: '/api/market/vibe',
    PULSE: '/api/market/pulse',
    FAMOUS: '/api/market/insights/famous',
    GAINERS: '/api/market/gainers',
    LOSERS: '/api/market/losers',
    TRENDING: '/api/market/trending',
    SECTOR: '/api/market/sector',
  },
  DECISION: {
    BASE: '/api/decision',
    EVALUATE: '/api/decision/evaluate',
    STATS: '/api/decision/stats',
    INSIGHTS: '/api/decision/insights',
    ARCHETYPE: '/api/decision/archetype',
  },
  AI: {
    ONBOARDING: {
      SCENARIO: '/api/ai/onboarding/scenario',
      SCENARIOS: '/api/ai/onboarding/scenarios',
      FEEDBACK: '/api/ai/onboarding/feedback',
      SUMMARY: '/api/ai/onboarding/summary',
    },
    EXPLAIN: '/api/explain',
    TUTORIAL: '/api/tutorial/insight',
  },
  WATCHLIST: {
    BASE: '/api/watchlist',
    CHECK: (symbol) => `/api/watchlist/check/${symbol}`,
    TOGGLE: (symbol) => `/api/watchlist/${symbol}`,
  },
  VAULT: {
    DAILY: '/api/vault/daily',
    CARDS: '/api/vault/cards',
  }
};

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 45000,
});

// ─── Inflight GET deduper ───────────────────────────────────────────────────
// Multiple widgets mounting at once frequently fire identical GETs (e.g. three
// places asking for /api/market/vibe?marketType=INDIA in the same tick). This
// tiny map coalesces them into a single network call: the first request
// registers a promise; subsequent identical requests reuse it. Entries are
// removed in finally() so a failed request doesn't poison the cache.
//
// Scope: GET only (POST/PUT/DELETE/PATCH carry side-effects and must not be
// deduped). Key includes URL + sorted params + Authorization header so two
// users on the same browser can't share each other's responses.
const inflightGets = new Map();

const stableParamsKey = (params) => {
  if (!params || typeof params !== 'object') return '';
  const keys = Object.keys(params).sort();
  const parts = [];
  for (const k of keys) {
    const v = params[k];
    if (v === undefined) continue;
    parts.push(`${k}=${typeof v === 'object' ? JSON.stringify(v) : String(v)}`);
  }
  return parts.join('&');
};

const inflightKey = (config) => {
  const auth = config.headers?.Authorization || config.headers?.authorization || '';
  // Use last 12 chars of token only (avoids holding full JWT in a string key longer than needed).
  const authTail = typeof auth === 'string' && auth.length > 12 ? auth.slice(-12) : auth;
  return `GET ${config.baseURL || ''}${config.url}?${stableParamsKey(config.params)}|${authTail}`;
};

// Capture whatever default adapter axios shipped with (xhr in the browser).
// Works across axios v0.x and v1.x without relying on the v1-only getAdapter().
const __baseAdapter = api.defaults.adapter || axios.defaults.adapter;

api.defaults.adapter = async (config) => {
  if ((config.method || 'get').toLowerCase() !== 'get') return __baseAdapter(config);
  if (config._noDedupe) return __baseAdapter(config);

  const key = inflightKey(config);
  const existing = inflightGets.get(key);
  if (existing) return existing;

  const p = __baseAdapter(config).finally(() => {
    // Remove in next microtask so any sync .then() handlers can chain without
    // a fresh refire stomping on the same key.
    queueMicrotask(() => inflightGets.delete(key));
  });
  inflightGets.set(key, p);
  return p;
};

// Interceptor for JWT
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor for Expiration/Auth Errors + transient retry
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config;

    // Retry once on network/timeout failures (covers Render cold-start hangs)
    const isTransient = !error.response &&
      (error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK' || error.message === 'Network Error');

    if (isTransient && !config._retried) {
      config._retried = true;
      await new Promise(resolve => setTimeout(resolve, 1000));
      return api(config);
    }

    if (error.response && error.response.status === 401) {
      const isAuthPage = window.location.pathname === '/login' || window.location.pathname === '/register';
      if (!isAuthPage) {
        localStorage.removeItem('token');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Standard unwrap for the new ApiResponse structure:
 * { success: true, data: T, message: "" }
 * Returns ONLY the 'data' field to maintain backward compatibility with components.
 */
export const unwrapApiResponse = (response) => {
  const res = response?.data;
  if (!res) return { error: true, message: "Network Error" };

  if (res.success) return res.data;
  return { error: true, message: res.message || "Request failed" };
};

/**
 * Returns the FULL envelope {success, data, meta} for components that need to check meta.status
 */
export const unwrapEnvelope = (response) => {
  const res = response?.data;
  if (!res) return { success: false, error: true, message: "Network Error" };

  return {
    success: res.success,
    data: res.data,
    meta: res.meta,
    message: res.message
  };
};

export const readApiEnvelope = unwrapEnvelope;

// Auth APIs
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
};

// User APIs
export const updateProfile = async (email, data) => {
  const response = await api.put(API_ENDPOINTS.USER.PROFILE(email), data);
  return readApiEnvelope(response);
};

// Trading & Portfolio APIs
export const getPortfolio = async () => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.BASE);
  return readApiEnvelope(response);
};

export const getUserPortfolios = getPortfolio;

/**
 * Critical-path hydration. Pass {light: true} (default) to skip behavior insights
 * on the blocking call — the caller should fire `getUserInsights()` separately
 * after first paint.
 */
export const syncAll = async ({ light = true } = {}) => {
  const response = await api.get(`${API_ENDPOINTS.PORTFOLIO.BASE}/sync`, { params: light ? { light: true } : undefined });
  return readApiEnvelope(response);
};

export const getPortfolioMentorAdvice = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.MENTOR(portfolioId));
  return readApiEnvelope(response);
};

/**
 * Daily portfolio value series (cash + holdings) for the growth chart.
 * Returns data points oldest-first: [{ date, value, cash, holdings }, ...].
 * One entry per trading day captured at market close by the backend scheduler.
 * @param {number} portfolioId
 * @param {number} [days=90] window in calendar days (max 365)
 */
export const getPortfolioGrowth = async (portfolioId, days = 90) => {
  const response = await api.get(API_ENDPOINTS.PORTFOLIO.GROWTH(portfolioId), { params: { days } });
  return readApiEnvelope(response);
};

export const createPortfolio = async (name) => {
  const response = await api.post(API_ENDPOINTS.PORTFOLIO.BASE, { portfolioName: name });
  return readApiEnvelope(response);
};

export const updatePortfolioBalance = async (portfolioId, amount) => {
  const response = await api.post(API_ENDPOINTS.PORTFOLIO.BALANCE(portfolioId), { amount });
  return readApiEnvelope(response);
};

// User-facing /api/portfolios/{id}/reset was removed (now admin-only at
// /api/admin/users/{id}/reset). Keeping the export intentionally absent so any
// stale UI call surfaces as a build error rather than a silent 404.

// Holdings APIs
export const getHoldings = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.HOLDINGS.BASE, { params: { portfolioId } });
  return readApiEnvelope(response);
};

export const executeTrade = async (tradeData) => {
  const response = await api.post(API_ENDPOINTS.HOLDINGS.BASE, tradeData);
  return readApiEnvelope(response);
};

export const getTransactions = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.HOLDINGS.TRANSACTIONS, { params: { portfolioId } });
  return readApiEnvelope(response);
};

// Market Data APIs
export const getLiveQuote = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.QUOTE, { params: { symbol } });
  return readApiEnvelope(response);
};

export const getLiveQuotes = async (symbols) => {
  if (!Array.isArray(symbols) || symbols.length === 0) return { success: true, data: [], meta: { status: 'OK' } };
  const response = await api.get(API_ENDPOINTS.MARKET.QUOTES, { params: { symbols: symbols.join(',') } });
  return readApiEnvelope(response);
};

export const getQuotes = (symbols) => getLiveQuotes(symbols);

export const getNews = async (query = 'stock market') => {
  const response = await api.get(API_ENDPOINTS.MARKET.NEWS, { params: { query } });
  return readApiEnvelope(response);
};

export const getStockDetails = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.DETAILS, { params: { symbol } });
  return readApiEnvelope(response);
};

export const getChartData = async (symbol, timeframe = '1M') => {
  const response = await api.get(API_ENDPOINTS.MARKET.CHART, { params: { symbol, timeframe } });
  return readApiEnvelope(response);
};

export const getIndices = async (marketType = 'US') => {
  const response = await api.get(API_ENDPOINTS.MARKET.INDICES, { params: { marketType } });
  return readApiEnvelope(response);
};

export const getIndexInsight = async (symbol, value, change, marketType) => {
  const response = await api.get(API_ENDPOINTS.MARKET.INDEX_INSIGHT, { params: { symbol, value, change, marketType } });
  return readApiEnvelope(response);
};

export const getMarketVibe = async (marketType) => {
  const response = await api.get(API_ENDPOINTS.MARKET.VIBE, { params: { marketType } });
  return readApiEnvelope(response);
};

export const getMarketVibeResponse = getMarketVibe;

export const getMarketPulse = async (portfolioId) => {
  const response = await api.get(API_ENDPOINTS.MARKET.PULSE, { params: { portfolioId } });
  return readApiEnvelope(response);
};

export const getFamousInsights = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.MARKET.FAMOUS, { params: { symbol } });
  return readApiEnvelope(response);
};

export const searchStocks = async (q, signal) => {
  const response = await api.get(API_ENDPOINTS.MARKET.SEARCH, { params: { q }, signal });
  return readApiEnvelope(response);
};

export const getGainers = async (cap, sector) => {
  const response = await api.get(API_ENDPOINTS.MARKET.GAINERS, { params: { cap, sector } });
  return readApiEnvelope(response);
};

export const getLosers = async (cap, sector) => {
  const response = await api.get(API_ENDPOINTS.MARKET.LOSERS, { params: { cap, sector } });
  return readApiEnvelope(response);
};

export const getTrending = async () => {
  const response = await api.get(API_ENDPOINTS.MARKET.TRENDING);
  return readApiEnvelope(response);
};

export const getBySector = async (name) => {
  const response = await api.get(API_ENDPOINTS.MARKET.SECTOR, { params: { name } });
  return readApiEnvelope(response);
};

// Insight & AI APIs
export const trackDecision = async (decisionData) => {
  const response = await api.post(API_ENDPOINTS.DECISION.BASE, decisionData);
  return readApiEnvelope(response);
};

export const explainStock = async (stockData) => {
  const response = await api.post(API_ENDPOINTS.AI.EXPLAIN, stockData);
  return readApiEnvelope(response);
};

export const getDecisionStats = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.STATS);
  return readApiEnvelope(response);
};

export const getUserInsights = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.INSIGHTS);
  return readApiEnvelope(response);
};

export const getRecentDecisions = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.BASE);
  return readApiEnvelope(response);
};

export const getInsights = (symbol) => {
  // If we have a symbol-specific insight endpoint, use it. Otherwise fallback to user insights.
  return getUserInsights();
};

export const getArchetype = async () => {
  const response = await api.get(API_ENDPOINTS.DECISION.ARCHETYPE);
  return readApiEnvelope(response);
};

export const evaluateDecision = async (evaluationData) => {
  const response = await api.post(API_ENDPOINTS.DECISION.EVALUATE, evaluationData);
  return readApiEnvelope(response);
};

export const getOnboardingScenario = async (userType) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.SCENARIO, { userType });
  return readApiEnvelope(response);
};

export const getArenaScenarios = async (marketType) => {
  const response = await api.get(API_ENDPOINTS.AI.ONBOARDING.SCENARIOS, { params: { marketType } });
  return readApiEnvelope(response);
};

export const getOnboardingFeedback = async (choice, userType) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.FEEDBACK, { choice, userType });
  return readApiEnvelope(response);
};

export const getArenaSummary = async (decisions) => {
  const response = await api.post(API_ENDPOINTS.AI.ONBOARDING.SUMMARY, { decisions });
  return readApiEnvelope(response);
};

export const getTutorialInsight = async (topic, context) => {
  const response = await api.get(API_ENDPOINTS.AI.TUTORIAL, { params: { topic, context } });
  return readApiEnvelope(response);
};

export const getTutorialInsightResponse = getTutorialInsight;

// Watchlist APIs
export const getWatchlist = async () => {
  const response = await api.get(API_ENDPOINTS.WATCHLIST.BASE);
  return readApiEnvelope(response);
};

export const addToWatchlist = async (symbol) => {
  const response = await api.post(API_ENDPOINTS.WATCHLIST.TOGGLE(symbol));
  return readApiEnvelope(response);
};

export const removeFromWatchlist = async (symbol) => {
  const response = await api.delete(API_ENDPOINTS.WATCHLIST.TOGGLE(symbol));
  return readApiEnvelope(response);
};

export const checkWatchlist = async (symbol) => {
  const response = await api.get(API_ENDPOINTS.WATCHLIST.CHECK(symbol));
  return readApiEnvelope(response);
};

export const getVaultDaily = async () => {
  const response = await api.get(API_ENDPOINTS.VAULT.DAILY);
  return readApiEnvelope(response);
};

export const getVaultCards = async () => {
  const response = await api.get(API_ENDPOINTS.VAULT.CARDS);
  return readApiEnvelope(response);
};

// ─── Cold-start wake ping ────────────────────────────────────────────────────
// Render / Fly.io free-tier backends spin down after 15 min of inactivity.
// This fires exactly once per browser session (guarded by sessionStorage) and is
// completely non-blocking — the UI never waits for it.
if (typeof window !== 'undefined' && !sessionStorage.getItem('fp_pinged')) {
  sessionStorage.setItem('fp_pinged', '1');
  api.get('/actuator/health', { timeout: 12000, _noDedupe: true }).catch(() => {});
}
