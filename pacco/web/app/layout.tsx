import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "PACCO — Италия посылкой", template: "%s · PACCO" },
  description: "Итальянская одежда, обувь, косметика и товары для дома от небольших производителей с доставкой в Россию.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Onest:wght@400;500;600;700&family=Unbounded:wght@600;700&display=swap"
        />
      </head>
      <body>
        <header className="top">
          <div className="wrap">
            <Link href="/" className="logo">
              PACCO <small>из Италии посылкой</small>
            </Link>
            <nav className="nav" aria-label="Разделы">
              <Link href="/">Каталог</Link>
              <Link href="/cart">Корзина</Link>
              <Link href="/orders">Заказы</Link>
              <Link href="/seller">Продавцам</Link>
            </nav>
          </div>
        </header>
        <div className="demo">
          <div className="wrap">Тестовый запуск: бренды вымышленные, оплата подтверждается заглушкой.</div>
        </div>
        <main className="wrap">{children}</main>
      </body>
    </html>
  );
}
