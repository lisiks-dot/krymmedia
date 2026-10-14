"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { fmtDay } from "@/lib/order";
import type { OrderView } from "@/lib/types";

/**
 * Следующий операционный шаг по заказу — одна форма на статус.
 * Используется в очередях хаба и доставки и в карточке заказа.
 */
export function OpsAction({ order, onDone, compact = false }: {
  order: OrderView;
  onDone: (next: OrderView, message: string) => void;
  compact?: boolean;
}) {
  const { call } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unclaimed, setUnclaimed] = useState(false);
  const n = order.number;

  async function post(step: string, body: unknown, message: string) {
    setBusy(true);
    setError(null);
    try {
      const next = await call<OrderView>(`/api/ops/orders/${n}/${step}`, { method: "POST", body: JSON.stringify(body ?? {}) });
      onDone(next, message);
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Не удалось выполнить шаг");
    } finally {
      setBusy(false);
    }
  }

  function form(step: string, build: (f: FormData) => unknown, message: (next?: unknown) => string) {
    return (e: FormEvent<HTMLFormElement>) => {
      e.preventDefault();
      const f = new FormData(e.currentTarget);
      void post(step, build(f), message());
    };
  }

  const err = error ? <div className="note bad" role="alert">{error}</div> : null;
  const shipped = order.items.filter((l) => l.state === "SHIPPED");
  const estimate = Math.round((order.items.filter((l) => !["DECLINED", "REJECTED_AT_HUB", "RETURNED"].includes(l.state)).length * 450 + 200) / 10) * 10;

  switch (order.status) {
    case "TO_HUB":
      return (
        <form className="ops-form" onSubmit={form("accept", (f) => ({
          lines: shipped.map((l) => ({ lineNo: l.lineNo, ok: f.get(`ok-${l.lineNo}`) !== "no", note: f.get(`note-${l.lineNo}`) || null })),
        }), () => `${n}: приёмка завершена`)}>
          {!compact ? <b>Приёмка: сверить с заказом и сфотографировать</b> : null}
          {shipped.map((l) => (
            <div key={l.lineNo} className="check-line">
              <span>{l.title}{l.size ? `, ${l.size}` : ""}{l.qty > 1 ? ` × ${l.qty}` : ""}<small className="muted">{l.sellerTracking ? ` · трек ${l.sellerTracking}` : " · товар площадки"}</small></span>
              <div className="segmented sm" role="radiogroup" aria-label={`Результат проверки: ${l.title}`}>
                <label><input type="radio" name={`ok-${l.lineNo}`} value="yes" defaultChecked />Совпадает</label>
                <label><input type="radio" name={`ok-${l.lineNo}`} value="no" />Не совпадает</label>
              </div>
              <input className="inline-input wide" name={`note-${l.lineNo}`} placeholder="Замечание, если есть" aria-label={`Замечание: ${l.title}`} />
            </div>
          ))}
          {err}
          <button className="btn sm" type="submit" disabled={busy}>Принять на хабе</button>
        </form>
      );
    case "AT_HUB":
      return (
        <form className="ops-form row-form" onSubmit={form("pack", (f) => ({ weightGrams: Number(f.get("w")) }), () => `${n}: упакован`)}>
          <label className="small" htmlFor={`w-${n}`}>Вес посылки, г</label>
          <input id={`w-${n}`} name="w" className="inline-input" type="number" min={50} max={31500} step={10} defaultValue={estimate} required />
          <button className="btn sm" type="submit" disabled={busy}>Упаковано</button>
          {err}
        </form>
      );
    case "CONSOLIDATED":
      return (
        <form className="ops-form row-form" onSubmit={form("handover", (f) => ({ tracking: f.get("t") }), () => `${n}: передан перевозчику`)}>
          <label className="small" htmlFor={`t-${n}`}>Трек перевозчика</label>
          <input id={`t-${n}`} name="t" className="inline-input wide" placeholder="CDEK-1000000" required />
          <button className="btn sm" type="submit" disabled={busy}>Передать перевозчику</button>
          {err}
        </form>
      );
    case "HANDED_TO_CARRIER":
      return <Step busy={busy} err={err} label="Прибыл на таможню" onClick={() => post("customs", null, `${n}: на таможне`)} />;
    case "IN_CUSTOMS":
      return <Step busy={busy} err={err} label="Выпущен таможней" onClick={() => post("release", null, `${n}: выпущен`)} />;
    case "RELEASED":
      return <Step busy={busy} err={err} label="Прибыл в пункт выдачи" onClick={() => post("arrive", null, `${n}: в пункте выдачи, покупателю отправлен код`)} />;
    case "AT_PICKUP_POINT": {
      const expired = order.storageUntil ? new Date(order.storageUntil) < new Date(new Date().toDateString()) : false;
      return (
        <div className="ops-form">
          <form className="row-form" onSubmit={form("issue", (f) => ({ code: f.get("code") }), () => `${n}: выдан покупателю`)}>
            <label className="small" htmlFor={`c-${n}`}>Код от покупателя</label>
            <input id={`c-${n}`} name="code" className="inline-input" inputMode="numeric" pattern="[0-9 ]{6,7}" placeholder="000 000" required />
            <button className="btn sm" type="submit" disabled={busy}>Выдать</button>
          </form>
          <span className="small muted">Хранится до {order.storageUntil ? fmtDay(order.storageUntil) : "—"}{expired ? " — срок истёк" : ""}</span>
          {unclaimed ? (
            <div className="acts">
              <span className="small">Вернуть покупателю стоимость товаров и закрыть заказ?</span>
              <button className="btn sm danger" type="button" disabled={busy} onClick={() => post("unclaimed", null, `${n}: не забрали, деньги за товары вернём`)}>Да, не забрали</button>
              <button className="btn sm ghost" type="button" onClick={() => setUnclaimed(false)}>Нет</button>
            </div>
          ) : (
            <button className="btn sm ghost" type="button" onClick={() => setUnclaimed(true)}>Не забрали</button>
          )}
          {err}
        </div>
      );
    }
    case "RECEIVED":
      return (
        <Step busy={busy} err={err} label="Завершить заказ"
          hint={order.disputeUntil ? `Завершится сам ${fmtDay(order.disputeUntil)}, если не будет спора` : undefined}
          onClick={() => post("complete", null, `${n}: завершён`)} ghost />
      );
    default:
      return null;
  }
}

function Step({ label, onClick, busy, err, hint, ghost }: {
  label: string; onClick: () => void; busy: boolean; err: ReactNode; hint?: string; ghost?: boolean;
}) {
  return (
    <div className="ops-form row-form">
      <button className={`btn sm${ghost ? " ghost" : ""}`} type="button" disabled={busy} onClick={onClick}>{label}</button>
      {hint ? <span className="small muted">{hint}</span> : null}
      {err}
    </div>
  );
}
