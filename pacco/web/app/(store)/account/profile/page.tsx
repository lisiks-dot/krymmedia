"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import type { BuyerProfile, PickupPoint } from "@/lib/types";

export default function ProfilePage() {
  const { call } = useAuth();
  const [profile, setProfile] = useState<BuyerProfile | null>(null);
  const [points, setPoints] = useState<PickupPoint[]>([]);
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    call<BuyerProfile>("/api/me/profile").then(setProfile).catch(() => setMessage({ tone: "bad", text: "Профиль не загрузился" }));
    fetch("/api/pickup-points").then((r) => r.json()).then(setPoints).catch(() => setPoints([]));
  }, [call]);

  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    setMessage(null);
    try {
      const next = await call<BuyerProfile>("/api/me/profile", {
        method: "PUT",
        body: JSON.stringify({
          name: f.get("name"),
          phone: f.get("phone"),
          email: f.get("email"),
          pickupPointId: f.get("pvz") ? Number(f.get("pvz")) : null,
          notifySms: f.get("sms") === "on",
          notifyEmail: f.get("mail") === "on",
        }),
      });
      setProfile(next);
      setMessage({ tone: "ok", text: "Сохранено" });
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Не удалось сохранить" });
    } finally {
      setBusy(false);
    }
  }

  if (!profile) return <div className="skel" style={{ height: 320 }} />;
  const cities = [...new Set(points.map((p) => p.city))];

  return (
    <form className="profile" onSubmit={save} key={points.length}>
      <fieldset className="cstep">
        <legend>Данные для оформления</legend>
        <div className="two">
          <div className="field"><label htmlFor="p-name">Имя и фамилия как в паспорте</label><input id="p-name" name="name" defaultValue={profile.name ?? ""} autoComplete="name" /></div>
          <div className="field"><label htmlFor="p-phone">Телефон</label><input id="p-phone" name="phone" type="tel" defaultValue={profile.phone ? `+${profile.phone}` : ""} autoComplete="tel" /></div>
        </div>
        <div className="field"><label htmlFor="p-email">Email</label><input id="p-email" name="email" type="email" defaultValue={profile.email ?? ""} autoComplete="email" /></div>
        <div className="field">
          <label htmlFor="p-pvz">Пункт выдачи по умолчанию</label>
          <select id="p-pvz" name="pvz" defaultValue={profile.pickupPointId ?? ""}>
            <option value="">Выберу при оформлении</option>
            {cities.map((c) => (
              <optgroup key={c} label={c}>
                {points.filter((p) => p.city === c).map((p) => <option key={p.id} value={p.id}>{p.carrier} · {p.address}</option>)}
              </optgroup>
            ))}
          </select>
        </div>
        <p className="hint">Паспорт и ИНН не храним в профиле — их вводят при каждом заказе для таможни.</p>
      </fieldset>
      <fieldset className="cstep">
        <legend>Уведомления о заказах</legend>
        <label className="choice"><input type="checkbox" name="sms" defaultChecked={profile.notifySms} /><span><b>СМС</b> — оплата, передача перевозчику, код получения, возвраты</span></label>
        <label className="choice"><input type="checkbox" name="mail" defaultChecked={profile.notifyEmail} /><span><b>Письма</b> — то же с подробностями и ссылкой на заказ</span></label>
        <p className="hint">Код получения придёт в СМС, только если СМС включены. Иначе он виден в кабинете и в письме.</p>
      </fieldset>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      <div className="controls"><button className="btn" type="submit" disabled={busy}>{busy ? "Сохраняем…" : "Сохранить"}</button></div>
    </form>
  );
}
