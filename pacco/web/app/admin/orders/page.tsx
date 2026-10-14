"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { fmtDate, type OrderView } from "@/lib/cabinet";
import { rub } from "@/lib/format";
import { MODE_LABEL } from "@/lib/types";

const FILTERS: { key: string; label: string; test: (o: OrderView) => boolean }[] = [
  { key: "all", label: "Все", test: () => true },
  { key: "paid", label: "Ждут продавца", test: (o) => o.status === "PAID" },
  { key: "transit", label: "В пути", test: (o) => o.statusStep >= 3 && o.statusStep <= 10 },
  { key: "done", label: "Доставлены", test: (o) => o.statusStep >= 11 },
  { key: "new", label: "Не оплачены", test: (o) => o.status === "CREATED" },
];

export default function AdminOrders() {
  const { call } = useAuth();
  const [orders, setOrders] = useState<OrderView[] | null>(null);
  const [filter, setFilter] = useState("all");
  const [q, setQ] = useState("");
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);

  const load = useCallback(() => {
    call<OrderView[]>("/api/admin/orders?limit=200").then(setOrders).catch(() => setOrders([]));
  }, [call]);

  useEffect(load, [load]);

  const list = useMemo(() => {
    const f = FILTERS.find((x) => x.key === filter) ?? FILTERS[0];
    const needle = q.trim().toLowerCase();
    return (orders ?? []).filter((o) => f.test(o) && (!needle || `${o.number} ${o.buyerName} ${o.pickupPoint}`.toLowerCase().includes(needle)));
  }, [orders, filter, q]);

  async function advance(o: OrderView) {
    try {
      const next = await call<OrderView>(`/api/admin/orders/${o.number}/advance`, { method: "POST" });
      setMessage({ tone: "ok", text: `${o.number}: статус «${next.statusLabel}»` });
      load();
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не удалось сменить статус" });
    }
  }

  return (
    <>
      <h1>Заказы</h1>
      <div className="controls">
        <input className="inline-input" style={{ width: 240 }} type="search" aria-label="Поиск по заказам" placeholder="Номер, покупатель, ПВЗ"
          value={q} onChange={(e) => setQ(e.target.value)} />
        {FILTERS.map((f) => (
          <button key={f.key} type="button" className="chip" aria-current={filter === f.key} onClick={() => setFilter(f.key)}>{f.label}</button>
        ))}
      </div>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      <section className="panel">
        <p className="small muted" style={{ margin: 0 }}>
          В рабочей системе статусы ставят продавец, хаб, перевозчик и таможня. Кнопка «Следующий статус» — ручное управление для поддержки.
        </p>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Заказ</th><th>Создан</th><th>Покупатель</th><th>Доставка</th><th className="r">Сумма</th><th>Статус</th><th /></tr></thead>
            <tbody>
              {orders === null ? (
                <tr><td colSpan={7} className="muted">Загружаем…</td></tr>
              ) : list.length === 0 ? (
                <tr><td colSpan={7} className="muted">Под фильтр ничего не попало</td></tr>
              ) : list.map((o) => (
                <tr key={o.number}>
                  <td className="num">{o.number}<div className="small muted">{o.items.map((i) => i.title).join(", ")}</div></td>
                  <td className="num">{fmtDate(o.createdAt)}</td>
                  <td>{o.buyerName}</td>
                  <td>{MODE_LABEL[o.mode].name}<div className="small muted">{o.pickupPoint}</div></td>
                  <td className="r num">{rub(o.totalRub)}</td>
                  <td><span className={`pill ${o.statusStep >= 11 ? "ok" : o.status === "CREATED" ? "neutral" : "warn"}`}>{o.statusStep}. {o.statusLabel}</span></td>
                  <td>
                    {o.status !== "CREATED" && o.status !== "COMPLETED" && o.status !== "CANCELLED" ? (
                      <button className="btn ghost sm" type="button" onClick={() => advance(o)}>Следующий статус</button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
