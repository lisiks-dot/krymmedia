import type { Category } from "@/lib/types";

export function Thumb({ category, seller, region, ratio }: { category: Category; seller: string; region?: string | null; ratio?: string }) {
  const initials = seller
    .split(" ")
    .map((w) => w[0])
    .join("")
    .slice(0, 2);
  return (
    <div className="thumb" style={{ background: `var(--t-${category})`, aspectRatio: ratio }}>
      <span className="mono" aria-hidden="true">{initials}</span>
      <span className="tag">Проверено PACCO</span>
      {region ? <span className="tag region">{region}</span> : null}
    </div>
  );
}
