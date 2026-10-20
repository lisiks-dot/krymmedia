"use client";

import { useState, type FormEvent } from "react";
import { ApiError } from "@/lib/client";

/** Ответ на отзыв из кабинета продавца или админки. */
export function ReviewReplyForm({ onSend, locale = "ru" }: { onSend: (text: string) => Promise<void>; locale?: "ru" | "it" }) {
  const it = locale === "it";
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSend(String(new FormData(e.currentTarget).get("text") ?? ""));
      setOpen(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.messages.join("; ") : it ? "Invio non riuscito" : "Не удалось отправить");
    } finally {
      setBusy(false);
    }
  }

  if (!open) return <button className="btn ghost sm" type="button" onClick={() => setOpen(true)}>{it ? "Rispondi" : "Ответить"}</button>;
  return (
    <form className="reply-form" onSubmit={submit}>
      <textarea name="text" rows={3} maxLength={2000} required autoFocus aria-label={it ? "Risposta" : "Ответ"}
        placeholder={it ? "La risposta è pubblica: la vedranno tutti i clienti" : "Ответ публичный — его увидят все покупатели"} />
      {error ? <div className="note bad" role="alert">{error}</div> : null}
      <div className="acts">
        <button className="btn sm" type="submit" disabled={busy}>{busy ? (it ? "Invio…" : "Отправляем…") : it ? "Pubblica risposta" : "Опубликовать ответ"}</button>
        <button className="btn ghost sm" type="button" onClick={() => setOpen(false)}>{it ? "Annulla" : "Отмена"}</button>
      </div>
    </form>
  );
}
