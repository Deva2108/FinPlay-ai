import React, { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, TrendingUp, TrendingDown, Info, Activity, PieChart, Shield, Zap } from 'lucide-react';
import { formatPrice } from '../utils/formatters';

export default function InsightPanel({ isOpen, onClose, content }) {
  // Prevent body scroll when open on mobile
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'auto';
    }
    return () => {
      document.body.style.overflow = 'auto';
    };
  }, [isOpen]);

  if (!content) return null;

  const normalizedContent = typeof content === 'string'
    ? { title: 'Insight', explanation: content }
    : content;

  if (!normalizedContent || typeof normalizedContent !== 'object') return null;

  const { title, explanation, data, insight, actions, type } = normalizedContent;
  const normalizedData = Array.isArray(data) ? data : [];
  const normalizedActions = Array.isArray(actions) ? actions : [];
  const insightObject = insight && typeof insight === 'object' && !Array.isArray(insight) ? insight : null;
  const insightText = typeof insight === 'string' ? insight.trim() : '';
  const safeTitle = typeof title === 'string' && title.trim() ? title : 'Insight';
  const safeExplanation = typeof explanation === 'string' && explanation.trim() ? explanation : '';

  const getIcon = () => {
    switch (type) {
      case 'return': return <TrendingUp className="text-emerald-400" />;
      case 'risk': return <Shield className="text-rose-400" />;
      case 'allocation': return <PieChart className="text-blue-400" />;
      case 'stock': return <Activity className="text-purple-400" />;
      case 'game': return <Zap className="text-yellow-400" />;
      default: return <Info className="text-blue-400" />;
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-[100]"
          />

          {/* Panel - Bottom Sheet (Mobile) / Side Drawer (Desktop) */}
          <motion.div
            initial={{ y: '100%', x: 0 }}
            animate={{ y: 0, x: 0 }}
            exit={{ y: '100%', x: 0 }}
            transition={{ type: 'spring', damping: 25, stiffness: 200 }}
            className="fixed bottom-0 left-0 right-0 md:bottom-auto md:top-0 md:right-0 md:left-auto md:w-[400px] md:h-screen max-h-[90vh] md:max-h-screen bg-slate-900 border-t md:border-t-0 md:border-l border-white/10 z-[101] rounded-t-3xl md:rounded-none overflow-hidden flex flex-col shadow-2xl"
          >
            {/* Header */}
            <div className="p-6 border-b border-white/5 flex items-center justify-between sticky top-0 bg-slate-900/90 backdrop-blur-md z-10">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-white/5 rounded-xl border border-white/5">
                  {getIcon()}
                </div>
                <h3 className="text-xl font-black text-white tracking-tight">{safeTitle}</h3>
              </div>
              <button 
                onClick={onClose}
                className="p-2 text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-full transition-colors"
              >
                <X size={20} />
              </button>
            </div>

            {/* Scrollable Content */}
            <div className="p-6 overflow-y-auto flex-1 space-y-8 no-scrollbar pb-32 md:pb-6">
              
              {/* Explanation Section */}
              {safeExplanation && (
                <section className="space-y-3">
                  <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest flex items-center gap-2">
                    <Info size={12} /> What this means
                  </h4>
                  <p className="text-sm text-slate-300 font-medium leading-relaxed bg-white/5 p-4 rounded-2xl border border-white/5">
                    {safeExplanation}
                  </p>
                </section>
              )}

              {/* Data Breakdown Section */}
              {normalizedData.length > 0 && (
                <section className="space-y-3">
                  <h4 className="text-[10px] font-black text-slate-500 uppercase tracking-widest">
                    Data Breakdown
                  </h4>
                  <div className="space-y-2">
                    {normalizedData.map((item, idx) => (
                      <div key={idx} className="flex items-center justify-between p-3 bg-slate-800/50 rounded-xl border border-white/5">
                        <span className="text-xs font-bold text-slate-300">{item.label}</span>
                        <span className={`text-sm font-black ${item.color || 'text-white'}`}>
                          {item.value}
                        </span>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* Visual Bar Split (if requested in data) */}
              {normalizedData.some(d => d && d.progress) && (
                <section className="space-y-2">
                   <div className="h-2 w-full flex rounded-full overflow-hidden gap-0.5 bg-slate-800">
                      {normalizedData.filter(d => d && d.progress).map((item, idx) => (
                         <div 
                           key={idx} 
                           style={{ width: `${item.progress}%`, backgroundColor: item.barColor || '#3b82f6' }}
                           className="h-full"
                         />
                      ))}
                   </div>
                </section>
              )}

              {/* Insight / Smart Reasoning */}
              {(insightText || insightObject) && (
                <section className="space-y-4">
                  <h4 className="text-[10px] font-black text-blue-400 uppercase tracking-widest flex items-center gap-2">
                    <Zap size={12} /> Smart Intelligence
                  </h4>
                  <div className="p-5 rounded-2xl bg-gradient-to-br from-blue-600/10 to-purple-600/10 border border-blue-500/20 relative overflow-hidden">
                    <div className="absolute top-0 right-0 p-2 opacity-10"><Zap size={40} /></div>
                    
                    <div className="relative z-10 space-y-4">
                      {insightText ? (
                        <p className="text-sm font-bold text-blue-100 leading-relaxed">
                          {insightText}
                        </p>
                      ) : insightObject && Object.keys(insightObject).length > 0 ? (
                        <>
                          {/* WHAT HAPPENED */}
                          <div className="space-y-1">
                            <p className="text-sm font-black text-white">{typeof insightObject.whatHappened === "string" ? insightObject.whatHappened : insightObject.whatHappened?.text || insightObject.whatHappened?.message || 'Market data is currently being analyzed.'}</p>
                            <p className="text-xs font-bold text-blue-200/70">{typeof insightObject.whyItMatters === "string" ? insightObject.whyItMatters : insightObject.whyItMatters?.text || insightObject.whyItMatters?.message || 'Observe price action for potential trend shifts.'}</p>
                          </div>

                          {/* IMPACTS */}
                          <div className="grid grid-cols-2 gap-2">
                             {(insightObject.globalImpact) && (
                               <div className="p-3 rounded-xl bg-white/5 border border-white/5">
                                 <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">Global Impact</p>
                                 <p className="text-[10px] font-bold text-white leading-tight">{insightObject.globalImpact}</p>
                               </div>
                             )}
                             {(insightObject.indiaImpact) && (
                               <div className="p-3 rounded-xl bg-white/5 border border-white/5">
                                 <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">India Impact</p>
                                 <p className="text-[10px] font-bold text-white leading-tight">{insightObject.indiaImpact}</p>
                               </div>
                             )}
                          </div>

                          {/* VOLATILITY CONTEXT (formerly analogy — now quantified) */}
                          {(insightObject.analogy) && (
                            <div className="bg-white/5 p-3 rounded-xl border border-white/5">
                              <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-1">Volatility Context</p>
                              <p className="text-xs font-bold text-blue-100/90 leading-relaxed tabular-nums">
                                {typeof insightObject.analogy === "string" ? insightObject.analogy : insightObject.analogy?.text || insightObject.analogy?.message || ''}
                              </p>
                            </div>
                          )}

                          {/* RANGE NOTE & LEARN */}
                          <div className="space-y-2">
                             {(insightObject.whatYouCanLearn) && (
                               <div className="flex items-start gap-2">
                                 <div className="w-1 h-1 rounded-full bg-blue-400 mt-1.5 shrink-0" />
                                 <p className="text-xs font-bold text-white">{typeof insightObject.whatYouCanLearn === "string" ? insightObject.whatYouCanLearn : insightObject.whatYouCanLearn?.text || insightObject.whatYouCanLearn?.message || ''}</p>
                               </div>
                             )}
                             {(insightObject.investorPerspective) && (
                               <div className="border-l-2 border-emerald-500/40 pl-3 py-1">
                                 <p className="text-[9px] font-black text-slate-500 uppercase tracking-widest mb-0.5">Range Position</p>
                                 <p className="text-[10px] text-emerald-300 font-bold tabular-nums">
                                   {typeof insightObject.investorPerspective === "string" ? insightObject.investorPerspective : insightObject.investorPerspective?.text || insightObject.investorPerspective?.message || ''}
                                 </p>
                               </div>
                             )}
                          </div>

                          {/* Technical Footnote */}
                          <div className="pt-2 flex items-center gap-2 border-t border-white/5">
                            <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                            <span className="text-[8px] font-black text-slate-500 uppercase tracking-widest">Deterministic Engine · Groq-backed</span>
                          </div>

                          {/* RESOURCES */}
                          {Array.isArray(insightObject.resources) && insightObject.resources.length > 0 && (
                            <div className="pt-2 flex flex-wrap gap-2">
                              {insightObject.resources.map((res, idx) => (
                                <a 
                                  key={idx} 
                                  href={res?.url || '#'} 
                                  target="_blank" 
                                  rel="noopener noreferrer"
                                  className="text-[9px] font-black text-blue-400 uppercase tracking-widest bg-blue-500/10 hover:bg-blue-500/20 px-3 py-1.5 rounded-lg border border-blue-500/20 transition-colors inline-block"
                                >
                                  {res?.title || 'Learn More'}
                                </a>
                              ))}
                            </div>
                          )}
                        </>
                      ) : (
                        <p className="text-sm font-bold text-blue-100 leading-relaxed">
                          Market context is currently being analyzed. Please check back shortly for deeper insights.
                        </p>
                      )}
                    </div>
                  </div>
                </section>
              )}

              {/* Action Buttons */}
              {normalizedActions.length > 0 && (
                <section className="pt-4 border-t border-white/5 space-y-3">
                  {normalizedActions.map((action, idx) => (
                    <button
                      key={idx}
                      onClick={() => {
                        if(action.onClick) action.onClick();
                        if(!action.keepOpen) onClose();
                      }}
                      className={`w-full py-4 rounded-2xl text-xs font-black uppercase tracking-widest transition-all active:scale-95 flex items-center justify-center gap-2 ${
                        action.primary 
                          ? 'bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-600/20' 
                          : 'bg-white/5 hover:bg-white/10 text-white border border-white/10'
                      }`}
                    >
                      {action.label}
                    </button>
                  ))}
                </section>
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
