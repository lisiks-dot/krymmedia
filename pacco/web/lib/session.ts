"use client";

import type Keycloak from "keycloak-js";

/**
 * Одна сессия Keycloak на вкладку (realm pacco, клиент pacco-web, PKCE).
 * Инициализация без принудительного входа (check-sso в скрытом iframe): витрина узнаёт, вошёл ли покупатель,
 * а кабинеты сами отправляют на страницу входа.
 */
let kcPromise: Promise<{ kc: Keycloak; authenticated: boolean }> | null = null;

export function session() {
  if (!kcPromise) {
    kcPromise = (async () => {
      const { default: KeycloakCtor } = await import("keycloak-js");
      const kc = new KeycloakCtor({ url: `${window.location.origin}/auth`, realm: "pacco", clientId: "pacco-web" });
      const authenticated = await kc.init({
        onLoad: "check-sso",
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        pkceMethod: "S256",
        checkLoginIframe: false,
      });
      return { kc, authenticated };
    })();
    kcPromise.catch(() => {
      kcPromise = null;
    });
  }
  return kcPromise;
}

/** Токен покупателя, если он вошёл; иначе null. Не открывает страницу входа. */
export async function optionalToken(): Promise<string | null> {
  try {
    const { kc, authenticated } = await session();
    if (!authenticated) return null;
    await kc.updateToken(30);
    return kc.token ?? null;
  } catch {
    return null;
  }
}

export type TokenInfo = { sub: string; name: string; email: string | null; username: string };

export async function whoAmI(): Promise<TokenInfo | null> {
  try {
    const { kc, authenticated } = await session();
    if (!authenticated) return null;
    const t = (kc.tokenParsed ?? {}) as { sub?: string; name?: string; given_name?: string; email?: string; preferred_username?: string };
    return { sub: t.sub ?? "", name: t.name ?? t.given_name ?? t.preferred_username ?? "", email: t.email ?? null, username: t.preferred_username ?? "" };
  } catch {
    return null;
  }
}
