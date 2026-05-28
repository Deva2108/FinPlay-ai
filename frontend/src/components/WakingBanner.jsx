import { useEffect, useState } from 'react';
import { onBackendWaking, isBackendWaking } from '../services/api';

/**
 * Mounted once at the App shell. Renders a compact non-blocking banner
 * whenever the API layer detects the backend is asleep (Render free-tier
 * cold start). Hides itself the moment liveness returns.
 *
 * This is the difference between a 45-second blank screen and an honest
 * "the server is waking up" microcopy — same wait, dramatically better UX.
 */
export default function WakingBanner() {
  const [waking, setWaking] = useState(() => isBackendWaking());

  useEffect(() => {
    const unsub = onBackendWaking(setWaking);
    return unsub;
  }, []);

  if (!waking) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed top-3 left-1/2 -translate-x-1/2 z-[9999] flex items-center gap-2.5 px-4 py-2 rounded-full bg-amber-500/10 border border-amber-500/30 backdrop-blur-md shadow-2xl"
    >
      <div className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
      <span className="text-[11px] font-bold text-amber-200 tracking-wide">
        Waking the server — this takes ~20 seconds on the first visit.
      </span>
    </div>
  );
}
