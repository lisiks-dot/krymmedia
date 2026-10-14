"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ProductArt } from "@/components/ProductArt";
import { myOrders, orderApi } from "@/lib/client";
import { rub } from "@/lib/format";
import { fmtDay, statusTone } from "@/lib/order";
import type { OrderView } from "@/lib/types";

type Row = { number: string; order: OrderView | null };

export default function OrdersPage() {
  const router = useRouter();
  const [rows, setRows] = useState<Row[] | null>(null);

  useEffect(() => {
    const numbers = myOrders();
    setRows(numbers.map((number) => ({ number, order: null })));
    numbers.forEach((number) =>
      orderApi<OrderView>(number)
        .then((order) => setRows((prev) => (prev ?? []).map((r) => (r.number === number ? { number, order } : r))))
        .catch(() => undefined),
    );
  }, []);

  function find(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const n = String(new FormData(e.currentTarget).get("no") ?? "").trim().toUpperCase();
    if (n) router.push(`/orders/${n.startsWith("PC-") ? n : `PC-${n}`}`);
  }

  const active = rows?.filter((r) => !r.order || !["COMPLETED", "CANCELLED"].includes(r.order.status)) ?? [];
  const past = rows?.filter((r) => r.order && ["COMPLETED", "CANCELLED"].includes(r.order.status)) ?? [];

  return (
    <>
      <div className="order-head">
        <h1>Мои заказы</h1>
        <form className="search inline" onSubmit={find} role="search">
          <label className="sr-only" htmlFor="find-no">Номер заказа</label>
          <input id="find-no" name="no" placeholder="Найти по номеру, например PC-10500" />
          <button type="submit">Найти</button>
        </form>
      </div>
      {rows === null ? null : rows.length === 0 ? (
        <div className="empty">
          <b>Здесь появятся ваши заказы</b>
          <span>Заказы хранятся в этом браузере. Если оформляли на другом устройстве — откройте ссылку из СМС.</span>
          <Link className="btn" href="/catalog">Перейти в каталог</Link>
        </div>
      ) : (
        <>
          {active.length ? <section className="stack"><h2>Активные</h2>{active.map((r) => <OrderCard key={r.number} row={r} />)}</section> : null}
          {past.length ? <section className="stack"><h2>Завершённые</h2>{past.map((r) => <OrderCard key={r.number} row={r} />)}</section> : null}
        </>
      )}
    </>
  );
}

function OrderCard({ row }: { row: Row }) {
  const o = row.order;
  return (
    <Link href={`/orders/${row.number}`} className="ocard">
      <div className="ocard-arts">
        {o ? o.items.slice(0, 3).map((l) => <ProductArt key={l.lineNo} id={l.productId} title={l.title} category={l.category} size="mini" label="" />)
          : <div className="skel" style={{ width: 56, height: 64 }} />}
      </div>
      <div className="ocard-main">
        <b>Заказ {row.number}</b>
        <span className="small muted">{o ? `от ${fmtDay(o.createdAt)} · ${o.pickupPoint}` : "Загружаем…"}</span>
      </div>
      {o ? (
        <div className="ocard-side">
          <span className={`pill ${statusTone(o.status)}`}>{o.status === "AT_PICKUP_POINT" ? "Можно забирать" : o.statusLabel}</span>
          <span className="num">{rub(o.totalRub)}</span>
        </div>
      ) : null}
    </Link>
  );
}
