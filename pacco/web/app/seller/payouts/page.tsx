"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { Payouts } from "@/lib/cabinet";
import { eur } from "@/lib/format";
import { useSeller } from "@/lib/seller-context";

export default function SellerPayouts() {
  const { call } = useAuth();
  const seller = useSeller();
  const [p, setP] = useState<Payouts | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    call<Payouts>("/api/seller/payouts").then(setP).catch(() => setError(true));
  }, [call]);

  if (error) return <div className="empty">Dati non disponibili. Riprova tra un minuto.</div>;
  if (!p) return <div className="empty">Caricamento…</div>;

  return (
    <>
      <h1>Pagamenti</h1>
      <p className="muted" style={{ margin: 0, maxWidth: "65ch" }}>
        Paghiamo in euro ogni due settimane gli ordini completati — 14 giorni dopo la consegna, se il cliente non apre un reclamo — sull&apos;IBAN {seller.iban}. Gli articoli rimborsati al cliente non vengono pagati. La commissione dipende dalla categoria: 18 % abbigliamento e calzature, 15 % cosmetica, 12 % casa e alimentari.
      </p>
      <div className="tiles">
        <div className="tile"><span className="small muted">Da pagare (ordini completati)</span><span className="v num">{eur(p.payableNetEurCents)}</span><span className="small muted num">vendite {eur(p.payableGrossEurCents)} − commissione {eur(p.payableCommissionEurCents)}</span></div>
        <div className="tile"><span className="small muted">In arrivo (ordini in corso)</span><span className="v num">{eur(p.pendingNetEurCents)}</span><span className="small muted num">vendite {eur(p.pendingGrossEurCents)} − commissione {eur(p.pendingCommissionEurCents)}</span></div>
      </div>
    </>
  );
}
