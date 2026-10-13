"use client";

import { useI18n } from "@/lib/i18n/provider";
import Reveal from "../reveal";

export default function Mission() {
  const { t } = useI18n();

  return (
    <section id="mission" className="relative overflow-hidden py-24 md:py-36">
      <div className="glow-orb left-[-8%] top-[30%] h-[380px] w-[380px] bg-grape/30" />
      <div className="relative mx-auto max-w-7xl px-5 md:px-8">
        <div className="grid gap-12 lg:grid-cols-[1fr_1.1fr] lg:gap-20">
          <div>
            <Reveal>
              <div className="font-mono2 text-[11px] uppercase tracking-[0.3em] text-violet">
                {t.mission.kicker}
              </div>
              <h2 className="font-display mt-5 text-4xl font-bold leading-[1.05] tracking-tight text-cream md:text-6xl">
                {t.mission.title}
              </h2>
            </Reveal>
          </div>
          <div className="flex flex-col justify-end">
            <Reveal delay={120}>
              <p className="max-w-xl border-l-2 border-violet/70 pl-6 text-[15px] leading-relaxed text-muted md:text-base">
                {t.mission.text}
              </p>
            </Reveal>
            <Reveal delay={200}>
              <div className="mt-10 grid grid-cols-2 gap-px bg-lilac/15 md:grid-cols-4">
                {t.mission.stats.map((s) => (
                  <div key={s.label} className="bg-void p-5">
                    <div className="font-display text-2xl font-bold text-cream md:text-3xl">
                      {s.value}
                    </div>
                    <div className="font-mono2 mt-2 text-[10px] uppercase leading-snug tracking-[0.16em] text-muted">
                      {s.label}
                    </div>
                  </div>
                ))}
              </div>
            </Reveal>
          </div>
        </div>
      </div>
    </section>
  );
}
