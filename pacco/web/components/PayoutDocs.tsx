"use client";

import { useState } from "react";
import type { PayoutDetail } from "@/lib/cabinet";

const CAT_IT: Record<string, string> = { CLOTHES: "Abbigliamento", SHOES: "Calzature", BEAUTY: "Cosmetica", HOME: "Casa", FOOD: "Alimentari" };

/** Реквизиты площадки — заполнить после регистрации юрлица. */
const PLATFORM = {
  name: "OOO «Ильпакко» (ilpacco.ru)",
  address: "Mosca, Federazione Russa — indirizzo legale da completare",
  tax: "INN/KPP da completare",
};

const euro = (cents: number) => (cents / 100).toLocaleString("it-IT", { style: "currency", currency: "EUR" });
const day = (iso: string) => new Date(iso).toLocaleDateString("it-IT", { day: "2-digit", month: "2-digit", year: "numeric" });

/**
 * Документы по выплате: отчёт агента (что продано, комиссия, к перечислению) и счёт на комиссию площадки.
 * Двуязычные заголовки: итальянский — для продавца и его бухгалтера, русский — для учёта площадки.
 * Печать в PDF — из браузера. Формулировки и налоговые отметки проверяет бухгалтер.
 */
export function PayoutDocs({ d }: { d: PayoutDetail }) {
  const [doc, setDoc] = useState<"report" | "invoice">("report");
  const p = d.payout;
  return (
    <div className="slip">
      <div className="acts no-print">
        <div className="segmented sm" role="radiogroup" aria-label="Документ">
          <button type="button" role="radio" aria-checked={doc === "report"} onClick={() => setDoc("report")}>Rendiconto · Отчёт агента</button>
          <button type="button" role="radio" aria-checked={doc === "invoice"} onClick={() => setDoc("invoice")}>Fattura · Счёт на комиссию</button>
        </div>
        <button className="btn sm" type="button" onClick={() => window.print()}>Stampa / PDF</button>
      </div>

      <article className="slip-page doc">
        <header>
          <span className="logo">ilpacco<span>.ru</span></span>
          <div className="slip-no">
            <span className="small">{doc === "report" ? "Rendiconto vendite · Отчёт агента" : "Fattura · Счёт"}</span>
            <b className="num">{doc === "report" ? p.number : `F-${p.number}`}</b>
            <span className="small">{day(p.createdAt)}</span>
          </div>
        </header>

        <div className="two">
          <div>
            <span className="small muted">{doc === "report" ? "Agente · Агент" : "Fornitore · Исполнитель"}</span>
            <p>{PLATFORM.name}<br />{PLATFORM.address}<br />{PLATFORM.tax}</p>
          </div>
          <div>
            <span className="small muted">{doc === "report" ? "Venditore · Продавец" : "Cliente · Заказчик"}</span>
            <p>{d.seller.legalName}<br />P. IVA {d.seller.partitaIva}<br />IBAN {d.seller.iban}</p>
          </div>
        </div>

        {doc === "report" ? (
          <>
            <p className="small">Vendite completate e incassate dall&apos;agente per conto del venditore, al netto della commissione. Продажи, завершённые и оплаченные покупателями, за вычетом комиссии агента.</p>
            <table className="t">
              <thead><tr><th>Ordine</th><th>Articolo</th><th>Categoria</th><th className="r">Q.tà</th><th className="r">Importo</th><th className="r">Comm.</th><th className="r">Netto</th></tr></thead>
              <tbody>
                {d.lines.map((l) => (
                  <tr key={`${l.orderNumber}-${l.lineNo}`}>
                    <td className="num">{l.orderNumber}<div className="small muted">{day(l.completedAt)}</div></td>
                    <td>{l.title}{l.size ? `, ${l.size}` : ""}</td>
                    <td>{CAT_IT[l.category] ?? l.category} · {Math.round(l.rate * 100)}%</td>
                    <td className="r num">{l.qty}</td>
                    <td className="r num">{euro(l.grossEurCents)}</td>
                    <td className="r num">− {euro(l.commissionEurCents)}</td>
                    <td className="r num">{euro(l.grossEurCents - l.commissionEurCents)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="doc-totals">
              <div className="row"><span>Totale vendite · Продажи</span><span className="num">{euro(p.grossEurCents)}</span></div>
              <div className="row"><span>Commissione · Комиссия</span><span className="num">− {euro(p.commissionEurCents)}</span></div>
              <div className="row total"><span>Da bonificare · К перечислению</span><span className="num">{euro(p.netEurCents)}</span></div>
              <div className="small muted">{p.status === "PAID" ? `Bonifico eseguito il ${day(p.paidAt!)} · ${p.bankRef}` : "Bonifico in preparazione · Перевод готовится"}</div>
            </div>
          </>
        ) : (
          <>
            <table className="t">
              <thead><tr><th>Descrizione · Описание</th><th className="r">Imponibile</th></tr></thead>
              <tbody>
                <tr>
                  <td>Commissione di intermediazione per vendite tramite ilpacco.ru, rendiconto {p.number} ({p.linesCount} articoli) · Агентская комиссия по отчёту {p.number}</td>
                  <td className="r num">{euro(p.commissionEurCents)}</td>
                </tr>
              </tbody>
            </table>
            <div className="doc-totals">
              <div className="row"><span>Imponibile</span><span className="num">{euro(p.commissionEurCents)}</span></div>
              <div className="row"><span>IVA</span><span className="num">—</span></div>
              <div className="row total"><span>Totale · Итого</span><span className="num">{euro(p.commissionEurCents)}</span></div>
            </div>
            <p className="small">Operazione non soggetta a IVA ai sensi dell&apos;art. 7-ter DPR 633/72 — inversione contabile a carico del committente. Importo già trattenuto dal rendiconto {p.number}: nessun pagamento dovuto.</p>
            <p className="small muted">Комиссия удержана из выплаты по отчёту {p.number}, отдельная оплата не требуется. Налоговые формулировки — проверить с бухгалтером.</p>
          </>
        )}
      </article>
    </div>
  );
}
