"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ProductArt } from "@/components/ProductArt";
import { useAuth } from "@/lib/auth";
import { allOrderKeys } from "@/lib/client";
import { rub } from "@/lib/format";
import { NEXT_STEP, fmtDay, statusTone } from "@/lib/order";
import type { OrderView } from "@/lib/types";

export default function AccountOrders() {
  const { call } = useAuth();
  const [orders, setOrders] = useState<OrderView[] | null>(null);
  const [claimed, setClaimed] = useState(0);

  useEffect(() => {
    // Заказы, оформленные в этом браузере без входа, привязываем к аккаунту по их ключам.
    const local = allOrderKeys();
    const load = local.length
      ? call<OrderView[]>("/api/me/orders/claim", { method: "POST", body: JSON.stringify({ orders: local }) })
      : call<OrderView[]>("/api/me/orders");
    load.then((list) => {
      setOrders(list);
      setClaimed(list.filter((o) => local.some((l) => l.number === o.number)).length);
    }).catch(() => setOrders([]));
  }, [call]);

  if (orders === null) return <div className="skel" style={{ height: 240 }} />;
  if (!orders.length) {
    return (
      <div className="empty">
        <b>Заказов пока нет</b>
        <span>Оформленные после входа заказы появятся здесь и будут доступны на любом устройстве.</span>
        <Link className="btn" href="/catalog">Перейти в каталог</Link>
      </div>
    );
  }

  const active = orders.filter((o) => !["COMPLETED", "CANCELLED"].includes(o.status));
  const past = orders.filter((o) => ["COMPLETED", "CANCELLED"].includes(o.status));
  return (
    <>
      {claimed ? <div className="note ok">Заказы из этого браузера сохранены в кабинете: {claimed}.</div> : null}
      {active.length ? (
        <section className="stack">
          <h2>Активные</h2>
          {active.map((o) => <Card key={o.number} o={o} />)}
        </section>
      ) : null}
      {past.length ? (
        <section className="stack">
          <h2>Завершённые</h2>
          {past.map((o) => <Card key={o.number} o={o} />)}
        </section>
      ) : null}
    </>
  );
}

function Card({ o }: { o: OrderView }) {
  return (
    <Link href={`/orders/${o.number}`} className="ocard">
      <div className="ocard-arts">
        {o.items.slice(0, 3).map((l) => <ProductArt key={l.lineNo} id={l.productId} title={l.title} category={l.category} size="mini" label="" />)}
      </div>
      <div className="ocard-main">
        <b>Заказ {o.number}</b>
        <span className="small muted">от {fmtDay(o.createdAt)} · {o.pickupPoint}</span>
        {o.status === "AT_PICKUP_POINT" && o.pickupCode ? <span className="small"><b>Код получения {o.pickupCode}</b></span>
          : <span className="small muted ocard-next">{NEXT_STEP[o.status]}</span>}
      </div>
      <div className="ocard-side">
        <span className={`pill ${statusTone(o.status)}`}>{o.status === "AT_PICKUP_POINT" ? "Можно забирать" : o.statusLabel}</span>
        <span className="num">{rub(o.totalRub)}</span>
      </div>
    </Link>
  );
}
