import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence, useMotionValue, useTransform, animate } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { api, getOnboardingScenario, getOnboardingFeedback } from '../services/api';
import { Briefcase, GraduationCap, Building2, Globe, Loader2, Sparkles, User, ArrowRight } from 'lucide-react';

const CountUp = ({ from, to, duration = 2 }) => {
  const count = useMotionValue(from);
  const rounded = useTransform(count, (latest) => Math.round(latest));
  const [displayValue, setDisplayValue] = useState(from);

  useEffect(() => {
    const controls = animate(count, to, { duration, ease: "easeOut" });
    return controls.stop;
  }, [count, to, duration]);

  useEffect(() => {
    return rounded.onChange((v) => setDisplayValue(v));
  }, [rounded]);

  return <span>{displayValue}</span>;
};

const Onboarding = () => {
  const navigate = useNavigate();
  
  const [step, setStep] = useState(0); // Step 0: User Type Selection
  const [userType, setUserType] = useState(null);
  const [dynamicScenario, setDynamicScenario] = useState("");
  const [aiFeedback, setAiFeedback] = useState("");
  const [isSyncing, setIsSyncing] = useState(false);
  const [quizAnswer, setQuizAnswer] = useState(null);
  const [lang, setLang] = useState("en");

  const personas = [
    { id: 'STUDENT', label: 'Student', icon: <GraduationCap size={24}/>, desc: 'Starting early, learning basics' },
    { id: 'WORKING', label: 'Working Professional', icon: <Briefcase size={24}/>, desc: 'Managing salary, building wealth' },
    { id: 'BUSINESS', label: 'Business Owner', icon: <Building2 size={24}/>, desc: 'Investing profits, scaling up' },
    { id: 'EXPLORING', label: 'Just Exploring', icon: <Globe size={24}/>, desc: 'Curious about the market' }
  ];

  const handleUserTypeSelect = async (type) => {
    setUserType(type);
    setIsSyncing(true);
    setStep(1);
    
    try {
      const res = await getOnboardingScenario(type);
      setDynamicScenario(res?.scenario || "You just received ₹2,000 from a side gig. What's your first move?");
    } catch (err) {
      console.error("Scenario fetch failed", err);
      setDynamicScenario("You just received ₹2,000 from a side gig. What's your first move?");
    } finally {
      setIsSyncing(false);
    }
  };

  const handleDecision = async (choice) => {
    setIsSyncing(true);
    try {
      const res = await getOnboardingFeedback(choice, userType);
      setAiFeedback(res?.feedback || (choice === 'GROW' ? "Smart choice. Let's see how this grows." : "Instant joy, but it's gone now."));
      setStep(choice === 'GROW' ? 'grow_result' : 'spend_result');
    } catch (err) {
      setAiFeedback(choice === 'GROW' ? "Smart choice. Let's see how this grows." : "Instant joy, but it's gone now.");
      setStep(choice === 'GROW' ? 'grow_result' : 'spend_result');
    } finally {
      setIsSyncing(false);
    }
  };

  const [isTransitioning, setIsTransitioning] = useState(false);
  
  const handleFinish = () => {
    localStorage.setItem("finplay_onboarding_done", "true");
    localStorage.setItem("finplay_arena_done", "true");
    navigate('/arena');
  };

  const containerVariants = {
    hidden: { opacity: 0 },
    visible: { 
      opacity: 1,
      transition: { staggerChildren: 0.2, delayChildren: 0.1 }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: { opacity: 1, y: 0 }
  };

  return (
    <div className="fixed inset-0 bg-[#020617] flex items-center justify-center p-6 z-[9999] overflow-y-auto font-sans text-white">
      
      {/* Background Atmosphere */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-blue-600/5 blur-[120px] rounded-full pointer-events-none" />

      <AnimatePresence mode="wait">
        
        {/* STEP 0: PERSONA SELECTION */}
        {step === 0 && (
          <motion.div 
            key="persona"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, scale: 0.95 }}
            className="max-w-2xl w-full text-center space-y-10"
          >
            <div className="space-y-4">
              <motion.div variants={itemVariants} className="flex items-center justify-center gap-2 text-blue-400">
                <User size={16} />
                <span className="text-[10px] font-black uppercase tracking-[0.3em]">Personalize Your Journey</span>
              </motion.div>
              <motion.h1 variants={itemVariants} className="text-4xl md:text-5xl font-black text-white tracking-tighter leading-tight">
                Who are you in the <br /> <span className="text-blue-500 text-glow">Real World?</span>
              </motion.h1>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {personas.map((p) => (
                <motion.button
                  key={p.id}
                  variants={itemVariants}
                  whileHover={{ scale: 1.02, y: -4 }}
                  whileTap={{ scale: 0.98 }}
                  onClick={() => handleUserTypeSelect(p.id)}
                  className="group p-6 text-left rounded-3xl bg-slate-900/40 border border-white/5 hover:border-blue-500/30 hover:bg-slate-900/60 transition-all flex items-center gap-4"
                >
                  <div className="w-14 h-14 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-400 group-hover:bg-blue-500 group-hover:text-white transition-all">
                    {p.icon}
                  </div>
                  <div>
                    <h3 className="font-black text-white uppercase tracking-tight text-sm">{p.label}</h3>
                    <p className="text-xs text-slate-500 font-medium">{p.desc}</p>
                  </div>
                </motion.button>
              ))}
            </div>
          </motion.div>
        )}

        {/* STEP 1: AI SYNCING / INTRO */}
        {step === 1 && (
          <motion.div 
            key="sync"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="max-w-md w-full text-center space-y-12"
          >
            {isSyncing ? (
              <div className="space-y-6">
                <Loader2 size={48} className="animate-spin text-blue-500 mx-auto" />
                <div className="space-y-2">
                  <p className="text-[10px] font-black text-blue-400 uppercase tracking-[0.4em] animate-pulse">Syncing with Persona...</p>
                  <p className="text-slate-500 text-xs font-bold uppercase">Building your scenario</p>
                </div>
              </div>
            ) : (
              <div className="space-y-12">
                 <div className="space-y-4">
                   <h2 className="text-3xl font-black text-white tracking-tighter">Ready for your first <br/><span className="text-blue-500 uppercase">Real Decision?</span></h2>
                   <p className="text-slate-400 font-medium">We've built a situation just for you.</p>
                 </div>
                 <motion.button
                   onClick={() => setStep(2)}
                   whileHover={{ scale: 1.05 }}
                   whileTap={{ scale: 0.95 }}
                   className="w-full bg-white text-slate-950 font-black py-6 rounded-3xl uppercase tracking-widest text-xs flex items-center justify-center gap-2"
                 >
                   Enter Scenario <ArrowRight size={14}/>
                 </motion.button>
              </div>
            )}
          </motion.div>
        )}

        {/* STEP 2: THE DYNAMIC SCENARIO */}
        {step === 2 && (
          <motion.div 
            key="scenario"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            className="max-w-md w-full text-center space-y-10"
          >
            <motion.div variants={itemVariants} className="p-10 rounded-[3rem] bg-slate-900/60 border border-white/10 relative overflow-hidden text-left shadow-2xl">
              <div className="absolute top-0 right-0 p-6 opacity-5"><Sparkles size={120} className="text-blue-400" /></div>
              <div className="space-y-6 relative z-10">
                <div className="flex items-center gap-2 text-blue-400">
                  <Sparkles size={14} />
                  <span className="text-[10px] font-black uppercase tracking-widest">Personalized Dilemma</span>
                </div>
                <p className="text-xl font-bold leading-relaxed text-white">
                  "{dynamicScenario}"
                </p>
              </div>
            </motion.div>

            <motion.div variants={itemVariants} className="flex flex-col gap-4">
              <button
                onClick={() => handleDecision('SPEND')}
                className="group w-full p-6 text-left rounded-3xl bg-slate-900/40 border border-white/5 hover:border-rose-500/30 transition-all"
              >
                <h3 className="font-black text-white uppercase tracking-tight group-hover:text-rose-400">Spend it now</h3>
                <p className="text-xs text-slate-500">Live in the moment</p>
              </button>
              <button
                onClick={() => handleDecision('GROW')}
                className="group w-full p-6 text-left rounded-3xl bg-blue-600 text-white shadow-xl shadow-blue-900/20 transition-all hover:bg-blue-500"
              >
                <h3 className="font-black uppercase tracking-tight">Let's grow this</h3>
                <p className="text-xs text-blue-100/70 opacity-80">Wealth for tomorrow</p>
              </button>
            </motion.div>
          </motion.div>
        )}

        {/* RESULT SCREENS */}
        {(step === 'spend_result' || step === 'grow_result') && (
          <motion.div 
            key="result"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            className="max-w-md w-full text-center space-y-10"
          >
            <div className="space-y-6">
              <motion.h2 variants={itemVariants} className={`text-4xl font-black tracking-tighter ${step === 'grow_result' ? 'text-blue-500' : 'text-rose-500'}`}>
                {step === 'grow_result' ? 'Strategic move.' : 'That felt good.'}
              </motion.h2>
              
              <motion.div variants={itemVariants} className="p-8 rounded-[2.5rem] bg-white/5 border border-white/10 text-left relative overflow-hidden">
                <div className="absolute top-0 right-0 p-4 opacity-10"><Sparkles size={60} className="text-blue-400" /></div>
                <p className="text-base text-white font-bold leading-relaxed relative z-10">
                  "{aiFeedback}"
                </p>
              </motion.div>
            </div>

            <motion.button
              variants={itemVariants}
              onClick={() => setStep(9)}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="w-full bg-white text-slate-950 font-black py-6 rounded-3xl uppercase tracking-widest text-xs"
            >
              Continue to Market
            </motion.button>
          </motion.div>
        )}

        {/* FINAL STEP */}
        {step === 9 && (
          <motion.div 
            key="ready"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            className="max-w-md w-full text-center space-y-12"
          >
            <div className="space-y-6">
              <motion.h2 variants={itemVariants} className="text-5xl font-black text-white tracking-tighter uppercase leading-none">
                Now you're <br/> <span className="text-blue-500">Ready.</span>
              </motion.h2>
              <div className="space-y-2">
                <motion.p variants={itemVariants} className="text-lg text-slate-400 font-medium">You don't need to know it all.</motion.p>
                <motion.p variants={itemVariants} className="text-lg text-blue-400 font-black uppercase">You just need to start.</motion.p>
              </div>
            </div>

            <motion.button
              onClick={handleFinish}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="w-full bg-blue-600 text-white font-black py-6 rounded-3xl uppercase tracking-widest text-sm shadow-xl shadow-blue-900/40"
            >
              Enter Decision Arena
            </motion.button>
          </motion.div>
        )}

      </AnimatePresence>
    </div>
  );
};

export default Onboarding;
