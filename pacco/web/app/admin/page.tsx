"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { OrderView, Payouts, Product, Seller } from "@/lib/cabinet";
import { eur, rub } from "@/lib/format";
import type { Conversation, DisputeView, OrderStatus } from "@/lib/types";

type Tile = { href: string; label: string; value: string | number; hint: string; alert?: boolean };

export default function AdminHome() {
  const { call, has, username } = useAuth();
  const [sellers, setSellers] = useState<Seller[] | null>(null);
  const [pending, setPending] = useState<Product[] | null>(null);
  const [orders, setOrders] = useState<OrderView[] | null>(null);
  const [disputes, setDisputes] = useState<{ dispute: DisputeView }[] | null>(null);
  const [payouts, setPayouts] = useState<{ payouts: Payouts }[] | null>(null);
  const [chats, setChats] = useState<Conversation[] | null>(null);

  useEffect(() => {
    if (has("admin", "moderator")) {
      call<Seller[]>("/api/admin/sellers?status=PENDING").then(setSellers).catch(() => setSellers([]));
      call<Product[]>("/api/admin/products/pending").then(setPending).catch(() => setPending([]));
    }
    if (has("admin", "support", "ops")) call<OrderView[]>("/api/admin/orders?limit=200").then(setOrders).catch(() => setOrders([]));
    if (has("admin", "support")) call<{ dispute: DisputeView }[]>("/api/admin/disputes?status=OPEN").then(setDisputes).catch(() => setDisputes([]));
    if (has("admin", "support", "moderator")) call<Conversation[]>("/api/admin/chats").then(setChats).catch(() => setChats([]));
    if (has("admin", "finance")) call<{ payouts: Payouts }[]>("/api/admin/payouts").then(setPayouts).catch(() => setPayouts([]));
  }, [call, has]);

  const count = (...s: OrderStatus[]) => orders?.filter((o) => s.includes(o.status)).length ?? 0;
  const groups: { title: string; tiles: Tile[] }[] = [];

  if (orders) {
    const paidLate = orders.filter((o) => o.status === "PAID" && Date.now() - new Date(o.updatedAt).getTime() > 20 * 3600_000).length;
    groups.push({
      title: "Заказы по этапам",
      tiles: [
        { href: "/admin/orders", label: "Ждут продавца", value: count("PAID", "CONFIRMED"), hint: paidLate ? `${paidLate} — почти 24 часа без ответа` : "подтверждение до 24 ч", alert: paidLate > 0 },
        ...(has("admin", "ops") ? [
          { href: "/admin/hub", label: "Хаб: приёмка", value: count("TO_HUB"), hint: "едут от продавцов" },
          { href: "/admin/hub", label: "Хаб: упаковка и передача", value: count("AT_HUB", "CONSOLIDATED"), hint: "отправить сегодня" },
          { href: "/admin/delivery", label: "В пути и на таможне", value: count("HANDED_TO_CARRIER", "IN_CUSTOMS", "RELEASED"), hint: "до пункта выдачи" },
          { href: "/admin/delivery", label: "В пунктах выдачи", value: count("AT_PICKUP_POINT"), hint: "ждут покупателя" },
        ] : []),
      ],
    });
    const money = orders.filter((o) => o.status !== "CREATED" && o.status !== "CANCELLED");
    groups.push({
      title: "Деньги покупателей",
      tiles: [
        { href: "/admin/orders", label: "Оплачено", value: rub(money.reduce((s, o) => s + o.totalRub, 0)), hint: `${money.length} заказов из последних ${orders.length}` },
        { href: "/admin/orders", label: "Возвращено", value: rub(orders.reduce((s, o) => s + o.refundRub, 0)), hint: "отказы, проверки, споры" },
      ],
    });
  }
  const care: Tile[] = [];
  if (disputes) care.push({ href: "/admin/disputes", label: "Открытые споры", value: disputes.length, hint: "ответ за 2 рабочих дня", alert: disputes.length > 0 });
  if (chats) {
    const waiting = chats.filter((c) => c.unread > 0).length;
    care.push({ href: "/admin/chats", label: "Переписка", value: chats.length, hint: waiting ? `${waiting} — вопросы к ilpacco без ответа` : "диалоги покупателей и продавцов", alert: waiting > 0 });
  }
  if (sellers) care.push({ href: "/admin/sellers", label: "Заявки продавцов", value: sellers.length, hint: "проверка за 3 дня" });
  if (pending) care.push({ href: "/admin/products", label: "Карточки на модерации", value: pending.length, hint: "SLA — 24 часа" });
  if (payouts) care.push({ href: "/admin/payouts", label: "К выплате продавцам", value: eur(payouts.reduce((s, r) => s + r.payouts.payableNetEurCents, 0)), hint: "в ближайшем реестре" });
  if (care.length) groups.push({ title: "Требует внимания", tiles: care });

  return (
    <>
      <div className="page-head">
        <h1>Сводка</h1>
        <p className="muted">{username}: разделы, доступные вашей роли.</p>
      </div>
      {groups.length === 0 ? <div className="skel" style={{ height: 200 }} /> : groups.map((g) => (
        <section key={g.title} className="stack">
          <h2 className="h-small">{g.title}</h2>
          <div className="tiles">
            {g.tiles.map((t) => (
              <Link key={t.label} className={`tile${t.alert ? " alert" : ""}`} href={t.href}>
                <span className="small muted">{t.label}</span>
                <span className="v num">{t.value}</span>
                <span className="small muted">{t.hint}</span>
              </Link>
            ))}
          </div>
        </section>
      ))}
    </>
  );
}
