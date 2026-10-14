import Link from "next/link";
import { Thumb } from "@/components/Thumb";
import { rub } from "@/lib/format";
import { serverGet } from "@/lib/server";
import { CATEGORY_LABEL, type Category, type Product } from "@/lib/types";

export const dynamic = "force-dynamic";

const CATEGORIES = Object.keys(CATEGORY_LABEL) as Category[];

export default async function CatalogPage({ searchParams }: { searchParams: Promise<{ category?: string; q?: string }> }) {
  const { category, q } = await searchParams;
  const active = CATEGORIES.includes(category as Category) ? (category as Category) : undefined;
  const params = new URLSearchParams();
  if (active) params.set("category", active);
  if (q) params.set("q", q);
  const res = await serverGet<Product[]>(`/api/products?${params}`);

  const href = (c?: Category) => {
    const p = new URLSearchParams();
    if (c) p.set("category", c);
    if (q) p.set("q", q);
    const s = p.toString();
    return s ? `/?${s}` : "/";
  };

  return (
    <>
      <section className="hero">
        <h1>Италия в одной посылке</h1>
        <div className="facts">
          <span><b>0 %</b> пошлины до 200 € в посылке</span>
          <span><b>16–24</b> дня до ПВЗ, экспресс 10–18</span>
          <span><b>до 290 €</b> за единицу</span>
        </div>
      </section>

      <form className="controls" action="/" method="get" role="search">
        {active ? <input type="hidden" name="category" value={active} /> : null}
        <label className="search" htmlFor="q">
          <input id="q" name="q" type="search" defaultValue={q ?? ""} placeholder="Лоферы, оливковое масло, Тоскана…" aria-label="Поиск по каталогу" />
        </label>
        <button className="btn ghost" type="submit">Найти</button>
      </form>

      <nav className="controls" aria-label="Категории">
        <Link className="chip" href={href()} aria-current={!active}>Все</Link>
        {CATEGORIES.map((c) => (
          <Link key={c} className="chip" href={href(c)} aria-current={active === c}>
            {CATEGORY_LABEL[c]}
          </Link>
        ))}
      </nav>

      {!res.ok ? (
        <div className="empty">Каталог временно недоступен. Попробуйте обновить страницу через минуту.</div>
      ) : res.data.length === 0 ? (
        <div className="empty">Ничего не нашлось. Попробуйте другой запрос или категорию.</div>
      ) : (
        <div className="grid">
          {res.data.map((p) => (
            <Link key={p.id} href={`/p/${p.id}`} className="card">
              <Thumb category={p.category} seller={p.sellerName} region={p.region} />
              <span className="brand">{p.sellerName}</span>
              <span className="name">{p.title}</span>
              <span className="price num">{rub(p.priceRub)}</span>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
