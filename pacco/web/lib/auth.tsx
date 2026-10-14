"use client";

import type Keycloak from "keycloak-js";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api } from "./client";

/** Вход через Keycloak (realm pacco, клиент pacco-web, PKCE). Один экземпляр на вкладку. */
let kcPromise: Promise<{ kc: Keycloak; authenticated: boolean }> | null = null;

function keycloak() {
  if (!kcPromise) {
    kcPromise = (async () => {
      const { default: KeycloakCtor } = await import("keycloak-js");
      const kc = new KeycloakCtor({ url: `${window.location.origin}/auth`, realm: "pacco", clientId: "pacco-web" });
      const authenticated = await kc.init({ onLoad: "login-required", pkceMethod: "S256", checkLoginIframe: false });
      return { kc, authenticated };
    })();
  }
  return kcPromise;
}

type AuthState = {
  ready: boolean;
  error: string | null;
  username: string;
  sub: string;
  roles: string[];
};

type Auth = AuthState & {
  has: (...roles: string[]) => boolean;
  call: <T>(path: string, init?: RequestInit) => Promise<T>;
  logout: () => void;
};

const AuthContext = createContext<Auth | null>(null);

export function AuthProvider({ children, locale = "ru" }: { children: ReactNode; locale?: string }) {
  const [state, setState] = useState<AuthState>({ ready: false, error: null, username: "", sub: "", roles: [] });

  useEffect(() => {
    let alive = true;
    keycloak()
      .then(({ kc }) => {
        if (!alive) return;
        const t = (kc.tokenParsed ?? {}) as { preferred_username?: string; sub?: string; realm_access?: { roles?: string[] } };
        setState({ ready: true, error: null, username: t.preferred_username ?? "", sub: t.sub ?? "", roles: t.realm_access?.roles ?? [] });
      })
      .catch(() => {
        if (alive) {
          setState((s) => ({
            ...s,
            ready: true,
            error: locale === "it" ? "Servizio di accesso non raggiungibile. Riprova tra un minuto." : "Сервис входа недоступен. Попробуйте через минуту.",
          }));
        }
      });
    return () => {
      alive = false;
    };
  }, [locale]);

  const call = useCallback(async <T,>(path: string, init?: RequestInit): Promise<T> => {
    const { kc } = await keycloak();
    await kc.updateToken(30);
    return api<T>(path, { ...init, headers: { ...(init?.headers ?? {}), Authorization: `Bearer ${kc.token}` } });
  }, []);

  const logout = useCallback(() => {
    void keycloak().then(({ kc }) => kc.logout({ redirectUri: window.location.origin }));
  }, []);

  const has = useCallback((...roles: string[]) => roles.some((r) => state.roles.includes(r)), [state.roles]);

  if (!state.ready) {
    return <div className="empty">{locale === "it" ? "Accesso in corso…" : "Входим…"}</div>;
  }
  if (state.error) {
    return <div className="empty">{state.error}</div>;
  }
  return <AuthContext.Provider value={{ ...state, has, call, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): Auth {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth вне AuthProvider");
  return ctx;
}
