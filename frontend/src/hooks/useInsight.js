import { useState, useEffect, useRef } from 'react';
import { api, readApiEnvelope } from '../services/api';

/**
 * Async Insight Hook (MANDATORY).
 * Polls the specified endpoint every 3 seconds until status is "OK" or "ERROR".
 */
export function useInsight(endpoint, params = {}, options = {}) {
  const [data, setData] = useState(null);
  const [status, setStatus] = useState('SYNCING');
  const [error, setError] = useState(null);
  const [meta, setMeta] = useState(null);
  
  const pollCount = useRef(0);
  const maxPolls = options.maxPolls || 40;

  useEffect(() => {
    let timer;
    let isActive = true;

    const poll = async () => {
      if (!endpoint || endpoint === '/') return;
      try {
        const response = await api.get(endpoint, { params });
        const envelope = readApiEnvelope(response);
        
        if (!isActive) return;

        setData(envelope.data);
        setStatus(envelope.meta?.status || 'SYNCING');
        setMeta(envelope.meta);

        if (envelope.meta?.status === 'OK' || envelope.meta?.status === 'ERROR' || pollCount.current >= maxPolls) {
          return; // Stop polling
        }

        pollCount.current += 1;
        timer = setTimeout(poll, 3000);
      } catch (err) {
        if (!isActive) return;
        // Transient failure (timeout/network) — retry instead of permanently stopping
        const isTransient = !err.response;
        if (isTransient && pollCount.current < maxPolls) {
          pollCount.current += 1;
          timer = setTimeout(poll, 5000);
        } else {
          setError(err.message);
          setStatus('ERROR');
        }
      }
    };

    poll();

    return () => {
      isActive = false;
      if (timer) clearTimeout(timer);
    };
  }, [endpoint, JSON.stringify(params)]);

  return { data, status, error, meta };
}
