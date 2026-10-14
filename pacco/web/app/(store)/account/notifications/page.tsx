"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import { fmtTime } from "@/lib/order";
import type { NotificationView } from "@/lib/types";

export default function NotificationsPage() {
  const { call } = useAuth();
  const [list, setList] = useState<NotificationView[] | null>(null);

  useEffect(() => {
    const load = () => call<NotificationView[]>("/api/me/notifications").then(setList).catch(() => setList((prev) => prev ?? []));
    load();
    // сообщения отправляются фоновой рассылкой — подтягиваем новые
    const t = window.setInterval(load, 10000);
    return () => window.clearInterval(t);
  }, [call]);

  if (list === null) return <div className="skel" style={{ height: 200 }} />;
  if (!list.length) {
    return (
      <div className="empty">
        <b>Сообщений пока нет</b>
        <span>Здесь будут СМС и письма о ваших заказах. Каналы можно выбрать в <Link href="/account/profile">профиле</Link>.</span>
      </div>
    );
  }
  return (
    <ul className="feed">
      {list.map((n) => (
        <li key={n.id}>
          <span className={`pill ${n.channel === "SMS" ? "brand" : "neutral"}`}>{n.channel === "SMS" ? "СМС" : "Письмо"}</span>
          <div>
            <b>{n.subject ?? n.body.split(". ")[0]}</b>
            <p className="muted">{n.channel === "SMS" ? n.body : n.body}</p>
            <span className="small muted">{fmtTime(n.createdAt)} · {n.recipient}{n.status === "FAILED" ? " · не доставлено" : ""}</span>
          </div>
        </li>
      ))}
    </ul>
  );
}
