import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AddToCart } from "@/components/AddToCart";
import { AskSeller } from "@/components/AskSeller";
import { Gallery } from "@/components/Gallery";
import { Reviews } from "@/components/Reviews";
import { SeenMarker } from "@/components/Client";
import { IconCheck, IconShield, IconTruck } from "@/components/Icons";
import { ProductCard } from "@/components/ProductCard";
import { RecentlyViewed } from "@/components/Recently";
import { eur, kg, rub, toRub } from "@/lib/format";
import { serverGet } from "@/lib/server";
import { CATEGORY_LABEL, type Product, type Tariffs } from "@/lib/types";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const res = await serverGet<Product>(`/api/products/${encodeURIComponent(id)}`);
  return res.ok
    ? { title: `${res.data.title}, ${res.data.sellerName}`, description: res.data.description ?? undefined }
    : { title: "Товар" };
}

export default async function ProductPage({ params }: Props) {
  const { id } = await params;
  const [res, tariffs, all] = await Promise.all([
    serverGet<Product>(`/api/products/${encodeURIComponent(id)}`),
    serverGet<Tariffs>("/api/pricing/tariffs"),
    serverGet<Product[]>("/api/products"),
  ]);
  if (!res.ok) {
    if (res.status === 404 || res.status === 400) notFound();
    return <div className="empty">Товар временно недоступен. Обновите страницу через минуту.</div>;
  }
  const p = res.data;
  const others = all.ok ? all.data : [];
  const sameSeller = others.filter((x) => x.sellerName === p.sellerName && x.id !== p.id).slice(0, 8);
  const similar = others.filter((x) => x.category === p.category && x.sellerName !== p.sellerName).slice(0, 8);

  let ground: number | null = null;
  let air: number | null = null;
  let duty = 0;
  let grams = p.weightGrams;
  if (tariffs.ok) {
    const t = tariffs.data;
    grams = Math.max(100, Math.ceil((p.weightGrams + t.packagingGrams) / 100) * 100);
    ground = toRub(t.ground.perParcelEur + t.ground.perKgEur * (grams / 1000), t.rubPerEur, t.fxBuffer);
    air = toRub(t.air.perParcelEur + t.air.perKgEur * (grams / 1000), t.rubPerEur, t.fxBuffer);
    duty = p.priceEurCents > t.dutyFreeEurCents
      ? toRub(Math.max(p.priceEurCents * 0.05, grams / 10) / 100, t.rubPerEur, t.fxBuffer)
      : 0;
  }

  return (
    <>
      <SeenMarker id={p.id} />
      <nav className="crumbs" aria-label="Навигация">
        <Link href="/">Главная</Link><span aria-hidden="true">/</span>
        <Link href={`/catalog?category=${p.category}`}>{CATEGORY_LABEL[p.category]}</Link><span aria-hidden="true">/</span>
        <span>{p.title}</span>
      </nav>

      <div className="pdp">
        <Gallery id={p.id} title={p.title} category={p.category} media={p.media ?? []} />

        <div className="pdp-info">
          <div className="stack" style={{ gap: 6 }}>
            <Link className="pdp-brand" href={`/catalog?q=${encodeURIComponent(p.sellerName)}`}>{p.sellerName}</Link>
            <h1 style={{ fontSize: "clamp(26px, 3vw, 36px)" }}>{p.title}</h1>
            {p.region ? <span className="muted">Сделано в регионе {p.region}</span> : null}
          </div>

          <div className="stack" style={{ gap: 6 }}>
            <div className="pdp-price">
              <b className="num">{rub(p.priceRub)}</b>
              <span className="muted num">{eur(p.priceEurCents)} в Италии</span>
            </div>
            <div className="controls">
              {duty ? <span className="badge" style={{ background: "var(--warn-bg)", color: "var(--warn)" }}>Пошлина {rub(duty)}: дороже 200 €</span> : <span className="badge ok">Без пошлины</span>}
              {ground !== null ? <span className="badge brand num">Доставка от {rub(ground)}</span> : null}
            </div>
          </div>

          <AddToCart productId={p.id} title={p.title} sizes={p.sizes} priceLabel={rub(p.priceRub)} />
          <AskSeller productId={p.id} sellerName={p.sellerName} />

          <ul className="facts-list">
            <li><IconShield /><span>Проверим соответствие заказу и сфотографируем товар на хабе в Милане</span></li>
            <li><IconTruck /><span>Наземная доставка до ПВЗ за 16–24 дня, экспресс-авиа — за 10–18 дней</span></li>
            <li><IconCheck /><span>Оплата картой МИР или через СБП. Итог с доставкой и пошлиной — в корзине до оплаты</span></li>
          </ul>

          <div>
            {p.description ? (
              <details className="acc" open>
                <summary>Описание</summary>
                <div>{p.description}</div>
              </details>
            ) : null}
            <details className="acc">
              <summary>Доставка и пошлина</summary>
              <div>
                {ground !== null && air !== null ? (
                  <p style={{ margin: 0 }} className="num">
                    Если в заказе только этот товар: наземная доставка {rub(ground)}, экспресс-авиа {rub(air)}. Вес в посылке {kg(grams)}.{" "}
                    {duty ? `Пошлина ${rub(duty)} — 5 % от стоимости посылки дороже 200 €.` : "Пошлины нет: посылка до 200 € ввозится без неё."}
                  </p>
                ) : null}
                <p style={{ marginBottom: 0 }}>Несколько товаров едут одной посылкой, итог считается в корзине. <Link href="/help#delivery">Подробнее о доставке</Link></p>
              </div>
            </details>
            <details className="acc">
              <summary>О производителе</summary>
              <div>
                {p.sellerName}{p.region ? `, ${p.region}` : ""}. Продавец проверен ilpacco: компания зарегистрирована в Италии, товар сверяем на хабе перед отправкой.{" "}
                <Link href={`/catalog?q=${encodeURIComponent(p.sellerName)}`}>Все товары производителя</Link>
              </div>
            </details>
            <details className="acc">
              <summary>Характеристики</summary>
              <div className="num">Категория: {CATEGORY_LABEL[p.category]}. Код ТН ВЭД: {p.hsCode}. Вес: {kg(p.weightGrams)}.{p.sizes.length ? ` Размеры: ${p.sizes.join(", ")}.` : ""}</div>
            </details>
          </div>
        </div>
      </div>

      <Reviews productId={p.id} />

      {sameSeller.length ? (
        <section className="shelf" aria-labelledby="seller-h">
          <div className="shelf-head"><h2 id="seller-h">Ещё от {p.sellerName}</h2></div>
          <div className="pgrid scroller">{sameSeller.map((x) => <ProductCard key={x.id} p={x} />)}</div>
        </section>
      ) : null}
      {similar.length ? (
        <section className="shelf" aria-labelledby="similar-h">
          <div className="shelf-head"><h2 id="similar-h">Похожие товары</h2><Link href={`/catalog?category=${p.category}`}>Вся категория</Link></div>
          <div className="pgrid scroller">{similar.map((x) => <ProductCard key={x.id} p={x} />)}</div>
        </section>
      ) : null}
      <RecentlyViewed products={others} exclude={p.id} />
    </>
  );
}
