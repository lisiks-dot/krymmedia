"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ProductArt } from "@/components/ProductArt";
import { api } from "@/lib/client";
import { rub } from "@/lib/format";
import { MODE_LABEL, ORDER_STEPS, type OrderView } from "@/lib/types";

export default function OrderPage() {
  const { no } = useParams<{ no: string }>();
  const [order, setOrder] = useState<OrderView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    const load = () =>
      api<OrderView>(`/api/orders/${encodeURIComponent(no)}`)
        .then((o) => alive && setOrder(o))
        .catch(() => alive && setError("Заказ не найден или сервис временно недоступен."));
    void load();
    const t = window.setInterval(load, 15000);
    return () => {
      alive = false;
      window.clearInterval(t);
    };
  }, [no]);

  if (error) return <div className="empty">{error}</div>;
  if (!order) return <div className="empty">Загружаем заказ…</div>;

  const step = order.statusStep - 1;
  return (
    <>
      <div className="row" style={{ flexWrap: "wrap", alignItems: "baseline" }}>
        <h1>Заказ {order.number}</h1>
        <span className="num" style={{ fontWeight: 700, fontSize: 18 }}>{rub(order.totalRub)}</span>
      </div>
      <div className="note ok">
        Сейчас: <b>{order.statusLabel}</b> · {MODE_LABEL[order.mode].name.toLowerCase()} доставка · {order.pickupPoint}
      </div>
      {order.status === "CANCELLED" ? null : (
        <ol className="steps">
          {ORDER_STEPS.map((s, k) => (
            <li key={s} className={k < step ? "done" : k === step ? "now" : ""}>
              <span className="num" style={{ fontWeight: 600 }}>{k + 1}</span>{s}
            </li>
          ))}
        </ol>
      )}
      <section className="parcel">
        <h3>Состав</h3>
        {order.items.map((i) => (
          <div className="line" key={`${i.productId}-${i.size ?? ""}`}>
            <ProductArt id={i.productId} title={i.title} category={i.category} size="mini" />
            <div style={{ minWidth: 0 }}>
              {i.title}{i.size ? `, ${i.size} IT` : ""}
              <div className="small muted">{i.sellerName}</div>
            </div>
            <div className="num">× {i.qty}</div>
          </div>
        ))}
        <div className="row"><span className="muted">Товары</span><span className="num">{rub(order.goodsRub)}</span></div>
        <div className="row"><span className="muted">Доставка</span><span className="num">{rub(order.deliveryRub)}</span></div>
        <div className="row"><span className="muted">Пошлина</span><span className="num">{rub(order.dutyRub)}</span></div>
        {order.vatRub ? <div className="row"><span className="muted">НДС и сбор</span><span className="num">{rub(order.vatRub)}</span></div> : null}
      </section>
      <Link href="/" className="btn ghost" style={{ alignSelf: "flex-start" }}>Вернуться в каталог</Link>
    </>
  );
}
