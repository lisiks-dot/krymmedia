"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";
import { ChatView, ConversationList } from "@/components/Chat";
import { useAuth } from "@/lib/auth";
import type { ChatThread, Conversation } from "@/lib/types";

function Messages() {
  const { call, ready } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const active = params.get("c") ? Number(params.get("c")) : null;
  const [items, setItems] = useState<Conversation[] | null>(null);

  const refresh = useCallback(() => {
    call<Conversation[]>("/api/me/chats").then(setItems).catch(() => setItems([]));
  }, [call]);

  useEffect(() => {
    if (!ready) return;
    refresh();
    const t = window.setInterval(refresh, 15000);
    return () => window.clearInterval(t);
  }, [ready, refresh]);

  // на широком экране сразу открываем последний диалог
  useEffect(() => {
    if (active === null && items?.length && window.matchMedia("(min-width: 861px)").matches) {
      router.replace(`/account/messages?c=${items[0].id}`);
    }
  }, [active, items, router]);

  const load = useCallback(() => call<ChatThread>(`/api/me/chats/${active}`), [call, active]);
  const send = useCallback((text: string) => call<ChatThread>(`/api/me/chats/${active}/messages`, { method: "POST", body: JSON.stringify({ text }) }), [call, active]);
  const onChange = useCallback((th: ChatThread) => {
    setItems((prev) => prev?.map((c) => (c.id === th.conversation.id ? th.conversation : c)) ?? prev);
  }, []);

  if (items !== null && !items.length) {
    return (
      <div className="empty">
        <b>Здесь будет переписка с продавцами</b>
        <span>Задайте вопрос на странице товара: про размер, материал или комплектацию. Ответ придёт сюда.</span>
        <Link className="btn" href="/catalog">Перейти в каталог</Link>
      </div>
    );
  }

  return (
    <div className={`messenger${active !== null ? " has-active" : ""}`}>
      <nav className="messenger-list" aria-label="Диалоги">
        <ConversationList items={items} active={active} side="BUYER" href={(c) => `/account/messages?c=${c.id}`} />
      </nav>
      <section className="messenger-thread">
        {active !== null ? (
          <ChatView key={active} side="BUYER" load={load} send={send} onChange={onChange}
            header={(c) => (
              <>
                <Link className="back-link small" href="/account/messages">← Все диалоги</Link>
                <b>{c.sellerName}</b>
                {c.productId ? <Link className="small" href={`/p/${c.productId}`}>{c.productTitle}</Link> : null}
                {c.orderNumber ? <Link className="small" href={`/orders/${c.orderNumber}`}>Заказ {c.orderNumber}</Link> : null}
              </>
            )} />
        ) : <div className="chat-empty muted">Выберите диалог</div>}
      </section>
    </div>
  );
}

export default function MessagesPage() {
  return <Suspense fallback={<div className="skel" style={{ height: 320 }} />}><Messages /></Suspense>;
}
