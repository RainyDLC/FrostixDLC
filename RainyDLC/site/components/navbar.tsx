"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useI18n } from "@/lib/i18n/provider";
import { createClient } from "@/lib/supabase/client";
import LanguageSwitcher from "./language-switcher";

export default function Navbar() {
  const { t } = useI18n();
  const [loggedIn, setLoggedIn] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const supabase = createClient();
    supabase.auth.getUser().then(({ data }) => setLoggedIn(!!data.user));
    const { data: sub } = supabase.auth.onAuthStateChange((_e, s) => setLoggedIn(!!s?.user));
    const onScroll = () => setScrolled(window.scrollY > 24);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      sub.subscription.unsubscribe();
      window.removeEventListener("scroll", onScroll);
    };
  }, []);

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-all duration-300 ${
        scrolled ? "border-b border-lilac/15 bg-void/80 backdrop-blur-xl" : "bg-transparent"
      }`}
    >
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-5 md:px-8">
        <Link href="/" className="flex items-center gap-3">
          <img src="/logo.png" alt="RainyDLC" className="h-8 w-8 object-contain" draggable={false} />
          <span className="font-display text-[13px] font-bold uppercase tracking-[0.24em] text-cream">
            RainyDLC
          </span>
        </Link>

        <nav className="font-mono2 hidden items-center gap-8 text-[11px] uppercase tracking-[0.22em] text-muted md:flex">
          <a href="/#mission" className="transition-colors hover:text-cream">{t.nav.mission}</a>
          <a href="/#chapters" className="transition-colors hover:text-cream">{t.nav.chapters}</a>
          <a href="/#download" className="transition-colors hover:text-cream">{t.nav.download}</a>
          <a href="/#faq" className="transition-colors hover:text-cream">{t.nav.faq}</a>
        </nav>

        <div className="flex items-center gap-3">
          <LanguageSwitcher />
          <Link
            href={loggedIn ? "/account" : "/auth"}
            className="font-mono2 bg-cream px-5 py-2.5 text-[11px] font-bold uppercase tracking-[0.2em] text-void transition-colors hover:bg-violet hover:text-white"
          >
            {loggedIn ? t.nav.account : t.nav.login}
          </Link>
        </div>
      </div>
    </header>
  );
}
