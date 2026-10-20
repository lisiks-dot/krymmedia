"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, api } from "@/lib/client";
import { optionalToken, session } from "@/lib/session";
import type { ChatThread } from "@/lib/types";

/** «Задать вопрос продавцу» на карточке товара. Нужен вход: так ответ придёт в кабинет. */
export function AskSeller({ productId, sellerName }: { productId: number; sellerName: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // вернулись со входа по ссылке …#ask — сразу открываем форму
  useEffect(() => {
    if (window.location.hash === "#ask") setOpen(true);
  }, []);

  async function start() {
    const token = await optionalToken();
    if (!token) {
      const { kc } = await session();
      void kc.login({ redirectUri: `${window.location.origin}${window.location.pathname}#ask` });
      return;
    }
    setOpen(true);
  }

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const token = await optionalToken();
      const th = await api<ChatThread>("/api/me/chats", {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: JSON.stringify({ productId, text: new FormData(e.currentTarget).get("text") }),
      });
      router.push(`/account/messages?c=${th.conversation.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.messages.join("; ") : "Не удалось отправить вопрос");
      setBusy(false);
    }
  }

  if (!open) {
    return <button className="btn ghost ask-btn" type="button" onClick={() => void start()}>Задать вопрос продавцу</button>;
  }
  return (
    <form className="ask" id="ask" onSubmit={submit}>
      <label htmlFor="ask-text"><b>Вопрос для {sellerName}</b></label>
      <textarea id="ask-text" name="text" rows={3} maxLength={4000} required autoFocus
        placeholder="Например: как садится по размеру, есть ли другой цвет" />
      <p className="hint">Продавец ответит в личном кабинете, обычно в течение рабочего дня. Обмениваться телефонами и почтой нельзя — переписку видит поддержка ilpacco.</p>
      {error ? <div className="note bad" role="alert">{error}</div> : null}
      <div className="controls">
        <button className="btn" type="submit" disabled={busy}>{busy ? "Отправляем…" : "Отправить"}</button>
        <button className="btn ghost" type="button" onClick={() => setOpen(false)}>Отмена</button>
      </div>
    </form>
  );
}
