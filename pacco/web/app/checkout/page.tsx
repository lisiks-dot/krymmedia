"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, api, cartId, rememberOrder } from "@/lib/client";
import { notifyCart } from "@/lib/local";
import { rub } from "@/lib/format";
import { MODE_LABEL, type CartView, type DeliveryMode, type OrderView } from "@/lib/types";

export default function CheckoutPage() {
  const router = useRouter();
  const [mode, setMode] = useState<DeliveryMode>("GROUND");
  const [cart, setCart] = useState<CartView | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const m = new URLSearchParams(window.location.search).get("mode");
    const chosen: DeliveryMode = m === "AIR" ? "AIR" : "GROUND";
    setMode(chosen);
    void (async () => {
      const id = await cartId();
      setCart(await api<CartView>(`/api/carts/${id}?mode=${chosen}`));
    })().catch(() => setErrors(["Не удалось загрузить корзину."]));
  }, []);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    setErrors([]);
    try {
      const id = await cartId();
      const order = await api<OrderView>("/api/orders", {
        method: "POST",
        body: JSON.stringify({
          cartId: id,
          mode,
          buyerName: f.get("name"),
          phone: f.get("phone"),
          passport: f.get("passport"),
          inn: f.get("inn"),
          pickupPoint: f.get("pvz"),
          consent: f.get("consent") === "on",
        }),
      });
      rememberOrder(order.number);
      notifyCart();
      await api<OrderView>(`/api/orders/${order.number}/pay`, { method: "POST" });
      router.push(`/orders/${order.number}`);
    } catch (err) {
      setErrors(err instanceof ApiError ? err.messages : ["Не удалось оформить заказ. Проверьте соединение."]);
      setBusy(false);
    }
  }

  const q = cart?.quote;

  return (
    <>
      <h1>Оформление</h1>
      <div className="cols">
        <form onSubmit={submit} noValidate>
          <div className="two">
            <div className="field"><label htmlFor="name">Имя и фамилия как в паспорте</label><input id="name" name="name" required autoComplete="name" /></div>
            <div className="field"><label htmlFor="phone">Телефон</label><input id="phone" name="phone" required autoComplete="tel" placeholder="+7 900 000-00-00" /></div>
          </div>
          <div className="two">
            <div className="field"><label htmlFor="passport">Серия и номер паспорта</label><input id="passport" name="passport" required inputMode="numeric" placeholder="45 00 000000" /></div>
            <div className="field"><label htmlFor="inn">ИНН</label><input id="inn" name="inn" required inputMode="numeric" placeholder="12 цифр" /></div>
          </div>
          <p className="small muted" style={{ margin: 0 }}>Паспорт и ИНН нужны таможне для посылок физлицам. Мы храним только маску паспорта.</p>
          <div className="field">
            <label htmlFor="pvz">Пункт выдачи</label>
            <select id="pvz" name="pvz" defaultValue="СДЭК · Москва, ул. Покровка, 12">
              <option>СДЭК · Москва, ул. Покровка, 12</option>
              <option>Boxberry · Москва, Ленинградский пр-т, 39</option>
              <option>Почта России · Санкт-Петербург, 191186</option>
              <option>СДЭК · Казань, ул. Баумана, 44</option>
            </select>
          </div>
          <label className="choice" htmlFor="consent">
            <input type="checkbox" id="consent" name="consent" defaultChecked />
            <span>Согласен на обработку персональных данных и передачу их таможенному представителю</span>
          </label>
          {errors.length ? <div className="note bad">Проверьте форму: {errors.join("; ")}.</div> : null}
          <div className="controls">
            <button type="button" className="btn ghost" onClick={() => router.push("/cart")}>Назад в корзину</button>
            <button className="btn" type="submit" disabled={busy || !q || q.foodOnly}>
              {busy ? "Оформляем…" : q ? `Оплатить ${rub(q.totalRub)}` : "Оплатить"}
            </button>
          </div>
        </form>
        <aside className="summary" aria-label="Итог заказа">
          <h3>Итог</h3>
          {q ? (
            <>
              <div className="row"><span className="muted">Товары</span><span className="num">{rub(q.goodsRub)}</span></div>
              <div className="row"><span className="muted">Доставка, {MODE_LABEL[q.mode].name.toLowerCase()}</span><span className="num">{rub(q.deliveryRub)}</span></div>
              <div className="row"><span className="muted">Пошлина</span><span className="num">{rub(q.dutyRub)}</span></div>
              {q.vatRub ? <div className="row"><span className="muted">НДС и сбор</span><span className="num">{rub(q.vatRub)}</span></div> : null}
              <div className="row total"><span>К оплате</span><span className="num">{rub(q.totalRub)}</span></div>
              <div className="small muted">Оплата картой МИР или через СБП. В тестовом запуске платёж подтверждается заглушкой.</div>
            </>
          ) : (
            <div className="muted">Корзина пуста или ещё загружается.</div>
          )}
        </aside>
      </div>
    </>
  );
}
