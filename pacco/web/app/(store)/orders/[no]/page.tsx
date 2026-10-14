"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { LineRow, Money, StageBar, Timeline } from "@/components/Order";
import { ApiError, orderApi, rememberOrder } from "@/lib/client";
import { rub } from "@/lib/format";
import { DISPUTE_REASONS, NEXT_STEP, eta, fmtDay, statusTone } from "@/lib/order";
import { MODE_LABEL, type OrderView } from "@/lib/types";

export default function OrderPage() {
  const { no } = useParams<{ no: string }>();
  const [order, setOrder] = useState<OrderView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paid, setPaid] = useState(false);
  const [panel, setPanel] = useState<"none" | "cancel" | "dispute">("none");
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    orderApi<OrderView>(no)
      .then((o) => {
        setOrder(o);
        setError(null);
      })
      .catch(() => setError("Заказ не найден или сервис временно недоступен."));
  }, [no]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    // Ссылка из СМС: /orders/PC-10500?key=… — запоминаем ключ в этом браузере.
    const key = params.get("key");
    if (key) {
      rememberOrder(no, key);
      window.history.replaceState(null, "", `/orders/${no}`);
    }
    setPaid(params.get("paid") === "1");
    load();
    const t = window.setInterval(load, 20000);
    return () => window.clearInterval(t);
  }, [no, load]);

  async function act(path: string, body?: unknown, ok?: string) {
    setBusy(true);
    setMessage(null);
    try {
      const next = await orderApi<OrderView>(no, path, { method: "POST", body: body ? JSON.stringify(body) : undefined });
      setOrder(next);
      setPanel("none");
      if (ok) setMessage({ tone: "ok", text: ok });
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не получилось. Попробуйте ещё раз." });
    } finally {
      setBusy(false);
    }
  }

  function openDispute(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const lines = f.getAll("line").map(Number);
    if (!lines.length) {
      setMessage({ tone: "bad", text: "Отметьте товары, с которыми проблема." });
      return;
    }
    void act("/disputes", { lines, reason: f.get("reason"), comment: f.get("comment") }, "Спор открыт. Поддержка ответит в течение 2 рабочих дней.");
  }

  if (error && !order) {
    return (
      <div className="empty">
        <b>{error}</b>
        <Link className="btn ghost" href="/orders">Мои заказы</Link>
      </div>
    );
  }
  if (!order) return <div className="skel" style={{ height: 420 }} />;

  const owner = order.accessKey !== null;
  const tone = statusTone(order.status);
  const until = eta(order);

  return (
    <>
      <nav className="crumbs" aria-label="Навигация"><Link href="/orders">Мои заказы</Link><span>/</span><span>{order.number}</span></nav>
      <header className="order-head">
        <div>
          <h1>Заказ {order.number}</h1>
          <span className="muted">от {fmtDay(order.createdAt)} · {MODE_LABEL[order.mode].name} доставка · {order.pickupPoint}</span>
        </div>
        <span className={`pill big ${tone}`}>{order.statusLabel}</span>
      </header>

      {paid && order.status !== "CREATED" ? (
        <div className="note ok" role="status">Оплата прошла. Продавец подтвердит заказ в течение 24 часов — пришлём СМС.</div>
      ) : null}
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}

      <div className="cols">
        <div className="stack">
          <section className={`status-card ${tone}`}>
            <StageBar status={order.status} />
            <p className="now">{order.status === "CANCELLED" && order.cancelReason ? `${order.cancelReason}.` : NEXT_STEP[order.status]}</p>
            {until ? <p className="small muted">Ориентировочно в пункте выдачи: <b>{until}</b></p> : null}
            {order.carrierTracking ? <p className="small muted">Трек перевозчика: <b className="num">{order.carrierTracking}</b></p> : null}

            {order.status === "AT_PICKUP_POINT" ? (
              owner && order.pickupCode ? (
                <div className="pickup">
                  <span className="small">Код получения</span>
                  <b className="code num" aria-label={`Код ${order.pickupCode.split("").join(" ")}`}>{order.pickupCode.slice(0, 3)} {order.pickupCode.slice(3)}</b>
                  <span className="small">{order.pickupPoint}{order.storageUntil ? ` · хранится до ${fmtDay(order.storageUntil)}` : ""}</span>
                </div>
              ) : (
                <div className="note info">Код получения виден только в браузере, где оформлен заказ, или по ссылке из СМС.</div>
              )
            ) : null}

            <div className="controls">
              {order.canPay ? <Link className="btn" href={`/pay/${order.number}`}>Оплатить {rub(order.totalRub)}</Link> : null}
              {order.canDispute ? (
                <button className="btn ghost" type="button" onClick={() => setPanel(panel === "dispute" ? "none" : "dispute")} aria-expanded={panel === "dispute"}>
                  Проблема с товаром
                </button>
              ) : null}
              {order.canCancel ? (
                <button className="btn ghost" type="button" onClick={() => setPanel(panel === "cancel" ? "none" : "cancel")} aria-expanded={panel === "cancel"}>
                  Отменить заказ
                </button>
              ) : null}
            </div>

            {panel === "cancel" ? (
              <div className="confirm-box">
                <b>Отменить заказ {order.number}?</b>
                <span className="muted">{order.status === "CREATED" ? "Заказ не оплачен, списаний не было." : `Вернём ${rub(order.totalRub)} на карту за 1–3 рабочих дня.`}</span>
                <div className="controls">
                  <button className="btn danger" type="button" disabled={busy} onClick={() => act("/cancel", undefined, "Заказ отменён.")}>Да, отменить</button>
                  <button className="btn ghost" type="button" onClick={() => setPanel("none")}>Оставить заказ</button>
                </div>
              </div>
            ) : null}

            {panel === "dispute" ? (
              <form className="confirm-box" onSubmit={openDispute}>
                <b>Что случилось?</b>
                <fieldset className="plain">
                  <legend className="small muted">Товары</legend>
                  {order.items.filter((l) => !["DECLINED", "REJECTED_AT_HUB", "RETURNED"].includes(l.state)).map((l) => (
                    <label key={l.lineNo} className="choice"><input type="checkbox" name="line" value={l.lineNo} />{l.title}{l.size ? `, ${l.size}` : ""}</label>
                  ))}
                </fieldset>
                <div className="field">
                  <label htmlFor="reason">Причина</label>
                  <select id="reason" name="reason" defaultValue="DAMAGED">
                    {DISPUTE_REASONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="comment">Подробности</label>
                  <textarea id="comment" name="comment" rows={3} maxLength={1000} placeholder="Например: отклеилась подошва на левом ботинке" />
                </div>
                <p className="hint">Отправлять товар обратно в Италию не нужно. Сохраните его и упаковку до решения спора.</p>
                <div className="controls">
                  <button className="btn" type="submit" disabled={busy}>Открыть спор</button>
                  <button className="btn ghost" type="button" onClick={() => setPanel("none")}>Отмена</button>
                </div>
              </form>
            ) : null}
          </section>

          {order.dispute ? (
            <section className={`panel dispute ${order.dispute.status === "OPEN" ? "warn" : order.dispute.status === "REFUNDED" ? "ok" : "bad"}`}>
              <div className="row"><h3>Спор: {order.dispute.reasonLabel.toLowerCase()}</h3><span className="pill neutral">{order.dispute.statusLabel}</span></div>
              {order.dispute.comment ? <p className="muted">«{order.dispute.comment}»</p> : null}
              {order.dispute.status === "REFUNDED" ? <p>Вернули {rub(order.dispute.refundRub)}. Деньги придут на карту за 1–3 рабочих дня.</p> : null}
              {order.dispute.resolution && order.dispute.status !== "REFUNDED" ? <p>{order.dispute.resolution}</p> : null}
            </section>
          ) : null}

          <section className="panel">
            <h3>Товары</h3>
            {order.items.map((l) => <LineRow key={l.lineNo} line={l} />)}
          </section>

          <section className="panel">
            <h3>История</h3>
            <Timeline events={order.events} />
          </section>
        </div>

        <aside className="summary" aria-label="Оплата">
          <h3>Оплата</h3>
          <Money order={order} />
          {order.paymentMethod ? <span className="small muted">{order.paymentMethod === "SBP" ? "Оплачено через СБП" : "Оплачено картой МИР"}</span> : null}
          <hr />
          <span className="small muted">Получатель</span>
          <span>{order.buyerName}</span>
          {!owner ? <span className="small muted">Полные данные и действия с заказом — в браузере, где он оформлен, или по ссылке из СМС.</span> : null}
          <Link className="small" href="/help#returns">Как работают возврат и споры</Link>
        </aside>
      </div>
    </>
  );
}
