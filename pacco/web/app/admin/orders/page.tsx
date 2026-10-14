"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth";
import { fmtDate, type OrderView } from "@/lib/cabinet";
import { rub } from "@/lib/format";
import { STAGES, statusTone } from "@/lib/order";
import { MODE_LABEL } from "@/lib/types";

const FILTERS: { key: string; label: string; test: (o: OrderView) => boolean }[] = [
  { key: "all", label: "Все", test: () => true },
  ...STAGES.map((s) => ({ key: s.key, label: s.label, test: (o: OrderView) => s.statuses.includes(o.status) && o.status !== "CREATED" })),
  { key: "new", label: "Не оплачены", test: (o: OrderView) => o.status === "CREATED" },
  { key: "refund", label: "С возвратом", test: (o: OrderView) => o.refundRub > 0 },
  { key: "cancelled", label: "Отменены", test: (o: OrderView) => o.status === "CANCELLED" },
];

export default function AdminOrders() {
  const { call } = useAuth();
  const [orders, setOrders] = useState<OrderView[] | null>(null);
  const [filter, setFilter] = useState("all");
  const [q, setQ] = useState("");

  useEffect(() => {
    call<OrderView[]>("/api/admin/orders?limit=200").then(setOrders).catch(() => setOrders([]));
  }, [call]);

  const list = useMemo(() => {
    const f = FILTERS.find((x) => x.key === filter) ?? FILTERS[0];
    const needle = q.trim().toLowerCase();
    return (orders ?? []).filter((o) => f.test(o) && (!needle || `${o.number} ${o.buyerName} ${o.pickupPoint} ${o.carrierTracking ?? ""}`.toLowerCase().includes(needle)));
  }, [orders, filter, q]);

  return (
    <>
      <div className="page-head">
        <h1>Заказы</h1>
        <input className="inline-input wide" type="search" aria-label="Поиск по заказам" placeholder="Номер, покупатель, ПВЗ, трек"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div className="chips-row">
        {FILTERS.map((f) => (
          <button key={f.key} type="button" className="chip" aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label}<span className="num muted">{orders ? orders.filter(f.test).length : ""}</span>
          </button>
        ))}
      </div>
      <section className="panel">
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Заказ</th><th>Создан</th><th>Покупатель</th><th>Доставка</th><th className="r">Сумма</th><th>Статус</th></tr></thead>
            <tbody>
              {orders === null ? (
                <tr><td colSpan={6} className="muted">Загружаем…</td></tr>
              ) : list.length === 0 ? (
                <tr><td colSpan={6} className="muted">Под фильтр ничего не попало</td></tr>
              ) : list.map((o) => (
                <tr key={o.number} className="clickable">
                  <td className="num"><Link href={`/admin/orders/${o.number}`}><b>{o.number}</b></Link><div className="small muted">{o.items.map((i) => i.title).join(", ")}</div></td>
                  <td className="num">{fmtDate(o.createdAt)}</td>
                  <td>{o.buyerName}<div className="small muted num">{o.buyerPhone}</div></td>
                  <td>{MODE_LABEL[o.mode].name}<div className="small muted">{o.pickupPoint}</div></td>
                  <td className="r num">{rub(o.totalRub)}{o.refundRub ? <div className="small bad-text">− {rub(o.refundRub)}</div> : null}</td>
                  <td><span className={`pill ${statusTone(o.status)}`}>{o.statusLabel}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
