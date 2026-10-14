import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { ProductCard } from "@/components/ProductCard";
import { SortSelect } from "@/components/SortSelect";
import { goods } from "@/lib/format";
import { serverGet } from "@/lib/server";
import { CATEGORY_LABEL, type Category, type Product } from "@/lib/types";

export const dynamic = "force-dynamic";

type SP = { category?: string; q?: string; region?: string; sort?: string };
const CATS = Object.keys(CATEGORY_LABEL) as Category[];

export async function generateMetadata({ searchParams }: { searchParams: Promise<SP> }): Promise<Metadata> {
  const { category, q } = await searchParams;
  const c = CATS.includes(category as Category) ? CATEGORY_LABEL[category as Category] : null;
  return { title: q ? `Поиск: ${q}` : c ? `${c} из Италии` : "Каталог итальянских товаров" };
}

export default async function Catalog({ searchParams }: { searchParams: Promise<SP> }) {
  const sp = await searchParams;
  const category = CATS.includes(sp.category as Category) ? (sp.category as Category) : undefined;
  const q = sp.q?.trim() || undefined;
  const region = sp.region || undefined;
  const sort = sp.sort ?? "popular";

  const res = await serverGet<Product[]>(`/api/products${q ? `?q=${encodeURIComponent(q)}` : ""}`);
  if (!res.ok) return <div className="empty">Каталог обновляется. Обновите страницу через минуту.</div>;

  const base = res.data;
  const inCat = category ? base.filter((p) => p.category === category) : base;
  let list = region ? inCat.filter((p) => p.region === region) : inCat;
  list = [...list].sort((a, b) =>
    sort === "price_asc" ? a.priceRub - b.priceRub : sort === "price_desc" ? b.priceRub - a.priceRub : sort === "new" ? b.id - a.id : a.id - b.id);

  const regions = Object.entries(inCat.reduce<Record<string, number>>((m, p) => {
    if (p.region) m[p.region] = (m[p.region] ?? 0) + 1;
    return m;
  }, {})).sort((a, b) => b[1] - a[1]);

  const href = (patch: Partial<SP>) => {
    const next = { category, q, region, sort: sort === "popular" ? undefined : sort, ...patch };
    const p = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => { if (v) p.set(k, v); });
    const s = p.toString();
    return s ? `/catalog?${s}` : "/catalog";
  };

  const title = q ? `Поиск: «${q}»` : category ? CATEGORY_LABEL[category] : "Все товары";

  return (
    <>
      <nav className="crumbs" aria-label="Навигация">
        <Link href="/">Главная</Link><span aria-hidden="true">/</span>
        <Link href="/catalog">Каталог</Link>
        {category ? <><span aria-hidden="true">/</span><span>{CATEGORY_LABEL[category]}</span></> : null}
      </nav>
      <div className="listing">
        <aside className="filters" aria-label="Фильтры">
          <section>
            <h3>Категория</h3>
            <ul>
              <li><Link href={href({ category: undefined, region: undefined })} aria-current={!category}><span>Все</span><span className="num">{base.length}</span></Link></li>
              {CATS.map((c) => {
                const n = base.filter((p) => p.category === c).length;
                return n ? (
                  <li key={c}><Link href={href({ category: c, region: undefined })} aria-current={category === c}><span>{CATEGORY_LABEL[c]}</span><span className="num">{n}</span></Link></li>
                ) : null;
              })}
            </ul>
          </section>
          {regions.length > 1 ? (
            <section>
              <h3>Регион Италии</h3>
              <ul>
                <li><Link href={href({ region: undefined })} aria-current={!region}><span>Все регионы</span></Link></li>
                {regions.map(([r, n]) => (
                  <li key={r}><Link href={href({ region: r })} aria-current={region === r}><span>{r}</span><span className="num">{n}</span></Link></li>
                ))}
              </ul>
            </section>
          ) : null}
        </aside>
        <div>
          <div className="sortbar">
            <div>
              <h1 style={{ fontSize: "clamp(26px, 3vw, 34px)" }}>{title}</h1>
              <span className="muted small num">{goods(list.length)}{region ? ` · ${region}` : ""}</span>
            </div>
            <Suspense fallback={null}><SortSelect value={sort} /></Suspense>
          </div>
          {list.length ? (
            <div className="pgrid">{list.map((p) => <ProductCard key={p.id} p={p} />)}</div>
          ) : (
            <div className="empty">
              {q ? `По запросу «${q}» ничего не нашлось.` : "В этой подборке пока нет товаров."}
              <Link className="btn ghost" href="/catalog">Смотреть все товары</Link>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
