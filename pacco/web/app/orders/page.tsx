"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { myOrders } from "@/lib/client";

export default function OrdersPage() {
  const [numbers, setNumbers] = useState<string[] | null>(null);

  useEffect(() => {
    setNumbers(myOrders());
  }, []);

  return (
    <>
      <h1>Заказы</h1>
      {numbers === null ? null : numbers.length === 0 ? (
        <div className="empty">Заказов в этом браузере пока нет. Оформите заказ из корзины, чтобы следить за посылкой.</div>
      ) : (
        <div className="stack">
          {numbers.map((n) => (
            <Link key={n} href={`/orders/${n}`} className="parcel" style={{ textDecoration: "none" }}>
              <div className="row"><b>Заказ {n}</b><span className="muted">Открыть трекинг</span></div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
