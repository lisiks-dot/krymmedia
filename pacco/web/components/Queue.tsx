"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { OpsAction } from "@/components/OpsAction";
import { ProductArt } from "@/components/ProductArt";
import { useAuth } from "@/lib/auth";
import { fmtTime } from "@/lib/order";
import type { OrderStatus, OrderView } from "@/lib/types";

export type Tab = { status: OrderStatus; label: string; hint: string };

/** Очередь рабочего места: вкладки по статусам, в каждой карточке — следующий шаг. */
export function QueuePage({ title, intro, queue, tabs }: { title: string; intro: string; queue: string; tabs: Tab[] }) {
  const { call } = useAuth();
  const [orders, setOrders] = useState<OrderView[] | null>(null);
  const [tab, setTab] = useState<OrderStatus>(tabs[0].status);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState(false);

  const load = useCallback(() => {
    call<OrderView[]>(`/api/ops/queues/${queue}`).then((o) => { setOrders(o); setError(false); }).catch(() => setError(true));
  }, [call, queue]);
  useEffect(load, [load]);

  const count = (s: OrderStatus) => orders?.filter((o) => o.status === s).length ?? 0;
  const list = orders?.filter((o) => o.status === tab) ?? [];
  const current = tabs.find((t) => t.status === tab)!;

  return (
    <>
      <div className="page-head">
        <h1>{title}</h1>
        <p className="muted">{intro}</p>
      </div>
      <div className="tabs" role="tablist" aria-label="Этапы">
        {tabs.map((t) => (
          <button key={t.status} type="button" role="tab" aria-selected={tab === t.status} onClick={() => setTab(t.status)}>
            {t.label}<span className="num">{count(t.status)}</span>
          </button>
        ))}
      </div>
      {message ? <div className="note ok" role="status">{message}</div> : null}
      {error ? <div className="note bad">Очередь не загрузилась. Обновите страницу.</div> : null}
      <p className="small muted">{current.hint}</p>
      {orders === null ? (
        <div className="skel" style={{ height: 160 }} />
      ) : list.length === 0 ? (
        <div className="empty">Здесь пусто — все заказы этого этапа обработаны.</div>
      ) : (
        <div className="stack">
          {list.map((o) => (
            <article key={o.number} className="qcard">
              <header>
                <Link href={`/admin/orders/${o.number}`} className="num"><b>{o.number}</b></Link>
                <span className="small muted">{o.buyerName} · {o.pickupPoint}</span>
                <span className="small muted">с {fmtTime(o.updatedAt)}</span>
              </header>
              <div className="qcard-arts">
                {o.items.map((l) => <ProductArt key={l.lineNo} id={l.productId} title={l.title} category={l.category} size="mini" />)}
              </div>
              <OpsAction order={o} compact onDone={(_, m) => { setMessage(m); load(); }} />
            </article>
          ))}
        </div>
      )}
    </>
  );
}
