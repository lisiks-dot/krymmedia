import Link from "next/link";
import { RecentlyViewed } from "@/components/Recently";
import { IconReturn, IconRuble, IconShield, IconTruck } from "@/components/Icons";
import { ProductArt, paletteOf } from "@/components/ProductArt";
import { ProductCard } from "@/components/ProductCard";
import { goods } from "@/lib/format";
import { serverGet } from "@/lib/server";
import { CATEGORY_LABEL, type Category, type Product } from "@/lib/types";

export const dynamic = "force-dynamic";

const CATS = Object.keys(CATEGORY_LABEL) as Category[];

export default async function Home() {
  const res = await serverGet<Product[]>("/api/products");
  if (!res.ok) {
    return <div className="empty">Каталог обновляется. Обновите страницу через минуту.</div>;
  }
  const all = res.data;
  const first = (c: Category) => all.find((p) => p.category === c);
  const hero = first("SHOES") ?? all[0];
  const beauty = first("BEAUTY");
  const home = first("HOME");
  const newest = [...all].sort((a, b) => b.id - a.id).slice(0, 8);
  const gifts = all.filter((p) => p.priceRub <= 5000).sort((a, b) => a.priceRub - b.priceRub).slice(0, 8);
  const regions = Object.entries(all.reduce<Record<string, number>>((m, p) => {
    if (p.region) m[p.region] = (m[p.region] ?? 0) + 1;
    return m;
  }, {})).sort((a, b) => b[1] - a[1]);
  const brands = [...new Set(all.map((p) => p.sellerName))];

  return (
    <>
      <section className="campaign" aria-label="Подборки">
        <Link href="/catalog?category=SHOES" className="camp camp-main">
          <div className="copy">
            <h1>Обувь и трикотаж от семейных мастерских Италии</h1>
            <p>Цена с доставкой и пошлиной видна сразу. Каждую посылку проверяем в Милане перед отправкой.</p>
            <span className="btn">Смотреть обувь</span>
          </div>
          {hero ? <ProductArt id={hero.id} title={hero.title} category={hero.category} size="hero" label="" /> : null}
        </Link>
        <div className="camp-side">
          {beauty ? (
            <Link href="/catalog?category=BEAUTY" className="camp camp-small" style={{ background: paletteOf(beauty.id).bg, color: "#15171c" }}>
              <div className="copy">
                <h2>Косметика из аптечных лабораторий Тосканы</h2>
                <span className="more">Смотреть косметику</span>
              </div>
              <ProductArt id={beauty.id} title={beauty.title} category={beauty.category} size="hero" label="" />
            </Link>
          ) : null}
          {home ? (
            <Link href="/catalog?category=HOME" className="camp camp-small" style={{ background: paletteOf(home.id).bg, color: "#15171c" }}>
              <div className="copy">
                <h2>Лён и майолика для дома</h2>
                <span className="more">Смотреть товары для дома</span>
              </div>
              <ProductArt id={home.id} title={home.title} category={home.category} size="hero" label="" />
            </Link>
          ) : null}
        </div>
      </section>

      <section className="usp" aria-label="Почему ilpacco">
        <div><IconRuble /><p style={{ margin: 0 }}><b>Цена «под ключ»</b><span>Доставка и пошлина считаются в корзине до оплаты</span></p></div>
        <div><IconShield /><p style={{ margin: 0 }}><b>Проверка в Милане</b><span>Сверяем товар с заказом и фотографируем перед отправкой</span></p></div>
        <div><IconTruck /><p style={{ margin: 0 }}><b>16–24 дня до ПВЗ</b><span>СДЭК, Boxberry или Почта России, экспресс — за 10–18 дней</span></p></div>
        <div><IconReturn /><p style={{ margin: 0 }}><b>Деньги вернём</b><span>Если товар не совпал с карточкой, его не отправят</span></p></div>
      </section>

      <section className="shelf" aria-labelledby="cats-h">
        <div className="shelf-head"><h2 id="cats-h">Категории</h2><Link href="/catalog">Все товары</Link></div>
        <div className="cats">
          {CATS.map((c) => {
            const p = first(c);
            const n = all.filter((x) => x.category === c).length;
            return (
              <Link key={c} href={`/catalog?category=${c}`} className="cat">
                {p ? <ProductArt id={p.id} title={p.title} category={c} label="" /> : <div className="art skel" />}
                <b>{CATEGORY_LABEL[c]}</b>
                <span>{goods(n)}</span>
              </Link>
            );
          })}
        </div>
      </section>

      <section className="shelf" aria-labelledby="new-h">
        <div className="shelf-head"><h2 id="new-h">Новое на ilpacco</h2><Link href="/catalog?sort=new">Смотреть все</Link></div>
        <div className="pgrid scroller">{newest.map((p) => <ProductCard key={p.id} p={p} />)}</div>
      </section>

      <section className="shelf" aria-labelledby="reg-h">
        <div className="shelf-head"><h2 id="reg-h">Италия по регионам</h2></div>
        <div className="regions">
          {regions.map(([r, n]) => (
            <Link key={r} href={`/catalog?region=${encodeURIComponent(r)}`} className="region">
              <b>{r}</b><span>{goods(n)}</span>
            </Link>
          ))}
        </div>
      </section>

      {gifts.length ? (
        <section className="shelf" aria-labelledby="gift-h">
          <div className="shelf-head"><h2 id="gift-h">Подарки до 5 000 ₽</h2><Link href="/catalog?sort=price_asc">Сначала недорогие</Link></div>
          <div className="pgrid scroller">{gifts.map((p) => <ProductCard key={p.id} p={p} />)}</div>
        </section>
      ) : null}

      <RecentlyViewed products={all} />

      <section className="shelf" aria-labelledby="brands-h">
        <div className="shelf-head"><h2 id="brands-h">Производители</h2><Link href="/seller">Продавать на ilpacco</Link></div>
        <div className="brands">
          {brands.map((b) => <Link key={b} href={`/catalog?q=${encodeURIComponent(b)}`}>{b}</Link>)}
        </div>
      </section>
    </>
  );
}
