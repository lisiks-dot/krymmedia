"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, cartId } from "@/lib/client";
import { CART_EVENT, FAV_EVENT, favorites, markSeen, toggleFavorite } from "@/lib/local";
import type { CartView } from "@/lib/types";
import { IconBag, IconBox, IconHeart, IconHome, IconStore } from "./Icons";

export function FavoriteButton({ id, title }: { id: number; title: string }) {
  const [on, setOn] = useState(false);
  useEffect(() => {
    const sync = () => setOn(favorites().includes(id));
    sync();
    window.addEventListener(FAV_EVENT, sync);
    return () => window.removeEventListener(FAV_EVENT, sync);
  }, [id]);
  return (
    <button
      type="button"
      className="fav"
      aria-pressed={on}
      aria-label={on ? `Убрать «${title}» из избранного` : `Добавить «${title}» в избранное`}
      onClick={(e) => {
        e.preventDefault();
        setOn(toggleFavorite(id));
      }}
    >
      <IconHeart />
    </button>
  );
}

function useCounts() {
  const [cart, setCart] = useState(0);
  const [fav, setFav] = useState(0);
  useEffect(() => {
    const loadFav = () => setFav(favorites().length);
    const loadCart = () => {
      let id: string | null = null;
      try {
        id = window.localStorage.getItem("pacco_cart_id");
      } catch {
        id = null;
      }
      if (!id) return setCart(0);
      api<CartView>(`/api/carts/${id}`)
        .then((c) => setCart(c.items.reduce((s, i) => s + i.qty, 0)))
        .catch(() => setCart(0));
    };
    loadFav();
    loadCart();
    window.addEventListener(FAV_EVENT, loadFav);
    window.addEventListener(CART_EVENT, loadCart);
    return () => {
      window.removeEventListener(FAV_EVENT, loadFav);
      window.removeEventListener(CART_EVENT, loadCart);
    };
  }, []);
  return { cart, fav };
}

export function HeaderActions() {
  const { cart, fav } = useCounts();
  return (
    <nav className="actions" aria-label="Покупки">
      <Link className="action" href="/sell"><IconStore /><span>Продавцам</span></Link>
      <Link className="action" href="/orders"><IconBox /><span>Заказы</span></Link>
      <Link className="action" href="/favorites" aria-label={`Избранное: ${fav}`}>
        <IconHeart /><span>Избранное</span>{fav ? <b className="count num">{fav}</b> : null}
      </Link>
      <Link className="action" href="/cart" aria-label={`Корзина: ${cart}`}>
        <IconBag /><span>Корзина</span>{cart ? <b className="count num">{cart}</b> : null}
      </Link>
    </nav>
  );
}

export function TabBar() {
  const { cart, fav } = useCounts();
  return (
    <nav className="tabbar" aria-label="Меню">
      <Link className="action" href="/"><IconHome /><span>Главная</span></Link>
      <Link className="action" href="/favorites"><IconHeart /><span>Избранное</span>{fav ? <b className="count num">{fav}</b> : null}</Link>
      <Link className="action" href="/cart"><IconBag /><span>Корзина</span>{cart ? <b className="count num">{cart}</b> : null}</Link>
      <Link className="action" href="/orders"><IconBox /><span>Заказы</span></Link>
    </nav>
  );
}

/** Отмечает товар как просмотренный — для блока «Вы смотрели». */
export function SeenMarker({ id }: { id: number }) {
  useEffect(() => markSeen(id), [id]);
  return null;
}

/** Создаёт корзину заранее, чтобы первое добавление было мгновенным. */
export function WarmCart() {
  useEffect(() => {
    void cartId().catch(() => undefined);
  }, []);
  return null;
}
