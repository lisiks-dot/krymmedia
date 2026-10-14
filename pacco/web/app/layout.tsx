import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.PUBLIC_URL ?? "https://ilpacco.ru"),
  title: { default: "ilpacco — итальянские товары с доставкой в Россию", template: "%s — ilpacco" },
  description:
    "Одежда, обувь, косметика и товары для дома от небольших итальянских производителей. Цена с доставкой и пошлиной видна сразу, каждую посылку проверяем в Милане.",
  openGraph: { siteName: "ilpacco.ru", locale: "ru_RU", type: "website" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#0f1115" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;500;600;700&family=Prata&display=swap" />
      </head>
      <body>{children}</body>
    </html>
  );
}
