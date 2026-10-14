"use client";

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

export function rememberOrder(number: string) {
  const list = myOrders().filter((n) => n !== number);
  writeStorage(ORDERS_KEY, JSON.stringify([number, ...list].slice(0, 20)));
}

export function myOrders(): string[] {
  try {
    return JSON.parse(readStorage(ORDERS_KEY) ?? "[]") as string[];
  } catch {
    return [];
  }
}
