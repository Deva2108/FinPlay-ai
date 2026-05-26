import React, { useEffect, useState, memo } from 'react';
import { Newspaper, ArrowUpRight } from 'lucide-react';

/**
 * Self-contained "updated Xs ago" label. Owns its own 1-second interval so the
 * parent Dashboard's render tree is untouched by the tick — only this leaf
 * re-renders, and only the text inside the <span> updates.
 *
 * `since` is a Date (or millis number). Pass-by-value so memo works.
 */
function FreshnessLabelInner({ since }) {
  const sinceMs = since instanceof Date ? since.getTime() : Number(since) || Date.now();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const secs = Math.max(0, Math.floor((now - sinceMs) / 1000));
  const label = secs < 60 ? `${secs}s ago` : `${Math.floor(secs / 60)}m ago`;
  return <span className="text-[10px] font-mono text-slate-500 tabular-nums">updated {label}</span>;
}
export const FreshnessLabel = memo(FreshnessLabelInner);

/**
 * Self-contained rotating "Live Feed" card. Owns its own 5-second interval so
 * the rotation never re-renders the Dashboard parent. Accepts a stable
 * `newsItems` array prop (parent should memoize). Returns null when empty.
 */
function LiveFeedRotatorInner({ newsItems }) {
  const [index, setIndex] = useState(0);
  const safeItems = Array.isArray(newsItems) ? newsItems : [];

  useEffect(() => {
    if (safeItems.length <= 1) return;
    const id = setInterval(() => setIndex((i) => (i + 1) % safeItems.length), 5000);
    return () => clearInterval(id);
  }, [safeItems.length]);

  // Reset to 0 if the array shrinks under us.
  useEffect(() => {
    if (index >= safeItems.length) setIndex(0);
  }, [safeItems.length, index]);

  if (safeItems.length === 0) return null;
  const featured = safeItems[index] || safeItems[0];
  const tone = featured?.isRisk ? 'rose' : featured?.isOpportunity ? 'emerald' : 'blue';
  const toneMap = {
    rose: { dot: 'bg-rose-500', label: 'text-rose-400', tag: 'Risk' },
    emerald: { dot: 'bg-emerald-500', label: 'text-emerald-400', tag: 'Opportunity' },
    blue: { dot: 'bg-blue-500', label: 'text-blue-400', tag: 'Signal' }
  };
  const t = toneMap[tone];
  return (
    <a href={featured?.url} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/40 border border-white/5 hover:border-white/15 transition-colors rounded-3xl p-5 space-y-3 group">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2"><Newspaper size={14} className="text-slate-400" /><h3 className="text-[10px] font-black text-white uppercase tracking-widest">Live Feed</h3></div>
        <div className="flex items-center gap-1.5">
          <span className={`w-1.5 h-1.5 rounded-full ${t.dot}`} />
          <span className={`text-[9px] font-black uppercase tracking-widest ${t.label}`}>{t.tag}</span>
        </div>
      </div>
      <p className="text-sm font-bold text-slate-100 leading-snug line-clamp-3 group-hover:text-white transition-colors">{featured?.headline}</p>
      <div className="flex items-center justify-between pt-1">
        <span className="text-[9px] font-black text-slate-500 uppercase tracking-widest">{featured?.source || 'Market Wire'}</span>
        <span className="text-[9px] font-bold text-slate-600 tabular-nums">{index + 1} / {safeItems.length}</span>
      </div>
    </a>
  );
}
export const LiveFeedRotator = memo(LiveFeedRotatorInner, (prev, next) => {
  // Only rerender when the news array reference changes.
  return prev.newsItems === next.newsItems;
});

// Re-export for convenience
export default { FreshnessLabel, LiveFeedRotator };
