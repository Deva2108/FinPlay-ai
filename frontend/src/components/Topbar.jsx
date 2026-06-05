import React, { useState, useRef, useEffect } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { LayoutDashboard, Briefcase, BarChart3, Lightbulb, Bell, TrendingUp, Shield, LogOut } from 'lucide-react';
import { useMarket } from '../context/MarketContext';
import { useLogout } from '../hooks/useLogout';
import { getStoredUser } from '../services/auth';

export default function Topbar() {
  const { marketMode, setMarketMode, accentColor } = useMarket();
  const navigate = useNavigate();
  const logout = useLogout();

  // Real identity from the persisted session, replacing the hardcoded
  // "Student 01" placeholder. Read once on mount — name/email don't change
  // mid-session, and logout hard-reloads the document so a stale value can't
  // survive into the next session.
  const user = getStoredUser();
  const displayName = user?.name?.trim() || 'Trader';
  const displayEmail = user?.email || 'Paper Trading';
  const avatarInitial = displayName.charAt(0).toUpperCase();

  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) setMenuOpen(false);
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const navItems = [
    { path: '/', name: 'Dashboard', icon: LayoutDashboard },
    { path: '/portfolio', name: 'Portfolio', icon: Briefcase },
    { path: '/market', name: 'Market', icon: BarChart3 },
    { path: '/vault', name: 'Vault', icon: Shield },
    { path: '/insights', name: 'Insights', icon: Lightbulb },
  ];

  const activeAccentColor = marketMode === "INDIA" ? "#FF9933" : "#3B82F6";

  return (
    <header className="h-20 bg-[#020617] border-b border-white/5 flex items-center justify-between px-8 sticky top-0 z-[100] backdrop-blur-xl bg-opacity-80">
      {/* Left: Logo */}
      <div className="flex items-center gap-3 cursor-pointer" onClick={() => navigate('/')}>
        <div className="w-10 h-10 rounded-2xl flex items-center justify-center shadow-lg" style={{ backgroundColor: activeAccentColor }}>
          <TrendingUp size={22} className="text-white" />
        </div>
        <div className="hidden md:block">
          <h1 className="text-xl font-black text-white tracking-tight leading-none">FinPlay</h1>
          <p className="text-[9px] font-bold text-slate-500 uppercase tracking-widest">Arena</p>
        </div>
      </div>

      {/* Center: Navigation */}
      <nav className="hidden lg:flex items-center gap-1 bg-white/5 p-1 rounded-2xl border border-white/5">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) => `
              flex items-center gap-2 px-6 py-2.5 rounded-xl transition-all text-xs font-black uppercase tracking-widest
              ${isActive ? 'bg-white/10 text-white shadow-xl' : 'text-slate-500 hover:text-slate-300'}
            `}
          >
            <item.icon size={16} />
            {item.name}
          </NavLink>
        ))}
      </nav>

      {/* Right: Actions */}
      <div className="flex items-center gap-6">
        {/* Market Toggle */}
        <div className="flex bg-slate-900/80 p-1 rounded-2xl border border-slate-800">
          <button 
            onClick={() => setMarketMode("INDIA")}
            className={`px-4 py-2 rounded-xl text-[10px] font-black tracking-widest transition-all ${marketMode === "INDIA" ? 'text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}
            style={marketMode === "INDIA" ? { backgroundColor: activeAccentColor } : {}}
          >
            INDIA
          </button>
          <button 
            onClick={() => setMarketMode("US")}
            className={`px-4 py-2 rounded-xl text-[10px] font-black tracking-widest transition-all ${marketMode === "US" ? 'text-white shadow-lg' : 'text-slate-500 hover:text-slate-300'}`}
            style={marketMode === "US" ? { backgroundColor: activeAccentColor } : {}}
          >
            US
          </button>
        </div>

        <div className="flex items-center gap-4">
          <button className="relative p-2 text-gray-400 hover:text-white transition-colors">
            <Bell size={20} />
            <span className="absolute top-1 right-1 w-2 h-2 bg-rose-500 rounded-full animate-pulse"></span>
          </button>
          
          <div className="h-8 w-[1px] bg-white/10 mx-2 hidden md:block" />

          <div className="relative" ref={menuRef}>
            <div
              onClick={() => setMenuOpen((o) => !o)}
              className="flex items-center gap-3 cursor-pointer group"
            >
              <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-blue-500 to-purple-500 p-[2px] group-hover:scale-105 transition-transform shadow-lg shadow-blue-500/10">
                <div className="w-full h-full bg-slate-950 rounded-full flex items-center justify-center">
                  <span className="text-sm font-black text-white">{avatarInitial}</span>
                </div>
              </div>
              <div className="hidden xl:block max-w-[140px]">
                <span className="text-sm font-black text-white block leading-none truncate">{displayName}</span>
                <span className="text-[9px] font-bold text-slate-500 lowercase tracking-tighter truncate block">{displayEmail}</span>
              </div>
            </div>

            {menuOpen && (
              <div className="absolute right-0 mt-3 w-56 bg-slate-900 border border-white/10 rounded-2xl shadow-2xl shadow-black/40 overflow-hidden z-[110]">
                <div className="px-4 py-3 border-b border-white/5">
                  <p className="text-sm font-black text-white truncate">{displayName}</p>
                  <p className="text-[10px] font-bold text-slate-500 lowercase truncate">{displayEmail}</p>
                </div>
                <button
                  type="button"
                  onClick={logout}
                  className="w-full flex items-center gap-3 px-4 py-3 text-left text-xs font-black uppercase tracking-widest text-slate-300 hover:bg-white/5 hover:text-rose-400 transition-colors"
                >
                  <LogOut size={16} />
                  Logout
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
