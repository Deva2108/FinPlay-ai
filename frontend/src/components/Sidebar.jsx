import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Briefcase, BarChart3, Lightbulb } from 'lucide-react';

export default function Sidebar() {
  const navItems = [
    { path: '/', name: 'Dashboard', icon: LayoutDashboard },
    { path: '/portfolio', name: 'Portfolio', icon: Briefcase },
    { path: '/market', name: 'Live Market', icon: BarChart3 },
    { path: '/insights', name: 'Insights', icon: Lightbulb },
  ];

  return (
    <div className="w-20 md:w-64 h-full bg-[#0f172a] border-r border-slate-800 flex flex-col p-4">
      <div className="mb-10 px-2">
        <h1 className="hidden md:block text-xl font-bold text-blue-500 tracking-tight">FINANCE PLAY</h1>
        <div className="md:hidden w-8 h-8 bg-blue-500 rounded-lg mx-auto" />
      </div>

      <nav className="flex-1 space-y-2">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) => `
              flex items-center gap-4 px-3 py-3 rounded-lg transition-all
              ${isActive ? 'bg-blue-600/10 text-blue-500 border border-blue-600/20' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'}
            `}
          >
            <item.icon size={22} />
            <span className="hidden md:block font-medium">{item.name}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
