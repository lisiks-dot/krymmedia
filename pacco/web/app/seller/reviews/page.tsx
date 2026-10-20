"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ReviewReplyForm } from "@/components/ReviewReplyForm";
import { Stars } from "@/components/Stars";
import { useAuth } from "@/lib/auth";
import type { Review } from "@/lib/types";

const day = (iso: string) => new Date(iso).toLocaleDateString("it-IT", { day: "numeric", month: "short", year: "numeric" });

export default function SellerReviews() {
  const { call } = useAuth();
  const [items, setItems] = useState<Review[] | null>(null);
  const [filter, setFilter] = useState<"all" | "open">("all");

  const load = useCallback(() => {
    call<Review[]>("/api/seller/reviews").then(setItems).catch(() => setItems([]));
  }, [call]);
  useEffect(load, [load]);

  const unanswered = items?.filter((r) => !r.replies.some((x) => x.role === "SELLER")) ?? [];
  const shown = filter === "open" ? unanswered : items ?? [];
  const avg = items?.length ? items.reduce((s, r) => s + r.rating, 0) / items.length : 0;

  return (
    <>
      <h1>Recensioni</h1>
      <p className="muted" style={{ margin: 0 }}>
        Solo i clienti che hanno ritirato l&apos;ordine possono lasciare una recensione. Le risposte sono pubbliche e appaiono sotto la recensione sulla pagina del prodotto.
      </p>
      {items?.length ? (
        <div className="kpis">
          <div className="kpi"><span className="small muted">Valutazione media</span><b className="num">{avg.toFixed(1).replace(".", ",")}</b><Stars value={avg} /></div>
          <div className="kpi"><span className="small muted">Recensioni</span><b className="num">{items.length}</b></div>
          <div className="kpi"><span className="small muted">Senza risposta</span><b className="num">{unanswered.length}</b></div>
        </div>
      ) : null}
      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={filter === "all"} onClick={() => setFilter("all")}>Tutte</button>
        <button type="button" role="tab" aria-selected={filter === "open"} onClick={() => setFilter("open")}>Senza risposta{unanswered.length ? ` (${unanswered.length})` : ""}</button>
      </div>
      {items === null ? <div className="skel" style={{ height: 200 }} /> : !shown.length ? (
        <div className="empty"><b>{items.length ? "Hai risposto a tutte le recensioni" : "Ancora nessuna recensione"}</b></div>
      ) : (
        <div className="stack">
          {shown.map((r) => (
            <article key={r.id} className={`panel review${r.hidden ? " is-hidden" : ""}`}>
              <header>
                <Stars value={r.rating} />
                <b>{r.author}</b>
                <span className="small muted">{day(r.createdAt)}{r.size ? `, taglia ${r.size}` : ""}</span>
                <Link className="small" href={`/p/${r.productId}#reviews`} target="_blank">{r.productTitle}</Link>
                {r.hidden ? <span className="pill bad">Nascosta dalla moderazione</span> : null}
              </header>
              <p lang="ru">{r.text}</p>
              {r.replies.map((x) => (
                <div key={x.id} className={`reply ${x.role === "ADMIN" ? "staff" : ""}`}>
                  <b>{x.role === "ADMIN" ? "Risposta di ilpacco" : "La tua risposta"}</b>
                  <span className="small muted">{day(x.createdAt)}</span>
                  <p>{x.text}</p>
                </div>
              ))}
              <ReviewReplyForm locale="it" onSend={async (text) => {
                const next = await call<Review>(`/api/seller/reviews/${r.id}/replies`, { method: "POST", body: JSON.stringify({ text }) });
                setItems((prev) => prev?.map((x) => (x.id === next.id ? next : x)) ?? prev);
              }} />
            </article>
          ))}
        </div>
      )}
    </>
  );
}
