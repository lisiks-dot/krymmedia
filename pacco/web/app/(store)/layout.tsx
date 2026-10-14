import Link from "next/link";
import { HeaderActions, TabBar } from "@/components/Client";
import { IconSearch } from "@/components/Icons";
import { CATEGORY_LABEL, type Category } from "@/lib/types";

const CATS = Object.keys(CATEGORY_LABEL) as Category[];

/** Витрина: промо-полоса, шапка с поиском, подвал и нижнее меню на телефоне. */
export default function StoreLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
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
              <li><Link href="/sell">Стать продавцом</Link></li>
              <li><Link href="/help#rules">Правила для товаров</Link></li>
              <li><Link href="/help#payouts">Выплаты и комиссия</Link></li>
            </ul>
          </div>
          <div>
            <h3>ilpacco.ru</h3>
            <ul>
              <li><Link href="/help#about">О сервисе</Link></li>
              <li><Link href="/legal">Оферта и персональные данные</Link></li>
              <li><Link href="/help#check">Проверка на хабе</Link></li>
              <li><Link href="/admin">Вход для сотрудников</Link></li>
            </ul>
          </div>
        </div>
      </footer>
      <TabBar />
    </>
  );
}
