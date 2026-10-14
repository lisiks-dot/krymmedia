"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { fmtDate, type SellerOrder } from "@/lib/cabinet";
import { eur } from "@/lib/format";

const STATUS_IT: Record<string, string> = {
  PAID: "Pagato, da confermare",
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

export default function SellerOrdersPage() {
  const { call } = useAuth();
  const [orders, setOrders] = useState<SellerOrder[] | null>(null);
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);

  const load = useCallback(() => {
    call<SellerOrder[]>("/api/seller/orders").then(setOrders).catch(() => setOrders([]));
  }, [call]);
  useEffect(load, [load]);

  async function confirm(o: SellerOrder) {
    try {
      await call(`/api/seller/orders/${o.number}/confirm`, { method: "POST" });
      setMessage({ tone: "ok", text: `Ordine ${o.number} confermato. Spediscilo all'hub di Milano entro 3 giorni lavorativi.` });
      load();
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Conferma non riuscita" });
    }
  }

  const toConfirm = orders?.filter((o) => o.status === "PAID" && !o.confirmedBySeller) ?? [];

  return (
    <>
      <h1>Ordini</h1>
      <p className="muted" style={{ margin: 0 }}>
        Conferma ogni ordine pagato entro 24 ore, poi spedisci la merce all&apos;hub con il foglio di accompagnamento.
      </p>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      {toConfirm.length ? <div className="note warn">Da confermare: {toConfirm.length}</div> : null}
      <section className="panel">
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Ordine</th><th>Data</th><th>I tuoi articoli</th><th className="r">Importo</th><th>Stato</th><th /></tr></thead>
            <tbody>
              {orders === null ? (
                <tr><td colSpan={6} className="muted">Caricamento…</td></tr>
              ) : orders.length === 0 ? (
                <tr><td colSpan={6} className="muted">Ancora nessun ordine pagato</td></tr>
              ) : orders.map((o) => (
                <tr key={o.number}>
                  <td className="num">{o.number}</td>
                  <td className="num">{fmtDate(o.createdAt, "it-IT")}</td>
                  <td>{o.lines.map((l) => `${l.title}${l.size ? ` (${l.size})` : ""} × ${l.qty}`).join("; ")}</td>
                  <td className="r num">{eur(o.goodsEurCents)}</td>
                  <td>
                    <span className={`pill ${o.status === "PAID" && !o.confirmedBySeller ? "warn" : o.status === "COMPLETED" ? "ok" : "neutral"}`}>
                      {o.status === "PAID" && o.confirmedBySeller ? "Confermato, in attesa degli altri venditori" : STATUS_IT[o.status] ?? o.statusLabel}
                    </span>
                  </td>
                  <td>
                    {o.status === "PAID" && !o.confirmedBySeller ? (
                      <button className="btn sm" type="button" onClick={() => confirm(o)}>Conferma</button>
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
