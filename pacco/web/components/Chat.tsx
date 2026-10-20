"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from "react";
import { ApiError } from "@/lib/client";
import type { ChatMessage, ChatThread, Conversation } from "@/lib/types";

type Side = "BUYER" | "SELLER" | "ADMIN";

const T = {
  ru: {
    placeholder: "Напишите сообщение", send: "Отправить", sending: "Отправляем…", empty: "Сообщений пока нет",
    masked: "Контакты скрыты: общение и оплата — только через ilpacco, так мы защищаем покупку.",
    failed: "Не удалось отправить", load: "Не удалось загрузить переписку", order: "Заказ", product: "Товар",
    roles: { BUYER: "Покупатель", SELLER: "Продавец", ADMIN: "Поддержка ilpacco" },
    none: "Диалогов пока нет", pick: "Выберите диалог слева", hint: "Enter — отправить, Shift+Enter — новая строка",
  },
  it: {
    placeholder: "Scrivi un messaggio", send: "Invia", sending: "Invio…", empty: "Ancora nessun messaggio",
    masked: "Contatti nascosti: la comunicazione e i pagamenti passano solo tramite ilpacco.",
    failed: "Invio non riuscito", load: "Impossibile caricare la conversazione", order: "Ordine", product: "Prodotto",
    roles: { BUYER: "Cliente", SELLER: "Venditore", ADMIN: "Assistenza ilpacco" },
    none: "Nessuna conversazione", pick: "Seleziona una conversazione", hint: "Invio — inviare, Maiusc+Invio — nuova riga",
  },
};

const time = (iso: string, locale: "ru" | "it") => {
  const d = new Date(iso);
  const today = new Date().toDateString() === d.toDateString();
  return d.toLocaleString(locale === "it" ? "it-IT" : "ru-RU", today
    ? { hour: "2-digit", minute: "2-digit" }
    : { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
};

/** Список диалогов. `href` — куда ведёт строка. */
export function ConversationList({ items, active, href, side, locale = "ru" }: {
  items: Conversation[] | null; active?: number | null; href: (c: Conversation) => string; side: Side; locale?: "ru" | "it";
}) {
  const t = T[locale];
  if (items === null) return <div className="skel" style={{ height: 200 }} />;
  if (!items.length) return <div className="chat-empty muted">{t.none}</div>;
  return (
    <ul className="convs">
      {items.map((c) => {
        const who = side === "BUYER" ? c.sellerName : side === "SELLER" ? c.buyerName : `${c.buyerName} ↔ ${c.sellerName}`;
        return (
          <li key={c.id}>
            <Link href={href(c)} aria-current={active === c.id ? "true" : undefined} className={c.unread ? "unread" : ""}>
              <span className="conv-top">
                <b>{who}</b>
                <span className="small muted">{time(c.lastMessageAt, locale)}</span>
              </span>
              {c.productTitle ? <span className="small conv-product">{c.productTitle}{c.orderNumber ? `, ${c.orderNumber}` : ""}</span> : null}
              <span className="conv-last small muted">{c.lastMessage}</span>
              {c.unread ? <span className="count" aria-label={`Непрочитано: ${c.unread}`}>{c.unread}</span> : null}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

/** Переписка с опросом раз в 8 секунд. `side` — чьими глазами смотрим: свои сообщения справа. */
export function ChatView({ load, send, side, locale = "ru", header, onChange }: {
  load: () => Promise<ChatThread>;
  send: (text: string) => Promise<ChatThread>;
  side: Side;
  locale?: "ru" | "it";
  header?: (c: Conversation) => ReactNode;
  onChange?: (t: ChatThread) => void;
}) {
  const t = T[locale];
  const [thread, setThread] = useState<ChatThread | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);
  const count = useRef(0);

  const apply = useCallback((th: ChatThread) => {
    setThread(th);
    onChange?.(th);
  }, [onChange]);

  useEffect(() => {
    let alive = true;
    const tick = () => load().then((th) => alive && apply(th)).catch(() => alive && setError(t.load));
    tick();
    const id = window.setInterval(tick, 8000);
    return () => {
      alive = false;
      window.clearInterval(id);
    };
  }, [load, apply, t.load]);

  useEffect(() => {
    const n = thread?.messages.length ?? 0;
    if (n !== count.current) {
      count.current = n;
      bottom.current?.scrollIntoView({ block: "end" });
    }
  }, [thread]);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (!text.trim() || busy) return;
    setBusy(true);
    setError(null);
    try {
      apply(await send(text));
      setText("");
    } catch (err) {
      setError(err instanceof ApiError ? err.messages.join("; ") : t.failed);
    } finally {
      setBusy(false);
    }
  }

  function key(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void submit();
    }
  }

  if (!thread) return error ? <div className="note bad">{error}</div> : <div className="skel" style={{ height: 320 }} />;
  const masked = thread.messages.some((m) => m.masked);
  return (
    <div className="chat">
      {header ? <div className="chat-head">{header(thread.conversation)}</div> : null}
      <div className="chat-log" aria-live="polite">
        {thread.messages.length ? thread.messages.map((m, i) => (
          <Bubble key={m.id} m={m} mine={m.role === side} locale={locale} showAuthor={i === 0 || thread.messages[i - 1].role !== m.role} />
        )) : <div className="chat-empty muted">{t.empty}</div>}
        <div ref={bottom} />
      </div>
      {masked ? <div className="note info small">{t.masked}</div> : null}
      <form className="chat-input" onSubmit={submit}>
        <label className="sr-only" htmlFor="chat-text">{t.placeholder}</label>
        <textarea id="chat-text" rows={2} maxLength={4000} placeholder={t.placeholder} value={text}
          onChange={(e) => setText(e.target.value)} onKeyDown={key} />
        <button className="btn" type="submit" disabled={busy || !text.trim()}>{busy ? t.sending : t.send}</button>
      </form>
      <span className="small muted chat-hint">{t.hint}</span>
      {error ? <div className="note bad" role="alert">{error}</div> : null}
    </div>
  );
}

function Bubble({ m, mine, locale, showAuthor }: { m: ChatMessage; mine: boolean; locale: "ru" | "it"; showAuthor: boolean }) {
  const t = T[locale];
  return (
    <div className={`msg ${mine ? "mine" : ""} role-${m.role.toLowerCase()}`}>
      {showAuthor ? <span className="msg-author small">{m.role === "ADMIN" ? t.roles.ADMIN : m.author}{m.role !== "ADMIN" ? <span className="muted"> · {t.roles[m.role]}</span> : null}</span> : null}
      <div className="msg-body">{m.text}</div>
      <span className="msg-time small muted">{time(m.createdAt, locale)}</span>
    </div>
  );
}
