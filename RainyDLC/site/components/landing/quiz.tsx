"use client";

import { useState } from "react";
import { Eye, Check, X, RotateCcw } from "lucide-react";
import { useI18n } from "@/lib/i18n/provider";
import Reveal from "../reveal";

export default function Quiz() {
  const { t } = useI18n();
  const [step, setStep] = useState(0);
  const [picked, setPicked] = useState<0 | 1 | null>(null);
  const [score, setScore] = useState(0);
  const [done, setDone] = useState(false);

  const sc = t.quiz.scenarios[step];
  // правильный ответ: optB во всех сценариях — «палево»
  const correctIdx = 1;

  const pick = (i: 0 | 1) => {
    if (picked !== null) return;
    setPicked(i);
    if (i === correctIdx) setScore((s) => s + 1);
  };

  const next = () => {
    if (step + 1 >= t.quiz.scenarios.length) {
      setDone(true);
    } else {
      setStep((s) => s + 1);
      setPicked(null);
    }
  };

  const restart = () => {
    setStep(0);
    setPicked(null);
    setScore(0);
    setDone(false);
  };

  return (
    <section className="relative overflow-hidden py-24 md:py-36">
      <div className="glow-orb right-[-6%] top-[20%] h-[360px] w-[360px] bg-violet/20" />
      <div className="relative mx-auto max-w-4xl px-5 md:px-8">
        <Reveal className="text-center">
          <div className="font-mono2 text-[11px] uppercase tracking-[0.3em] text-violet">
            {t.quiz.kicker}
          </div>
          <h2 className="font-display mt-4 text-4xl font-bold tracking-tight text-cream md:text-6xl">
            {t.quiz.title}
          </h2>
          <p className="mx-auto mt-4 max-w-xl text-muted">{t.quiz.subtitle}</p>
        </Reveal>

        <Reveal delay={120}>
          <div className="card mt-12 p-8 md:p-12">
            {!done ? (
              <div key={step}>
                <div className="flex items-center justify-between">
                  <span className="font-mono2 text-[11px] uppercase tracking-[0.3em] text-muted">
                    {step + 1} / {t.quiz.scenarios.length}
                  </span>
                  <span className="font-mono2 flex items-center gap-2 text-[11px] uppercase tracking-[0.25em] text-violet">
                    <Eye size={14} />
                    {score}
                  </span>
                </div>

                <p className="font-display mt-6 text-xl font-bold leading-snug text-cream md:text-3xl">
                  {sc.situation}
                </p>

                <div className="mt-8 grid gap-3 sm:grid-cols-2">
                  {[sc.optA, sc.optB].map((opt, i) => {
                    const isPicked = picked === i;
                    const isRight = picked !== null && i === correctIdx;
                    const isWrongPick = isPicked && i !== correctIdx;
                    return (
                      <button
                        key={opt}
                        onClick={() => pick(i as 0 | 1)}
                        disabled={picked !== null}
                        className={`font-mono2 border px-6 py-5 text-xs font-bold uppercase tracking-[0.25em] transition-all ${
                          isRight
                            ? "border-mint bg-mint/10 text-mint"
                            : isWrongPick
                              ? "border-rosepink bg-rosepink/10 text-rosepink"
                              : "border-lilac/25 text-cream hover:border-violet hover:bg-violet/10"
                        } ${picked !== null && !isPicked && !isRight ? "opacity-40" : ""}`}
                      >
                        <span className="flex items-center justify-center gap-2">
                          {isRight && <Check size={15} />}
                          {isWrongPick && <X size={15} />}
                          {opt}
                        </span>
                      </button>
                    );
                  })}
                </div>

                {picked !== null && (
                  <div className="mt-8 border-t border-lilac/15 pt-6">
                    <div
                      className={`font-mono2 text-[11px] font-bold uppercase tracking-[0.3em] ${
                        picked === correctIdx ? "text-mint" : "text-rosepink"
                      }`}
                    >
                      {picked === correctIdx ? t.quiz.correct : t.quiz.wrong}
                    </div>
                    <p className="mt-3 text-sm leading-relaxed text-muted">{sc.verdict}</p>
                    <button
                      onClick={next}
                      className="font-mono2 mt-6 bg-cream px-7 py-3.5 text-[11px] font-bold uppercase tracking-[0.25em] text-void transition-colors hover:bg-violet hover:text-white"
                    >
                      →
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="text-center">
                <div className="font-display text-6xl font-bold text-cream md:text-7xl">
                  {score}/{t.quiz.scenarios.length}
                </div>
                <p className="mx-auto mt-4 max-w-md text-muted">
                  {score === t.quiz.scenarios.length ? t.quiz.finalGood : t.quiz.finalBad}
                </p>
                <button
                  onClick={restart}
                  className="font-mono2 mt-8 inline-flex items-center gap-2 border border-lilac/30 px-7 py-3.5 text-[11px] font-bold uppercase tracking-[0.25em] text-cream transition-colors hover:border-violet"
                >
                  <RotateCcw size={14} />
                  {t.quiz.retry}
                </button>
              </div>
            )}
          </div>
        </Reveal>
      </div>
    </section>
  );
}
