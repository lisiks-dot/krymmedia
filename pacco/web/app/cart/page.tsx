"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ApiError, api, cartId } from "@/lib/client";
import { eur, kg, rub } from "@/lib/format";
import { MODE_LABEL, type CartView, type DeliveryMode } from "@/lib/types";

export default function CartPage() {
  const [cart, setCart] = useState<CartView | null>(null);
  const [mode, setMode] = useState<DeliveryMode>("GROUND");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (m: DeliveryMode) => {
    try {
      const id = await cartId();
      setCart(await api<CartView>(`/api/carts/${id}?mode=${m}`));
      setError(null);
    } catch {
      setError("Не удалось загрузить корзину. Проверьте соединение и обновите страницу.");
    }
  }, []);

  useEffect(() => {
    void load(mode);
  }, [load, mode]);

  async function setQty(productId: number, size: string | null, qty: number) {
    try {
      const id = await cartId();
      setCart(await api<CartView>(`/api/carts/${id}/items?mode=${mode}`, {
        method: "PUT",
        body: JSON.stringify({ productId, size, qty }),
      }));
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Не удалось изменить количество.");
    }
  }

  if (!cart) {
    return error ? <div className="empty">{error}</div> : <div className="empty">Загружаем корзину…</div>;
  }

  const q = cart.quote;
  if (!q || cart.items.length === 0) {
    return (
      <>
        <h1>Корзина</h1>
        <div className="empty">Корзина пуста. <Link href="/" className="btn ghost" style={{ marginLeft: 8 }}>Открыть каталог</Link></div>
      </>
    );
  }

  const over = !q.dutyFree;
  const pct = Math.min(100, (q.goodsEurCents / 20000) * 100);
  const hint = q.removalHintProductId ? cart.items.find((i) => i.productId === q.removalHintProductId) : undefined;

  return (
    <>
      <h1>Корзина</h1>
      <div className="cols">
        <div className="stack">
          <article className="parcel">
            <div className="row" style={{ alignItems: "baseline" }}>
              <h3>Одна посылка на заказ</h3>
              <span className="small muted num">{kg(q.weightGrams)} · хаб Милан</span>
            </div>
            <div>
              <div className={`meter${over ? " over" : ""}`}><i style={{ width: `${pct}%` }} /></div>
              <div className="row small" style={{ marginTop: 4 }}>
                <span className="num" style={{ fontWeight: 600, color: over ? "var(--bad)" : "var(--accent)" }}>
                  {eur(q.goodsEurCents)} из 200 € {over ? `— пошлина ${eur(q.dutyEurCents)}` : "без пошлины"}
                </span>
                {!over ? <span className="muted num">ещё {eur(20000 - q.goodsEurCents)}</span> : null}
              </div>
            </div>
            {hint ? (
              <div className="note warn">
                Уберите «{hint.title}» — посылка уложится в 200 €, и пошлина {rub(q.dutyRub)} исчезнет.
              </div>
            ) : null}
            {cart.items.map((i) => (
              <div className="line" key={`${i.productId}-${i.size ?? ""}`}>
                <div className="mini" style={{ background: `var(--t-${i.category})` }} />
                <div style={{ minWidth: 0 }}>
                  <div>{i.title}{i.size ? `, ${i.size} IT` : ""}</div>
                  <div className="small muted num">{i.sellerName} · {rub(i.unitPriceRub)} за шт.</div>
                </div>
                <div className="qty">
                  <button type="button" aria-label="Уменьшить" onClick={() => setQty(i.productId, i.size, i.qty - 1)}>−</button>
                  <span className="num" style={{ minWidth: 20, textAlign: "center" }}>{i.qty}</span>
                  <button type="button" aria-label="Увеличить" onClick={() => setQty(i.productId, i.size, i.qty + 1)}>+</button>
                </div>
              </div>
            ))}
          </article>
          {error ? <div className="note bad">{error}</div> : null}
        </div>

        <aside className="summary" aria-label="Итог заказа">
          <h3>Итог</h3>
          <div className="row"><span className="muted">Товары</span><span className="num">{rub(q.goodsRub)}</span></div>
          <div className="row"><span className="muted">Доставка, {MODE_LABEL[q.mode].name.toLowerCase()}</span><span className="num">{rub(q.deliveryRub)}</span></div>
          <div className="row">
            <span className="muted">Пошлина</span>
            <span className="num" style={q.dutyRub ? undefined : { color: "var(--accent)", fontWeight: 600 }}>{rub(q.dutyRub)}</span>
          </div>
          {q.vatRub ? <div className="row"><span className="muted">НДС 22 % и сбор 100 ₽</span><span className="num">{rub(q.vatRub)}</span></div> : null}
          <div className="row total"><span>К оплате</span><span className="num">{rub(q.totalRub)}</span></div>
          <div className="small muted num">Курс 1 € = {String(q.rubPerEur).replace(".", ",")} ₽ + буфер 1,5 %</div>

          <fieldset style={{ border: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 8 }}>
            <legend style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>Способ доставки</legend>
            {(["GROUND", "AIR"] as DeliveryMode[]).map((m) => (
              <label key={m} className="choice" htmlFor={`mode-${m}`}>
                <input type="radio" id={`mode-${m}`} name="mode" value={m} checked={mode === m} onChange={() => setMode(m)} />
                <span>
                  {MODE_LABEL[m].name} · {MODE_LABEL[m].days}
                  <br />
                  <span className="small muted num">{rub(q.deliveryRubByMode[m])}</span>
                </span>
              </label>
            ))}
          </fieldset>

          {q.foodOnly ? (
            <div className="note warn">
              Гастрономию отправляем только вместе с товарами других категорий: доставка одной еды стоила бы почти столько же, сколько сами продукты.
            </div>
          ) : null}
          {q.foodOnly ? (
            <button className="btn" type="button" disabled>Перейти к оформлению</button>
          ) : (
            <Link className="btn" href={`/checkout?mode=${mode}`}>Перейти к оформлению</Link>
          )}
        </aside>
      </div>
    </>
  );
}
