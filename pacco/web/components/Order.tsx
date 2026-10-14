import type { ReactNode } from "react";
import { ProductArt } from "@/components/ProductArt";
import { rub, eur } from "@/lib/format";
import { ACTOR_LABEL, INACTIVE, LINE_TONE, STAGES, fmtTime, stageIndex } from "@/lib/order";
import type { OrderEvent, OrderLine, OrderStatus, OrderView } from "@/lib/types";
import { MODE_LABEL } from "@/lib/types";

/** Шесть этапов вместо двенадцати статусов. */
export function StageBar({ status }: { status: OrderStatus }) {
  if (status === "CANCELLED") return null;
  const now = stageIndex(status);
  const done = status === "RECEIVED" || status === "COMPLETED";
  return (
    <ol className="stagebar" aria-label="Этапы доставки">
      {STAGES.map((s, i) => {
        const state = i < now || (done && i === now) ? "done" : i === now ? "now" : "";
        return (
          <li key={s.key} className={state} aria-current={state === "now" ? "step" : undefined}>
            <i aria-hidden="true" />
            <span>{s.label}</span>
          </li>
        );
      })}
    </ol>
  );
}

/** История заказа, новые события сверху. */
export function Timeline({ events, actors = ACTOR_LABEL, locale = "ru-RU" }: {
  events: OrderEvent[];
  actors?: Record<string, string>;
  locale?: string;
}) {
  const list = [...events].reverse();
  return (
    <ol className="timeline">
      {list.map((e, i) => (
        <li key={`${e.at}-${i}`} className={i === 0 ? "last" : ""}>
          <time className="num" dateTime={e.at}>{fmtTime(e.at, locale)}</time>
          <div>
            <b>{e.title}</b>
            {e.note ? <p>{e.note}</p> : null}
            <span className="who">{actors[e.actor] ?? e.actor}</span>
          </div>
        </li>
      ))}
    </ol>
  );
}

export function LineRow({ line, children, showSeller = true, price = "rub" }: {
  line: OrderLine;
  children?: ReactNode;
  showSeller?: boolean;
  price?: "rub" | "eur";
}) {
  const off = INACTIVE.includes(line.state);
  return (
    <div className={`oline${off ? " off" : ""}`}>
      <ProductArt id={line.productId} title={line.title} category={line.category} size="mini" />
      <div className="oline-main">
        <span className="t">{line.title}{line.size ? `, ${line.size} IT` : ""}{line.qty > 1 ? ` × ${line.qty}` : ""}</span>
        <span className="small muted">
          {showSeller ? line.sellerName : null}
          {line.sellerTracking ? `${showSeller ? " · " : ""}трек ${line.sellerTracking}` : null}
        </span>
        <span className={`pill ${LINE_TONE[line.state]}`}>{line.stateLabel}</span>
        {line.note ? <span className="small muted">{line.note}</span> : null}
        {children}
      </div>
      <span className={`num oline-price${off ? " struck" : ""}`}>{price === "rub" ? rub(line.lineRub) : eur(line.priceEurCents * line.qty)}</span>
    </div>
  );
}

export function Money({ order }: { order: OrderView }) {
  return (
    <div className="money">
      <div className="row"><span className="muted">Товары</span><span className="num">{rub(order.goodsRub)}</span></div>
      <div className="row"><span className="muted">Доставка, {MODE_LABEL[order.mode].name.toLowerCase()}</span><span className="num">{rub(order.deliveryRub)}</span></div>
      <div className="row"><span className="muted">Пошлина</span><span className={`num${order.dutyRub === 0 ? " good" : ""}`}>{rub(order.dutyRub)}</span></div>
      {order.vatRub ? <div className="row"><span className="muted">НДС и сбор</span><span className="num">{rub(order.vatRub)}</span></div> : null}
      <div className="row total"><span>{order.status === "CREATED" ? "К оплате" : "Оплачено"}</span><span className="num">{rub(order.totalRub)}</span></div>
      {order.refundRub ? (
        <div className="row refund"><span>Возвращено</span><span className="num">− {rub(order.refundRub)}</span></div>
      ) : null}
    </div>
  );
}
