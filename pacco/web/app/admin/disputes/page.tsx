"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { LineRow } from "@/components/Order";
import { Photos } from "@/components/Photos";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { rub } from "@/lib/format";
import { fmtTime } from "@/lib/order";
import type { DisputeStatus, DisputeView, OrderView } from "@/lib/types";

type Case = { dispute: DisputeView; order: OrderView };

const TABS: { key: DisputeStatus | "ALL"; label: string }[] = [
  { key: "OPEN", label: "Открытые" },
  { key: "REFUNDED", label: "С возвратом" },
  { key: "REJECTED", label: "Отклонённые" },
];

export default function DisputesPage() {
  const { call } = useAuth();
  const [cases, setCases] = useState<Case[] | null>(null);
  const [tab, setTab] = useState<DisputeStatus | "ALL">("OPEN");
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);

  const load = useCallback(() => {
    call<Case[]>("/api/admin/disputes").then(setCases).catch(() => setCases([]));
  }, [call]);
  useEffect(load, [load]);

  async function resolve(c: Case, refund: boolean, resolution: string) {
    setMessage(null);
    try {
      await call(`/api/admin/disputes/${c.dispute.id}/resolve`, { method: "POST", body: JSON.stringify({ refund, resolution }) });
      setMessage({ tone: "ok", text: refund ? `${c.order.number}: деньги вернём покупателю` : `${c.order.number}: спор отклонён, покупатель увидит ответ` });
      load();
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не удалось сохранить решение" });
    }
  }

  const list = cases?.filter((c) => tab === "ALL" || c.dispute.status === tab) ?? [];

  return (
    <>
      <div className="page-head">
        <h1>Споры</h1>
        <p className="muted">Ответ покупателю — за 2 рабочих дня. Товар обратно в Италию не пересылаем: при возврате деньги за позицию списываются с выплаты продавцу.</p>
      </div>
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>
            {t.label}<span className="num">{cases?.filter((c) => c.dispute.status === t.key).length ?? 0}</span>
          </button>
        ))}
      </div>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      {cases === null ? <div className="skel" style={{ height: 160 }} /> : list.length === 0 ? (
        <div className="empty">{tab === "OPEN" ? "Открытых споров нет." : "Пока пусто."}</div>
      ) : (
        <div className="stack">
          {list.map((c) => <DisputeCard key={c.dispute.id} c={c} onResolve={resolve} />)}
        </div>
      )}
    </>
  );
}

function DisputeCard({ c, onResolve }: { c: Case; onResolve: (c: Case, refund: boolean, text: string) => void }) {
  const { bearer } = useAuth();
  const [text, setText] = useState("");
  const lines = c.order.items.filter((l) => c.dispute.lines.includes(l.lineNo));
  const sum = lines.reduce((s, l) => s + l.lineRub, 0);
  return (
    <article className="qcard">
      <header>
        <Link href={`/admin/orders/${c.order.number}`} className="num"><b>{c.order.number}</b></Link>
        <span className="pill warn">{c.dispute.reasonLabel}</span>
        <span className="small muted">{c.order.buyerName} · открыт {fmtTime(c.dispute.createdAt)}</span>
      </header>
      {c.dispute.comment ? <blockquote>«{c.dispute.comment}»</blockquote> : null}
      {lines.map((l) => <LineRow key={l.lineNo} line={l} />)}
      {c.order.photos.length ? (
        <div className="stack tight">
          <span className="small muted">Фото: с хаба при приёмке и от покупателя — сравните</span>
          <Photos photos={c.order.photos.filter((p) => p.lineNo === null || c.dispute.lines.includes(p.lineNo))}
            src={(id) => `/api/admin/orders/${encodeURIComponent(c.order.number)}/photos/${id}`} headers={bearer}
            caption={(p) => (p.kind === "HUB" ? "Хаб, приёмка" : "Покупатель")} />
        </div>
      ) : null}
      {c.dispute.status === "OPEN" ? (
        <div className="stack tight">
          <label className="small" htmlFor={`r-${c.dispute.id}`}>Ответ покупателю</label>
          <textarea id={`r-${c.dispute.id}`} rows={2} value={text} onChange={(e) => setText(e.target.value)}
            placeholder="Обязателен при отказе. Например: на фото с хаба подошва целая, повреждение не подтверждается" />
          <div className="acts">
            <button className="btn sm" type="button" onClick={() => onResolve(c, true, text)}>Вернуть {rub(sum)}</button>
            <button className="btn sm ghost" type="button" onClick={() => onResolve(c, false, text)}>Отклонить</button>
          </div>
        </div>
      ) : (
        <p className="small muted">{c.dispute.statusLabel}{c.dispute.refundRub ? `: ${rub(c.dispute.refundRub)}` : ""}{c.dispute.resolution ? ` — ${c.dispute.resolution}` : ""}</p>
      )}
    </article>
  );
}
