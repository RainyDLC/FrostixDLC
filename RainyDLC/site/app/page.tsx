"use client";

import Hero from "@/components/landing/hero";
import Marquee from "@/components/landing/marquee";
import Mission from "@/components/landing/mission";
import Chapters from "@/components/landing/chapters";
import Quiz from "@/components/landing/quiz";
import Download from "@/components/landing/download";
import Faq from "@/components/landing/faq";
import HalftoneWordmarkFooter from "@/components/ui/halftone-wordmark-footer";
import { useI18n } from "@/lib/i18n/provider";

export default function Home() {
  const { t } = useI18n();

  return (
    <main className="min-h-screen bg-void">
      <Hero />
      <Marquee />
      <Mission />
      <Chapters />
      <Quiz />
      <Download />
      <Faq />
      <div className="w-full">
        <HalftoneWordmarkFooter
          wordmark="rainydlc"
          label={t.footer.label}
          meta={t.footer.meta}
          columns={t.footer.columns}
          backToTopLabel={t.footer.backToTop}
          preset="night"
          palette={{ background: "#0e0718", ink: "#eeede7", warm: "#9565ff" }}
          height="540px"
        />
      </div>
    </main>
  );
}
