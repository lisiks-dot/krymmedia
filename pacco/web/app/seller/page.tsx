"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError, api } from "@/lib/client";
import { PRODUCT_STATUS_IT, type Product, type Violation } from "@/lib/cabinet";
import { eur, rub } from "@/lib/format";
import type { Category } from "@/lib/types";
import { useSeller } from "@/lib/seller-context";

const CATEGORY_IT: Record<Category, string> = {
  CLOTHES: "Abbigliamento",
  SHOES: "Calzature",
  BEAUTY: "Cosmetica",
  HOME: "Casa",
  FOOD: "Alimentari",
};

const toCents = (s: string) => Math.round(Number(s.replace(",", ".")) * 100);

export default function SellerProducts() {
  const { call } = useAuth();
  const seller = useSeller();
  const [products, setProducts] = useState<Product[] | null>(null);
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);
  const [prices, setPrices] = useState<Record<number, string>>({});

  // Modulo nuovo prodotto
  const [category, setCategory] = useState<Category>("SHOES");
  const [hsCode, setHsCode] = useState("");
  const [price, setPrice] = useState("");
  const [aerosol, setAerosol] = useState(false);
  const [check, setCheck] = useState<Violation[] | null>(null);
  const [busy, setBusy] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);

  const load = useCallback(() => {
    call<Product[]>("/api/seller/products").then(setProducts).catch(() => setProducts([]));
  }, [call]);
  useEffect(load, [load]);

  // Verifica in tempo reale: stesso motore delle regole usato alla pubblicazione
  useEffect(() => {
    const cents = toCents(price);
    if (!hsCode && !price) {
      setCheck(null);
      return;
    }
    const t = window.setTimeout(() => {
      api<{ passed: boolean; violations: Violation[] }>("/api/compliance/check", {
        method: "POST",
        body: JSON.stringify({ category, hsCode: hsCode.replace(/\s/g, ""), priceEurCents: Number.isFinite(cents) ? cents : 0, aerosol }),
      }).then((r) => setCheck(r.violations)).catch(() => setCheck(null));
    }, 350);
    return () => window.clearTimeout(t);
  }, [category, hsCode, price, aerosol]);

  async function create(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    try {
      const p = await call<Product>("/api/seller/products", {
        method: "POST",
        body: JSON.stringify({
          title: f.get("title"),
          category,
          hsCode: hsCode.replace(/\s/g, ""),
          priceEurCents: toCents(price),
          weightGrams: Number(f.get("weight")),
          sizes: String(f.get("sizes") ?? "").split(",").map((s) => s.trim()).filter(Boolean),
          description: f.get("description"),
          aerosol,
        }),
      });
      setMessage({ tone: "ok", text: `«${p.title}» inviato in revisione. Di solito entro 24 ore.` });
      formRef.current?.reset();
      setHsCode("");
      setPrice("");
      setAerosol(false);
      load();
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Salvataggio non riuscito" });
    } finally {
      setBusy(false);
    }
  }

  async function savePrice(p: Product) {
    const cents = toCents(prices[p.id] ?? "");
    if (!(cents > 0)) return;
    try {
      await call(`/api/seller/products/${p.id}/price`, { method: "PUT", body: JSON.stringify({ priceEurCents: cents }) });
      setMessage({ tone: "ok", text: `Prezzo di «${p.title}» aggiornato` });
      setPrices({ ...prices, [p.id]: "" });
      load();
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Aggiornamento non riuscito" });
    }
  }

  return (
    <>
      <h1>Prodotti</h1>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}

      <section className="panel">
        <div className="row" style={{ alignItems: "baseline" }}>
          <h2>I tuoi prodotti</h2>
          <span className="small muted">{seller.legalName}</span>
        </div>
        <div className="scroll">
          <table className="t">
            <thead><tr><th>Prodotto</th><th>Codice NC</th><th className="r">Prezzo</th><th>Stato</th><th>Nuovo prezzo, €</th></tr></thead>
            <tbody>
              {products === null ? (
                <tr><td colSpan={5} className="muted">Caricamento…</td></tr>
              ) : products.length === 0 ? (
                <tr><td colSpan={5} className="muted">Nessun prodotto. Aggiungi il primo con il modulo qui sotto.</td></tr>
              ) : products.map((p) => (
                <tr key={p.id}>
                  <td>{p.title}<div className="small muted">{CATEGORY_IT[p.category]}</div></td>
                  <td className="num">{p.hsCode}</td>
                  <td className="r num">{eur(p.priceEurCents)}<div className="small muted">{rub(p.priceRub)} al cliente</div></td>
                  <td>
                    <span className={`pill ${PRODUCT_STATUS_IT[p.status]?.tone ?? "neutral"}`}>{PRODUCT_STATUS_IT[p.status]?.label ?? p.status}</span>
                    {p.blockReason ? <div className="small muted">{p.blockReason}</div> : null}
                  </td>
                  <td>
                    <div className="acts">
                      <input className="inline-input" inputMode="decimal" aria-label={`Nuovo prezzo per ${p.title}`} placeholder={String(p.priceEurCents / 100)}
                        value={prices[p.id] ?? ""} onChange={(e) => setPrices({ ...prices, [p.id]: e.target.value })} />
                      <button className="btn ghost sm" type="button" onClick={() => savePrice(p)} disabled={!prices[p.id]}>Salva</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <form ref={formRef} className="panel" onSubmit={create}>
        <h2>Nuovo prodotto</h2>
        <div className="fields">
          <div className="field" style={{ gridColumn: "1 / -1" }}><label htmlFor="title">Nome del prodotto (in russo o italiano)</label><input id="title" name="title" required /></div>
          <div className="field">
            <label htmlFor="category">Categoria</label>
            <select id="category" value={category} onChange={(e) => setCategory(e.target.value as Category)}>
              {(Object.keys(CATEGORY_IT) as Category[]).map((c) => <option key={c} value={c}>{CATEGORY_IT[c]}</option>)}
            </select>
          </div>
          <div className="field"><label htmlFor="hs">Codice NC (8 cifre)</label><input id="hs" inputMode="numeric" value={hsCode} onChange={(e) => setHsCode(e.target.value)} required /></div>
          <div className="field"><label htmlFor="price">Prezzo per unità, €</label><input id="price" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required /></div>
          <div className="field"><label htmlFor="weight">Peso, grammi</label><input id="weight" name="weight" type="number" min={1} required /></div>
          <div className="field"><label htmlFor="sizes">Taglie, separate da virgola</label><input id="sizes" name="sizes" placeholder="40, 41, 42" /></div>
        </div>
        <div className="field"><label htmlFor="description">Descrizione</label><input id="description" name="description" /></div>
        <label className="choice" htmlFor="aerosol">
          <input id="aerosol" type="checkbox" checked={aerosol} onChange={(e) => setAerosol(e.target.checked)} />
          <span>Aerosol o prodotto infiammabile</span>
        </label>
        {check === null ? null : check.length === 0 ? (
          <div className="note ok" role="status">Verifica superata: il prodotto può essere venduto in Russia.</div>
        ) : (
          <div className="note bad" role="status">
            Il prodotto non può essere pubblicato:
            <ul style={{ margin: "6px 0 0", paddingLeft: 18 }}>{check.map((v, i) => <li key={i}><b>{v.code}</b> {v.message}</li>)}</ul>
          </div>
        )}
        <div className="acts"><button className="btn" type="submit" disabled={busy || (check !== null && check.length > 0)}>{busy ? "Invio…" : "Invia in revisione"}</button></div>
      </form>
    </>
  );
}
