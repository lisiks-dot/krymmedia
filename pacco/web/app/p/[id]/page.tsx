import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { AddToCart } from "@/components/AddToCart";
import { Thumb } from "@/components/Thumb";
import { eur, kg, rub, toRub } from "@/lib/format";
import { serverGet } from "@/lib/server";
import type { Product, Tariffs } from "@/lib/types";

export const dynamic = "force-dynamic";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const res = await serverGet<Product>(`/api/products/${encodeURIComponent(id)}`);
  return res.ok ? { title: res.data.title, description: res.data.description ?? undefined } : { title: "Товар" };
}

export default async function ProductPage({ params }: Props) {
  const { id } = await params;
  const [res, tariffs] = await Promise.all([
    serverGet<Product>(`/api/products/${encodeURIComponent(id)}`),
    serverGet<Tariffs>("/api/pricing/tariffs"),
  ]);
  if (!res.ok) {
    if (res.status === 404 || res.status === 400) notFound();
    return <div className="empty">Товар временно недоступен. Попробуйте обновить страницу через минуту.</div>;
  }
  const p = res.data;

  let delivery: number | null = null;
  let duty: number | null = null;
  let grams = 0;
  if (tariffs.ok) {
    const t = tariffs.data;
    grams = Math.max(100, Math.ceil((p.weightGrams + t.packagingGrams) / 100) * 100);
    delivery = toRub(t.ground.perParcelEur + t.ground.perKgEur * (grams / 1000), t.rubPerEur, t.fxBuffer);
    duty = p.priceEurCents > t.dutyFreeEurCents
      ? toRub(Math.max(p.priceEurCents * 0.05, grams / 10) / 100, t.rubPerEur, t.fxBuffer)
      : 0;
  }

  return (
    <div className="cols">
      <div className="stack">
        <Thumb category={p.category} seller={p.sellerName} region={p.region} ratio="5 / 4" />
      </div>
      <div className="stack">
        <span className="small muted">{p.sellerName}{p.region ? ` · ${p.region}` : ""}</span>
        <h1>{p.title}</h1>
        <div className="row" style={{ justifyContent: "flex-start", alignItems: "baseline" }}>
          <span className="num" style={{ fontFamily: "var(--display)", fontSize: 26, fontWeight: 700 }}>{rub(p.priceRub)}</span>
          <span className="small muted num">{eur(p.priceEurCents)} у продавца</span>
        </div>
        <AddToCart productId={p.id} sizes={p.sizes} />
        <div className="box small">
          {delivery !== null ? (
            <div className="row"><span className="muted">Наземная доставка, если заказ из одного товара</span><span className="num">{rub(delivery)}</span></div>
          ) : null}
          {duty !== null ? (
            <div className="row">
              <span className="muted">Пошлина</span>
              <span className="num" style={{ fontWeight: 600, color: duty ? "var(--warn)" : "var(--accent)" }}>
                {duty ? `${rub(duty)} — дороже 200 €` : "0 ₽"}
              </span>
            </div>
          ) : null}
          <div className="row"><span className="muted">Вес в посылке · код ТН ВЭД</span><span className="num">{kg(grams || p.weightGrams)} · {p.hsCode}</span></div>
        </div>
        {p.description ? <p style={{ margin: 0, maxWidth: "65ch" }}>{p.description}</p> : null}
      </div>
    </div>
  );
}
