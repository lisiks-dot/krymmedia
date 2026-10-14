"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { OrderView, Product, Seller } from "@/lib/cabinet";
import { rub } from "@/lib/format";

export default function AdminHome() {
  const { call, has } = useAuth();
  const [sellers, setSellers] = useState<Seller[] | null>(null);
  const [pending, setPending] = useState<Product[] | null>(null);
  const [orders, setOrders] = useState<OrderView[] | null>(null);

  useEffect(() => {
    if (has("admin", "moderator")) {
      call<Seller[]>("/api/admin/sellers?status=PENDING").then(setSellers).catch(() => setSellers([]));
      call<Product[]>("/api/admin/products/pending").then(setPending).catch(() => setPending([]));
    }
    if (has("admin", "support")) {
      call<OrderView[]>("/api/admin/orders?limit=200").then(setOrders).catch(() => setOrders([]));
    }
  }, [call, has]);

  const paid = orders?.filter((o) => o.status === "PAID").length ?? 0;
  const inTransit = orders?.filter((o) => o.statusStep >= 4 && o.statusStep <= 10).length ?? 0;
  const gmv = orders?.filter((o) => o.status !== "CREATED" && o.status !== "CANCELLED").reduce((s, o) => s + o.totalRub, 0) ?? 0;

  return (
    <>
      <h1>Обзор</h1>
      <div className="tiles">
        {sellers ? (
          <Link className="tile" href="/admin/sellers"><span className="small muted">Заявки продавцов</span><span className="v num">{sellers.length}</span><span className="small muted">ждут проверки</span></Link>
        ) : null}
        {pending ? (
          <Link className="tile" href="/admin/products"><span className="small muted">Карточки на модерации</span><span className="v num">{pending.length}</span><span className="small muted">SLA — 24 часа</span></Link>
        ) : null}
        {orders ? (
          <>
            <Link className="tile" href="/admin/orders"><span className="small muted">Оплачены, ждут продавца</span><span className="v num">{paid}</span><span className="small muted">подтверждение до 24 ч</span></Link>
            <Link className="tile" href="/admin/orders"><span className="small muted">В пути</span><span className="v num">{inTransit}</span><span className="small muted">от хаба до ПВЗ</span></Link>
            <div className="tile"><span className="small muted">Оплачено покупателями</span><span className="v num">{rub(gmv)}</span><span className="small muted">последние {orders.length} заказов</span></div>
          </>
        ) : null}
      </div>
      {!has("admin", "moderator", "support") ? (
        <div className="panel muted">Для роли «Финансы» разделы выплат и реестров появятся на следующем этапе.</div>
      ) : null}
    </>
  );
}
