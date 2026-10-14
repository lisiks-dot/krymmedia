"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { PayoutDocs } from "@/components/PayoutDocs";
import { useAuth } from "@/lib/auth";
import type { PayoutDetail } from "@/lib/cabinet";

export default function AdminPayoutDocs() {
  const { id } = useParams<{ id: string }>();
  const { call } = useAuth();
  const [d, setD] = useState<PayoutDetail | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    call<PayoutDetail>(`/api/admin/payouts/batches/${id}`).then(setD).catch(() => setError(true));
  }, [call, id]);
  if (error) return <div className="empty">Выплата не найдена. <Link href="/admin/payouts">К реестру</Link></div>;
  if (!d) return <div className="skel" style={{ height: 400 }} />;
  return (
    <>
      <nav className="crumbs no-print"><Link href="/admin/payouts">Выплаты</Link><span>/</span><span>{d.payout.number}</span></nav>
      <PayoutDocs d={d} />
    </>
  );
}
