import Link from "next/link";

export default function Home() {
  const isSupabaseConfigured = Boolean(
    process.env.NEXT_PUBLIC_SUPABASE_URL && process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY
  );

  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-6 text-center">
      <div className="max-w-2xl w-full rounded-2xl border border-slate-800 bg-[#09101d] p-8 shadow-2xl">
        <h1 className="text-3xl font-extrabold tracking-tight text-sky-400 sm:text-4xl">
          RainyDLC
        </h1>
        <p className="mt-3 text-slate-300">
          Сайт очищен и готов к разработке. База данных и подключение Supabase настроены.
        </p>

        <div className="mt-6 flex items-center justify-center gap-2">
          <span
            className={`inline-block h-3 w-3 rounded-full ${
              isSupabaseConfigured ? "bg-emerald-400 animate-pulse" : "bg-amber-400"
            }`}
          />
          <span className="text-sm font-medium text-slate-300">
            {isSupabaseConfigured
              ? "Supabase подключен (.env)"
              : "Supabase готов к подключению (укажите ключи в .env.local)"}
          </span>
        </div>

        <div className="mt-8 rounded-xl border border-slate-800 bg-[#05080f] p-4 text-left font-mono text-xs text-slate-300">
          <p className="font-semibold text-slate-400 mb-2">// Готовые клиенты БД:</p>
          <p>
            <span className="text-purple-400">import</span> &#123; createClient &#125;{" "}
            <span className="text-purple-400">from</span>{" "}
            <span className="text-emerald-300">&quot;@/lib/supabase/client&quot;</span>;{" "}
            <span className="text-slate-500">// В &apos;use client&apos;</span>
          </p>
          <p className="mt-1">
            <span className="text-purple-400">import</span> &#123; createClient &#125;{" "}
            <span className="text-purple-400">from</span>{" "}
            <span className="text-emerald-300">&quot;@/lib/supabase/server&quot;</span>;{" "}
            <span className="text-slate-500">// В Server Components / Route Handlers</span>
          </p>
        </div>

        <p className="mt-6 text-xs text-slate-500">
          Отредактируйте <code className="text-slate-400">app/page.tsx</code>, чтобы начать верстку нового сайта.
        </p>
      </div>
    </main>
  );
}
