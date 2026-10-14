"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { SELLER_STATUS_RU, fmtDate, type Seller, type SellerStatus } from "@/lib/cabinet";

export default function AdminSellers() {
  const { call } = useAuth();
  const [filter, setFilter] = useState<SellerStatus | "">("PENDING");
  const [list, setList] = useState<Seller[] | null>(null);
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(() => {
    call<Seller[]>(`/api/admin/sellers${filter ? `?status=${filter}` : ""}`).then(setList).catch(() => setList([]));
  }, [call, filter]);

  useEffect(load, [load]);

  async function act(s: Seller, action: "approve" | "reject") {
    try {
      await call(`/api/admin/sellers/${s.id}/${action}`, {
        method: "POST",
        body: action === "reject" ? JSON.stringify({ reason: reasons[s.id] ?? "" }) : undefined,
      });
      setMessage(action === "approve" ? `${s.legalName}: продавец одобрен` : `${s.legalName}: заявка отклонена`);
      load();
    } catch (e) {
      setMessage(e instanceof ApiError ? e.messages.join("; ") : "Не удалось сохранить решение");
    }
  }

  return (
    <>
      <h1>Продавцы</h1>
      <div className="controls">
        <label className="small" htmlFor="f" style={{ fontWeight: 600 }}>Статус</label>
        <select id="f" className="inline-input" style={{ width: 180 }} value={filter} onChange={(e) => setFilter(e.target.value as SellerStatus | "")}>
          <option value="PENDING">Ждут проверки</option>
          <option value="ACTIVE">Активные</option>
          <option value="REJECTED">Отклонённые</option>
          <option value="">Все</option>
        </select>
      </div>
      {message ? <div className="note ok" role="status">{message}</div> : null}
      <section className="panel">
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Компания</th><th>Partita IVA</th><th>IBAN</th><th>Регион</th><th>Подана</th><th>Статус</th><th>Решение</th></tr></thead>
            <tbody>
              {list === null ? (
                <tr><td colSpan={7} className="muted">Загружаем…</td></tr>
              ) : list.length === 0 ? (
                <tr><td colSpan={7} className="muted">Заявок с таким статусом нет</td></tr>
              ) : list.map((s) => (
                <tr key={s.id}>
                  <td>{s.legalName}<div className="small muted">{s.contactEmail}</div></td>
                  <td className="num">{s.partitaIva}</td>
                  <td className="num small">{s.iban}</td>
                  <td>{s.region ?? "—"}</td>
                  <td className="num">{fmtDate(s.createdAt)}</td>
                  <td>
                    <span className={`pill ${SELLER_STATUS_RU[s.status].tone}`}>{SELLER_STATUS_RU[s.status].label}</span>
                    {s.rejectReason ? <div className="small muted">{s.rejectReason}</div> : null}
                  </td>
                  <td>
                    {s.status === "PENDING" ? (
                      <div className="acts">
                        <button className="btn sm" type="button" onClick={() => act(s, "approve")}>Одобрить</button>
                        <input className="inline-input" aria-label="Причина отказа" placeholder="Причина" value={reasons[s.id] ?? ""}
                          onChange={(e) => setReasons({ ...reasons, [s.id]: e.target.value })} />
                        <button className="btn danger sm" type="button" onClick={() => act(s, "reject")}>Отклонить</button>
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
