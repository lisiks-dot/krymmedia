"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import { fmtDate, type Product, type Rules } from "@/lib/cabinet";
import { CATEGORY_LABEL, type Category } from "@/lib/types";

const CATS = Object.keys(CATEGORY_LABEL) as Category[];
const split = (s: string) => s.split(/[,\s]+/).map((x) => x.trim()).filter(Boolean);

export default function AdminCompliance() {
  const { call, has } = useAuth();
  const canEdit = has("admin");
  const [rules, setRules] = useState<Rules | null>(null);
  const [threshold, setThreshold] = useState("");
  const [cats, setCats] = useState<Category[]>([]);
  const [embargo, setEmbargo] = useState("");
  const [alcohol, setAlcohol] = useState("");
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);

  function apply(r: Rules) {
    setRules(r);
    setThreshold(String(r.thresholdEurCents / 100));
    setCats(r.thresholdCategories);
    setEmbargo(r.embargoPrefixes.join(", "));
    setAlcohol(r.alcoholPrefixes.join(", "));
  }

  useEffect(() => {
    call<Rules>("/api/admin/compliance/rules").then(apply).catch(() => setMessage({ tone: "bad", text: "Не удалось загрузить правила" }));
  }, [call]);

  async function save(e: FormEvent) {
    e.preventDefault();
    const value = Number(threshold.replace(",", "."));
    if (!(value > 0)) {
      setMessage({ tone: "bad", text: "Порог должен быть положительным числом в евро." });
      return;
    }
    try {
      const countBlocked = async () => (await call<Product[]>("/api/admin/products")).filter((p) => p.status === "BLOCKED").length;
      const before = await countBlocked();
      const r = await call<Rules>("/api/admin/compliance/rules", {
        method: "PUT",
        body: JSON.stringify({
          thresholdEurCents: Math.round(value * 100),
          thresholdCategories: cats,
          embargoPrefixes: split(embargo),
          alcoholPrefixes: split(alcohol),
        }),
      });
      apply(r);
      const after = await countBlocked();
      setMessage({ tone: after > before ? "warn" : "ok", text: `Сохранено. Каталог перепроверен: снято правилами ${after} карточек (было ${before}).` });
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Не удалось сохранить правила" });
    }
  }

  return (
    <>
      <h1>Комплаенс</h1>
      <p className="muted" style={{ margin: 0, maxWidth: "65ch" }}>
        Правила проверяются при сохранении карточки, при публикации и при каждом изменении — весь каталог перепроверяется сразу.
        {rules ? ` Последнее изменение: ${fmtDate(rules.updatedAt)}.` : ""}
      </p>
      {!canEdit ? <div className="note warn">Менять правила может только администратор; вам доступен просмотр.</div> : null}
      <form className="panel" onSubmit={save}>
        <div className="fields">
          <div className="field">
            <label htmlFor="th">C-01 · порог цены за единицу, €</label>
            <input id="th" inputMode="decimal" value={threshold} onChange={(e) => setThreshold(e.target.value)} disabled={!canEdit} />
            <span className="small muted">Санкции ЕС: 300 €, порог ниже — запас на курс</span>
          </div>
          <div className="field">
            <label htmlFor="emb">C-03 · префиксы кодов продэмбарго</label>
            <input id="emb" value={embargo} onChange={(e) => setEmbargo(e.target.value)} disabled={!canEdit} />
            <span className="small muted">Указ № 560, действует до 31.12.2028</span>
          </div>
          <div className="field">
            <label htmlFor="alc">C-04 · префиксы кодов алкоголя</label>
            <input id="alc" value={alcohol} onChange={(e) => setAlcohol(e.target.value)} disabled={!canEdit} />
          </div>
        </div>
        <fieldset style={{ border: "none", padding: 0, margin: 0 }}>
          <legend style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>C-01 · категории под порогом</legend>
          <div className="acts">
            {CATS.map((c) => (
              <label key={c} className="choice" htmlFor={`c-${c}`} style={{ marginRight: 12 }}>
                <input id={`c-${c}`} type="checkbox" checked={cats.includes(c)} disabled={!canEdit}
                  onChange={(e) => setCats(e.target.checked ? [...cats, c] : cats.filter((x) => x !== c))} />
                <span>{CATEGORY_LABEL[c]}</span>
              </label>
            ))}
          </div>
        </fieldset>
        {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}
        {canEdit ? <div className="acts"><button className="btn" type="submit">Сохранить и перепроверить каталог</button></div> : null}
      </form>
    </>
  );
}
