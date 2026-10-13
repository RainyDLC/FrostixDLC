"use client";

import { useRouter } from "next/navigation";
import { LogOut, Download, User as UserIcon, Languages } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import { createClient } from "@/lib/supabase/client";
import LanguageSwitcher from "@/components/language-switcher";
import Reveal from "@/components/reveal";

const RELEASES = "https://github.com/RainyDLC/FrostixDLC/releases";

export default function AccountClient({ email, createdAt }: { email: string; createdAt: string }) {
  const { t } = useI18n();
  const router = useRouter();

  const logout = async () => {
    const supabase = createClient();
    await supabase.auth.signOut();
    router.push("/");
    router.refresh();
  };

  const date = new Date(createdAt).toLocaleDateString();

  return (
    <main className="relative min-h-screen overflow-hidden px-5 pb-24 pt-32">
      <div className="bg-grid absolute inset-0" />

      <div className="relative z-10 mx-auto max-w-3xl">
        <Reveal>
          <div className="border border-white/15 bg-white/[0.02] p-8 backdrop-blur-xl md:p-10">
            <div className="flex items-center gap-5">
              <div className="flex h-14 w-14 items-center justify-center border border-violet/30 bg-violet/10 text-violet">
                <UserIcon size={24} />
              </div>
              <div>
                <h1 className="font-display text-xl font-extrabold uppercase tracking-wide text-white">{t.account.title}</h1>
                <p className="font-mono2 mt-1 text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                  {t.account.welcome} · {email}
                </p>
              </div>
            </div>

            <div className="mt-8 grid gap-px bg-white/10 sm:grid-cols-2">
              <div className="bg-[#0a0a0c] p-5">
                <div className="font-mono2 text-[10px] uppercase tracking-[0.25em] text-zinc-600">
                  {t.account.email}
                </div>
                <div className="mt-2 truncate text-sm font-semibold text-white">{email}</div>
              </div>
              <div className="bg-[#0a0a0c] p-5">
                <div className="font-mono2 text-[10px] uppercase tracking-[0.25em] text-zinc-600">
                  {t.account.memberSince}
                </div>
                <div className="mt-2 text-sm font-semibold text-white">{date}</div>
              </div>
            </div>

            <div className="mt-px flex items-center justify-between gap-4 bg-[#0a0a0c] p-5">
              <div className="flex items-center gap-3">
                <Languages size={17} className="text-violet" />
                <div>
                  <div className="text-sm font-semibold text-white">{t.account.langTitle}</div>
                  <div className="font-mono2 mt-0.5 text-[10px] uppercase tracking-[0.18em] text-zinc-600">{t.account.langDesc}</div>
                </div>
              </div>
              <LanguageSwitcher />
            </div>

            <button
              onClick={logout}
              className="font-mono2 mt-6 flex items-center gap-2 border border-red-400/40 px-5 py-2.5 text-[11px] font-bold uppercase tracking-[0.2em] text-red-300 transition-colors hover:bg-red-400/10"
            >
              <LogOut size={14} />
              {t.account.logout}
            </button>
          </div>
        </Reveal>

        <Reveal delay={120}>
          <div className="mt-6 flex flex-col gap-5 border border-white/15 bg-white/[0.02] p-8 md:flex-row md:items-center md:justify-between">
            <div className="flex items-center gap-5">
              <img src="/logo.png" alt="" className="h-12 w-12 object-contain" draggable={false} />
              <div>
                <div className="font-display text-sm font-extrabold uppercase tracking-wide text-white">{t.account.clientTitle}</div>
                <div className="font-mono2 mt-1 text-[10px] uppercase tracking-[0.2em] text-zinc-600">{t.account.clientDesc}</div>
              </div>
            </div>
            <a
              href={RELEASES}
              target="_blank"
              rel="noopener noreferrer"
              className="font-mono2 flex items-center gap-2.5 bg-white px-7 py-3.5 text-[11px] font-bold uppercase tracking-[0.22em] text-black transition-colors hover:bg-violet"
            >
              <Download size={15} />
              {t.account.clientButton}
            </a>
          </div>
        </Reveal>
      </div>
    </main>
  );
}
