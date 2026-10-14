"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { PayoutSummary, Payouts } from "@/lib/cabinet";
import { eur } from "@/lib/format";
import { useSeller } from "@/lib/seller-context";

export default function SellerPayouts() {
  const { call } = useAuth();
  const seller = useSeller();
  const [p, setP] = useState<Payouts | null>(null);
  const [history, setHistory] = useState<PayoutSummary[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    call<Payouts>("/api/seller/payouts").then(setP).catch(() => setError(true));
    call<PayoutSummary[]>("/api/seller/payouts/history").then(setHistory).catch(() => setHistory([]));
  }, [call]);

  if (error) return <div className="empty">Dati non disponibili. Riprova tra un minuto.</div>;
  if (!p) return <div className="skel" style={{ height: 240 }} />;
  const waiting = history?.filter((h) => h.status === "READY") ?? [];

  return (
    <>
      <div className="page-head">
        <h1>Pagamenti</h1>
        <p className="muted">Paghiamo in euro ogni due settimane gli ordini completati — 14 giorni dopo la consegna, se il cliente non apre un reclamo — sull&apos;IBAN {seller.iban}. Gli articoli rimborsati al cliente non vengono pagati. Per ogni pagamento trovi il rendiconto e la fattura della commissione.</p>
      </div>
      <div className="tiles">
        <div className="tile"><span className="small muted">Nel prossimo pagamento</span><span className="v num">{eur(p.payableNetEurCents)}</span><span className="small muted num">vendite {eur(p.payableGrossEurCents)} − commissione {eur(p.payableCommissionEurCents)}</span></div>
        <div className="tile"><span className="small muted">Bonifico in preparazione</span><span className="v num">{eur(waiting.reduce((s, h) => s + h.netEurCents, 0))}</span><span className="small muted">{waiting.length ? waiting.map((w) => w.number).join(", ") : "nessuno"}</span></div>
        <div className="tile"><span className="small muted">In arrivo (ordini in corso)</span><span className="v num">{eur(p.pendingNetEurCents)}</span><span className="small muted num">vendite {eur(p.pendingGrossEurCents)} − commissione {eur(p.pendingCommissionEurCents)}</span></div>
      </div>
      <section className="panel">
        <h3>Storico pagamenti</h3>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Numero</th><th>Data</th><th className="r">Vendite</th><th className="r">Commissione</th><th className="r">Netto</th><th>Stato</th><th>Documenti</th></tr></thead>
            <tbody>
              {history === null ? <tr><td colSpan={7} className="muted">Caricamento…</td></tr> : history.length === 0 ? (
                <tr><td colSpan={7} className="muted">Ancora nessun pagamento. Il primo arriva dopo il completamento dei primi ordini.</td></tr>
              ) : history.map((h) => (
                <tr key={h.id}>
                  <td className="num"><b>{h.number}</b></td>
                  <td className="num">{new Date(h.createdAt).toLocaleDateString("it-IT")}</td>
                  <td className="r num">{eur(h.grossEurCents)}</td>
                  <td className="r num">− {eur(h.commissionEurCents)}</td>
                  <td className="r num"><b>{eur(h.netEurCents)}</b></td>
                  <td>{h.status === "PAID"
                    ? <span className="pill ok">Pagato {h.paidAt ? new Date(h.paidAt).toLocaleDateString("it-IT") : ""}</span>
                    : <span className="pill warn">In preparazione</span>}
                    {h.bankRef ? <div className="small muted">{h.bankRef}</div> : null}</td>
                  <td><Link href={`/seller/payouts/${h.id}`}>Rendiconto e fattura</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
