import Link from "next/link";
import { eur, rub } from "@/lib/format";
import type { Product } from "@/lib/types";
import { FavoriteButton } from "./Client";
import { ProductArt } from "./ProductArt";

export function ProductCard({ p }: { p: Product }) {
  const dutyFree = p.priceEurCents <= 20000;
  return (
    <article className="pcard">
      <Link href={`/p/${p.id}`}>
        <ProductArt id={p.id} title={p.title} category={p.category} label={`${p.sellerName}: ${p.title}`} />
        <div className="tags">
          {p.region ? <span className="badge">{p.region}</span> : null}
          {dutyFree ? <span className="badge ok">Без пошлины</span> : null}
        </div>
        <span className="brand">{p.sellerName}</span>
        <span className="name">{p.title}</span>
        <span className="price num">{rub(p.priceRub)}<small>{eur(p.priceEurCents)} в Италии</small></span>
      </Link>
      <FavoriteButton id={p.id} title={p.title} />
    </article>
  );
}

export function ProductGridSkeleton({ n = 4 }: { n?: number }) {
  return (
    <div className="pgrid" aria-hidden="true">
      {Array.from({ length: n }, (_, i) => (
        <div key={i} className="pcard">
          <div className="skel" style={{ aspectRatio: "4 / 5" }} />
          <div className="skel" style={{ height: 14, width: "50%", marginTop: 8 }} />
          <div className="skel" style={{ height: 14, width: "80%" }} />
        </div>
      ))}
    </div>
  );
}
