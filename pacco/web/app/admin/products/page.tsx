"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { PRODUCT_STATUS_RU, type Product } from "@/lib/cabinet";
import { eur, kg, rub } from "@/lib/format";
import { CATEGORY_LABEL } from "@/lib/types";

export default function AdminProducts() {
  const { call } = useAuth();
  const [pending, setPending] = useState<Product[] | null>(null);
  const [all, setAll] = useState<Product[] | null>(null);
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);

  const load = useCallback(() => {
    call<Product[]>("/api/admin/products/pending").then(setPending).catch(() => setPending([]));
    call<Product[]>("/api/admin/products").then(setAll).catch(() => setAll([]));
  }, [call]);

  useEffect(load, [load]);

  async function act(p: Product, action: "approve" | "reject") {
    try {
      await call(`/api/admin/products/${p.id}/${action}`, {
        method: "POST",
        body: action === "reject" ? JSON.stringify({ reason: reasons[p.id] ?? "" }) : undefined,
      });
      setMessage({ tone: "ok", text: action === "approve" ? `«${p.title}» опубликован` : `«${p.title}» отклонён, продавец увидит причину` });
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не удалось сохранить решение" });
    }
    load();
  }

  return (
    <>
      <h1>Карточки</h1>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      <section className="panel">
        <h2>На модерации</h2>
        <p className="small muted" style={{ margin: 0 }}>
          Комплаенс уже пройден при сохранении. Проверьте перевод, фото и соответствие категории; при публикации правила проверяются ещё раз.
        </p>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Карточка</th><th>Продавец</th><th>Категория</th><th>Код</th><th className="r">Цена</th><th>Вес</th><th>Решение</th></tr></thead>
            <tbody>
              {pending === null ? (
                <tr><td colSpan={7} className="muted">Загружаем…</td></tr>
              ) : pending.length === 0 ? (
                <tr><td colSpan={7} className="muted">Очередь пуста</td></tr>
              ) : pending.map((p) => (
                <tr key={p.id}>
                  <td>{p.title}{p.description ? <div className="small muted">{p.description}</div> : null}</td>
                  <td>{p.sellerName}</td>
                  <td>{CATEGORY_LABEL[p.category]}</td>
                  <td className="num">{p.hsCode}</td>
                  <td className="r num">{eur(p.priceEurCents)}<div className="small muted">{rub(p.priceRub)}</div></td>
                  <td className="num">{kg(p.weightGrams)}</td>
                  <td>
                    <div className="acts">
                      <button className="btn sm" type="button" onClick={() => act(p, "approve")}>Опубликовать</button>
                      <input className="inline-input" aria-label="Причина отказа" placeholder="Причина" value={reasons[p.id] ?? ""}
                        onChange={(e) => setReasons({ ...reasons, [p.id]: e.target.value })} />
                      <button className="btn danger sm" type="button" onClick={() => act(p, "reject")}>Отклонить</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <section className="panel">
        <h2>Весь каталог</h2>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Товар</th><th>Продавец</th><th>Категория</th><th className="r">Цена</th><th>Статус</th></tr></thead>
            <tbody>
              {(all ?? []).map((p) => (
                <tr key={p.id}>
                  <td>{p.title}</td>
                  <td>{p.sellerName}</td>
                  <td>{CATEGORY_LABEL[p.category]}</td>
                  <td className="r num">{eur(p.priceEurCents)}</td>
                  <td>
                    <span className={`pill ${PRODUCT_STATUS_RU[p.status]?.tone ?? "neutral"}`}>{PRODUCT_STATUS_RU[p.status]?.label ?? p.status}</span>
                    {p.blockReason ? <div className="small muted">{p.blockReason}</div> : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
