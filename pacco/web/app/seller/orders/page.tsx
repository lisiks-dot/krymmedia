"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ProductArt } from "@/components/ProductArt";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import type { SellerOrder, SellerOrderLine } from "@/lib/cabinet";
import { eur } from "@/lib/format";
import type { LineState, OrderStatus } from "@/lib/types";

const STATUS_IT: Record<OrderStatus, string> = {
  CREATED: "In attesa di pagamento",
  PAID: "Da confermare",
  CONFIRMED: "Confermato",
  TO_HUB: "In viaggio verso l'hub",
  AT_HUB: "Ricevuto all'hub",
  CONSOLIDATED: "Imballato",
  HANDED_TO_CARRIER: "Affidato al corriere",
  IN_CUSTOMS: "In dogana",
  RELEASED: "Sdoganato",
  AT_PICKUP_POINT: "Al punto di ritiro",
  RECEIVED: "Consegnato",
  COMPLETED: "Completato",
  CANCELLED: "Annullato",
};

const LINE_IT: Record<LineState, { label: string; tone: string }> = {
  PENDING: { label: "Da confermare", tone: "warn" },
  CONFIRMED: { label: "Confermato, da spedire", tone: "warn" },
  SHIPPED: { label: "Spedito all'hub", tone: "neutral" },
  ACCEPTED: { label: "Verificato all'hub", tone: "ok" },
  DECLINED: { label: "Non disponibile, rimborsato", tone: "bad" },
  REJECTED_AT_HUB: { label: "Non conforme all'hub", tone: "bad" },
  RETURNED: { label: "Rimborsato dopo reclamo", tone: "bad" },
};

const TABS: { key: string; label: string; test: (o: SellerOrder) => boolean }[] = [
  { key: "answer", label: "Da confermare", test: (o) => o.needsAnswer },
  { key: "ship", label: "Da spedire", test: (o) => o.needsShipping },
  { key: "transit", label: "In corso", test: (o) => !o.needsAnswer && !o.needsShipping && !["COMPLETED", "CANCELLED"].includes(o.status) },
  { key: "done", label: "Completati", test: (o) => o.status === "COMPLETED" },
  { key: "cancelled", label: "Annullati", test: (o) => o.status === "CANCELLED" },
];

const REASONS = ["Esaurito", "Taglia non disponibile", "Difetto rilevato prima della spedizione"];

function left(iso: string): string {
  const ms = new Date(iso).getTime() - Date.now();
  if (ms <= 0) return "scaduto";
  const h = Math.floor(ms / 3600_000);
  return h >= 1 ? `${h} h` : `${Math.ceil(ms / 60_000)} min`;
}

export default function SellerOrdersPage() {
  const { call } = useAuth();
  const [orders, setOrders] = useState<SellerOrder[] | null>(null);
  const [tab, setTab] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);

  const load = useCallback(() => {
    call<SellerOrder[]>("/api/seller/orders").then((list) => {
      setOrders(list);
      setTab((t) => t ?? (TABS.find((x) => list.some(x.test))?.key ?? "answer"));
    }).catch(() => setOrders([]));
  }, [call]);
  useEffect(load, [load]);

  async function act(o: SellerOrder, path: string, body: unknown, ok: string) {
    setMessage(null);
    try {
      await call(`/api/seller/orders/${o.number}/${path}`, { method: "POST", body: body ? JSON.stringify(body) : undefined });
      setMessage({ tone: "ok", text: ok });
      load();
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Operazione non riuscita" });
    }
  }

  const current = TABS.find((t) => t.key === tab) ?? TABS[0];
  const list = orders?.filter(current.test) ?? [];

  return (
    <>
      <div className="page-head">
        <h1>Ordini</h1>
        <p className="muted">Conferma entro 24 ore dal pagamento, poi spedisci all&apos;hub di Milano entro 3 giorni lavorativi con il foglio di accompagnamento.</p>
      </div>
      <div className="tabs" role="tablist" aria-label="Stato degli ordini">
        {TABS.map((t) => (
          <button key={t.key} type="button" role="tab" aria-selected={current.key === t.key} onClick={() => setTab(t.key)}>
            {t.label}<span className="num">{orders?.filter(t.test).length ?? 0}</span>
          </button>
        ))}
      </div>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      {orders === null ? <div className="skel" style={{ height: 180 }} /> : list.length === 0 ? (
        <div className="empty">{current.key === "answer" ? "Nessun ordine da confermare. Ti avvisiamo via email quando arriva un nuovo ordine." : "Nessun ordine in questa sezione."}</div>
      ) : (
        <div className="stack">
          {list.map((o) => <OrderCard key={o.number} o={o} act={act} />)}
        </div>
      )}
    </>
  );
}

function OrderCard({ o, act }: { o: SellerOrder; act: (o: SellerOrder, path: string, body: unknown, ok: string) => void }) {
  const [declining, setDeclining] = useState(false);
  function decline(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const lines = f.getAll("line").map(Number);
    if (!lines.length) return;
    act(o, "decline", { lines, reason: f.get("reason") }, `Ordine ${o.number}: articoli segnati come non disponibili, il cliente viene rimborsato.`);
  }
  function ship(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    act(o, "ship", { tracking: new FormData(e.currentTarget).get("tracking") }, `Ordine ${o.number}: spedizione registrata. Ti avvisiamo quando l'hub lo riceve.`);
  }
  const pending = o.lines.filter((l) => l.state === "PENDING");

  return (
    <article className="qcard">
      <header>
        <b className="num">{o.number}</b>
        <span className={`pill ${o.needsAnswer ? "warn" : o.status === "CANCELLED" ? "bad" : o.status === "COMPLETED" ? "ok" : "neutral"}`}>
          {o.needsShipping ? "Da spedire" : STATUS_IT[o.status]}
        </span>
        <span className="small muted num">{new Date(o.createdAt).toLocaleString("it-IT", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })}</span>
        {o.needsAnswer ? <span className="small deadline">Conferma entro {left(o.confirmBy)}</span> : null}
        <span className="num qcard-sum">{eur(o.goodsEurCents)}</span>
      </header>
      {o.lines.map((l) => <SellerLine key={l.lineNo} l={l} />)}

      {o.needsAnswer ? (
        declining ? (
          <form className="ops-form" onSubmit={decline}>
            <b>Quali articoli non puoi spedire?</b>
            {pending.map((l) => (
              <label key={l.lineNo} className="choice"><input type="checkbox" name="line" value={l.lineNo} defaultChecked={pending.length === 1} />{l.title}{l.size ? `, taglia ${l.size}` : ""}</label>
            ))}
            <div className="field">
              <label htmlFor={`reason-${o.number}`}>Motivo</label>
              <select id={`reason-${o.number}`} name="reason">{REASONS.map((r) => <option key={r}>{r}</option>)}</select>
            </div>
            <p className="hint">Il cliente viene rimborsato subito per questi articoli. Troppi rifiuti abbassano la posizione dei tuoi prodotti nel catalogo.</p>
            <div className="acts">
              <button className="btn sm danger" type="submit">Conferma non disponibilità</button>
              <button className="btn sm ghost" type="button" onClick={() => setDeclining(false)}>Indietro</button>
            </div>
          </form>
        ) : (
          <div className="acts">
            <button className="btn sm" type="button" onClick={() => act(o, "confirm", null, `Ordine ${o.number} confermato. Spediscilo all'hub entro 3 giorni lavorativi.`)}>Conferma l&apos;ordine</button>
            <button className="btn sm ghost" type="button" onClick={() => setDeclining(true)}>Articolo non disponibile</button>
          </div>
        )
      ) : null}

      {o.needsShipping ? (
        <form className="ops-form row-form" onSubmit={ship}>
          <Link className="btn sm ghost" href={`/seller/orders/${o.number}`} target="_blank">Stampa il foglio di accompagnamento</Link>
          <label className="small" htmlFor={`trk-${o.number}`}>Tracking del corriere</label>
          <input id={`trk-${o.number}`} name="tracking" className="inline-input wide" placeholder="BRT, GLS, Poste…" required />
          <button className="btn sm" type="submit">Segna come spedito</button>
        </form>
      ) : null}
      {o.status === "CONFIRMED" && !o.needsShipping ? <p className="small muted">Spedito. In attesa degli altri venditori dell&apos;ordine.</p> : null}
      {o.status === "PAID" && !o.needsAnswer ? <p className="small muted">Confermato. In attesa degli altri venditori dell&apos;ordine.</p> : null}
    </article>
  );
}

function SellerLine({ l }: { l: SellerOrderLine }) {
  const s = LINE_IT[l.state];
  return (
    <div className={`oline${["DECLINED", "REJECTED_AT_HUB", "RETURNED"].includes(l.state) ? " off" : ""}`}>
      <ProductArt id={l.productId} title={l.title} category="SHOES" size="mini" />
      <div className="oline-main">
        <span className="t">{l.title}{l.size ? `, taglia ${l.size}` : ""}{l.qty > 1 ? ` × ${l.qty}` : ""}</span>
        <span className={`pill ${s.tone}`}>{s.label}</span>
        {l.tracking ? <span className="small muted">Tracking {l.tracking}</span> : null}
        {l.note ? <span className="small muted">{l.note}</span> : null}
      </div>
      <span className="num oline-price">{eur(l.priceEurCents * l.qty)}</span>
    </div>
  );
}
