"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Mail, Lock, Loader2 } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import { createClient } from "@/lib/supabase/client";
import Reveal from "@/components/reveal";

export default function AuthPage() {
  const { t } = useI18n();
  const router = useRouter();
  const [tab, setTab] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [honeypot, setHoneypot] = useState(""); // ловушка для ботов
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    // Бот заполнил скрытое поле — делаем вид, что всё ок, и ничего не делаем
    if (honeypot) {
      setDone(true);
      return;
    }
    if (password.length < 8) {
      setError(t.auth.passwordHint);
      return;
    }

    setLoading(true);
    try {
      const supabase = createClient();
      if (tab === "login") {
        const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
        // Намеренно общее сообщение: не раскрываем, существует ли email
        if (error) setError(t.auth.errorLogin);
        else router.push("/account");
      } else {
        const { error } = await supabase.auth.signUp({
          email: email.trim(),
          password,
          options: { emailRedirectTo: `${window.location.origin}/account` },
        });
        if (error) setError(t.auth.errorRegister);
        else setDone(true);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden px-5 pt-16">
      <div className="bg-grid absolute inset-0" />
      <div className="halftone pointer-events-none absolute left-1/2 top-16 h-[380px] w-[680px] -translate-x-1/2 opacity-40 [mask-image:radial-gradient(ellipse,black,transparent_70%)]" />

      <Reveal className="relative z-10 w-full max-w-md">
        <div className="border border-white/15 bg-white/[0.02] p-8 backdrop-blur-xl md:p-10">
          <img src="/logo.png" alt="RainyDLC" className="mx-auto h-14 w-14 object-contain" draggable={false} />
          <h1 className="font-display mt-5 text-center text-lg font-extrabold uppercase tracking-wide text-white">{t.auth.title}</h1>
          <p className="font-mono2 mt-2 text-center text-[10px] uppercase tracking-[0.22em] text-zinc-500">{t.auth.tagline}</p>

          <div className="mt-7 grid grid-cols-2 gap-1 border border-white/10 bg-black/60 p-1">
            {(["login", "register"] as const).map((k) => (
              <button
                key={k}
                onClick={() => { setTab(k); setError(""); setDone(false); }}
                className={`font-mono2 py-2.5 text-[11px] font-bold uppercase tracking-[0.2em] transition-all ${
                  tab === k ? "bg-white text-black" : "text-zinc-500 hover:text-white"
                }`}
              >
                {k === "login" ? t.auth.loginTab : t.auth.registerTab}
              </button>
            ))}
          </div>

          {done ? (
            <div className="font-mono2 mt-7 border border-emerald-400/30 bg-emerald-400/10 p-5 text-center text-[11px] uppercase leading-relaxed tracking-[0.15em] text-emerald-200">
              {t.auth.checkEmail}
            </div>
          ) : (
            <form onSubmit={submit} className="mt-7 space-y-4">
              {/* honeypot — невидимое для людей поле */}
              <input
                type="text"
                tabIndex={-1}
                autoComplete="off"
                value={honeypot}
                onChange={(e) => setHoneypot(e.target.value)}
                className="absolute -left-[9999px] h-0 w-0 opacity-0"
                aria-hidden="true"
              />
              <label className="block">
                <span className="font-mono2 mb-1.5 block text-[10px] font-bold uppercase tracking-[0.22em] text-zinc-500">
                  {t.auth.email}
                </span>
                <div className="relative">
                  <Mail size={15} className="absolute left-4 top-1/2 -translate-y-1/2 text-zinc-600" />
                  <input
                    type="email"
                    required
                    maxLength={254}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder={t.auth.emailPh}
                    className="w-full border border-white/10 bg-black/60 py-3 pl-11 pr-4 text-sm text-white placeholder:text-zinc-700 outline-none transition-colors focus:border-violet/60"
                  />
                </div>
              </label>
              <label className="block">
                <span className="font-mono2 mb-1.5 block text-[10px] font-bold uppercase tracking-[0.22em] text-zinc-500">
                  {t.auth.password}
                </span>
                <div className="relative">
                  <Lock size={15} className="absolute left-4 top-1/2 -translate-y-1/2 text-zinc-600" />
                  <input
                    type="password"
                    required
                    minLength={8}
                    maxLength={128}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder={t.auth.passwordPh}
                    autoComplete={tab === "login" ? "current-password" : "new-password"}
                    className="w-full border border-white/10 bg-black/60 py-3 pl-11 pr-4 text-sm text-white placeholder:text-zinc-700 outline-none transition-colors focus:border-violet/60"
                  />
                </div>
                <span className="font-mono2 mt-1.5 block text-[10px] uppercase tracking-[0.18em] text-zinc-700">{t.auth.passwordHint}</span>
              </label>

              {error && (
                <div className="border border-red-400/30 bg-red-400/10 p-3.5 text-center text-sm text-red-200">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={loading}
                className="font-mono2 flex w-full items-center justify-center gap-2 bg-white py-4 text-[11px] font-bold uppercase tracking-[0.25em] text-black transition-colors hover:bg-violet disabled:opacity-60"
              >
                {loading && <Loader2 size={15} className="animate-spin" />}
                {loading ? t.auth.loading : tab === "login" ? t.auth.submitLogin : t.auth.submitRegister}
              </button>
            </form>
          )}
        </div>
      </Reveal>
    </main>
  );
}
