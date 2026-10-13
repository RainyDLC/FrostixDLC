"use client";

import { useEffect, useState } from "react";
import { useI18n } from "@/lib/i18n/provider";

/**
 * Быстрая катсцена при первом заходе: логотип RainyDLC собирается
 * из блюра со вспышкой, слово печатается, прогресс-бар — и шторка уходит.
 * Клик — пропуск. Показывается раз за сессию.
 */
export default function Intro() {
  const { t } = useI18n();
  const [show, setShow] = useState(false);
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    let seen = false;
    try {
      seen = sessionStorage.getItem("rainydlc-intro") === "1";
    } catch {
      /* ignore */
    }
    if (seen) return;
    setShow(true);
    document.body.style.overflow = "hidden";

    const finish = () => {
      setLeaving(true);
      setTimeout(() => {
        setShow(false);
        document.body.style.overflow = "";
        try {
          sessionStorage.setItem("rainydlc-intro", "1");
        } catch {
          /* ignore */
        }
      }, 450);
    };
    const timer = setTimeout(finish, 2100);
    return () => {
      clearTimeout(timer);
      document.body.style.overflow = "";
    };
  }, []);

  if (!show) return null;

  const skip = () => {
    setLeaving(true);
    setTimeout(() => {
      setShow(false);
      document.body.style.overflow = "";
      try {
        sessionStorage.setItem("rainydlc-intro", "1");
      } catch {
        /* ignore */
      }
    }, 450);
  };

  return (
    <div
      onClick={skip}
      className={`fixed inset-0 z-[100] flex cursor-pointer flex-col items-center justify-center bg-black ${
        leaving ? "intro-leaving" : ""
      }`}
      aria-hidden="true"
    >
      <div className="glow-orb left-1/2 top-1/2 h-[420px] w-[420px] -translate-x-1/2 -translate-y-1/2 bg-violet/25" />
      <div className="intro-ring absolute h-40 w-40 rounded-full border border-violet/60" />
      <img src="/logo.png" alt="" className="intro-logo relative h-36 w-36 object-contain drop-shadow-[0_0_45px_rgba(149,101,255,0.45)]" draggable={false} />
      <div className="intro-word font-display mt-8 text-3xl font-extrabold uppercase text-white md:text-4xl">
        RainyDLC
      </div>
      <div className="intro-tag font-mono2 mt-3 text-[11px] uppercase tracking-[0.3em] text-zinc-500">
        {t.intro.tagline}
      </div>
      <div className="mt-10 h-px w-52 overflow-hidden bg-white/10">
        <div className="intro-bar-fill h-full w-full bg-gradient-to-r from-violet to-white" />
      </div>
      <button className="font-mono2 absolute bottom-8 text-[10px] uppercase tracking-[0.3em] text-zinc-600 transition-colors hover:text-zinc-300">
        {t.intro.skip}
      </button>
    </div>
  );
}
