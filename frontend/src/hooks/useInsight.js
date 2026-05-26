import { useState, useEffect, useRef } from 'react';
import { api, readApiEnvelope } from '../services/api';

// Exponential-backoff polling schedule (ms). After the last entry, polling stops.
// 5 polls total over ~110s — down from up to 40 polls over ~120s in the old version.
const BACKOFF_SCHEDULE_MS = [5_000, 15_000, 30_000, 60_000];
const TRANSIENT_RETRY_MS = 10_000;

/**
 * Async Insight Hook with stale-while-revalidate.
 *
 * Polling contract:
 *   - First request fires immediately.
 *   - If meta.status !== OK/ERROR, polls again after 5s, 15s, 30s, 60s — then stops.
 *   - Polling pauses when the tab is hidden and resumes on visibilitychange.
 *   - Previous successful `data` is kept on screen across re-polls (SWR); the caller
 *     can show a small "Updating…" badge via the `revalidating` flag.
 *   - When endpoint/params change, the cached payload (data) is retained until
 *     a fresh response arrives, again to avoid blank loaders.
 */
export function useInsight(endpoint, params = {}, options = {}) {
  const [data, setData] = useState(null);
  const [status, setStatus] = useState('SYNCING');
  const [error, setError] = useState(null);
  const [meta, setMeta] = useState(null);
  const [revalidating, setRevalidating] = useState(false);

  const pollIndexRef = useRef(0);
  const timerRef = useRef(null);
  const isActiveRef = useRef(true);
  const maxPolls = options.maxPolls || BACKOFF_SCHEDULE_MS.length + 1;

  const paramsKey = JSON.stringify(params);

  useEffect(() => {
    isActiveRef.current = true;
    pollIndexRef.current = 0;

    const clearTimer = () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };

    const scheduleNext = (delayMs) => {
      clearTimer();
      if (!isActiveRef.current) return;
      // Don't schedule polls while the tab is hidden. visibilitychange will resume.
      if (typeof document !== 'undefined' && document.hidden) return;
      timerRef.current = setTimeout(poll, delayMs);
    };

    const poll = async () => {
      if (!endpoint || endpoint === '/') return;
      // Tab hidden between schedule and fire — bail; resume handler will retrigger.
      if (typeof document !== 'undefined' && document.hidden) return;

      setRevalidating(true);
      try {
        const response = await api.get(endpoint, { params });
        const envelope = readApiEnvelope(response);
        if (!isActiveRef.current) return;

        setData(envelope.data);
        setStatus(envelope.meta?.status || 'SYNCING');
        setMeta(envelope.meta);
        setError(null);

        const done = envelope.meta?.status === 'OK' || envelope.meta?.status === 'ERROR';
        if (done) {
          setRevalidating(false);
          return; // terminal — stop polling
        }

        const idx = pollIndexRef.current;
        if (idx >= BACKOFF_SCHEDULE_MS.length || idx >= maxPolls - 1) {
          setRevalidating(false);
          return; // exhausted schedule — stop polling, keep last data on screen
        }
        const nextDelay = BACKOFF_SCHEDULE_MS[idx];
        pollIndexRef.current = idx + 1;
        scheduleNext(nextDelay);
      } catch (err) {
        if (!isActiveRef.current) return;
        const isTransient = !err.response;
        if (isTransient && pollIndexRef.current < BACKOFF_SCHEDULE_MS.length) {
          pollIndexRef.current += 1;
          scheduleNext(TRANSIENT_RETRY_MS);
        } else {
          setError(err.message);
          setStatus('ERROR');
        }
      } finally {
        if (isActiveRef.current) setRevalidating(false);
      }
    };

    // Resume polling on tab refocus if we were mid-cycle.
    const onVisibility = () => {
      if (document.hidden) {
        clearTimer();
      } else if (pollIndexRef.current < BACKOFF_SCHEDULE_MS.length) {
        // Refire immediately on resume — cheap and matches user intent.
        poll();
      }
    };
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibility);
    }

    poll();

    return () => {
      isActiveRef.current = false;
      clearTimer();
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', onVisibility);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [endpoint, paramsKey]);

  return { data, status, error, meta, revalidating };
}
