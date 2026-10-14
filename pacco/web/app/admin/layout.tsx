"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { AuthProvider, useAuth } from "@/lib/auth";

const SECTIONS: { href: string; label: string; roles: string[] }[] = [
  { href: "/admin", label: "Обзор", roles: ["admin", "moderator", "support", "finance"] },
  { href: "/admin/sellers", label: "Продавцы", roles: ["admin", "moderator"] },
  { href: "/admin/products", label: "Карточки", roles: ["admin", "moderator"] },
  { href: "/admin/compliance", label: "Комплаенс", roles: ["admin", "moderator"] },
  { href: "/admin/orders", label: "Заказы", roles: ["admin", "support"] },
];

const ROLE_LABEL: Record<string, string> = { admin: "Администратор", moderator: "Модератор", support: "Поддержка", finance: "Финансы" };

function Shell({ children }: { children: ReactNode }) {
  const { username, roles, has, logout } = useAuth();
  const path = usePathname();
  const staff = has("admin", "moderator", "support", "finance");

  if (!staff) {
    return (
      <div className="empty">
        У пользователя «{username}» нет прав сотрудника PACCO. Продавцам — в <Link href="/seller">кабинет продавца</Link>.{" "}
        <button className="btn ghost sm" type="button" onClick={logout}>Выйти</button>
      </div>
    );
  }

  return (
    <div className="cabinet">
      <nav className="side" aria-label="Разделы админки">
        {SECTIONS.filter((s) => has(...s.roles)).map((s) => (
          <Link key={s.href} href={s.href} aria-current={path === s.href ? "page" : undefined}>{s.label}</Link>
        ))}
        <span className="who">
          {username} · {roles.filter((r) => ROLE_LABEL[r]).map((r) => ROLE_LABEL[r]).join(", ")}
        </span>
        <button className="btn ghost sm" type="button" onClick={logout}>Выйти</button>
      </nav>
      <div className="stack">{children}</div>
    </div>
  );
}

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <Shell>{children}</Shell>
    </AuthProvider>
  );
}
