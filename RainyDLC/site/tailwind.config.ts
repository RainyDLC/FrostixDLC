import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        void: "#0e0718",
        abyss: "#160b26",
        panel: "#1c1033",
        cream: "#eeede7",
        muted: "#a894c4",
        violet: "#9565ff",
        grape: "#6d2d93",
        lilac: "#b48eda",
        rosepink: "#fea6d0",
        mint: "#7bf583",
      },
      fontFamily: {
        display: ['"Golos Text"', "Inter", "system-ui", "sans-serif"],
        mono2: ['"JetBrains Mono"', "ui-monospace", "monospace"],
      },
    },
  },
  plugins: [],
};
export default config;
