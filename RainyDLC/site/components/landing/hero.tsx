"use client";

import { ArrowDown, ChevronDown } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import Reveal from "../reveal";
import Particles from "./particles";

export default function Hero() {
  const { t } = useI18n();

  return (
    <section className="grain relative flex min-h-screen flex-col overflow-hidden">
      {/* поле частиц + свечения */}
      <Particles className="absolute inset-0 h-full w-full" density={1.2} />
      <div className="glow-orb left-1/2 top-[-12%] h-[480px] w-[720px] -translate-x-1/2 bg-grape/40" />
      <div className="glow-orb bottom-[-18%] right-[8%] h-[340px] w-[340px] bg-violet/20" />

      <div className="relative z-10 mx-auto flex w-full max-w-7xl flex-1 flex-col justify-center px-5 pb-20 pt-32 md:px-8">
        <Reveal>
          <div className="font-mono2 flex flex-wrap items-center gap-x-5 gap-y-2 text-[11px] uppercase tracking-[0.3em] text-muted">
            {t.hero.meta.map((m, i) => (
              <span key={m} className="flex items-center gap-5">
                {i > 0 && <span className="h-1 w-1 rounded-full bg-grape" />}
                <span className={i === t.hero.meta.length - 1 ? "text-mint" : ""}>{m}</span>
              </span>
            ))}
          </div>
        </Reveal>

        <Reveal delay={120}>
          <h1 className="font-display mt-8 max-w-5xl text-[clamp(2.6rem,7.5vw,6.5rem)] font-bold leading-[1.04] tracking-tight text-cream">
            {t.hero.line1}
            <br />
            <span className="bg-gradient-to-r from-violet via-rosepink to-violet bg-clip-text text-transparent">
              {t.hero.line2}
            </span>
          </h1>
        </Reveal>

        <Reveal delay={220}>
          <p className="mt-7 max-w-xl text-base leading-relaxed text-muted md:text-lg">
            {t.hero.statement}
          </p>
        </Reveal>

        <Reveal delay={300}>
          <div className="mt-10 flex flex-wrap items-center gap-4">
            <a
              href="/#mission"
              className="font-mono2 group flex items-center gap-3 bg-violet px-8 py-4 text-xs font-bold uppercase tracking-[0.25em] text-white transition-all hover:bg-cream hover:text-void"
            >
              {t.hero.ctaMission}
              <ChevronDown size={15} className="transition-transform group-hover:translate-y-0.5" />
            </a>
            <a
              href="/#download"
              className="font-mono2 group flex items-center gap-3 border border-lilac/30 px-8 py-4 text-xs font-bold uppercase tracking-[0.25em] text-cream transition-colors hover:border-violet hover:text-white"
            >
              {t.hero.ctaDownload}
              <ArrowDown size={15} className="transition-transform group-hover:translate-y-0.5" />
            </a>
          </div>
        </Reveal>
      </div>

      <div className="font-mono2 relative z-10 mx-auto w-full max-w-7xl px-5 pb-8 md:px-8">
        <div className="flex items-center justify-between text-[10px] uppercase tracking-[0.3em] text-muted/70">
          <span>scroll</span>
          <span className="h-px w-24 bg-gradient-to-r from-violet/60 to-transparent" />
        </div>
      </div>
    </section>
  );
}
