"use client";

import { useState } from "react";
import { ShieldCheck, Ban, CheckCircle, Users, Wrench, Save, Loader2 } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import type { AdminUserRow } from "./page";
import Reveal from "@/components/reveal";

async function api(path: string, body: unknown) {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error("request failed");
  return res.json();
}

export default function AdminPanel({
  me,
  users: initialUsers,
  initialMaintenance,
  initialMessage,
}: {
  me: string;
  users: AdminUserRow[];
  initialMaintenance: boolean;
  initialMessage: string;
}) {
  const { t } = useI18n();
  const [users, setUsers] = useState(initialUsers);
  const [maintenance, setMaintenance] = useState(initialMaintenance);
  const [message, setMessage] = useState(initialMessage);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  const bannedCount = users.filter((u) => u.banned).length;

  const doBan = async (id: string, banned: boolean) => {
    setBusy(id);
    try {
      await api("/api/admin/users/ban", { id, banned });
      setUsers((us) => us.map((u) => (u.id === id ? { ...u, banned } : u)));
    } finally {
      setBusy(null);
    }
  };

  const doDelete = async (id: string) => {
    if (!window.confirm(t.admin.confirmDelete)) return;
    setBusy(id);
    try {
      await api("/api/admin/users/delete", { id });
      setUsers((us) => us.filter((u) => u.id !== id));
    } finally {
      setBusy(null);
    }
  };

  const saveMaintenance = async () => {
    setSaving(true);
    setSaved(false);
    try {
      await api("/api/admin/maintenance", { maintenance, message });
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="relative min-h-screen overflow-hidden px-5 pb-24 pt-32">
      <div className="bg-grid absolute inset-0" />
      <div className="glow-orb left-1/2 top-[5%] h-[380px] w-[600px] -translate-x-1/2 bg-red-500/8" />

      <div className="relative z-10 mx-auto max-w-5xl">
        <Reveal>
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-red-400/25 to-orange-500/25 text-red-300">
              <ShieldCheck size={26} />
            </div>
            <div>
              <h1 className="font-display text-2xl font-extrabold text-white">{t.admin.title}</h1>
              <p className="text-sm text-zinc-500">{t.admin.subtitle}</p>
            </div>
          </div>

          <div className="mt-6 grid grid-cols-2 gap-4 sm:max-w-md">
            <div className="glass rounded-2xl p-5">
              <div className="flex items-center gap-2 text-zinc-400"><Users size={15} /><span className="font-mono2 text-[10px] uppercase tracking-[0.2em]">{t.admin.statsTotal}</span></div>
              <div className="font-display mt-2 text-3xl font-bold text-white">{users.length}</div>
            </div>
            <div className="glass rounded-2xl p-5">
              <div className="flex items-center gap-2 text-zinc-400"><Ban size={15} /><span className="font-mono2 text-[10px] uppercase tracking-[0.2em]">{t.admin.statsBanned}</span></div>
              <div className="font-display mt-2 text-3xl font-bold text-red-300">{bannedCount}</div>
            </div>
          </div>
        </Reveal>

        {/* техработы */}
        <Reveal delay={80}>
          <div className="glass mt-6 rounded-3xl p-7">
            <div className="flex items-center gap-3">
              <Wrench size={18} className="text-amber-300" />
              <h2 className="font-display text-base font-bold text-white">{t.admin.maintTitle}</h2>
            </div>
            <p className="mt-2 text-sm text-zinc-400">{t.admin.maintDesc}</p>
            <div className="mt-5 flex flex-wrap items-center gap-4">
              <button
                onClick={() => setMaintenance((v) => !v)}
                className={`relative h-8 w-14 rounded-full transition-colors ${maintenance ? "bg-red-500" : "bg-white/15"}`}
                aria-pressed={maintenance}
              >
                <span className={`absolute top-1 h-6 w-6 rounded-full bg-white transition-all ${maintenance ? "left-7" : "left-1"}`} />
              </button>
              <span className={`text-sm font-semibold ${maintenance ? "text-red-300" : "text-emerald-300"}`}>
                {maintenance ? t.admin.maintOn : t.admin.maintOff}
              </span>
            </div>
            <label className="mt-5 block">
              <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-zinc-400">{t.admin.maintMsg}</span>
              <input
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder={t.admin.maintMsgPh}
                maxLength={200}
                className="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-sm text-white placeholder:text-zinc-600 outline-none focus:border-violet/60"
              />
            </label>
            <button
              onClick={saveMaintenance}
              disabled={saving}
              className="mt-5 flex items-center gap-2 rounded-full bg-white px-6 py-2.5 text-sm font-bold text-black transition-all hover:scale-[1.03] disabled:opacity-60"
            >
              {saving ? <Loader2 size={15} className="animate-spin" /> : <Save size={15} />}
              {saved ? t.admin.saved : t.admin.save}
            </button>
          </div>
        </Reveal>

        {/* пользователи */}
        <Reveal delay={140}>
          <div className="glass mt-6 overflow-hidden rounded-3xl">
            <h2 className="font-display p-7 pb-4 text-base font-bold text-white">{t.admin.usersTitle}</h2>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead>
                  <tr className="border-y border-white/10 text-[11px] uppercase tracking-wider text-zinc-500">
                    <th className="px-7 py-3 font-semibold">{t.admin.colEmail}</th>
                    <th className="px-4 py-3 font-semibold">{t.admin.colCreated}</th>
                    <th className="px-4 py-3 font-semibold">{t.admin.colStatus}</th>
                    <th className="px-7 py-3 text-right font-semibold">{t.admin.colActions}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.id} className="border-b border-white/5 transition-colors last:border-0 hover:bg-white/[0.02]">
                      <td className="px-7 py-4 font-medium text-white">
                        {u.email}
                        {u.email === me && (
                          <span className="ml-2 rounded-full bg-violet/15 px-2 py-0.5 text-[10px] font-bold uppercase text-violet">
                            {t.admin.you}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-4 text-zinc-400">{new Date(u.created_at).toLocaleDateString()}</td>
                      <td className="px-4 py-4">
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold ${u.banned ? "bg-red-400/15 text-red-300" : "bg-emerald-400/15 text-emerald-300"}`}>
                          {u.banned ? <Ban size={11} /> : <CheckCircle size={11} />}
                          {u.banned ? t.admin.banned : t.admin.active}
                        </span>
                      </td>
                      <td className="px-7 py-4">
                        <div className="flex justify-end gap-2">
                          <button
                            onClick={() => doBan(u.id, !u.banned)}
                            disabled={busy === u.id || u.email === me}
                            className={`rounded-full border px-3.5 py-1.5 text-xs font-semibold transition-colors disabled:opacity-40 ${
                              u.banned
                                ? "border-emerald-400/40 text-emerald-300 hover:bg-emerald-400/10"
                                : "border-red-400/40 text-red-300 hover:bg-red-400/10"
                            }`}
                          >
                            {busy === u.id ? "…" : u.banned ? t.admin.unban : t.admin.ban}
                          </button>
                          <button
                            onClick={() => doDelete(u.id)}
                            disabled={busy === u.id || u.email === me}
                            className="rounded-full border border-white/15 px-3.5 py-1.5 text-xs font-semibold text-zinc-400 transition-colors hover:border-red-400/50 hover:text-red-300 disabled:opacity-40"
                          >
                            {busy === u.id ? "…" : t.admin.remove}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </Reveal>
      </div>
    </main>
  );
}
