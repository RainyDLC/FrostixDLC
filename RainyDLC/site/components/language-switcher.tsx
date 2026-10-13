"use client";

import { useState } from "react";
import { Globe, Check } from "lucide-react";
import { LANGS, type Lang } from "@/lib/i18n/dictionaries";
import { useI18n } from "@/lib/i18n/provider";

export default function LanguageSwitcher() {
  const { lang, setLang } = useI18n();
  const [open, setOpen] = useState(false);

  const pick = (l: Lang) => {
    setLang(l);
    setOpen(false);
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="font-mono2 flex items-center gap-2 border border-lilac/25 bg-transparent px-3 py-2 text-[11px] uppercase tracking-[0.18em] text-muted transition-colors hover:border-violet hover:text-cream"
        aria-label="Language"
      >
        <Globe size={13} />
        {lang}
      </button>
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-50 mt-2 w-44 border border-lilac/20 bg-void p-1">
            {LANGS.map((l) => (
              <button
                key={l.code}
                onClick={() => pick(l.code)}
                className="font-mono2 flex w-full items-center justify-between px-3 py-2.5 text-[11px] uppercase tracking-[0.18em] text-muted transition-colors hover:bg-violet/15 hover:text-cream"
              >
                {l.label}
                {lang === l.code && <Check size={13} className="text-violet" />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
