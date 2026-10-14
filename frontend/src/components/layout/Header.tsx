import React, { useState, useRef, useEffect, useMemo } from 'react';
import { PieChart, Pie, Cell } from 'recharts';
import { LogOut, ChevronDown, FolderOpen } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useSprint } from '../../context/SprintContext';

const Header: React.FC = () => {
  const { sprint, currentProject, setCurrentProject } = useSprint();
  const healthScore = sprint?.healthScore ?? null;
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);

  // Close profile dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setIsProfileOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleLogout = () => {
    setIsProfileOpen(false);
    logout();
    navigate('/login', { replace: true });
  };

  // ---------- Sprint Timer Calculations ----------
  const { daysLeft, totalDays, timePercent, timeColor } = useMemo(() => {
    if (!sprint) {
      return { daysLeft: 0, totalDays: 1, timePercent: 0, timeColor: '#475569' };
    }
    const start = new Date(sprint.startDate).getTime();
    const end   = new Date(sprint.endDate).getTime();
    if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) {
      return { daysLeft: 0, totalDays: 1, timePercent: 0, timeColor: '#475569' };
    }
    const total = Math.max(1, Math.ceil((end - start) / 86400000));
    const left  = Math.max(0, Math.ceil((end - now) / 86400000));
    const pct   = Math.max(0, Math.min(100, ((total - left) / total) * 100));

    let color = '#10b981'; // green
    if (left <= 2)      color = '#ef4444'; // red
    else if (left <= 5) color = '#f59e0b'; // amber

    return { daysLeft: left, totalDays: total, timePercent: pct, timeColor: color };
  }, [sprint, now]);

  const timeData = [
    { name: 'Elapsed',   value: timePercent },
    { name: 'Remaining', value: 100 - timePercent },
  ];

  // ---------- Health Score Data ----------
  const healthData = [
    { name: 'Score',     value: healthScore ?? 0 },
    { name: 'Remaining', value: 100 - (healthScore ?? 0) },
  ];
  let healthColor = '#10b981';
  if (healthScore === null) healthColor = '#475569';
  else if (healthScore < 50) healthColor = '#ef4444';
  else if (healthScore < 80) healthColor = '#f59e0b';

  return (
    <header className="h-16 bg-[#0F172A] border-b border-[#334155] flex items-center justify-between px-6 z-10 sticky top-0 shadow-md">
      {/* Brand logo & workspace details */}
      <div className="flex items-center gap-4 min-w-0">
        <div 
          onClick={() => setCurrentProject(null)}
          className="flex items-center gap-2 font-black text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-indigo-500 hover:opacity-90 transition-opacity cursor-pointer"
        >
          <div className="w-8 h-8 rounded-lg bg-indigo-500/20 flex items-center justify-center border border-indigo-500/30 shadow-md">
            <span className="text-indigo-400 text-sm font-black">L</span>
          </div>
          <span className="text-sm font-extrabold uppercase tracking-widest hidden sm:inline-block">AgileMind</span>
        </div>

        {currentProject && (
          <div className="flex items-center gap-2 border-l border-slate-800 pl-4 min-w-0">
            <span className="text-xs font-semibold text-slate-300 truncate max-w-[120px] md:max-w-[200px]">
              {currentProject.name}
            </span>
            {sprint && (
              <span className="hidden md:inline-block text-[9px] bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full font-medium truncate max-w-[100px]">
                {sprint.name}
              </span>
            )}
          </div>
        )}
      </div>

      <div className="flex items-center gap-4 shrink-0">
        {/* Dynamic Sprint details inside header only when currentProject is active */}
        {currentProject && sprint && (
          <>
            {/* Sprint Timer */}
            <div className="hidden md:flex items-center gap-2 bg-slate-900/60 px-3 py-1 rounded-xl border border-slate-800">
              <div className="relative w-8 h-8 flex items-center justify-center">
                <PieChart width={36} height={36}>
                  <Pie data={timeData} cx={14} cy={14} innerRadius={10} outerRadius={14} startAngle={90} endAngle={-270} dataKey="value" stroke="none">
                    <Cell fill={timeColor} />
                    <Cell fill="#1e293b" />
                  </Pie>
                </PieChart>
                <span className="absolute inset-0 flex items-center justify-center text-[9px] font-bold" style={{ color: timeColor }}>
                  {daysLeft}d
                </span>
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] font-semibold text-slate-350 leading-tight">Sprint Timer</span>
                <span className="text-[9px] text-slate-550 leading-tight">{daysLeft} of {totalDays} days</span>
              </div>
            </div>

            {/* Health Score */}
            <div className="hidden md:flex items-center gap-2 bg-slate-900/60 px-3 py-1 rounded-xl border border-slate-800">
              <div className="relative w-8 h-8 flex items-center justify-center">
                <PieChart width={36} height={36}>
                  <Pie data={healthData} cx={14} cy={14} innerRadius={10} outerRadius={14} startAngle={90} endAngle={-270} dataKey="value" stroke="none">
                    <Cell fill={healthColor} />
                    <Cell fill="#1e293b" />
                  </Pie>
                </PieChart>
                <span className="absolute inset-0 flex items-center justify-center text-[9px] font-bold" style={{ color: healthColor }}>
                  {healthScore ?? '—'}
                </span>
              </div>
              <div className="flex flex-col">
                <span className="text-[10px] font-semibold text-slate-350 leading-tight">Sprint Health</span>
                <span className="text-[9px] text-slate-550 leading-tight">Score Rating</span>
              </div>
            </div>
          </>
        )}

        {/* Profile Dropdown */}
        <div className="relative" ref={profileRef}>
          <button
            onClick={() => setIsProfileOpen(v => !v)}
            className="flex items-center gap-2 bg-slate-900 hover:bg-slate-800/80 px-2.5 py-1.5 rounded-xl border border-slate-800 transition-colors focus:outline-none cursor-pointer"
          >
            <div className="w-6 h-6 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center text-[9px] font-bold text-white uppercase shrink-0">
              {user?.fullName.charAt(0)}
            </div>
            <span className="text-xs text-slate-300 font-semibold hidden md:inline truncate max-w-[100px]">
              {user?.fullName.split(' ')[0]}
            </span>
            <ChevronDown size={12} className="text-slate-500" />
          </button>

          {isProfileOpen && (
            <div className="absolute right-0 mt-2 w-52 bg-slate-900 border border-slate-800 rounded-xl shadow-2xl overflow-hidden z-50">
              <div className="px-4 py-3 border-b border-slate-800 bg-slate-950/60">
                <p className="text-xs font-bold text-slate-200 truncate">{user?.fullName}</p>
                <p className="text-[10px] text-slate-500 truncate mt-0.5">{user?.email}</p>
              </div>
              <div className="py-1">
                <button
                  onClick={() => {
                    setIsProfileOpen(false);
                    setCurrentProject(null);
                  }}
                  className="w-full flex items-center gap-2 px-4 py-2 text-xs text-slate-300 hover:bg-slate-850 hover:text-slate-100 transition-colors"
                >
                  <FolderOpen size={14} className="text-indigo-400" />
                  Workspace Dashboard
                </button>
              </div>
              <div className="border-t border-slate-800 py-1">
                <button
                  onClick={handleLogout}
                  className="w-full flex items-center gap-2 px-4 py-2 text-xs text-red-400 hover:bg-red-500/10 transition-colors"
                >
                  <LogOut size={14} />
                  Sign out
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default Header;
