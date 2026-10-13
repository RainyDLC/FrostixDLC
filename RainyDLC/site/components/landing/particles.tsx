"use client";

import { useEffect, useRef } from "react";

const COLORS = ["149,101,255", "254,166,208", "180,142,218", "123,245,131"];

interface P {
  x: number;
  y: number;
  vx: number;
  vy: number;
  r: number;
  c: string;
  tw: number;
  ts: number;
}

/** Поле дрейфующих частиц данных — отсылка к лоадеру референса. */
export default function Particles({ density = 1, className = "" }: { density?: number; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let w = 0;
    let h = 0;
    let raf = 0;
    let mx = -1e5;
    let my = -1e5;
    let ps: P[] = [];

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      w = Math.max(1, Math.round(rect.width));
      h = Math.max(1, Math.round(rect.height));
      canvas.width = w;
      canvas.height = h;
      const n = Math.round(((w * h) / 16000) * density);
      ps = Array.from({ length: Math.min(n, 160) }, () => ({
        x: Math.random() * w,
        y: Math.random() * h,
        vx: (Math.random() - 0.5) * 0.25,
        vy: (Math.random() - 0.5) * 0.25,
        r: 0.8 + Math.random() * 2.2,
        c: COLORS[(Math.random() * COLORS.length) | 0],
        tw: Math.random() * Math.PI * 2,
        ts: 0.008 + Math.random() * 0.02,
      }));
    };

    const tick = () => {
      ctx.clearRect(0, 0, w, h);
      for (const p of ps) {
        p.tw += p.ts;
        p.x += p.vx;
        p.y += p.vy;
        // лёгкое отталкивание от курсора
        const dx = p.x - mx;
        const dy = p.y - my;
        const d2 = dx * dx + dy * dy;
        if (d2 < 120 * 120 && d2 > 1) {
          const d = Math.sqrt(d2);
          p.x += (dx / d) * 0.6;
          p.y += (dy / d) * 0.6;
        }
        if (p.x < -10) p.x = w + 10;
        if (p.x > w + 10) p.x = -10;
        if (p.y < -10) p.y = h + 10;
        if (p.y > h + 10) p.y = -10;
        const a = 0.25 + 0.55 * (0.5 + 0.5 * Math.sin(p.tw));
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, 6.2832);
        ctx.fillStyle = `rgba(${p.c},${a.toFixed(3)})`;
        ctx.fill();
      }
      raf = requestAnimationFrame(tick);
    };

    const onMove = (e: PointerEvent) => {
      const b = canvas.getBoundingClientRect();
      mx = e.clientX - b.left;
      my = e.clientY - b.top;
    };
    const onLeave = () => {
      mx = -1e5;
      my = -1e5;
    };

    resize();
    tick();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);
    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("pointerleave", onLeave);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerleave", onLeave);
    };
  }, [density]);

  return <canvas ref={ref} className={className} aria-hidden="true" />;
}
