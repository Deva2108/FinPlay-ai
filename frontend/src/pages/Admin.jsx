import { useEffect, useMemo, useState } from 'react';
import {
  adminListUsers, adminGetUser, adminResetUser, adminDisableUser, adminEnableUser,
  adminResetPassword, adminAdjustBalance, adminLeaderboard, adminBonusEvent,
  adminStats, adminAudit,
} from '../services/api';
import { formatPrice } from '../utils/formatters';

/**
 * /admin — single-page admin console.
 * Server-side authorization is the source of truth (ROLE_ADMIN). This page
 * just refuses to render if /api/auth/me said admin=false.
 */
export default function Admin() {
  const [tab, setTab] = useState('users');
  const [users, setUsers] = useState([]);
  const [leaders, setLeaders] = useState([]);
  const [stats, setStats] = useState(null);
  const [audit, setAudit] = useState([]);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState(null);
  const [tempPwd, setTempPwd] = useState(null);     // shown in modal once
  const [bonusOpen, setBonusOpen] = useState(false);
  const [confirm, setConfirm] = useState(null);     // {action: () => Promise, message: string}

  const reload = async () => {
    setLoading(true);
    try {
      const [u, l, s, a] = await Promise.all([
        adminListUsers(), adminLeaderboard(), adminStats(), adminAudit(100),
      ]);
      setUsers(u || []);
      setLeaders(l || []);
      setStats(s || null);
      setAudit(a || []);
      setErr(null);
    } catch (e) {
      setErr(e?.response?.data?.message || e.message || 'Failed to load admin data');
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { reload(); }, []);

  const tabs = [
    { id: 'users',       label: 'Users' },
    { id: 'leaderboard', label: 'Leaderboard' },
    { id: 'events',      label: 'Events' },
    { id: 'audit',       label: 'Audit' },
  ];

  return (
    <div className="min-h-screen bg-[#020617] text-slate-100 px-6 py-8">
      <div className="max-w-7xl mx-auto">
        <Header stats={stats} />

        <nav className="flex gap-2 my-6 border-b border-slate-800">
          {tabs.map(t => (
            <button key={t.id}
              onClick={() => setTab(t.id)}
              className={`px-4 py-2 text-sm font-medium transition border-b-2 ${
                tab === t.id
                  ? 'border-amber-400 text-amber-300'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}>
              {t.label}
            </button>
          ))}
          <button onClick={reload} className="ml-auto text-xs text-slate-400 hover:text-slate-200">
            ↻ Refresh
          </button>
        </nav>

        {err && <ErrorBanner message={err} />}
        {loading && <p className="text-slate-400 text-sm">Loading…</p>}

        {!loading && tab === 'users' && (
          <UsersTab
            users={users}
            onReset={(u) => setConfirm({
              message: `Reset all portfolios + holdings for ${u.email}?`,
              action: async () => { await adminResetUser(u.userId); await reload(); }
            })}
            onDisable={(u) => setConfirm({
              message: `Disable ${u.email}? They'll be locked out at next login.`,
              action: async () => { await adminDisableUser(u.userId); await reload(); }
            })}
            onEnable={(u) => setConfirm({
              message: `Re-enable ${u.email}?`,
              action: async () => { await adminEnableUser(u.userId); await reload(); }
            })}
            onResetPwd={async (u) => {
              if (!window.confirm(`Generate a one-time password for ${u.email}?`)) return;
              const resp = await adminResetPassword(u.userId);
              setTempPwd({ email: u.email, pwd: resp?.tempPassword });
            }}
            onAdjustBalance={async (u) => {
              const portfolioId = window.prompt(`Portfolio ID for ${u.email}? (find it in user detail)`);
              if (!portfolioId) return;
              const amount = window.prompt(`Amount (negative to deduct)?`);
              if (!amount) return;
              const reason = window.prompt(`Reason (audit log)?`) || 'manual adjust';
              await adminAdjustBalance(u.userId, { portfolioId: Number(portfolioId), amount: Number(amount), reason });
              await reload();
            }}
          />
        )}

        {!loading && tab === 'leaderboard' && <LeaderboardTab rows={leaders} />}
        {!loading && tab === 'events'      && (
          <EventsTab
            onOpenBonus={() => setBonusOpen(true)}
          />
        )}
        {!loading && tab === 'audit'       && <AuditTab rows={audit} />}

        {tempPwd && <TempPwdModal {...tempPwd} onClose={() => setTempPwd(null)} />}
        {bonusOpen && (
          <BonusModal
            onClose={() => setBonusOpen(false)}
            onSubmit={async ({ amount, reason }) => {
              const r = await adminBonusEvent({ amount, reason });
              setBonusOpen(false);
              await reload();
              alert(`Bonus distributed to ${r.affectedUsers} users — total ₹${r.totalDistributed}`);
            }}
          />
        )}
        {confirm && (
          <ConfirmModal
            message={confirm.message}
            onConfirm={async () => { await confirm.action(); setConfirm(null); }}
            onCancel={() => setConfirm(null)}
          />
        )}
      </div>
    </div>
  );
}

// ----------------------------------------------------------------------

function Header({ stats }) {
  return (
    <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4">
      <div>
        <p className="text-xs uppercase tracking-[0.3em] text-amber-400/80">Admin Console</p>
        <h1 className="text-3xl font-black mt-1">FinPlay Operations</h1>
        <p className="text-slate-400 text-sm mt-1">Damage control · game master · observability</p>
      </div>
      {stats && (
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3 text-sm">
          <Stat label="Users"        value={stats.totalUsers} />
          <Stat label="Active"       value={stats.activeUsers} />
          <Stat label="Portfolios"   value={stats.totalPortfolios} />
          <Stat label="Holdings"     value={stats.totalHoldings} />
          <Stat label="Trades today" value={stats.tradesToday} />
        </div>
      )}
    </div>
  );
}
function Stat({ label, value }) {
  return (
    <div className="rounded-lg bg-slate-900/60 border border-slate-800 px-3 py-2">
      <div className="text-[10px] uppercase tracking-wider text-slate-500">{label}</div>
      <div className="text-xl font-bold text-amber-300">{value ?? '—'}</div>
    </div>
  );
}

function ErrorBanner({ message }) {
  return (
    <div className="bg-red-950/40 border border-red-900/60 text-red-300 text-sm rounded-md px-3 py-2 mb-4">
      {message}
    </div>
  );
}

// ---------- Users tab ----------
function UsersTab({ users, onReset, onDisable, onEnable, onResetPwd, onAdjustBalance }) {
  const [q, setQ] = useState('');
  const filtered = useMemo(() =>
    users.filter(u => `${u.name} ${u.email}`.toLowerCase().includes(q.toLowerCase())),
    [users, q]);

  return (
    <div>
      <input
        value={q} onChange={e => setQ(e.target.value)}
        placeholder="Search name or email…"
        className="w-full md:w-80 bg-slate-900/60 border border-slate-800 rounded px-3 py-2 text-sm mb-4 focus:outline-none focus:border-amber-400/50"/>
      <div className="overflow-x-auto rounded-lg border border-slate-800">
        <table className="w-full text-sm">
          <thead className="bg-slate-900/60 text-slate-400 text-xs uppercase tracking-wider">
            <tr>
              <th className="text-left px-3 py-2">User</th>
              <th className="text-left px-3 py-2">Status</th>
              <th className="text-right px-3 py-2">Portfolios</th>
              <th className="text-right px-3 py-2">Equity</th>
              <th className="text-left px-3 py-2">Last login</th>
              <th className="text-left px-3 py-2">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(u => (
              <tr key={u.userId} className="border-t border-slate-800 hover:bg-slate-900/40">
                <td className="px-3 py-2">
                  <div className="font-medium">{u.name}</div>
                  <div className="text-slate-500 text-xs">{u.email}</div>
                </td>
                <td className="px-3 py-2">
                  {u.admin && <span className="text-xs px-2 py-0.5 bg-amber-500/20 text-amber-300 rounded mr-1">admin</span>}
                  {u.enabled
                    ? <span className="text-xs px-2 py-0.5 bg-green-500/15 text-green-300 rounded">active</span>
                    : <span className="text-xs px-2 py-0.5 bg-red-500/20 text-red-300 rounded">disabled</span>}
                </td>
                <td className="px-3 py-2 text-right">{u.portfolioCount}</td>
                <td className="px-3 py-2 text-right">₹{Number(u.totalEquity || 0).toLocaleString()}</td>
                <td className="px-3 py-2 text-slate-500 text-xs">
                  {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—'}
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-1">
                    <ActionBtn onClick={() => onReset(u)}>Reset</ActionBtn>
                    {u.enabled
                      ? <ActionBtn onClick={() => onDisable(u)} kind="danger">Disable</ActionBtn>
                      : <ActionBtn onClick={() => onEnable(u)}  kind="ok">Enable</ActionBtn>}
                    <ActionBtn onClick={() => onResetPwd(u)}>Reset Pwd</ActionBtn>
                    <ActionBtn onClick={() => onAdjustBalance(u)}>Adjust Balance</ActionBtn>
                  </div>
                </td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No users match.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
function ActionBtn({ onClick, children, kind }) {
  const palette = kind === 'danger'
    ? 'border-red-900/60 text-red-300 hover:bg-red-900/30'
    : kind === 'ok'
    ? 'border-green-900/60 text-green-300 hover:bg-green-900/30'
    : 'border-slate-700 text-slate-300 hover:bg-slate-800';
  return (
    <button onClick={onClick}
      className={`text-xs px-2 py-1 border rounded transition ${palette}`}>
      {children}
    </button>
  );
}

// ---------- Leaderboard tab ----------
function LeaderboardTab({ rows }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-800">
      <table className="w-full text-sm">
        <thead className="bg-slate-900/60 text-slate-400 text-xs uppercase tracking-wider">
          <tr>
            <th className="text-left px-3 py-2">Rank</th>
            <th className="text-left px-3 py-2">User</th>
            <th className="text-right px-3 py-2">Equity</th>
            <th className="text-right px-3 py-2">Initial</th>
            <th className="text-right px-3 py-2">Return %</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(r => (
            <tr key={r.userId} className="border-t border-slate-800">
              <td className="px-3 py-2 text-amber-400 font-bold">#{r.rank}</td>
              <td className="px-3 py-2">
                <div>{r.name}</div>
                <div className="text-slate-500 text-xs">{r.email}</div>
              </td>
              <td className="px-3 py-2 text-right">{formatPrice(r.equity, 'INDIA')}</td>
              <td className="px-3 py-2 text-right text-slate-500">{formatPrice(r.initialBalance, 'INDIA')}</td>
              <td className={`px-3 py-2 text-right font-mono ${Number(r.returnPct) >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                {Number(r.returnPct).toFixed(2)}%
              </td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr><td colSpan={5} className="px-3 py-6 text-center text-slate-500">No traders yet.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

// ---------- Events tab ----------
function EventsTab({ onOpenBonus }) {
  return (
    <div className="grid md:grid-cols-2 gap-4">
      <Card title="Bonus event" subtitle="Drop a fixed amount into every active user's primary portfolio.">
        <button
          onClick={onOpenBonus}
          className="bg-amber-500 hover:bg-amber-400 text-black font-semibold text-sm px-4 py-2 rounded">
          New bonus
        </button>
      </Card>
      <Card title="More events…" subtitle="Themed contests, weekly resets, etc. — coming when we need them." />
    </div>
  );
}
function Card({ title, subtitle, children }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-4">
      <div className="text-sm font-semibold text-slate-200">{title}</div>
      {subtitle && <div className="text-xs text-slate-500 mt-1 mb-3">{subtitle}</div>}
      {children}
    </div>
  );
}

// ---------- Audit tab ----------
function AuditTab({ rows }) {
  return (
    <div className="rounded-lg border border-slate-800 overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-slate-900/60 text-slate-400 text-xs uppercase tracking-wider">
          <tr>
            <th className="text-left px-3 py-2">When</th>
            <th className="text-left px-3 py-2">Actor</th>
            <th className="text-left px-3 py-2">Action</th>
            <th className="text-left px-3 py-2">Target</th>
            <th className="text-left px-3 py-2">Details</th>
            <th className="text-left px-3 py-2 text-slate-600">rid</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-slate-800">
              <td className="px-3 py-2 text-xs">{new Date(r.at).toLocaleString()}</td>
              <td className="px-3 py-2 text-xs">{r.actor}</td>
              <td className="px-3 py-2 text-xs font-mono text-amber-300">{r.action}</td>
              <td className="px-3 py-2 text-xs">{r.target}</td>
              <td className="px-3 py-2 text-xs text-slate-400">{r.details}</td>
              <td className="px-3 py-2 text-xs text-slate-600">{r.requestId}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr><td colSpan={6} className="px-3 py-6 text-center text-slate-500">No admin actions yet.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

// ---------- Modals ----------
function ModalShell({ children }) {
  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-lg p-6 max-w-md w-full">
        {children}
      </div>
    </div>
  );
}
function TempPwdModal({ email, pwd, onClose }) {
  return (
    <ModalShell>
      <h3 className="text-lg font-bold mb-2">Temporary password</h3>
      <p className="text-sm text-slate-400 mb-3">
        For <span className="text-slate-200">{email}</span>. Copy this NOW — it will not be shown again.
      </p>
      <div className="bg-black/60 border border-amber-500/40 rounded px-3 py-2 font-mono text-amber-300 text-lg select-all break-all">
        {pwd}
      </div>
      <p className="text-xs text-slate-500 mt-3">Tell the user via WhatsApp/iMessage. Ask them to change it after login.</p>
      <div className="flex justify-end mt-4 gap-2">
        <button onClick={() => navigator.clipboard?.writeText(pwd)} className="text-xs border border-slate-700 px-3 py-1 rounded hover:bg-slate-800">Copy</button>
        <button onClick={onClose} className="text-xs bg-amber-500 text-black font-semibold px-3 py-1 rounded">Done</button>
      </div>
    </ModalShell>
  );
}
function BonusModal({ onClose, onSubmit }) {
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const submit = async () => {
    const a = Number(amount);
    if (!Number.isFinite(a) || a <= 0) { alert('Amount must be > 0'); return; }
    if (!reason.trim()) { alert('Reason is required (it goes in the audit log)'); return; }
    await onSubmit({ amount: a, reason: reason.trim() });
  };
  return (
    <ModalShell>
      <h3 className="text-lg font-bold mb-2">Bonus event</h3>
      <p className="text-sm text-slate-400 mb-4">
        Drops the same amount into every <span className="text-slate-200">active</span> user's primary portfolio.
      </p>
      <label className="block text-xs uppercase tracking-wider text-slate-500 mb-1">Amount</label>
      <input value={amount} onChange={e => setAmount(e.target.value)} type="number"
             className="w-full bg-black/40 border border-slate-700 rounded px-3 py-2 mb-3" />
      <label className="block text-xs uppercase tracking-wider text-slate-500 mb-1">Reason (audit)</label>
      <input value={reason} onChange={e => setReason(e.target.value)} type="text"
             placeholder="e.g. Launch week bonus"
             className="w-full bg-black/40 border border-slate-700 rounded px-3 py-2 mb-4" />
      <div className="flex justify-end gap-2">
        <button onClick={onClose} className="text-xs border border-slate-700 px-3 py-1 rounded hover:bg-slate-800">Cancel</button>
        <button onClick={submit} className="text-xs bg-amber-500 text-black font-semibold px-3 py-1 rounded">Distribute</button>
      </div>
    </ModalShell>
  );
}
function ConfirmModal({ message, onConfirm, onCancel }) {
  return (
    <ModalShell>
      <h3 className="text-lg font-bold mb-2">Are you sure?</h3>
      <p className="text-sm text-slate-300 mb-4">{message}</p>
      <div className="flex justify-end gap-2">
        <button onClick={onCancel}  className="text-xs border border-slate-700 px-3 py-1 rounded hover:bg-slate-800">Cancel</button>
        <button onClick={onConfirm} className="text-xs bg-red-500 text-white font-semibold px-3 py-1 rounded">Confirm</button>
      </div>
    </ModalShell>
  );
}
