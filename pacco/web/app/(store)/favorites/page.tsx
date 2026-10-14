"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ProductCard, ProductGridSkeleton } from "@/components/ProductCard";
import { api } from "@/lib/client";
import { FAV_EVENT, favorites } from "@/lib/local";
import type { Product } from "@/lib/types";

export default function FavoritesPage() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [ids, setIds] = useState<number[]>([]);
  const [error, setError] = useState(false);

  useEffect(() => {
    const sync = () => setIds(favorites());
    sync();
    window.addEventListener(FAV_EVENT, sync);
    api<Product[]>("/api/products").then(setProducts).catch(() => setError(true));
    return () => window.removeEventListener(FAV_EVENT, sync);
  }, []);

  const list = (products ?? []).filter((p) => ids.includes(p.id)).sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));

  return (
    <>
      <h1>Избранное</h1>
      {error ? (
        <div className="empty">Не удалось загрузить товары. Обновите страницу.</div>
      ) : products === null ? (
        <ProductGridSkeleton />
      ) : list.length === 0 ? (
        <div className="empty">
          Здесь появятся товары, отмеченные сердечком. Избранное хранится в этом браузере.
          <Link className="btn" href="/catalog">Перейти в каталог</Link>
        </div>
      ) : (
        <div className="pgrid">{list.map((p) => <ProductCard key={p.id} p={p} />)}</div>
      )}
    </>
  );
}
