"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

export function SortSelect({ value }: { value: string }) {
  const router = useRouter();
  const path = usePathname();
  const params = useSearchParams();
  return (
    <label className="small" style={{ display: "flex", gap: 8, alignItems: "center" }}>
      <span className="muted">Сортировка</span>
      <select
        value={value}
        onChange={(e) => {
          const p = new URLSearchParams(params.toString());
          if (e.target.value === "popular") p.delete("sort");
          else p.set("sort", e.target.value);
          router.push(`${path}${p.toString() ? `?${p}` : ""}`);
        }}
      >
        <option value="popular">Рекомендуем</option>
        <option value="new">Сначала новые</option>
        <option value="price_asc">Сначала недорогие</option>
        <option value="price_desc">Сначала дорогие</option>
      </select>
    </label>
  );
}
