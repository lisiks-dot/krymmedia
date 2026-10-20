"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";
import { ChatView, ConversationList } from "@/components/Chat";
import { useAuth } from "@/lib/auth";
import type { ChatThread, Conversation } from "@/lib/types";

function Messages() {
  const { call } = useAuth();
  const params = useSearchParams();
  const active = params.get("c") ? Number(params.get("c")) : null;
  const [items, setItems] = useState<Conversation[] | null>(null);

  const refresh = useCallback(() => {
    call<Conversation[]>("/api/seller/chats").then(setItems).catch(() => setItems([]));
  }, [call]);

  useEffect(() => {
    refresh();
    const t = window.setInterval(refresh, 15000);
    return () => window.clearInterval(t);
  }, [refresh]);

  const load = useCallback(() => call<ChatThread>(`/api/seller/chats/${active}`), [call, active]);
  const send = useCallback((text: string) => call<ChatThread>(`/api/seller/chats/${active}/messages`, { method: "POST", body: JSON.stringify({ text }) }), [call, active]);
  const onChange = useCallback((th: ChatThread) => {
    setItems((prev) => prev?.map((c) => (c.id === th.conversation.id ? th.conversation : c)) ?? prev);
  }, []);

  const unread = items?.reduce((s, c) => s + (c.unread ? 1 : 0), 0) ?? 0;
  return (
    <>
      <div className="row" style={{ alignItems: "baseline" }}>
        <h1>Messaggi</h1>
        {unread ? <span className="pill warn">{unread} da leggere</span> : null}
      </div>
      <p className="muted" style={{ margin: 0 }}>
        Domande dei clienti sui tuoi prodotti. Scrivi in italiano o in inglese: i clienti leggono con il traduttore del browser.
        Non condividere telefono, email o link esterni — vengono nascosti automaticamente. L&apos;assistenza ilpacco vede le conversazioni.
      </p>
      <div className={`messenger work${active !== null ? " has-active" : ""}`}>
        <nav className="messenger-list" aria-label="Conversazioni">
          <ConversationList items={items} active={active} side="SELLER" locale="it" href={(c) => `/seller/messages?c=${c.id}`} />
        </nav>
        <section className="messenger-thread">
          {active !== null ? (
            <ChatView key={active} side="SELLER" locale="it" load={load} send={send} onChange={onChange}
              header={(c) => (
                <>
                  <Link className="back-link small" href="/seller/messages">← Tutte</Link>
                  <b>{c.buyerName}</b>
                  {c.productTitle ? <span className="small muted">{c.productTitle}</span> : null}
                  {c.orderNumber ? <Link className="small" href={`/seller/orders?q=${c.orderNumber}`}>Ordine {c.orderNumber}</Link> : null}
                </>
              )} />
          ) : <div className="chat-empty muted">Seleziona una conversazione</div>}
        </section>
      </div>
    </>
  );
}

export default function SellerMessagesPage() {
  return <Suspense fallback={<div className="skel" style={{ height: 320 }} />}><Messages /></Suspense>;
}
