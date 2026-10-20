"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ReviewReplyForm } from "@/components/ReviewReplyForm";
import { Stars } from "@/components/Stars";
import { useAuth } from "@/lib/auth";
import { fmtDay } from "@/lib/order";
import type { Review } from "@/lib/types";

type Filter = "all" | "visible" | "hidden";

export default function AdminReviews() {
  const { call } = useAuth();
  const [filter, setFilter] = useState<Filter>("all");
  const [items, setItems] = useState<Review[] | null>(null);
  const [hiding, setHiding] = useState<number | null>(null);
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(() => {
    setItems(null);
    const qs = filter === "all" ? "" : `?hidden=${filter === "hidden"}`;
    call<Review[]>(`/api/admin/reviews${qs}`).then(setItems).catch(() => setItems([]));
  }, [call, filter]);
  useEffect(load, [load]);

  const replace = (next: Review) => setItems((prev) => prev?.map((x) => (x.id === next.id ? next : x)) ?? prev);

  async function hide(r: Review) {
    replace(await call<Review>(`/api/admin/reviews/${r.id}/hide`, { method: "POST", body: JSON.stringify({ reason }) }));
    setHiding(null);
    setReason("");
    setMessage(`Отзыв ${r.author} на «${r.productTitle}» скрыт с витрины`);
  }

  async function show(r: Review) {
    replace(await call<Review>(`/api/admin/reviews/${r.id}/show`, { method: "POST" }));
    setMessage(`Отзыв ${r.author} снова на витрине`);
  }

  return (
    <>
      <h1>Отзывы</h1>
      <p className="muted" style={{ margin: 0 }}>Отзывы оставляют только покупатели, получившие заказ. Скрытый отзыв пропадает с витрины, но остаётся здесь и у продавца с отметкой.</p>
      {message ? <div className="note ok" role="status">{message}</div> : null}
      <div className="tabs" role="tablist">
        {(["all", "visible", "hidden"] as Filter[]).map((f) => (
          <button key={f} type="button" role="tab" aria-selected={filter === f} onClick={() => setFilter(f)}>
            {{ all: "Все", visible: "На витрине", hidden: "Скрытые" }[f]}
          </button>
        ))}
      </div>
      {items === null ? <div className="skel" style={{ height: 240 }} /> : !items.length ? (
        <div className="empty"><b>Отзывов нет</b></div>
      ) : (
        <div className="stack">
          {items.map((r) => (
            <article key={r.id} className={`panel review${r.hidden ? " is-hidden" : ""}`}>
              <header>
                <Stars value={r.rating} />
                <b>{r.author}</b>
                <span className="small muted">{fmtDay(r.createdAt)}{r.size ? `, размер ${r.size}` : ""}</span>
                <Link className="small" href={`/p/${r.productId}#reviews`} target="_blank">{r.productTitle}</Link>
                {r.orderNumber ? <Link className="small" href={`/admin/orders/${r.orderNumber}`}>Заказ {r.orderNumber}</Link> : null}
                {r.hidden ? <span className="pill bad">Скрыт: {r.hiddenReason}</span> : null}
              </header>
              <p>{r.text}</p>
              {r.replies.map((x) => (
                <div key={x.id} className={`reply ${x.role === "ADMIN" ? "staff" : ""}`}>
                  <b>{x.role === "ADMIN" ? "Ответ ilpacco" : `Продавец, ${x.author}`}</b>
                  <span className="small muted">{fmtDay(x.createdAt)}</span>
                  <p>{x.text}</p>
                </div>
              ))}
              <div className="acts">
                <ReviewReplyForm onSend={async (text) => replace(await call<Review>(`/api/admin/reviews/${r.id}/replies`, { method: "POST", body: JSON.stringify({ text }) }))} />
                {r.hidden ? (
                  <button className="btn ghost sm" type="button" onClick={() => void show(r)}>Вернуть на витрину</button>
                ) : hiding === r.id ? (
                  <span className="acts">
                    <input className="inline-input wide" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Причина: оскорбления, реклама, не о товаре" aria-label="Причина скрытия" />
                    <button className="btn sm danger" type="button" onClick={() => void hide(r)}>Скрыть</button>
                    <button className="btn ghost sm" type="button" onClick={() => setHiding(null)}>Отмена</button>
                  </span>
                ) : (
                  <button className="btn ghost sm" type="button" onClick={() => setHiding(r.id)}>Скрыть с витрины</button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  );
}
