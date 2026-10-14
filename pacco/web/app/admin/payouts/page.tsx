"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import type { Payouts } from "@/lib/cabinet";
import { eur } from "@/lib/format";

type Row = { sellerId: number; legalName: string; iban: string; payouts: Payouts };

/** Ближайший реестр — каждый второй понедельник, считая от 5 января 2026. */
function nextRun(): Date {
  const start = new Date("2026-01-05T00:00:00");
  const day = 86_400_000;
  const passed = Math.floor((Date.now() - start.getTime()) / day);
  return new Date(start.getTime() + (Math.floor(passed / 14) + 1) * 14 * day);
}

export default function PayoutsPage() {
  const { call } = useAuth();
  const [rows, setRows] = useState<Row[] | null>(null);

  useEffect(() => {
    call<Row[]>("/api/admin/payouts").then(setRows).catch(() => setRows([]));
  }, [call]);

  const total = (k: keyof Payouts) => (rows ?? []).reduce((s, r) => s + r.payouts[k], 0);

  function csv() {
    const head = "Продавец;IBAN;Продажи EUR;Комиссия EUR;К выплате EUR\n";
    const body = (rows ?? []).filter((r) => r.payouts.payableNetEurCents > 0)
      .map((r) => [r.legalName, r.iban, r.payouts.payableGrossEurCents / 100, r.payouts.payableCommissionEurCents / 100, r.payouts.payableNetEurCents / 100].join(";"))
      .join("\n");
    const url = URL.createObjectURL(new Blob(["﻿" + head + body], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `ilpacco-payouts-${nextRun().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      <div className="page-head">
        <h1>Выплаты продавцам</h1>
        <p className="muted">В реестр попадают завершённые заказы — после 14 дней на споры. Позиции с возвратом не оплачиваются. Ближайший реестр: <b>{nextRun().toLocaleDateString("ru-RU", { day: "numeric", month: "long" })}</b>.</p>
      </div>
      <div className="tiles">
        <div className="tile"><span className="small muted">К выплате в реестре</span><span className="v num">{eur(total("payableNetEurCents"))}</span><span className="small muted num">комиссия {eur(total("payableCommissionEurCents"))}</span></div>
        <div className="tile"><span className="small muted">В пути, ещё не к оплате</span><span className="v num">{eur(total("pendingNetEurCents"))}</span><span className="small muted num">продажи {eur(total("pendingGrossEurCents"))}</span></div>
      </div>
      <section className="panel">
        <div className="row"><h3>Реестр</h3><button className="btn sm ghost" type="button" onClick={csv} disabled={!rows?.some((r) => r.payouts.payableNetEurCents > 0)}>Скачать CSV для банка</button></div>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Продавец</th><th>IBAN</th><th className="r">Продажи</th><th className="r">Комиссия</th><th className="r">К выплате</th><th className="r">В пути</th></tr></thead>
            <tbody>
              {rows === null ? <tr><td colSpan={6} className="muted">Загружаем…</td></tr> : rows.length === 0 ? (
                <tr><td colSpan={6} className="muted">Активных продавцов нет</td></tr>
              ) : rows.map((r) => (
                <tr key={r.sellerId}>
                  <td>{r.legalName}</td>
                  <td className="num small">{r.iban.replace(/(.{4})/g, "$1 ").trim()}</td>
                  <td className="r num">{eur(r.payouts.payableGrossEurCents)}</td>
                  <td className="r num">− {eur(r.payouts.payableCommissionEurCents)}</td>
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
