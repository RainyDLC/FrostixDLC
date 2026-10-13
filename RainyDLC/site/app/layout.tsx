import type { Metadata } from "next";
import { Inter, Golos_Text, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { I18nProvider } from "@/lib/i18n/provider";
import Intro from "@/components/intro";
import Navbar from "@/components/navbar";

const inter = Inter({ subsets: ["latin", "cyrillic"], display: "swap" });
const grotesk = Golos_Text({ subsets: ["latin", "cyrillic"], weight: ["400", "500", "700", "800", "900"], display: "swap" });
const jetbrains = JetBrains_Mono({ subsets: ["latin", "cyrillic"], display: "swap" });

export const metadata: Metadata = {
  title: "RainyDLC — бесплатный чит-клиент для Minecraft 1.21.11",
  description:
    "RainyDLC — бесплатный чит-клиент для Minecraft 1.21.11. Сервер видит читера. RainyDLC видит игрока.",
  icons: { icon: "/logo.png" },
  openGraph: {
    title: "RainyDLC — бесплатный чит-клиент",
    description: "Нейро-ротации. Умная AttackAura. Невидимость.",
    images: ["/logo.png"],
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru" className="dark">
      <body className={`${inter.className} bg-void text-cream`}>
        <style>{`
          .font-display { font-family: ${grotesk.style.fontFamily}, "Inter", system-ui, sans-serif; }
          .font-mono2 { font-family: ${jetbrains.style.fontFamily}, ui-monospace, monospace; }
        `}</style>
        <I18nProvider>
          <Intro />
          <Navbar />
          {children}
        </I18nProvider>
      </body>
    </html>
  );
}
