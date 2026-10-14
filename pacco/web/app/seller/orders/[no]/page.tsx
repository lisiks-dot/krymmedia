"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { SellerOrder } from "@/lib/cabinet";
import { useSeller } from "@/lib/seller-context";

/** Foglio di accompagnamento: va nel pacco per l'hub, così l'operatore abbina la merce all'ordine. */
export default function PackingSlip() {
  const { no } = useParams<{ no: string }>();
  const { call } = useAuth();
  const seller = useSeller();
  const [o, setO] = useState<SellerOrder | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    call<SellerOrder>(`/api/seller/orders/${encodeURIComponent(no)}`).then(setO).catch(() => setError(true));
  }, [call, no]);

  if (error) return <div className="empty">Ordine non trovato.</div>;
  if (!o) return <div className="skel" style={{ height: 300 }} />;
  const lines = o.lines.filter((l) => l.state === "CONFIRMED" || l.state === "SHIPPED");

  return (
    <div className="slip">
      <div className="acts no-print">
        <button className="btn sm" type="button" onClick={() => window.print()}>Stampa</button>
      </div>
      <div className="slip-page">
        <header>
          <span className="logo">ilpacco<span>.ru</span></span>
          <div className="slip-no"><span className="small">Ordine</span><b className="num">{o.number}</b></div>
        </header>
        <div className="two">
          <div><span className="small muted">Destinatario</span><p>ilpacco Hub Milano<br />Via dell&apos;Esempio 12, Magazzino 4<br />20090 Segrate (MI)<br />Ricevimento: lun–ven 9:00–17:00<br /><small className="muted">Indirizzo provvisorio, da confermare con il contratto dell&apos;hub</small></p></div>
          <div><span className="small muted">Mittente</span><p>{seller.legalName}<br />P. IVA {seller.partitaIva}<br />{seller.region ?? ""}</p></div>
        </div>
        <table className="t">
          <thead><tr><th>Riga</th><th>Articolo</th><th>Taglia</th><th className="r">Q.tà</th><th>Verificato all&apos;hub</th></tr></thead>
          <tbody>
            {lines.map((l) => (
              <tr key={l.lineNo}><td className="num">{o.number}/{l.lineNo + 1}</td><td>{l.title}</td><td>{l.size ?? "—"}</td><td className="r num">{l.qty}</td><td>☐</td></tr>
            ))}
          </tbody>
        </table>
        <p className="small">Inserisci questo foglio nel pacco: all&apos;hub abbiniamo la merce all&apos;ordine con il numero e la fotografiamo prima di imballarla per la Russia.</p>
      </div>
    </div>
  );
}
