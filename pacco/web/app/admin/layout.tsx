"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { WorkHeader, WorkShell } from "@/components/Work";
import { AuthProvider, useAuth } from "@/lib/auth";

type Section = { href: string; label: string; roles: string[] };

const GROUPS: { title: string; items: Section[] }[] = [
  {
    title: "Заказы",
    items: [
      { href: "/admin/orders", label: "Все заказы", roles: ["admin", "support", "ops"] },
      { href: "/admin/hub", label: "Хаб в Милане", roles: ["admin", "ops"] },
      { href: "/admin/delivery", label: "Таможня и ПВЗ", roles: ["admin", "ops"] },
      { href: "/admin/disputes", label: "Споры", roles: ["admin", "support"] },
      { href: "/admin/chats", label: "Переписка", roles: ["admin", "support", "moderator"] },
      { href: "/admin/notifications", label: "Уведомления", roles: ["admin", "support"] },
    ],
  },
  {
    title: "Каталог",
    items: [
      { href: "/admin/sellers", label: "Продавцы", roles: ["admin", "moderator"] },
      { href: "/admin/products", label: "Карточки", roles: ["admin", "moderator"] },
      { href: "/admin/reviews", label: "Отзывы", roles: ["admin", "moderator", "support"] },
      { href: "/admin/import", label: "Импорт с Zalando", roles: ["admin", "moderator"] },
      { href: "/admin/compliance", label: "Комплаенс", roles: ["admin", "moderator"] },
    ],
  },
  {
    title: "Деньги",
    items: [{ href: "/admin/payouts", label: "Выплаты продавцам", roles: ["admin", "finance"] }],
  },
];

const ROLE_LABEL: Record<string, string> = {
  admin: "Администратор", moderator: "Модератор", support: "Поддержка", ops: "Хаб и доставка", finance: "Финансы",
};

function Shell({ children }: { children: ReactNode }) {
  const { username, roles, has, logout } = useAuth();
  const path = usePathname();
  const staff = has(...Object.keys(ROLE_LABEL));
  const who = roles.filter((r) => ROLE_LABEL[r]).map((r) => ROLE_LABEL[r]).join(", ");
  const right = (
    <>
      <span className="work-user">{username}{who ? <small>{who}</small> : null}</span>
      <button className="btn sm on-dark" type="button" onClick={logout}>Выйти</button>
    </>
  );

  if (!staff) {
    return (
      <WorkShell>
        <WorkHeader area="Операции" home="/admin" right={right} />
        <main className="wrap work-main">
          <div className="empty">
            <b>У пользователя «{username}» нет прав сотрудника ilpacco</b>
            <span>Продавцам — в <Link href="/seller">кабинет продавца</Link>.</span>
          </div>
        </main>
      </WorkShell>
    );
  }

  const current = (href: string) => (href === "/admin" ? path === "/admin" : path.startsWith(href));

  return (
    <WorkShell>
      <WorkHeader area="Операции" home="/admin" right={right} />
      <div className="wrap work-main cabinet">
        <nav className="side" aria-label="Разделы">
          <Link href="/admin" aria-current={current("/admin") ? "page" : undefined}>Обзор</Link>
          {GROUPS.map((g) => {
            const items = g.items.filter((s) => has(...s.roles));
            if (!items.length) return null;
            return (
              <div key={g.title} className="side-group">
                <span className="side-title">{g.title}</span>
                {items.map((s) => (
                  <Link key={s.href} href={s.href} aria-current={current(s.href) ? "page" : undefined}>{s.label}</Link>
                ))}
              </div>
            );
          })}
          <Link href="/" className="side-out">Открыть витрину</Link>
        </nav>
        <main className="stack" id="content">{children}</main>
      </div>
    </WorkShell>
  );
}

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <Shell>{children}</Shell>
    </AuthProvider>
  );
}
