import Link from "next/link";
import type { ReactNode } from "react";

/** Шапка рабочих мест (операции, продавцы): тот же логотип, тёмно-синяя полоса бренда вместо витринной шапки. */
export function WorkHeader({ area, home, right }: { area: string; home: string; right?: ReactNode }) {
  return (
    <header className="work-top">
      <div className="wrap">
        <Link href={home} className="logo on-dark">ilpacco<span>.ru</span></Link>
        <span className="work-area">{area}</span>
        <div className="work-right">{right}</div>
      </div>
    </header>
  );
}

export function WorkShell({ children }: { children: ReactNode }) {
  return <div className="work">{children}</div>;
}
