"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { PayoutDocs } from "@/components/PayoutDocs";
import { useAuth } from "@/lib/auth";
import type { PayoutDetail } from "@/lib/cabinet";

export default function SellerPayoutDocs() {
  const { id } = useParams<{ id: string }>();
  const { call } = useAuth();
  const [d, setD] = useState<PayoutDetail | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    call<PayoutDetail>(`/api/seller/payouts/history/${id}`).then(setD).catch(() => setError(true));
  }, [call, id]);
  if (error) return <div className="empty">Pagamento non trovato. <Link href="/seller/payouts">Torna ai pagamenti</Link></div>;
  if (!d) return <div className="skel" style={{ height: 400 }} />;
  return (
    <>
      <nav className="crumbs no-print"><Link href="/seller/payouts">Pagamenti</Link><span>/</span><span>{d.payout.number}</span></nav>
      <PayoutDocs d={d} />
    </>
  );
}
