"use client";

import { useSearchParams } from "next/navigation";
import { Wrench } from "lucide-react";
import { Suspense } from "react";
import { useI18n } from "@/lib/i18n/provider";

function Body() {
  const { t } = useI18n();
  const params = useSearchParams();
  const custom = params.get("msg");

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden px-5">
      <div className="bg-grid absolute inset-0" />
      <div className="glow-orb left-1/2 top-1/2 h-[400px] w-[400px] -translate-x-1/2 -translate-y-1/2 bg-violet/15" />
      <div className="relative z-10 max-w-md text-center">
        <div className="floaty mx-auto flex h-20 w-20 items-center justify-center rounded-3xl bg-gradient-to-br from-violet/25 to-indigo-500/25 text-violet">
          <Wrench size={32} />
        </div>
        <h1 className="font-display mt-8 text-3xl font-extrabold text-white md:text-4xl">
          {t.maintenance.title}
        </h1>
        <p className="mt-4 text-zinc-400">{custom || t.maintenance.text}</p>
        <button
          onClick={() => window.location.reload()}
          className="mt-8 rounded-full border border-white/20 px-7 py-3 text-sm font-semibold text-zinc-200 transition-colors hover:border-violet/60 hover:text-white"
        >
          {t.maintenance.refresh}
        </button>
      </div>
    </main>
  );
}

export default function MaintenancePage() {
  return (
    <Suspense>
      <Body />
    </Suspense>
  );
}
