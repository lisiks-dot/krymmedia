"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { AuthProvider, useAuth } from "@/lib/auth";

function Shell({ children }: { children: ReactNode }) {
  const { username, logout } = useAuth();
  const path = usePathname();
  const router = useRouter();
  // Вход со страницы оформления — возвращаемся туда.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get("from") === "checkout") router.replace("/checkout");
  }, [router]);
  const tabs = [
    { href: "/account", label: "Заказы" },
    { href: "/account/messages", label: "Сообщения" },
    { href: "/account/profile", label: "Профиль" },
    { href: "/account/notifications", label: "Уведомления" },
  ];
  return (
    <div className="account">
      <header className="account-head">
        <div>
          <h1>Личный кабинет</h1>
          <span className="muted">{username}</span>
        </div>
        <button className="btn ghost sm" type="button" onClick={logout}>Выйти</button>
      </header>
      <nav className="tabs" aria-label="Разделы кабинета">
        {tabs.map((t) => (
          <Link key={t.href} href={t.href} role="tab" aria-selected={path === t.href} className="tab-link">{t.label}</Link>
        ))}
      </nav>
      {children}
    </div>
  );
}

/** Кабинет покупателя: вход через Keycloak (регистрация там же). Заказы без входа подтягиваются из браузера. */
export default function AccountLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <Shell>{children}</Shell>
    </AuthProvider>
  );
}
