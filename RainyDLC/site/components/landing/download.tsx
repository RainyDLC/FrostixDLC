"use client";

import { Download as DownloadIcon, ArrowUpRight } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import Reveal from "../reveal";

const RELEASES = "https://github.com/RainyDLC/FrostixDLC/releases";

export default function Download() {
  const { t } = useI18n();
  const last = t.download.term.length - 1;

  return (
    <section id="download" className="relative bg-abyss py-24 md:py-36">
      <div className="glow-orb left-[10%] top-[20%] h-[320px] w-[320px] bg-grape/30" />
      <div className="relative mx-auto max-w-5xl px-5 md:px-8">
        <Reveal>
          <div className="font-mono2 text-[11px] uppercase tracking-[0.3em] text-violet">
            {t.download.kicker}
          </div>
          <h2 className="font-display mt-4 max-w-2xl text-4xl font-bold leading-[1.05] tracking-tight text-cream md:text-6xl">
            {t.download.title}
          </h2>
          <p className="font-mono2 mt-4 text-[11px] uppercase tracking-[0.25em] text-muted">
            {t.download.subtitle}
          </p>
        </Reveal>

        <Reveal delay={120}>
          <div className="mt-12 border border-lilac/20 bg-void">
            <div className="flex items-center gap-2 border-b border-lilac/15 px-5 py-3.5">
              <span className="h-2.5 w-2.5 rounded-full bg-grape" />
              <span className="h-2.5 w-2.5 rounded-full bg-grape" />
              <span className="h-2.5 w-2.5 rounded-full bg-grape" />
              <span className="font-mono2 ml-4 text-[10px] uppercase tracking-[0.3em] text-muted">
                install.sh
              </span>
            </div>
            <div className="font-mono2 space-y-3.5 p-6 text-[13px] leading-relaxed md:p-9 md:text-sm">
              {t.download.term.map((line, i) => (
                <div key={i} className={i === last ? "text-mint" : "text-muted"}>
                  {line}
                  {i === last && (
                    <span className="pulse-glow ml-1 inline-block h-4 w-2 translate-y-0.5 bg-mint" />
                  )}
                </div>
              ))}
            </div>
          </div>
        </Reveal>

        <Reveal delay={180}>
          <div className="mt-9 flex flex-wrap items-center gap-x-8 gap-y-5">
            <a
              href={RELEASES}
              target="_blank"
              rel="noopener noreferrer"
              className="font-mono2 group flex items-center gap-3 bg-violet px-8 py-4 text-xs font-bold uppercase tracking-[0.25em] text-white transition-colors hover:bg-cream hover:text-void"
            >
              <DownloadIcon size={15} className="transition-transform group-hover:translate-y-0.5" />
              {t.download.button}
            </a>
            <span className="font-mono2 text-[10px] uppercase tracking-[0.25em] text-muted">
              {t.download.note}
            </span>
          </div>
          <p className="font-mono2 mt-6 flex items-center gap-2 text-[10px] uppercase tracking-[0.2em] text-muted/70">
            <ArrowUpRight size={13} />
            {t.download.noAccount}
          </p>
        </Reveal>
      </div>
    </section>
  );
}
