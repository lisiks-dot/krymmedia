"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { OpsAction } from "@/components/OpsAction";
import { LineRow, Money, StageBar, Timeline } from "@/components/Order";
import { Photos } from "@/components/Photos";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { fmtDay, statusTone } from "@/lib/order";
import { MODE_LABEL, type NotificationView, type OrderView } from "@/lib/types";

const STAFF_ACTORS: Record<string, string> = {
  buyer: "Покупатель", seller: "Продавец", hub: "Хаб", carrier: "Перевозчик", customs: "Таможня", pickup: "ПВЗ", support: "Поддержка", system: "Система",
};

export default function AdminOrder() {
  const { no } = useParams<{ no: string }>();
  const { call, has, bearer } = useAuth();
  const [notes, setNotes] = useState<NotificationView[] | null>(null);
  const [order, setOrder] = useState<OrderView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);
  const [panel, setPanel] = useState<"none" | "cancel" | "advance">("none");
  const [reason, setReason] = useState("");

  const load = useCallback(() => {
    call<OrderView>(`/api/admin/orders/${encodeURIComponent(no)}`).then(setOrder).catch(() => setError("Заказ не найден"));
    if (has("admin", "support")) call<NotificationView[]>(`/api/admin/notifications?order=${encodeURIComponent(no)}`).then(setNotes).catch(() => setNotes([]));
  }, [call, has, no]);
  useEffect(load, [load]);

  async function support(path: string, body: unknown, ok: string) {
    setMessage(null);
    try {
      setOrder(await call<OrderView>(`/api/admin/orders/${no}/${path}`, { method: "POST", body: JSON.stringify(body ?? {}) }));
      setMessage({ tone: "ok", text: ok });
      setPanel("none");
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не получилось" });
    }
  }

  if (error) return <div className="empty">{error}. <Link href="/admin/orders">К списку заказов</Link></div>;
  if (!order) return <div className="skel" style={{ height: 400 }} />;

  const ops = has("admin", "ops");
  const sup = has("admin", "support");
  const cancellable = !["HANDED_TO_CARRIER", "IN_CUSTOMS", "RELEASED", "AT_PICKUP_POINT", "RECEIVED", "COMPLETED", "CANCELLED"].includes(order.status);

  return (
    <>
      <nav className="crumbs"><Link href="/admin/orders">Заказы</Link><span>/</span><span>{order.number}</span></nav>
      <header className="order-head">
        <div>
          <h1>{order.number}</h1>
          <span className="muted">от {fmtDay(order.createdAt)} · {MODE_LABEL[order.mode].name} · {order.paymentMethod === "SBP" ? "СБП" : order.paymentMethod === "MIR" ? "МИР" : "не оплачен"}</span>
        </div>
        <span className={`pill big ${statusTone(order.status)}`}>{order.statusLabel}</span>
      </header>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}

      <div className="cols">
        <div className="stack">
          <section className="panel">
            <StageBar status={order.status} />
            {ops && !["CREATED", "PAID", "CONFIRMED", "COMPLETED", "CANCELLED"].includes(order.status) ? (
              <>
                <h3>Следующий шаг</h3>
                <OpsAction order={order} onDone={(next, m) => { setOrder(next); setMessage({ tone: "ok", text: m }); }} />
              </>
            ) : null}
            {["PAID", "CONFIRMED"].includes(order.status) ? (
              <p className="small muted">Ждём продавца: подтверждение — 24 часа после оплаты, отправка на хаб — 3 рабочих дня.</p>
            ) : null}
            {order.dispute?.status === "OPEN" && sup ? <Link className="btn sm" href="/admin/disputes">Открыт спор — решить</Link> : null}
          </section>

          <section className="panel">
            <h3>Товары</h3>
            {order.items.map((l) => <LineRow key={l.lineNo} line={l} />)}
            {order.parcelWeightG ? <span className="small muted">Вес посылки {(order.parcelWeightG / 1000).toLocaleString("ru-RU")} кг{order.carrierTracking ? ` · трек ${order.carrierTracking}` : ""}</span> : null}
          </section>

          {order.photos.length ? (
            <section className="panel">
              <h3>Фото</h3>
              <Photos photos={order.photos} src={(id) => `/api/admin/orders/${encodeURIComponent(order.number)}/photos/${id}`} headers={bearer}
                caption={(p) => `${p.kind === "HUB" ? "Хаб" : "Покупатель"}${p.lineNo !== null ? ` · ${order.items[p.lineNo]?.title ?? ""}` : ""}`} />
            </section>
          ) : null}

          {notes && notes.length ? (
            <section className="panel">
              <h3>Уведомления покупателю</h3>
              <ul className="feed compact">
                {notes.map((n) => (
                  <li key={n.id}>
                    <span className={`pill ${n.status === "FAILED" ? "bad" : n.status === "SENT" ? "ok" : "neutral"}`}>{n.channel === "SMS" ? "СМС" : "Письмо"} · {n.status === "SENT" ? "отправлено" : n.status === "LOGGED" ? "в журнале" : "ошибка"}</span>
                    <div><p>{n.subject ?? n.body}</p><span className="small muted">{n.recipient} · {new Date(n.createdAt).toLocaleString("ru-RU")}{n.error ? ` · ${n.error}` : ""}</span></div>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          <section className="panel">
            <h3>История</h3>
            <Timeline events={order.events} actors={STAFF_ACTORS} />
          </section>
        </div>

        <aside className="stack">
          <section className="summary">
            <h3>Деньги</h3>
            <Money order={order} />
          </section>
          <section className="panel">
            <h3>Покупатель</h3>
            <span>{order.buyerName}</span>
            <span className="num muted">{order.buyerPhone}</span>
            <span className="small muted">{order.pickupPoint}</span>
            {order.storageUntil ? <span className="small muted">Хранение до {fmtDay(order.storageUntil)}</span> : null}
          </section>
          {sup && order.status !== "COMPLETED" && order.status !== "CANCELLED" ? (
            <section className="panel">
              <h3>Поддержка</h3>
              {cancellable ? (
                panel === "cancel" ? (
                  <div className="stack tight">
                    <label className="small" htmlFor="why">Причина для покупателя</label>
                    <input id="why" className="inline-input wide" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Например: по просьбе покупателя" />
                    <div className="acts">
                      <button className="btn sm danger" type="button" onClick={() => support("cancel", { reason }, "Заказ отменён, деньги вернутся покупателю")}>Отменить и вернуть деньги</button>
                      <button className="btn sm ghost" type="button" onClick={() => setPanel("none")}>Назад</button>
                    </div>
                  </div>
                ) : <button className="btn sm ghost" type="button" onClick={() => setPanel("cancel")}>Отменить заказ</button>
              ) : <span className="small muted">Посылка уже у перевозчика: отмена невозможна, только спор после получения.</span>}
              {order.status !== "CREATED" ? (
                panel === "advance" ? (
                  <div className="stack tight">
                    <span className="small">Ставит следующий статус без действий партнёров. Только если событие от партнёра потерялось.</span>
                    <div className="acts">
                      <button className="btn sm dark" type="button" onClick={() => support("advance", null, "Статус изменён вручную")}>Перевести дальше</button>
                      <button className="btn sm ghost" type="button" onClick={() => setPanel("none")}>Назад</button>
                    </div>
                  </div>
                ) : <button className="linklike small" type="button" onClick={() => setPanel("advance")}>Сменить статус вручную</button>
              ) : null}
            </section>
          ) : null}
        </aside>
      </div>
    </>
  );
}
