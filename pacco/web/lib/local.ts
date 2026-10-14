"use client";

// Избранное и недавно просмотренные — в браузере покупателя; события держат шапку в актуальном состоянии.
const FAV = "ilpacco_favorites";
const SEEN = "ilpacco_seen";
export const FAV_EVENT = "ilpacco:favorites";
export const CART_EVENT = "ilpacco:cart";

function read(key: string): number[] {
  try {
    const v = JSON.parse(window.localStorage.getItem(key) ?? "[]");
    return Array.isArray(v) ? v.filter((x) => typeof x === "number") : [];
  } catch {
    return [];
  }
}

function write(key: string, ids: number[]) {
  try {
    window.localStorage.setItem(key, JSON.stringify(ids));
  } catch {
    /* приватный режим */
  }
}

export const favorites = () => read(FAV);

export function toggleFavorite(id: number): boolean {
  const list = read(FAV);
  const on = !list.includes(id);
  write(FAV, on ? [id, ...list] : list.filter((x) => x !== id));
  window.dispatchEvent(new Event(FAV_EVENT));
  return on;
}

export const recentlySeen = () => read(SEEN);

export function markSeen(id: number) {
  write(SEEN, [id, ...read(SEEN).filter((x) => x !== id)].slice(0, 12));
}

export const notifyCart = () => window.dispatchEvent(new Event(CART_EVENT));
