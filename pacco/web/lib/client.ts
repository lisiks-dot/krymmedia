"use client";

import { optionalToken } from "./session";
import type { Problem } from "./types";

export class ApiError extends Error {
  constructor(public status: number, public problem: Problem) {
    super(problem.detail ?? `Ошибка ${status}`);
  }
  get messages(): string[] {
    return this.problem.errors?.length ? this.problem.errors : [this.message];
  }
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
    cache: "no-store",
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, (body ?? {}) as Problem);
  return body as T;
}

const CART_KEY = "pacco_cart_id";
const ORDERS_KEY = "pacco_orders";

function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    /* приватный режим: корзина живёт до перезагрузки */
  }
}

let memoryCartId: string | null = null;

export async function cartId(): Promise<string> {
  const stored = readStorage(CART_KEY) ?? memoryCartId;
  if (stored) return stored;
  const { id } = await api<{ id: string }>("/api/carts", { method: "POST" });
  memoryCartId = id;
  writeStorage(CART_KEY, id);
  return id;
}

/** Заказы этого браузера: номер → ключ покупателя. Ключ открывает код получения, отмену и спор. */
export function rememberOrder(number: string, key?: string | null) {
  const map = orderKeys();
  if (key) map[number] = key;
  else if (!(number in map)) map[number] = "";
  const ordered = [number, ...Object.keys(map).filter((n) => n !== number)].slice(0, 30);
  writeStorage(ORDERS_KEY, JSON.stringify(Object.fromEntries(ordered.map((n) => [n, map[n]]))));
}

function orderKeys(): Record<string, string> {
  try {
    const raw = JSON.parse(readStorage(ORDERS_KEY) ?? "{}") as unknown;
    // старый формат — массив номеров без ключей
    if (Array.isArray(raw)) return Object.fromEntries(raw.map((n) => [String(n), ""]));
    return (raw ?? {}) as Record<string, string>;
  } catch {
    return {};
  }
}

export function myOrders(): string[] {
  return Object.keys(orderKeys());
}

export function orderKey(number: string): string | null {
  return orderKeys()[number] || null;
}

/** Заголовки владельца заказа: ключ из этого браузера и/или токен аккаунта. */
export async function ownerHeaders(number: string): Promise<Record<string, string>> {
  const key = orderKey(number);
  const token = await optionalToken();
  return { ...(key ? { "X-Order-Key": key } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) };
}

/** Запрос от имени покупателя заказа. */
export async function orderApi<T>(number: string, path = "", init?: RequestInit): Promise<T> {
  const owner = await ownerHeaders(number);
  return api<T>(`/api/orders/${encodeURIComponent(number)}${path}`, {
    ...init,
    headers: { ...(init?.headers ?? {}), ...owner },
  });
}

/** Загрузка файла (multipart): без Content-Type, его выставит браузер с границей. */
export async function upload<T>(path: string, form: FormData, headers: Record<string, string>): Promise<T> {
  const res = await fetch(path, { method: "POST", body: form, headers, cache: "no-store" });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, (body ?? {}) as Problem);
  return body as T;
}

/** Картинка, доступная только с заголовками (фото заказа), как blob-URL для <img>. */
export async function blobUrl(path: string, headers: Record<string, string>): Promise<string> {
  const res = await fetch(path, { headers, cache: "no-store" });
  if (!res.ok) throw new ApiError(res.status, {});
  return URL.createObjectURL(await res.blob());
}

export function allOrderKeys(): { number: string; key: string }[] {
  return Object.entries(orderKeys()).filter(([, k]) => k).map(([number, key]) => ({ number, key }));
}
