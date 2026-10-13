"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import Reveal from "../reveal";

export default function Faq() {
  const { t } = useI18n();
  const [open, setOpen] = useState<number | null>(0);

  return (
    <section id="faq" className="relative py-24 md:py-36">
      <div className="mx-auto max-w-4xl px-5 md:px-8">
        <Reveal>
          <div className="font-mono2 text-[11px] uppercase tracking-[0.3em] text-violet">
            {t.faq.kicker}
          </div>
          <h2 className="font-display mt-4 text-4xl font-bold tracking-tight text-cream md:text-6xl">
            {t.faq.title}
          </h2>
          <p className="font-mono2 mt-4 text-[11px] uppercase tracking-[0.25em] text-muted">
            {t.faq.subtitle}
          </p>
        </Reveal>

        <div className="mt-12 border-t border-lilac/15">
          {t.faq.items.map((item, i) => {
            const isOpen = open === i;
            return (
              <Reveal key={item.q} delay={Math.min(i, 3) * 60}>
                <div className="border-b border-lilac/15">
                  <button
                    onClick={() => setOpen(isOpen ? null : i)}
                    className="group flex w-full items-center justify-between gap-6 py-6 text-left"
                  >
                    <span className="flex items-baseline gap-5">
                      <span className="font-mono2 text-[11px] text-muted/60">
                        {String(i + 1).padStart(2, "0")}
                      </span>
                      <span
                        className={`text-[15px] font-bold transition-colors md:text-base ${
                          isOpen ? "text-cream" : "text-cream/80 group-hover:text-cream"
                        }`}
                      >
                        {item.q}
                      </span>
                    </span>
                    <span
                      className={`flex h-9 w-9 shrink-0 items-center justify-center border transition-all duration-300 ${
                        isOpen
                          ? "rotate-45 border-violet text-violet"
                          : "border-lilac/25 text-muted group-hover:border-violet group-hover:text-cream"
                      }`}
                    >
                      <Plus size={16} />
                    </span>
                  </button>
                  <div
                    className={`grid transition-all duration-300 ${
                      isOpen ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0"
                    }`}
                  >
                    <div className="overflow-hidden">
                      <p className="max-w-2xl pb-7 text-sm leading-relaxed text-muted md:pl-12">
                        {item.a}
                      </p>
                    </div>
                  </div>
                </div>
              </Reveal>
            );
          })}
        </div>
      </div>
    </section>
  );
}
