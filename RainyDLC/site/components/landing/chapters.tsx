"use client";

import { useState } from "react";
import { Check } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import Reveal from "../reveal";

const ACCENTS = ["text-violet", "text-rosepink", "text-mint"];
const BORDERS = ["border-violet", "border-rosepink", "border-mint"];

export default function Chapters() {
  const { t } = useI18n();
  const [active, setActive] = useState(1);
  const ch = t.chapters.items[active];

  return (
    <section id="chapters" className="relative bg-abyss py-24 md:py-36">
      <div className="mx-auto max-w-7xl px-5 md:px-8">
        <Reveal>
          <div className="font-mono2 text-[11px] uppercase tracking-[0.3em] text-violet">
            {t.chapters.kicker}
          </div>
          <div className="mt-4 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <h2 className="font-display max-w-xl text-4xl font-bold leading-[1.05] tracking-tight text-cream md:text-6xl">
              {t.chapters.title}
            </h2>
            <p className="font-mono2 max-w-xs text-[11px] uppercase leading-relaxed tracking-[0.18em] text-muted">
              {t.chapters.subtitle}
            </p>
          </div>
        </Reveal>

        {/* переключатель глав — как «миры» в референсе */}
        <Reveal delay={100}>
          <div className="mt-12 grid grid-cols-3 gap-px bg-lilac/15">
            {t.chapters.items.map((c, i) => (
              <button
                key={c.n}
                onClick={() => setActive(i)}
                className={`group bg-abyss p-5 text-left transition-colors md:p-7 ${
                  active === i ? "bg-panel" : "hover:bg-panel/60"
                }`}
              >
                <div className={`font-mono2 text-[11px] tracking-[0.25em] ${ACCENTS[i]} opacity-70`}>
                  {c.n}
                </div>
                <div
                  className={`font-display mt-2 text-lg font-bold uppercase tracking-wide md:text-2xl ${
                    active === i ? "text-cream" : "text-muted group-hover:text-cream"
                  }`}
                >
                  {c.tab}
                </div>
                <div
                  className={`mt-4 h-0.5 w-full bg-lilac/10 transition-colors ${
                    active === i ? BORDERS[i].replace("border-", "bg-") : ""
                  }`}
                />
              </button>
            ))}
          </div>
        </Reveal>

        {/* панель главы */}
        <div key={active} className="card mt-px p-8 md:p-12">
          <div className="grid gap-10 lg:grid-cols-[1fr_1fr] lg:gap-16">
            <div>
              <div className={`font-mono2 text-sm tracking-[0.3em] ${ACCENTS[active]}`}>
                {ch.n} / 03
              </div>
              <h3 className="font-display mt-3 text-3xl font-bold uppercase tracking-tight text-cream md:text-5xl">
                {ch.title}
              </h3>
              <p className="mt-5 max-w-lg leading-relaxed text-muted">{ch.desc}</p>
            </div>
            <ul className="flex flex-col justify-center gap-4">
              {ch.points.map((p) => (
                <li
                  key={p}
                  className="flex items-center gap-4 border border-lilac/15 bg-void/60 px-5 py-4"
                >
                  <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border ${BORDERS[active]}`}>
                    <Check size={13} className={ACCENTS[active]} />
                  </span>
                  <span className="font-display text-sm font-bold uppercase tracking-wider text-cream md:text-base">
                    {p}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}
