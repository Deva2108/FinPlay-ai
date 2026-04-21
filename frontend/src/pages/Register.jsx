import { useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate, Link } from 'react-router-dom';
import { registerUser } from '../services/api';
import { Rocket } from 'lucide-react';

export default function Register() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleRegister = async (e) => {
    e.preventDefault();

    // STEP 1: FIX REGISTER FLOW - Simple password validation
    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await registerUser({ name, email, password });
      if (res) {
        navigate('/login');
      } else {
        setError('Registration failed.');
      }
    } catch (err) {
      const message =
        err.response?.data?.message ||
        err.response?.data ||
        err.message ||
        "Registration failed";

      console.log("REGISTER FINAL ERROR:", message);
      console.log("FULL ERROR:", err);

      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="h-screen flex items-center justify-center relative overflow-hidden bg-[#020617]">
      <div className="absolute top-[-20%] right-[-10%] w-[40%] h-[40%] rounded-full bg-emerald-500/20 blur-[120px]" />
      <div className="absolute bottom-[-20%] left-[-10%] w-[40%] h-[40%] rounded-full bg-blue-500/20 blur-[120px]" />
      
      <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="z-10 w-full max-w-md p-8 glass-card">
        <div className="text-center mb-8">
          <div className="w-16 h-16 bg-emerald-500/20 rounded-2xl flex items-center justify-center mx-auto mb-4 border border-emerald-500/30">
            <Rocket size={32} className="text-emerald-500" />
          </div>
          <h2 className="text-3xl font-black text-white tracking-tight">CREATE ACCOUNT</h2>
          <p className="text-slate-400 text-sm mt-2">Start your learning journey</p>
        </div>

        {error && <div className="bg-rose-500/10 border border-rose-500/20 text-rose-400 p-3 rounded-lg text-sm font-bold text-center mb-6">{error}</div>}

        <form onSubmit={handleRegister} className="space-y-4">
          <div>
            <label className="block text-slate-400 text-xs font-bold uppercase mb-2">Full Name</label>
            <input 
              type="text" 
              required
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                setError(null);
              }}
              className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white focus:outline-none focus:border-emerald-500/50 transition-colors"
              placeholder="John Doe"
            />
          </div>
          <div>
            <label className="block text-slate-400 text-xs font-bold uppercase mb-2">Email</label>
            <input 
              type="email" 
              required
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setError(null);
              }}
              className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white focus:outline-none focus:border-emerald-500/50 transition-colors"
              placeholder="student@example.com"
            />
          </div>
          <div>
            <label className="block text-slate-400 text-xs font-bold uppercase mb-2">Password</label>
            <input 
              type="password" 
              required
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
              className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white focus:outline-none focus:border-emerald-500/50 transition-colors"
              placeholder="Min 6 chars"
            />
          </div>
          <button 
            type="submit" 
            disabled={loading}
            className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-black py-3 rounded-xl uppercase tracking-widest transition-all disabled:opacity-50 mt-4"
          >
            {loading ? 'Creating...' : 'Register Profile'}
          </button>
        </form>

        <p className="text-center text-slate-500 text-xs font-bold mt-6">
          ALREADY HAVE AN ACCOUNT? <Link to="/login" className="text-emerald-400 hover:text-emerald-300">LOGIN</Link>
        </p>
      </motion.div>
    </div>
  );
}
