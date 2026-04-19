import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence, useMotionValue, useTransform, animate } from 'framer-motion';
import { useNavigate } from 'react-router-dom';

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
  const region = "Bangalore"; 
  
  const regionExamples = {
    "Bangalore": { brand: "Swiggy", vibe: "tech-savvy" },
    "Hyderabad": { brand: "Zomato", vibe: "foodie" },
    "Mumbai": { brand: "Tata Motors", vibe: "business-heavy" },
    "Andhra": { brand: "Zomato", vibe: "local favorite" },
    "default": { brand: "Zomato", vibe: "popular brand" }
  };

  const localExample = regionExamples[region] || regionExamples["default"];
  const [step, setStep] = useState(1);
  const [quizAnswer, setQuizAnswer] = useState(null);
  const [lang, setLang] = useState("en");

  const translations = {
    en: {
      toggle: "EN",
      start: "Start",
      step1Title: <>Let’s try one quick <br /> <span className="text-blue-500">money decision.</span></>,
      step1Sub: "No experience needed. Just try.",
      step2Title: "₹100 just hit your pocket.",
      step2Sub: "Be honest… what’s your first move?",
      spendChoice: "Spend it now",
      spendSub: "Food, treats... live in the moment",
      spendHint: "5 min happiness. Then it's gone.",
      growChoice: "Let's grow this",
      growSub: "Control today... wealth tomorrow",
      growHint: "Small start. Big game.",
      step6Title: "Now let’s try something real.",
      step6Sub: "This is how people actually invest.",
      next: "Next",
      whatWillYouDo: "What would you do?",
      buyNow: "Buy now",
      waitWatch: "Wait and watch",
      youBought: "You bought.",
      priceMovedUp: "Price moved up.",
      youMadeMoney: "You made money.",
      youWaited: "You waited.",
      butMarketDidnt: "But the market didn’t.",
      patienceTiming: "Patience matters. But timing matters too.",
      quote: "Decisions matter more than waiting perfectly.",
      continue: "Continue",
      nowYouAreReady: "Now you’re ready.",
      dontNeedToKnowAll: "You don’t need to know everything.",
      justNeedToStart: "You just need to start.",
      enterMarket: "Enter Real Market",
      learnWhilePlay: "Learn while you play."
    },
    hi: {
      toggle: "Hinglish",
      start: "Start",
      step1Title: <>Chalo ek quick <br /> <span className="text-blue-500">money decision lete hain.</span></>,
      step1Sub: "Experience ki zaroorat nahi. Bas try karo.",
      step2Title: "Pocket mein ₹100 aaye hain.",
      step2Sub: "Batao… sabse pehla move kya hoga?",
      spendChoice: "Spend kar deta hoon",
      spendSub: "Food, reels… thoda mood ban jayega",
      spendHint: "5 min khushi. Phir khatam.",
      growChoice: "Isko grow karte hain",
      growSub: "Aaj control… kal paisa",
      growHint: "Chhota start. Bada game.",
      step6Title: "Ab kuch real try karte hain.",
      step6Sub: "Yahi tareeka hota hai actually invest karne ka.",
      next: "Next",
      whatWillYouDo: "Ab kya karoge?",
      buyNow: "Buy now",
      waitWatch: "Wait and watch",
      youBought: "Aapne buy kiya.",
      priceMovedUp: "Price upar gaya.",
      youMadeMoney: "Aapne profit banaya.",
      youWaited: "Aapne wait kiya.",
      butMarketDidnt: "Lekin market nahi ruka.",
      patienceTiming: "Patience zaroori hai, par timing bhi matter karti hai.",
      quote: "Perfect timing se zyada important decision lena hai.",
      continue: "Continue",
      nowYouAreReady: "Ab aap ready ho.",
      dontNeedToKnowAll: "Sab kuch jaanna zaroori nahi hai.",
      justNeedToStart: "Bas shuruwat karni hai.",
      enterMarket: "Enter Real Market",
      learnWhilePlay: "Play karte karte seekho."
    }
  };

  const s = translations[lang];

  const [selection, setSelection] = useState(null);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [userData, setUserData] = useState({
    decisions: [],
    profit: 0
  });

  useEffect(() => {
    console.log("DEBUG: Onboarding component mounted");
    // Reset state for clean journey audit
    try {
      const existing = JSON.parse(localStorage.getItem("finplay") || "{}");
      const resetData = {
        ...existing,
        progress: {
          ...(existing.progress || {}),
          onboardingDone: false
        }
      };
      localStorage.setItem("finplay", JSON.stringify(resetData));
      console.log("DEBUG: LocalStorage onboardingDone reset to false");
    } catch (err) {
      console.error("DEBUG: Reset error", err);
    }
  }, []);

  const trackAndNavigate = (decision, profitGain = 0, nextStep) => {
    if (isTransitioning) return;
    setIsTransitioning(true);
    console.log(`DEBUG: Onboarding Navigating to Step ${nextStep} | Decision: ${decision || 'None'}`);

    if (decision) {
      setUserData(prev => ({
        decisions: [...(prev?.decisions || []), decision],
        profit: (prev?.profit || 0) + profitGain
      }));
    }

    setStep(nextStep);
    setTimeout(() => setIsTransitioning(false), 600);
  };

  const handleFinish = () => {
    console.log("DEBUG: Onboarding handleFinish called");
    try {
      const existing = JSON.parse(localStorage.getItem("finplay") || "{}");
      const finalData = {
        ...existing,
        user: {
          decisions: userData?.decisions || [],
          profit: userData?.profit || 0,
          updatedAt: new Date().toISOString()
        },
        progress: {
          onboardingDone: true,
          lastStep: "dashboard"
        }
      };
      localStorage.setItem("finplay", JSON.stringify(finalData));
      console.log("DEBUG: Transitioning to /dashboard");
      setTimeout(() => navigate('/dashboard'), 50);
    } catch (err) {
      console.error("DEBUG: Onboarding Save Error:", err);
      navigate('/dashboard');
    }
  };

  // Animation variants
  const containerVariants = {
    hidden: { opacity: 0 },
    visible: { 
      opacity: 1,
      transition: { staggerChildren: 0.8, delayChildren: 0.2 }
    }
  };

  const itemVariants = {
    hidden: { opacity: 0, y: 10 },
    visible: { opacity: 1, y: 0 }
  };

  const cardVariants = {
    hidden: { opacity: 0, y: 20 },
    visible: { 
      opacity: 1, 
      y: 0,
      transition: { type: "spring", stiffness: 100, damping: 20 }
    }
  };

  return (
    <div className="fixed inset-0 bg-[#0b0b0b] flex items-center justify-center p-6 z-[9999] overflow-y-auto font-sans text-white">
      
      {/* Language Toggle */}
      <div className="fixed top-8 right-8 z-[10000] flex gap-2">
        {['en', 'hi'].map((l) => (
          <button
            key={l}
            onClick={() => setLang(l)}
            className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest transition-all border ${
              lang === l 
                ? 'bg-white text-black border-white' 
                : 'bg-transparent text-white/40 border-white/10 hover:border-white/30'
            }`}
          >
            {translations[l].toggle}
          </button>
        ))}
      </div>

      <AnimatePresence mode="wait">
        {/* STEP 1: ENTRY */}
        {step === 1 && (
          <motion.div 
            key="step1"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-12"
          >
            <div className="space-y-4">
              <h1 className="text-4xl md:text-5xl font-black text-white tracking-tighter leading-tight text-balance">
                {s.step1Title}
              </h1>
              <p className="text-lg text-slate-400 font-medium tracking-wide">
                {s.step1Sub}
              </p>
            </div>

            <motion.button
              disabled={isTransitioning}
              onClick={() => trackAndNavigate(null, 0, 2)}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="w-full bg-white text-[#0b0b0b] font-black py-6 rounded-3xl uppercase tracking-widest text-sm transition-shadow hover:shadow-[0_0_40px_rgba(255,255,255,0.2)] disabled:opacity-50"
            >
              {s.start}
            </motion.button>
          </motion.div>
        )}

        {/* STEP 2: THE EMOTIONAL SCENE */}
        {step === 2 && (
          <motion.div 
            key="step2"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-12"
          >
            {/* Setup */}
            <motion.div variants={itemVariants} className="space-y-2">
              <h2 className="text-4xl font-black text-white tracking-tighter uppercase leading-none text-balance">{s.step2Title}</h2>
              <p className="text-lg text-slate-400 font-medium tracking-wide text-balance">{s.step2Sub}</p>
            </motion.div>

            {/* Thoughts */}
            <div className="space-y-4 min-h-[60px]">
              <motion.p variants={itemVariants} className="text-base text-blue-400/40 font-black uppercase tracking-[0.2em] italic">
                {lang === 'en' ? '"Maybe order something..."' : '"Chalo kuch order karte hain..."'}
              </motion.p>
              <motion.p variants={itemVariants} className="text-base text-slate-500 font-black uppercase tracking-[0.2em] italic">
                {lang === 'en' ? '"Or should I try to grow this?"' : '"Ya isko badhane ka try karu?"'}
              </motion.p>
            </div>

            {/* Choices */}
            <motion.div variants={itemVariants} className="flex flex-col gap-4 pt-4">
              {/* CARD 1 — SPEND */}
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate("SPEND", 0, 'spend_result')}
                variants={cardVariants}
                whileHover={{ scale: 1.03, y: -4 }}
                whileTap={{ scale: 0.98 }}
                className="group relative text-left p-6 rounded-[2.5rem] bg-gradient-to-br from-white/5 to-transparent border border-white/10 hover:border-rose-500/30 transition-all hover:shadow-[0_20px_50px_rgba(244,63,94,0.1)] backdrop-blur-md disabled:opacity-50"
              >
                <div className="space-y-1 relative z-10">
                  <h3 className="text-xl font-black text-white uppercase tracking-tight group-hover:text-rose-400 transition-colors text-balance">{s.spendChoice}</h3>
                  <div className="space-y-1">
                    <p className="text-sm text-slate-400 font-medium leading-relaxed">"{s.spendSub}"</p>
                    <p className="text-[10px] text-slate-600 uppercase font-black tracking-widest opacity-60">{s.spendHint}</p>
                  </div>
                </div>
              </motion.button>

              {/* CARD 2 — GROW */}
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate("GROW", 0, 'grow_result')}
                variants={cardVariants}
                whileHover={{ scale: 1.03, y: -4 }}
                whileTap={{ scale: 0.98 }}
                className="group relative text-left p-6 rounded-[2.5rem] bg-gradient-to-br from-blue-600/5 to-transparent border border-blue-500/20 hover:border-blue-400/50 transition-all shadow-[0_10px_30px_rgba(59,130,246,0.05)] hover:shadow-[0_20px_50px_rgba(59,130,246,0.15)] backdrop-blur-md disabled:opacity-50"
              >
                <div className="absolute inset-0 bg-blue-500/5 rounded-[2.5rem] pointer-events-none" />
                <div className="space-y-1 relative z-10">
                  <h3 className="text-xl font-black text-white uppercase tracking-tight group-hover:text-blue-400 transition-colors">{s.growChoice}</h3>
                  <div className="space-y-1">
                    <p className="text-sm text-slate-400 font-medium leading-relaxed">"{s.growSub}"</p>
                    <p className="text-[10px] text-blue-400/60 uppercase font-black tracking-widest">{s.growHint}</p>
                  </div>
                </div>
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* SPEND RESULT */}
        {step === 'spend_result' && (
          <motion.div 
            key="spend_result"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-12"
          >
            <div className="space-y-6">
              <motion.h2 variants={itemVariants} className="text-3xl font-black text-white tracking-tighter leading-tight text-balance">
                Nice… <br />
                <span className="text-rose-500">that felt good for a moment.</span>
              </motion.h2>
              <motion.p variants={itemVariants} className="text-xl text-slate-400 font-black uppercase tracking-widest">
                Now it’s gone.
              </motion.p>
              <motion.p variants={itemVariants} className="text-sm text-slate-600 font-bold uppercase tracking-[0.2em] leading-relaxed text-balance">
                This is where most people stay.
              </motion.p>
            </div>

            <motion.div variants={itemVariants}>
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate(null, 0, 2)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="w-full bg-white text-[#0b0b0b] font-black py-6 rounded-3xl uppercase tracking-widest text-sm transition-shadow hover:shadow-[0_0_40px_rgba(255,255,255,0.2)] disabled:opacity-50"
              >
                Try again
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* GROW RESULT */}
        {step === 'grow_result' && (
          <motion.div 
            key="grow_result"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-10"
          >
            <div className="space-y-6">
              <motion.h2 variants={itemVariants} className="text-4xl font-black text-white tracking-tighter">
                Interesting…
              </motion.h2>
              <motion.p variants={itemVariants} className="text-xl text-blue-400 font-black uppercase tracking-widest">
                You didn’t rush.
              </motion.p>
              <motion.p variants={itemVariants} className="text-base text-slate-400 font-medium leading-relaxed text-balance">
                Let’s see what this becomes.
              </motion.p>
            </div>
            <motion.div variants={itemVariants} className="pt-4">
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate(null, 0, 3)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="w-full bg-blue-600 text-white font-black py-6 rounded-3xl uppercase tracking-widest text-sm shadow-xl shadow-blue-600/20 disabled:opacity-50"
              >
                Show me
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* STEP 3: EXPLAINING GROWTH */}
        {step === 3 && (
          <motion.div 
            key="step3"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-10"
          >
            <div className="space-y-6">
              <motion.h2 variants={itemVariants} className="text-3xl font-black text-white tracking-tighter">
                So… how does money grow?
              </motion.h2>
              <div className="space-y-2">
                <motion.p variants={itemVariants} className="text-base text-slate-400 font-medium text-balance">
                  You didn’t spend that ₹100…
                </motion.p>
                <motion.p variants={itemVariants} className="text-xl text-blue-400 font-black uppercase text-balance">
                  You gave it a chance to grow.
                </motion.p>
              </div>
            </div>

            <div className="space-y-6 pt-4 text-balance">
              <div className="space-y-2">
                <motion.p variants={itemVariants} className="text-base text-slate-500 font-bold uppercase tracking-widest text-balance">
                  Think of it like this…
                </motion.p>
                <motion.p variants={itemVariants} className="text-lg text-white font-bold leading-tight text-balance">
                  You put your money into a business.
                </motion.p>
                <motion.p variants={itemVariants} className="text-sm text-slate-400 italic">
                  Like {localExample.brand}, or any company you see daily in {region}.
                </motion.p>
              </div>
              <motion.p variants={itemVariants} className="text-lg text-emerald-400 font-black uppercase tracking-tighter">
                If they grow… your money grows.
              </motion.p>
            </div>

            <div className="space-y-2 pt-4 text-balance">
              <motion.p variants={itemVariants} className="text-base text-slate-400 font-medium text-balance">
                That small part you own?
              </motion.p>
              <motion.p variants={itemVariants} className="text-2xl text-white font-black uppercase tracking-tighter border-y border-white/10 py-4">
                That’s called a <span className="text-blue-500">stock.</span>
              </motion.p>
            </div>

            <motion.div variants={itemVariants} className="pt-6">
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate(null, 0, 4)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="px-10 py-4 bg-white/10 text-white border border-white/10 rounded-2xl font-black uppercase tracking-widest text-xs disabled:opacity-50"
              >
                Wait, really?
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* STEP 4: VISUAL MOMENT & QUIZ */}
        {step === 4 && (
          <motion.div 
            key="step4"
            initial="hidden"
            animate="visible"
            variants={containerVariants}
            className="max-w-md w-full text-center space-y-12"
          >
            <div className="space-y-8">
              <motion.p variants={itemVariants} className="text-sm text-slate-500 font-black uppercase tracking-widest">
                The Result
              </motion.p>
              <motion.div variants={itemVariants} className="flex items-center justify-center gap-6">
                <div className="text-3xl font-black text-slate-600 line-through opacity-50">₹100</div>
                <div className="text-6xl font-black text-emerald-400 drop-shadow-[0_0_30px_rgba(52,211,153,0.3)]">
                  ₹<CountUp from={100} to={110} />
                </div>
              </motion.div>
            </div>

            <div className="space-y-8 pt-8">
              <motion.h3 variants={itemVariants} className="text-xl font-black text-white uppercase tracking-tight text-balance">
                What did your ₹100 become?
              </motion.h3>
              <div className="grid grid-cols-3 gap-3">
                {[110, 105, 100].map((val) => (
                  <motion.button
                    disabled={isTransitioning}
                    key={val}
                    variants={itemVariants}
                    onClick={() => {
                      setQuizAnswer(val);
                      trackAndNavigate(null, 0, 5);
                    }}
                    whileHover={{ scale: 1.05 }}
                    whileTap={{ scale: 0.95 }}
                    className="py-4 rounded-2xl bg-white/5 border border-white/10 text-lg font-black text-white hover:bg-white/10 transition-all disabled:opacity-50"
                  >
                    ₹{val}
                  </motion.button>
                ))}
              </div>
            </div>
          </motion.div>
        )}

        {/* STEP 5: RESULT FEEDBACK */}
        {step === 5 && (
          <motion.div 
            key="step5"
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="max-w-md w-full text-center space-y-10"
          >
            {quizAnswer === 110 ? (
              <div className="space-y-6 text-balance">
                <h2 className="text-4xl font-black text-emerald-400 uppercase tracking-tighter">Nice. You made ₹10.</h2>
                <p className="text-xl text-white font-black uppercase tracking-widest">This is how money grows.</p>
              </div>
            ) : (
              <div className="space-y-6 text-balance">
                <h2 className="text-3xl font-black text-rose-500 uppercase tracking-tighter">Not quite.</h2>
                <div className="p-8 rounded-[2.5rem] bg-white/5 border border-white/10">
                   <p className="text-xl text-white font-bold leading-tight">
                    ₹100 became ₹110 → <br />
                    <span className="text-emerald-400">you gained ₹10.</span>
                   </p>
                </div>
              </div>
            )}

            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1 }} className="pt-6">
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate(null, 0, 6)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="w-full bg-white text-black font-black py-6 rounded-3xl uppercase tracking-widest text-sm disabled:opacity-50"
              >
                Try Market
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* STEP 6: INTRO TO REAL MARKET */}
        {step === 6 && (
          <motion.div 
            key="step6"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-12"
          >
            <div className="space-y-6">
              <motion.h2 variants={itemVariants} className="text-3xl font-black text-white tracking-tighter leading-tight text-balance">
                {s.step6Title}
              </motion.h2>
              <motion.p variants={itemVariants} className="text-lg text-slate-400 font-medium text-balance">
                {s.step6Sub}
              </motion.p>
            </div>

            <motion.div variants={itemVariants} className="pt-8 text-balance">
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate(null, 0, 7)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="w-full bg-blue-600 text-white font-black py-6 rounded-3xl uppercase tracking-widest text-sm shadow-xl shadow-blue-600/20 disabled:opacity-50"
              >
                {s.next}
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* STEP 7: STOCK CARD DECISION */}
        {step === 7 && (
          <motion.div 
            key="step7"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-10"
          >
            {/* Stock Card */}
            <motion.div variants={cardVariants} className="p-8 rounded-[3rem] bg-gradient-to-br from-slate-900 to-slate-950 border border-white/10 shadow-2xl space-y-8 relative overflow-hidden text-white">
               <div className="absolute top-0 right-0 p-6 opacity-20">
                  <div className="w-20 h-20 rounded-full bg-blue-500 blur-3xl" />
               </div>
               
               <div className="flex items-center justify-between">
                  <div className="text-left">
                     <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Company</p>
                     <h3 className="text-3xl font-black text-white tracking-tighter">RELIANCE</h3>
                  </div>
                  <div className="text-right">
                     <p className="text-[10px] font-black text-slate-500 uppercase tracking-widest mb-1">Confidence</p>
                     <span className="text-blue-400 font-black">62%</span>
                  </div>
               </div>

               <div className="flex items-center justify-between pt-4 border-t border-white/5 text-white">
                  <div className="text-left text-white">
                     <p className="text-2xl font-black text-white tracking-tight">₹2950</p>
                     <p className="text-sm font-black text-emerald-400">+1.2%</p>
                  </div>
                  <div className="text-right flex flex-col items-end text-white">
                     <div className="px-3 py-1 bg-emerald-500/10 rounded-full flex items-center gap-2 text-white">
                        <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse text-white" />
                        <span className="text-[10px] font-black text-emerald-500 uppercase text-white text-balance">
                          {t("Buying activity increasing", "Log zyada kharid rahe hain")}
                        </span>
                     </div>
                  </div>
               </div>
            </motion.div>

            <div className="space-y-6">
              <motion.h3 variants={itemVariants} className="text-xl font-black text-white uppercase tracking-tight text-balance">
                 {t("What would you do?", "Ab tum kya karoge?")}
              </motion.h3>
              
              <div className="flex flex-col gap-4">
                 <motion.button
                   disabled={isTransitioning}
                   variants={itemVariants}
                   onClick={() => { 
                     trackAndNavigate("BUY", 50, 8);
                     setSelection('buy'); 
                   }}
                   whileHover={{ scale: 1.05 }}
                   whileTap={{ scale: 0.95 }}
                   className="w-full bg-white text-black font-black py-5 rounded-2xl uppercase tracking-widest text-xs transition-shadow hover:shadow-[0_0_30px_rgba(255,255,255,0.2)] disabled:opacity-50"
                 >
                   {t("Buy now", "Abhi kharido")}
                 </motion.button>
                 <motion.button
                   disabled={isTransitioning}
                   variants={itemVariants}
                   onClick={() => { 
                     trackAndNavigate("WAIT", 0, 8);
                     setSelection('wait'); 
                   }}
                   whileHover={{ scale: 1.05 }}
                   whileTap={{ scale: 0.95 }}
                   className="w-full bg-white/5 border border-white/10 text-white font-black py-5 rounded-2xl uppercase tracking-widest text-xs disabled:opacity-50"
                 >
                   {t("Wait and watch", "Thoda wait karo")}
                 </motion.button>
              </div>
            </div>
          </motion.div>
        )}

        {/* STEP 8: RESULT OF MARKET DECISION */}
        {step === 8 && (
          <motion.div 
            key="step8"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            exit={{ opacity: 0, y: -20 }}
            className="max-w-md w-full text-center space-y-12"
          >
            {selection === 'buy' ? (
              <div className="space-y-6">
                <motion.h2 variants={itemVariants} className="text-3xl font-black text-white tracking-tighter uppercase text-balance text-balance">
                   {s.youBought}
                </motion.h2>
                <motion.p variants={itemVariants} className="text-xl text-emerald-400 font-black uppercase text-balance text-balance">
                   {s.priceMovedUp}
                </motion.p>
                <motion.p variants={itemVariants} className="text-base text-slate-400 font-medium text-balance">
                   {s.youMadeMoney}
                </motion.p>
              </div>
            ) : (
              <div className="space-y-6">
                <motion.h2 variants={itemVariants} className="text-3xl font-black text-white tracking-tighter uppercase text-balance">
                   {s.youWaited}
                </motion.h2>
                <motion.p variants={itemVariants} className="text-xl text-rose-500 font-black uppercase text-balance">
                   {s.butMarketDidnt}
                </motion.p>
                <motion.p variants={itemVariants} className="text-base text-slate-400 font-medium text-balance">
                   {s.patienceTiming}
                </motion.p>
              </div>
            )}

            <motion.div variants={itemVariants} className="p-8 rounded-[2.5rem] bg-blue-600/10 border border-blue-500/20 text-blue-200">
               <p className="text-sm font-bold leading-relaxed italic text-balance">
                 "{s.quote}"
               </p>
            </motion.div>

            <motion.div variants={itemVariants} className="pt-6 text-balance">
              <motion.button
                disabled={isTransitioning}
                onClick={() => trackAndNavigate(null, 0, 9)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="w-full bg-white text-black font-black py-6 rounded-3xl uppercase tracking-widest text-sm shadow-2xl disabled:opacity-50 text-balance"
              >
                {s.continue}
              </motion.button>
            </motion.div>
          </motion.div>
        )}

        {/* STEP 9: FINAL TRANSITION */}
        {step === 9 && (
          <motion.div 
            key="step9"
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            className="max-w-md w-full text-center space-y-12"
          >
            <div className="space-y-6 text-balance">
              <motion.h2 variants={itemVariants} className="text-4xl md:text-5xl font-black text-white tracking-tighter uppercase leading-none">
                {s.nowYouAreReady}
              </motion.h2>
              <div className="space-y-2">
                <motion.p variants={itemVariants} className="text-lg text-slate-400 font-medium text-balance">
                  {s.dontNeedToKnowAll}
                </motion.p>
                <motion.p variants={itemVariants} className="text-lg text-blue-400 font-black uppercase text-balance">
                  {s.justNeedToStart}
                </motion.p>
              </div>
            </div>

            <motion.div variants={itemVariants} className="space-y-4">
              <motion.button
                onClick={handleFinish}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="w-full bg-blue-600 text-white font-black py-6 rounded-3xl uppercase tracking-widest text-sm shadow-xl shadow-blue-600/20 disabled:opacity-50"
              >
                {s.enterMarket}
              </motion.button>
              <p className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em]">
                {s.learnWhilePlay}
              </p>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Subtle Background Glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-blue-600/5 blur-[120px] rounded-full pointer-events-none" />
    </div>
  );
};

export default Onboarding;
