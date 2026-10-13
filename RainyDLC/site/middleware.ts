import { NextResponse, type NextRequest } from "next/server";
import { createServerClient } from "@supabase/ssr";

/* ── Простой rate-limit: скользящее окно в памяти ──────────
 * Best-effort защита от перебора: на serverless каждый инстанс
 * держит своё окно, поэтому это дополнение к встроенным
 * лимитам Supabase Auth, а не замена им. */
const WINDOW_MS = 60_000;
const MAX_REQ = 60;
const hits = new Map<string, number[]>();

function rateLimited(ip: string): boolean {
  const now = Date.now();
  const arr = (hits.get(ip) ?? []).filter((t) => now - t < WINDOW_MS);
  arr.push(now);
  hits.set(ip, arr);
  // чистим старые записи, чтобы не текла память
  if (hits.size > 5000) {
    hits.forEach((v, k) => {
      if (v.length === 0 || now - v[v.length - 1] > WINDOW_MS) hits.delete(k);
    });
  }
  return arr.length > MAX_REQ;
}

const MAINT_ALLOW = ["/maintenance", "/auth", "/admin"];

function isAllowedDuringMaintenance(path: string) {
  return (
    MAINT_ALLOW.some((p) => path === p || path.startsWith(p + "/")) ||
    path.startsWith("/api/admin")
  );
}

export async function middleware(request: NextRequest) {
  // Middleware НИКОГДА не должен ронять сайт: любая неожиданная ошибка
  // (edge-рантайм, недоступная БД, кривые env) → просто пропускаем запрос.
  try {
    return await handle(request);
  } catch (err) {
    console.error("[middleware] fatal, passing through:", err);
    return NextResponse.next();
  }
}

async function handle(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // ── rate limit для API ──
  if (pathname.startsWith("/api/")) {
    const ip =
      request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
      request.headers.get("x-real-ip") ||
      "unknown";
    if (rateLimited(ip)) {
      return NextResponse.json({ error: "too_many_requests" }, { status: 429 });
    }
  }

  // ── supabase: обновление сессии + доступ к флагу техработ ──
  let response = NextResponse.next({ request });

  // ── режим техработ: env (хостинг) перекрывает всё; иначе флаг из БД ──
  let maintenance = process.env.SITE_MAINTENANCE === "true";
  let maintMessage = "";

  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseAnon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

  if (supabaseUrl && supabaseAnon) {
    const supabase = createServerClient(supabaseUrl, supabaseAnon, {
      cookies: {
        getAll: () => request.cookies.getAll(),
        setAll: (cookiesToSet) => {
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
          response = NextResponse.next({ request });
          cookiesToSet.forEach(({ name, value, options }) =>
            response.cookies.set(name, value, options),
          );
        },
      },
    });

    if (!maintenance) {
      try {
        const { data } = await supabase
          .from("site_settings")
          .select("maintenance, maintenance_message")
          .eq("id", 1)
          .single();
        maintenance = !!data?.maintenance;
        maintMessage = data?.maintenance_message || "";
      } catch {
        /* БД недоступна — не роняем сайт */
      }
    }
  }

  if (maintenance && !isAllowedDuringMaintenance(pathname)) {
    const url = request.nextUrl.clone();
    url.pathname = "/maintenance";
    if (maintMessage) url.searchParams.set("msg", maintMessage);
    return NextResponse.redirect(url);
  }

  return response;
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|logo.png|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
