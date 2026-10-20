"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState, type FormEvent } from "react";
import { ChatView, ConversationList } from "@/components/Chat";
import { useAuth } from "@/lib/auth";
import type { ChatThread, Conversation } from "@/lib/types";

function Chats() {
  const { call } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const active = params.get("c") ? Number(params.get("c")) : null;
  const q = params.get("q") ?? "";
  const [items, setItems] = useState<Conversation[] | null>(null);

  const refresh = useCallback(() => {
    call<Conversation[]>(`/api/admin/chats${q ? `?q=${encodeURIComponent(q)}` : ""}`).then(setItems).catch(() => setItems([]));
  }, [call, q]);

  useEffect(() => {
    refresh();
    const t = window.setInterval(refresh, 15000);
    return () => window.clearInterval(t);
  }, [refresh]);

  const load = useCallback(() => call<ChatThread>(`/api/admin/chats/${active}`), [call, active]);
  const send = useCallback((text: string) => call<ChatThread>(`/api/admin/chats/${active}/messages`, { method: "POST", body: JSON.stringify({ text }) }), [call, active]);
  const onChange = useCallback((th: ChatThread) => {
    setItems((prev) => prev?.map((c) => (c.id === th.conversation.id ? th.conversation : c)) ?? prev);
  }, []);

  function search(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const v = String(new FormData(e.currentTarget).get("q") ?? "").trim();
    router.push(`/admin/chats${v ? `?q=${encodeURIComponent(v)}` : ""}`);
  }
  const link = (c: Conversation) => `/admin/chats?c=${c.id}${q ? `&q=${encodeURIComponent(q)}` : ""}`;

  return (
    <>
      <div className="row" style={{ alignItems: "baseline", flexWrap: "wrap", gap: 12 }}>
        <h1>Переписка покупателей и продавцов</h1>
        <form className="search inline" role="search" onSubmit={search}>
          <label className="sr-only" htmlFor="chat-q">Поиск</label>
          <input id="chat-q" name="q" defaultValue={q} placeholder="Покупатель, продавец, товар, заказ или текст" />
          <button type="submit">Найти</button>
        </form>
      </div>
      <p className="muted" style={{ margin: 0 }}>
        Все диалоги площадки. Ответ отсюда уходит от имени «Поддержка ilpacco» и виден обеим сторонам. Контакты в сообщениях сторон скрываются автоматически — отметка «контакт скрыт» помогает найти попытки увести сделку.
      </p>
      <div className={`messenger work${active !== null ? " has-active" : ""}`}>
        <nav className="messenger-list" aria-label="Диалоги">
          <ConversationList items={items} active={active} side="ADMIN" href={link} />
        </nav>
        <section className="messenger-thread">
          {active !== null ? (
            <ChatView key={active} side="ADMIN" load={load} send={send} onChange={onChange}
              header={(c) => (
                <>
                  <Link className="back-link small" href="/admin/chats">← Все диалоги</Link>
                  <b>{c.buyerName} ↔ {c.sellerName}</b>
                  {c.productId ? <Link className="small" href={`/p/${c.productId}`} target="_blank">{c.productTitle}</Link> : null}
                  {c.orderNumber ? <Link className="small" href={`/admin/orders/${c.orderNumber}`}>Заказ {c.orderNumber}</Link> : null}
                  <span className="small muted">Открыт {new Date(c.createdAt).toLocaleDateString("ru-RU")}</span>
                </>
              )} />
          ) : <div className="chat-empty muted">Выберите диалог слева</div>}
        </section>
      </div>
    </>
  );
}

export default function AdminChatsPage() {
  return <Suspense fallback={<div className="skel" style={{ height: 320 }} />}><Chats /></Suspense>;
}
