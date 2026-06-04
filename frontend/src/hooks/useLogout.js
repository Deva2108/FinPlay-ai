import { logoutUser } from '../services/auth';
import { useTradeActions } from '../context/TradingContext';
import { useStockPanel } from '../context/StockPanelContext';

/**
 * End-to-end logout for the current architecture.
 *
 * Order matters:
 *   1. Tear down in-memory React state FIRST (trading + stock panel). The
 *      trading reset also latches a guard so any in-flight hydration / insight
 *      fetch / wake-retry that resolves during the unload window is dropped
 *      instead of repopulating the previous user's data.
 *   2. logoutUser() is the single owner of storage clearing — localStorage
 *      'token' + 'finplay_user' and the sessionStorage 'fp_swr_*' namespace.
 *      It deliberately does NOT touch 'finplay_market_mode', so the user's
 *      INDIA/US market preference (marketMode) is preserved across logout.
 *   3. Hard reset via window.location.href (NOT navigate()): a full document
 *      teardown guarantees no stale auth, context, timer, or in-flight request
 *      survives into the next session.
 */
export function useLogout() {
  const { logout: resetTradingState } = useTradeActions();
  const { resetStockPanel } = useStockPanel();

  return () => {
    resetTradingState();
    resetStockPanel();
    logoutUser();
    window.location.href = '/login';
  };
}
