"use client";

import { useEffect, useState } from "react";
import { recentlySeen } from "@/lib/local";
import type { Product } from "@/lib/types";
import { ProductCard } from "./ProductCard";

export function RecentlyViewed({ products, exclude }: { products: Product[]; exclude?: number }) {
  const [ids, setIds] = useState<number[]>([]);
  useEffect(() => setIds(recentlySeen().filter((id) => id !== exclude)), [exclude]);
  const list = ids.map((id) => products.find((p) => p.id === id)).filter((p): p is Product => Boolean(p)).slice(0, 8);
  if (!list.length) return null;
  return (
    <section className="shelf" aria-labelledby="seen-h">
      <div className="shelf-head"><h2 id="seen-h">Вы смотрели</h2></div>
      <div className="pgrid scroller">{list.map((p) => <ProductCard key={p.id} p={p} />)}</div>
    </section>
  );
}
