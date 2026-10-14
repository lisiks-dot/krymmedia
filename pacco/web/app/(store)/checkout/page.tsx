"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { PickupPicker } from "@/components/PickupPicker";
import { ProductArt } from "@/components/ProductArt";
import { ApiError, api, cartId, rememberOrder } from "@/lib/client";
import { optionalToken, whoAmI, type TokenInfo } from "@/lib/session";
import { notifyCart } from "@/lib/local";
import { goods, rub } from "@/lib/format";
import { MODE_LABEL, type BuyerProfile, type CartView, type DeliveryMode, type OrderView, type PaymentMethod, type PickupPoint } from "@/lib/types";

export default function CheckoutPage() {
  const router = useRouter();
  const [mode, setMode] = useState<DeliveryMode>("GROUND");
  const [cart, setCart] = useState<CartView | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [point, setPoint] = useState<PickupPoint | null>(null);
  const [me, setMe] = useState<TokenInfo | null>(null);
  const [profile, setProfile] = useState<BuyerProfile | null>(null);

  // Вошедший покупатель: подставляем данные профиля, заказ привяжется к аккаунту.
  useEffect(() => {
    void (async () => {
      const who = await whoAmI();
      if (!who) return;
      setMe(who);
      const token = await optionalToken();
      if (!token) return;
      const p = await api<BuyerProfile>("/api/me/profile", { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
      if (p?.pickupPointId) setPoint(null);
      setProfile(p);
    })();
  }, []);

  useEffect(() => {
    const m = new URLSearchParams(window.location.search).get("mode");
    const chosen: DeliveryMode = m === "AIR" ? "AIR" : "GROUND";
    setMode(chosen);
    void (async () => {
      const id = await cartId();
      setCart(await api<CartView>(`/api/carts/${id}?mode=${chosen}`));
    })().catch(() => setLoadError(true));
  }, []);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const method = (f.get("method") === "SBP" ? "SBP" : "MIR") as PaymentMethod;
    setBusy(true);
    setErrors([]);
    try {
      const id = await cartId();
      const token = await optionalToken();
      const order = await api<OrderView>("/api/orders", {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: JSON.stringify({
          cartId: id,
          mode,
          buyerName: f.get("name"),
          phone: f.get("phone"),
          passport: f.get("passport"),
          inn: f.get("inn"),
          pickupPointId: point?.id ?? null,
          email: f.get("email") || null,
          consent: f.get("consent") === "on",
        }),
      });
      rememberOrder(order.number, order.accessKey);
      notifyCart();
      router.push(`/pay/${order.number}?m=${method}`);
    } catch (err) {
      setErrors(err instanceof ApiError ? err.messages : ["Не удалось оформить заказ. Проверьте соединение и попробуйте ещё раз."]);
      setBusy(false);
    }
  }

  const q = cart?.quote;
  const empty = cart !== null && cart.items.length === 0;

  if (loadError) return <div className="empty">Не удалось загрузить корзину. Обновите страницу через минуту.</div>;
  if (empty) {
    return (
      <div className="empty">
        <b>Корзина пуста</b>
        <span>Добавьте товары, чтобы оформить заказ.</span>
        <Link className="btn" href="/catalog">Перейти в каталог</Link>
      </div>
    );
  }

  return (
    <>
      <nav className="crumbs" aria-label="Шаги покупки"><Link href="/cart">Корзина</Link><span>/</span><span>Оформление</span><span>/</span><span>Оплата</span></nav>
      <h1>Оформление заказа</h1>
      <div className="cols">
        <form className="checkout" onSubmit={submit} noValidate>
          {me ? (
            <div className="note info">Вы вошли как {me.name || me.username}. Заказ появится в <Link href="/account">личном кабинете</Link> на всех устройствах.</div>
          ) : (
            <div className="note info"><Link href="/account?from=checkout">Войдите</Link>, чтобы заказы были доступны на всех устройствах и данные подставлялись сами. Можно и без входа.</div>
          )}
          <fieldset className="cstep" key={profile ? "with-profile" : "empty"}>
            <legend><span className="n">1</span>Получатель</legend>
            <div className="two">
              <div className="field"><label htmlFor="name">Имя и фамилия как в паспорте</label><input id="name" name="name" required autoComplete="name" defaultValue={profile?.name ?? me?.name ?? ""} /></div>
              <div className="field"><label htmlFor="phone">Телефон</label><input id="phone" name="phone" required type="tel" autoComplete="tel" placeholder="+7 900 000-00-00" defaultValue={profile?.phone ? `+${profile.phone}` : ""} /></div>
            </div>
            <div className="field"><label htmlFor="email">Email <span className="muted">— необязательно</span></label><input id="email" name="email" type="email" autoComplete="email" placeholder="для писем о заказе" defaultValue={profile?.email ?? me?.email ?? ""} /></div>
            <p className="hint">На телефон придут СМС о статусе и код получения, на почту — письма с подробностями.</p>
          </fieldset>

          <fieldset className="cstep">
            <legend><span className="n">2</span>Данные для таможни</legend>
            <div className="two">
              <div className="field"><label htmlFor="passport">Серия и номер паспорта</label><input id="passport" name="passport" required inputMode="numeric" placeholder="45 00 000000" autoComplete="off" /></div>
              <div className="field"><label htmlFor="inn">ИНН</label><input id="inn" name="inn" required inputMode="numeric" placeholder="12 цифр" autoComplete="off" /></div>
            </div>
            <p className="hint">Таможня требует их для посылок физлицам. Мы передаём данные таможенному представителю и храним только последние цифры паспорта.</p>
          </fieldset>

          <fieldset className="cstep">
            <legend><span className="n">3</span>Пункт выдачи</legend>
            <PickupPicker key={profile?.pickupPointId ?? "default"} value={point?.id ?? profile?.pickupPointId ?? null} onChange={setPoint} />
            <p className="hint">Посылка хранится в пункте 7 дней. Примерить и отказаться от одежды можно там, где есть примерочная.</p>
          </fieldset>

          <fieldset className="cstep">
            <legend><span className="n">4</span>Оплата</legend>
            <div className="options two-up">
              <label className="option" htmlFor="pay-mir">
                <input type="radio" id="pay-mir" name="method" value="MIR" defaultChecked />
                <span><b>Карта МИР</b><span className="small muted">Любой российский банк</span></span>
              </label>
              <label className="option" htmlFor="pay-sbp">
                <input type="radio" id="pay-sbp" name="method" value="SBP" />
                <span><b>СБП</b><span className="small muted">По QR-коду в приложении банка</span></span>
              </label>
            </div>
          </fieldset>

          <label className="choice" htmlFor="consent">
            <input type="checkbox" id="consent" name="consent" defaultChecked />
            <span>Согласен с <Link href="/legal#offer">условиями оферты</Link> и на <Link href="/legal#privacy">обработку персональных данных</Link>, включая передачу таможенному представителю</span>
          </label>
          {errors.length ? <div className="note bad" role="alert">Проверьте форму: {errors.join("; ")}.</div> : null}
          <div className="controls">
            <button className="btn" type="submit" disabled={busy || !q || q.foodOnly || !point}>
              {busy ? "Оформляем…" : q ? `Перейти к оплате ${rub(q.totalRub)}` : "Перейти к оплате"}
            </button>
            <Link className="btn ghost" href="/cart">Вернуться в корзину</Link>
          </div>
        </form>

        <aside className="summary" aria-label="Ваш заказ">
          <h3>{cart ? goods(cart.items.reduce((s, i) => s + i.qty, 0)) : "Ваш заказ"}</h3>
          {cart ? (
            <ul className="mini-list">
              {cart.items.map((i) => (
                <li key={`${i.productId}-${i.size ?? ""}`}>
                  <ProductArt id={i.productId} title={i.title} category={i.category} size="mini" />
                  <span>{i.title}{i.size ? `, ${i.size}` : ""}<span className="small muted">{i.qty > 1 ? `${i.qty} шт. · ` : ""}{rub(i.unitPriceRub * i.qty)}</span></span>
                </li>
              ))}
            </ul>
          ) : <div className="skel" style={{ height: 120 }} />}
          {q ? (
            <>
              <div className="row"><span className="muted">Товары</span><span className="num">{rub(q.goodsRub)}</span></div>
              <div className="row"><span className="muted">Доставка, {MODE_LABEL[q.mode].name.toLowerCase()} · {MODE_LABEL[q.mode].days}</span><span className="num">{rub(q.deliveryRub)}</span></div>
              <div className="row"><span className="muted">Пошлина</span><span className={`num${q.dutyRub === 0 ? " good" : ""}`}>{rub(q.dutyRub)}</span></div>
              {q.vatRub ? <div className="row"><span className="muted">НДС и сбор</span><span className="num">{rub(q.vatRub)}</span></div> : null}
              <div className="row total"><span>Итого</span><span className="num">{rub(q.totalRub)}</span></div>
              {q.foodOnly ? <div className="note bad">Продукты отправляем только вместе с товарами других категорий.</div> : null}
            </>
          ) : null}
        </aside>
      </div>
    </>
  );
}
