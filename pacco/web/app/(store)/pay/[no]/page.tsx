"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { ApiError, orderApi, orderKey } from "@/lib/client";
import { rub } from "@/lib/format";
import type { OrderView, PaymentMethod } from "@/lib/types";

/**
 * Шаг оплаты. В рабочей системе отсюда покупатель уходит на страницу банка (МИР) или в приложение по QR (СБП),
 * а результат приходит вебхуком эквайринга. В тестовом запуске платёж подтверждает заглушка.
 */
export default function PayPage() {
  const { no } = useParams<{ no: string }>();
  const router = useRouter();
  const [order, setOrder] = useState<OrderView | null>(null);
  const [method, setMethod] = useState<PaymentMethod>("MIR");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [foreign, setForeign] = useState(false);

  useEffect(() => {
    setMethod(new URLSearchParams(window.location.search).get("m") === "SBP" ? "SBP" : "MIR");
    setForeign(!orderKey(no));
    orderApi<OrderView>(no).then(setOrder).catch(() => setError("Заказ не найден."));
  }, [no]);

  async function pay() {
    setBusy(true);
    setError(null);
    try {
      await orderApi<OrderView>(no, "/pay", { method: "POST", body: JSON.stringify({ method }) });
      router.push(`/orders/${no}?paid=1`);
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Платёж не прошёл. Деньги не списаны — попробуйте ещё раз.");
      setBusy(false);
    }
  }

  if (error && !order) return <div className="empty">{error} <Link href="/orders">Мои заказы</Link></div>;
  if (!order) return <div className="paybox"><div className="skel" style={{ height: 320 }} /></div>;

  if (order.status !== "CREATED") {
    return (
      <div className="paybox">
        <h1>Заказ {order.number} уже {order.status === "CANCELLED" ? "отменён" : "оплачен"}</h1>
        <Link className="btn" href={`/orders/${order.number}`}>Открыть заказ</Link>
      </div>
    );
  }

  return (
    <div className="paybox">
      <nav className="crumbs" aria-label="Шаги покупки"><span>Корзина</span><span>/</span><span>Оформление</span><span>/</span><b>Оплата</b></nav>
      <div className="pay-head">
        <span className="muted">Заказ {order.number}</span>
        <b className="num">{rub(order.totalRub)}</b>
        <span className="small muted">включая доставку {rub(order.deliveryRub)}{order.dutyRub ? ` и пошлину ${rub(order.dutyRub)}` : ", пошлина 0 ₽"}</span>
      </div>
      <div className="segmented" role="radiogroup" aria-label="Способ оплаты">
        <button type="button" role="radio" aria-checked={method === "MIR"} onClick={() => setMethod("MIR")}>Карта МИР</button>
        <button type="button" role="radio" aria-checked={method === "SBP"} onClick={() => setMethod("SBP")}>СБП</button>
      </div>
      {method === "MIR" ? (
        <p className="muted">Откроется защищённая страница банка. Данные карты вводятся там — ilpacco их не видит и не хранит.</p>
      ) : (
        <div className="sbp">
          <div className="qr" aria-hidden="true">{Array.from({ length: 49 }, (_, i) => <i key={i} className={(i * 7 + i * i) % 3 === 0 ? "on" : ""} />)}</div>
          <p className="muted">Отсканируйте QR-код камерой телефона или в приложении банка и подтвердите платёж.</p>
        </div>
      )}
      {foreign ? <div className="note warn">Этот заказ оформлен в другом браузере. Оплатить его можно по ссылке из СМС.</div> : null}
      {error ? <div className="note bad" role="alert">{error}</div> : null}
      <button className="btn block" type="button" onClick={pay} disabled={busy || foreign}>
        {busy ? "Ждём подтверждение банка…" : `Оплатить ${rub(order.totalRub)}`}
      </button>
      <p className="small muted">Тестовый запуск: платёж подтверждается без списания денег.</p>
      <Link className="small" href={`/orders/${order.number}`}>Оплатить позже</Link>
    </div>
  );
}
