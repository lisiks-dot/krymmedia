"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import type { PayoutSummary, Payouts } from "@/lib/cabinet";
import { eur } from "@/lib/format";

type Row = { sellerId: number; legalName: string; iban: string; payouts: Payouts };

/** Реестр — раз в две недели, по понедельникам, считая от 5 января 2026. */
function nextRun(): Date {
  const start = new Date("2026-01-05T00:00:00");
  const day = 86_400_000;
  const passed = Math.floor((Date.now() - start.getTime()) / day);
  return new Date(start.getTime() + (Math.floor(passed / 14) + 1) * 14 * day);
}

export default function PayoutsPage() {
  const { call } = useAuth();
  const [rows, setRows] = useState<Row[] | null>(null);
  const [batches, setBatches] = useState<PayoutSummary[] | null>(null);
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    call<Row[]>("/api/admin/payouts").then(setRows).catch(() => setRows([]));
    call<PayoutSummary[]>("/api/admin/payouts/batches").then(setBatches).catch(() => setBatches([]));
  }, [call]);
  useEffect(load, [load]);

  const total = (k: keyof Payouts) => (rows ?? []).reduce((s, r) => s + r.payouts[k], 0);
  const ready = batches?.filter((b) => b.status === "READY") ?? [];

  async function create() {
    setBusy(true);
    setMessage(null);
    try {
      const made = await call<PayoutSummary[]>("/api/admin/payouts/batches", { method: "POST" });
      setMessage({ tone: "ok", text: `Реестр сформирован: ${made.length} выплат на ${eur(made.reduce((s, b) => s + b.netEurCents, 0))}. Скачайте CSV для банка, после перевода отметьте выплаты.` });
      load();
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не удалось сформировать реестр" });
    } finally {
      setBusy(false);
    }
  }

  async function paid(b: PayoutSummary, ref: string) {
    try {
      await call(`/api/admin/payouts/batches/${b.id}/paid`, { method: "POST", body: JSON.stringify({ bankRef: ref }) });
      setMessage({ tone: "ok", text: `${b.number}: выплата отмечена, продавец видит её в кабинете` });
      load();
    } catch (e) {
      setMessage({ tone: "bad", text: e instanceof ApiError ? e.messages.join("; ") : "Не удалось отметить" });
    }
  }

  function csv() {
    const head = "Номер;Продавец;IBAN;Продажи EUR;Комиссия EUR;К выплате EUR\n";
    const iban = (id: number) => rows?.find((r) => r.sellerId === id)?.iban ?? "";
    const body = ready.map((b) => [b.number, b.sellerName, iban(b.sellerId), b.grossEurCents / 100, b.commissionEurCents / 100, b.netEurCents / 100].join(";")).join("\n");
    const url = URL.createObjectURL(new Blob(["﻿" + head + body], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `ilpacco-payouts-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      <div className="page-head">
        <h1>Выплаты продавцам</h1>
        <p className="muted">В реестр попадают завершённые заказы — через 14 дней после получения, если нет спора. Позиции с возвратом не оплачиваются. Плановый реестр: <b>{nextRun().toLocaleDateString("ru-RU", { day: "numeric", month: "long" })}</b>.</p>
      </div>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
      <div className="tiles">
        <div className="tile"><span className="small muted">Готово к реестру</span><span className="v num">{eur(total("payableNetEurCents"))}</span><span className="small muted num">комиссия {eur(total("payableCommissionEurCents"))}</span></div>
        <div className="tile"><span className="small muted">Ждут перевода</span><span className="v num">{eur(ready.reduce((s, b) => s + b.netEurCents, 0))}</span><span className="small muted">{ready.length} выплат в реестре</span></div>
        <div className="tile"><span className="small muted">В пути, ещё не к оплате</span><span className="v num">{eur(total("pendingNetEurCents"))}</span><span className="small muted">заказы до завершения</span></div>
      </div>
      <div className="controls">
        <button className="btn" type="button" onClick={create} disabled={busy || total("payableNetEurCents") === 0}>Сформировать реестр</button>
        <button className="btn ghost" type="button" onClick={csv} disabled={!ready.length}>Скачать CSV для банка</button>
      </div>

      <section className="panel">
        <h3>Выплаты</h3>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Номер</th><th>Продавец</th><th className="r">Продажи</th><th className="r">Комиссия</th><th className="r">К выплате</th><th>Статус</th><th /></tr></thead>
            <tbody>
              {batches === null ? <tr><td colSpan={7} className="muted">Загружаем…</td></tr> : batches.length === 0 ? (
                <tr><td colSpan={7} className="muted">Реестров ещё не было</td></tr>
              ) : batches.map((b) => <BatchRow key={b.id} b={b} onPaid={paid} />)}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <h3>По продавцам</h3>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Продавец</th><th>IBAN</th><th className="r">Готово к реестру</th><th className="r">В пути</th></tr></thead>
            <tbody>
              {(rows ?? []).map((r) => (
                <tr key={r.sellerId}>
                  <td>{r.legalName}</td>
                  <td className="num small">{r.iban.replace(/(.{4})/g, "$1 ").trim()}</td>
                  <td className="r num"><b>{eur(r.payouts.payableNetEurCents)}</b></td>
                  <td className="r num muted">{eur(r.payouts.pendingNetEurCents)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function BatchRow({ b, onPaid }: { b: PayoutSummary; onPaid: (b: PayoutSummary, ref: string) => void }) {
  const [ref, setRef] = useState("");
  return (
    <tr>
      <td className="num"><Link href={`/admin/payouts/${b.id}`}><b>{b.number}</b></Link><div className="small muted">{new Date(b.createdAt).toLocaleDateString("ru-RU")}</div></td>
      <td>{b.sellerName}<div className="small muted">{b.linesCount} поз.</div></td>
      <td className="r num">{eur(b.grossEurCents)}</td>
      <td className="r num">− {eur(b.commissionEurCents)}</td>
      <td className="r num"><b>{eur(b.netEurCents)}</b></td>
      <td>
        {b.status === "PAID"
          ? <span className="pill ok">Выплачено {b.paidAt ? new Date(b.paidAt).toLocaleDateString("ru-RU") : ""}</span>
          : <span className="pill warn">Ждёт перевода</span>}
        {b.bankRef ? <div className="small muted">{b.bankRef}</div> : null}
      </td>
      <td>
        {b.status === "READY" ? (
          <form className="row-form" onSubmit={(e) => { e.preventDefault(); onPaid(b, ref); }}>
            <input className="inline-input" value={ref} onChange={(e) => setRef(e.target.value)} placeholder="№ платёжки" aria-label={`Номер платёжки для ${b.number}`} />
            <button className="btn sm" type="submit">Выплачено</button>
          </form>
        ) : <Link className="small" href={`/admin/payouts/${b.id}`}>Документы</Link>}
      </td>
    </tr>
  );
}
