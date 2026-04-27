import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate, Link } from 'react-router-dom';
import { loginUser } from '../services/api';
import { Zap } from 'lucide-react';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();

    setError(null);
    setLoading(true);

    try {
      const data = await loginUser({
        email: email.trim(),
        password: password.trim()
      });

      if (data?.token) {
        navigate('/dashboard');
      } else {
        throw new Error('Authentication failed');
      }

    } catch (err) {
      console.error('LOGIN ERROR FULL:', err);

      const msg =
        err.response?.data?.message ||
        err.response?.data ||
        'Invalid email or password';

      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden bg-[#020617] px-4 py-12">
      {/* Background Decorative Elements */}
      <div className="absolute top-[-10%] left-[-5%] w-[50%] h-[50%] rounded-full bg-blue-500/10 blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[-5%] w-[50%] h-[50%] rounded-full bg-purple-500/10 blur-[120px] pointer-events-none" />
      
      <motion.div 
        initial={{ opacity: 0, y: 20 }} 
        animate={{ opacity: 1, y: 0 }} 
        className="z-10 w-full max-w-md md:max-w-xl p-6 md:p-12 glass-card shadow-[0_32px_64px_-16px_rgba(0,0,0,0.5)] border-white/10"
      >
        <div className="text-center mb-10">
          <div className="w-20 h-20 bg-blue-600/20 rounded-[2rem] flex items-center justify-center mx-auto mb-6 border border-blue-500/30 shadow-2xl shadow-blue-500/10">
            <Zap size={40} className="text-blue-500" />
          </div>
          <h2 className="text-3xl md:text-4xl font-black text-white tracking-tighter uppercase">Welcome Back</h2>
          <p className="text-slate-400 text-sm md:text-base mt-2 font-medium">Step back into the Finance Playground</p>
        </div>

        {error && (
          <motion.div 
            initial={{ opacity: 0, x: -10 }} 
            animate={{ opacity: 1, x: 0 }}
            className="bg-rose-500/10 border border-rose-500/20 text-rose-400 p-4 rounded-2xl text-xs md:text-sm font-bold text-center mb-8"
          >
            {error}
          </motion.div>
        )}

        <form onSubmit={handleSubmit} className="space-y-6">
          <div className="space-y-2">
            <label className="block text-slate-500 text-[10px] md:text-xs font-black uppercase tracking-[0.2em] ml-1">Email Address</label>
            <input 
              type="email" 
              required
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setError(null);
              }}
              className="w-full bg-white/5 border border-white/10 rounded-2xl px-5 py-4 text-white text-sm md:text-base focus:outline-none focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 transition-all placeholder-slate-600"
              placeholder="name@example.com"
            />
          </div>
          <div className="space-y-2">
            <label className="block text-slate-500 text-[10px] md:text-xs font-black uppercase tracking-[0.2em] ml-1">Secure Password</label>
            <input 
              type="password" 
              required
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
              className="w-full bg-white/5 border border-white/10 rounded-2xl px-5 py-4 text-white text-sm md:text-base focus:outline-none focus:border-blue-500/50 focus:ring-4 focus:ring-blue-500/10 transition-all placeholder-slate-600"
              placeholder="••••••••"
            />
          </div>
          
          <div className="pt-2">
            <button 
              type="submit" 
              disabled={loading}
              className="w-full bg-blue-600 hover:bg-blue-500 text-white font-black py-5 rounded-2xl uppercase tracking-[0.2em] text-xs md:text-sm transition-all disabled:opacity-50 shadow-xl shadow-blue-900/20 active:scale-[0.98]"
            >
              {loading ? 'Authenticating...' : 'Enter Arena'}
            </button>
          </div>
        </form>

        <div className="mt-10 pt-8 border-t border-white/5 text-center">
          <p className="text-slate-500 text-[10px] md:text-xs font-black uppercase tracking-widest">
            New to FinPlay? <Link to="/register" className="text-blue-400 hover:text-blue-300 ml-2 border-b border-blue-400/30 pb-0.5 transition-colors">Create Free Account</Link>
          </p>
        </div>
      </motion.div>
    </div>
  );
}
