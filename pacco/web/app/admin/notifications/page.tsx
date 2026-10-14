"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { NotificationView } from "@/lib/types";

const STATUS: Record<string, { label: string; tone: string }> = {
  SENT: { label: "Отправлено", tone: "ok" },
  LOGGED: { label: "Только журнал", tone: "neutral" },
  FAILED: { label: "Ошибка", tone: "bad" },
};

export default function NotificationsLog() {
  const { call } = useAuth();
  const [list, setList] = useState<NotificationView[] | null>(null);
  const [channel, setChannel] = useState<"ALL" | "SMS" | "EMAIL">("ALL");
  const [onlyFailed, setOnlyFailed] = useState(false);

  useEffect(() => {
    call<NotificationView[]>("/api/admin/notifications?limit=300").then(setList).catch(() => setList([]));
  }, [call]);

  const shown = useMemo(() => (list ?? []).filter((n) => (channel === "ALL" || n.channel === channel) && (!onlyFailed || n.status === "FAILED")), [list, channel, onlyFailed]);
  const logged = list?.some((n) => n.status === "LOGGED");

  return (
    <>
      <div className="page-head">
        <h1>Уведомления</h1>
        <p className="muted">СМС и письма покупателям по событиям заказа: оплата, передача перевозчику, код получения, получение, отмены и возвраты.</p>
      </div>
      {logged ? <div className="note info">Часть сообщений только записана в журнал: провайдер не настроен. СМС — ключ SMS_RU_API_ID, почта — MAIL_HOST (см. .env.example).</div> : null}
      <div className="chips-row">
        {(["ALL", "SMS", "EMAIL"] as const).map((c) => (
          <button key={c} type="button" className="chip" aria-pressed={channel === c} onClick={() => setChannel(c)}>{c === "ALL" ? "Все" : c === "SMS" ? "СМС" : "Письма"}</button>
        ))}
        <button type="button" className="chip" aria-pressed={onlyFailed} onClick={() => setOnlyFailed(!onlyFailed)}>Только ошибки</button>
      </div>
      <section className="panel">
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Когда</th><th>Канал</th><th>Кому</th><th>Текст</th><th>Статус</th></tr></thead>
            <tbody>
              {list === null ? <tr><td colSpan={5} className="muted">Загружаем…</td></tr> : shown.length === 0 ? (
                <tr><td colSpan={5} className="muted">Пусто</td></tr>
              ) : shown.map((n) => (
                <tr key={n.id}>
                  <td className="num small">{new Date(n.createdAt).toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</td>
                  <td>{n.channel === "SMS" ? "СМС" : "Письмо"}</td>
                  <td className="num small">{n.recipient}</td>
                  <td className="small">{n.subject ? <b>{n.subject}</b> : null}<div className="clamp">{n.body}</div></td>
                  <td><span className={`pill ${STATUS[n.status].tone}`}>{STATUS[n.status].label}</span>{n.error ? <div className="small bad-text">{n.error}</div> : null}<div className="small muted">{n.provider}</div></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <p className="small muted">Сообщения по конкретному заказу — в его карточке, раздел <Link href="/admin/orders">Заказы</Link>.</p>
    </>
  );
}
