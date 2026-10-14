"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, api, cartId } from "@/lib/client";
import { notifyCart } from "@/lib/local";
import { FavoriteButton } from "./Client";
import { IconBag } from "./Icons";

export function AddToCart({ productId, title, sizes, priceLabel }: { productId: number; title: string; sizes: string[]; priceLabel: string }) {
  const [size, setSize] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState(false);

  useEffect(() => {
    if (!toast) return;
    const t = window.setTimeout(() => setToast(false), 5000);
    return () => window.clearTimeout(t);
  }, [toast]);

  async function add() {
    if (sizes.length && !size) {
      setError("Выберите размер");
      document.getElementById("sizes")?.scrollIntoView({ behavior: "smooth", block: "center" });
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const id = await cartId();
      await api(`/api/carts/${id}/items`, { method: "POST", body: JSON.stringify({ productId, size, qty: 1 }) });
      notifyCart();
      setToast(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Не удалось добавить товар. Проверьте соединение.");
    } finally {
      setBusy(false);
    }
  }

  const button = (
    <button className="btn block" type="button" onClick={add} disabled={busy}>
      <IconBag />{busy ? "Добавляем…" : "Добавить в корзину"}
    </button>
  );

  return (
    <>
      {sizes.length ? (
        <div className="stack" style={{ gap: 8 }} id="sizes">
          <div className="row small">
            <b>Размер, итальянская сетка</b>
            <Link href="/help#sizes" className="muted">Таблица размеров</Link>
          </div>
          <div className="sizes" role="group" aria-label="Размер">
            {sizes.map((s) => (
              <button key={s} type="button" className="size" aria-pressed={size === s}
                onClick={() => { setSize(s); setError(null); }}>
                {s}
              </button>
            ))}
          </div>
        </div>
      ) : null}
      {error ? <div className="note bad" role="alert">{error}</div> : null}
      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) auto", gap: 10, alignItems: "center" }}>
        {button}
        <div style={{ position: "relative", width: 48, height: 48 }}>
          <FavoriteButton id={productId} title={title} />
        </div>
      </div>
      <div className="sticky-buy">
        <div><b className="num">{priceLabel}</b>{size ? <div className="small muted">Размер {size}</div> : null}</div>
        {button}
      </div>
      {toast ? (
        <div className="toast" role="status">
          <span>«{title}»{size ? `, размер ${size}` : ""} в корзине</span>
          <Link href="/cart">Открыть корзину</Link>
        </div>
      ) : null}
    </>
  );
}
