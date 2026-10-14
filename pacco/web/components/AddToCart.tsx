"use client";

import Link from "next/link";
import { useState } from "react";
import { ApiError, api, cartId } from "@/lib/client";

export function AddToCart({ productId, sizes }: { productId: number; sizes: string[] }) {
  const [size, setSize] = useState<string | null>(null);
  const [state, setState] = useState<"idle" | "busy" | "added">("idle");
  const [error, setError] = useState<string | null>(null);

  async function add() {
    if (sizes.length && !size) {
      setError("Выберите размер.");
      return;
    }
    setError(null);
    setState("busy");
    try {
      const id = await cartId();
      await api(`/api/carts/${id}/items`, { method: "POST", body: JSON.stringify({ productId, size, qty: 1 }) });
      setState("added");
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Не удалось добавить товар. Проверьте соединение.");
      setState("idle");
    }
  }

  return (
    <div className="stack" style={{ gap: 12 }}>
      {sizes.length ? (
        <div className="stack" style={{ gap: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600 }}>Размер (IT)</span>
          <div className="sizes">
            {sizes.map((s) => (
              <button key={s} type="button" className="size" aria-pressed={size === s} onClick={() => { setSize(s); setState("idle"); setError(null); }}>
                {s}
              </button>
            ))}
          </div>
        </div>
      ) : null}
      {error ? <div className="note bad">{error}</div> : null}
      {state === "added" ? (
        <Link className="btn" href="/cart">Добавлено · открыть корзину</Link>
      ) : (
        <button className="btn" type="button" onClick={add} disabled={state === "busy"}>
          {state === "busy" ? "Добавляем…" : "В корзину"}
        </button>
      )}
    </div>
  );
}
