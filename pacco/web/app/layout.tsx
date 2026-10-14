import type { Metadata, Viewport } from "next";
import Link from "next/link";
import { HeaderActions, TabBar } from "@/components/Client";
import { IconSearch } from "@/components/Icons";
import { CATEGORY_LABEL, type Category } from "@/lib/types";
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

const CATS = Object.keys(CATEGORY_LABEL) as Category[];

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Golos+Text:wght@400;500;600;700&family=Prata&display=swap" />
      </head>
      <body>
        <a className="sr-only" href="#content">Перейти к содержимому</a>
        <div className="promo">
          <div className="wrap">
            <span>Цена с доставкой и пошлиной — сразу в корзине</span>
            <span>Без пошлины до 200 € в посылке</span>
            <span>Доставка до ПВЗ за 16–24 дня</span>
          </div>
        </div>
        <header className="top">
          <div className="wrap">
            <div className="top-row">
              <Link href="/" className="logo" aria-label="ilpacco.ru — на главную">ilpacco<span>.ru</span></Link>
              <form className="search" action="/catalog" method="get" role="search">
                <IconSearch />
                <label className="sr-only" htmlFor="site-q">Поиск</label>
                <input id="site-q" name="q" type="search" placeholder="Лоферы, кашемир, оливковое масло, Тоскана" />
                <button type="submit">Найти</button>
              </form>
              <HeaderActions />
            </div>
            <nav className="catnav" aria-label="Категории">
              <Link href="/catalog">Все товары</Link>
              {CATS.map((c) => (
                <Link key={c} href={`/catalog?category=${c}`}>{CATEGORY_LABEL[c]}</Link>
              ))}
              <Link href="/help#delivery">Доставка и пошлина</Link>
            </nav>
          </div>
        </header>
        <main id="content" className="wrap">{children}</main>
        <footer className="footer">
          <div className="wrap">
            <div>
              <Link href="/" className="logo">ilpacco<span>.ru</span></Link>
              <p>Товары небольших итальянских производителей с доставкой в Россию. Посылки собираем и проверяем на хабе в Милане.</p>
            </div>
            <div>
              <h3>Покупателям</h3>
              <ul>
                <li><Link href="/help#delivery">Доставка и пошлина</Link></li>
                <li><Link href="/help#returns">Возврат и споры</Link></li>
                <li><Link href="/orders">Отследить заказ</Link></li>
                <li><Link href="/favorites">Избранное</Link></li>
              </ul>
            </div>
            <div>
              <h3>Продавцам</h3>
              <ul>
                <li><Link href="/seller">Стать продавцом</Link></li>
                <li><Link href="/help#rules">Правила для товаров</Link></li>
                <li><Link href="/help#payouts">Выплаты и комиссия</Link></li>
              </ul>
            </div>
            <div>
              <h3>ilpacco.ru</h3>
              <ul>
                <li><Link href="/help#about">О сервисе</Link></li>
                <li><Link href="/help#check">Проверка на хабе</Link></li>
                <li><Link href="/admin">Вход для сотрудников</Link></li>
              </ul>
            </div>
          </div>
        </footer>
        <TabBar />
      </body>
    </html>
  );
}
