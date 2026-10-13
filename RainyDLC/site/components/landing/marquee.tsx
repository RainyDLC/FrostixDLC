"use client";

import { useI18n } from "@/lib/i18n/provider";

export default function Marquee() {
  const { t } = useI18n();
  const items = [...t.marquee, ...t.marquee];

  return (
    <div className="relative overflow-hidden border-y border-lilac/15 bg-abyss py-4">
      <div className="marquee-track flex w-max items-center whitespace-nowrap">
        {items.map((m, i) => (
          <span key={i} className="flex items-center">
            <span className="font-mono2 px-8 text-xs font-bold uppercase tracking-[0.3em] text-muted">
              {m}
            </span>
            <span className="text-[10px] text-violet">{"//"}</span>
          </span>
        ))}
      </div>
    </div>
  );
}
