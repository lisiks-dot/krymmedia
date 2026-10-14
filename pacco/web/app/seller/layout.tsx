"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { AuthProvider, useAuth } from "@/lib/auth";
import { ApiError } from "@/lib/client";
import type { Seller } from "@/lib/cabinet";
import { SellerContext } from "@/lib/seller-context";

function Application({ previous, onDone }: { previous: Seller | null; onDone: (s: Seller) => void }) {
  const { call } = useAuth();
  const [errors, setErrors] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    setErrors([]);
    try {
      onDone(await call<Seller>("/api/seller/me", {
        method: "POST",
        body: JSON.stringify({
          legalName: f.get("legalName"),
          partitaIva: String(f.get("partitaIva") ?? "").replace(/\s/g, "").toUpperCase(),
          iban: String(f.get("iban") ?? "").replace(/\s/g, "").toUpperCase(),
          region: f.get("region"),
          contactEmail: f.get("email"),
        }),
      }));
    } catch (err) {
      setErrors(err instanceof ApiError ? err.messages : ["Invio non riuscito. Controlla la connessione."]);
      setBusy(false);
    }
  }

  return (
    <div className="cols">
      <form className="panel" onSubmit={submit} noValidate>
        <h1>Vendi in Russia con ilpacco.ru</h1>
        <p className="muted" style={{ margin: 0 }}>
          Compila la richiesta: la verifichiamo entro 3 giorni lavorativi. Dopo l&apos;approvazione potrai pubblicare i prodotti.
        </p>
        {previous?.status === "REJECTED" ? (
          <div className="note bad">La richiesta precedente è stata respinta: {previous.rejectReason}</div>
        ) : null}
        <div className="field"><label htmlFor="legalName">Ragione sociale</label><input id="legalName" name="legalName" required defaultValue={previous?.legalName} /></div>
        <div className="two">
          <div className="field"><label htmlFor="partitaIva">Partita IVA</label><input id="partitaIva" name="partitaIva" required placeholder="IT01234567890" defaultValue={previous?.partitaIva} /></div>
          <div className="field"><label htmlFor="region">Regione</label><input id="region" name="region" placeholder="Marche" defaultValue={previous?.region ?? ""} /></div>
        </div>
        <div className="field"><label htmlFor="iban">IBAN per i pagamenti in euro</label><input id="iban" name="iban" required placeholder="IT60 X054 2811 1010 0000 0123 456" defaultValue={previous?.iban} /></div>
        <div className="field"><label htmlFor="email">Email di contatto</label><input id="email" name="email" type="email" required defaultValue={previous?.contactEmail} /></div>
        {errors.length ? <div className="note bad">Controlla i dati: {errors.join("; ")}</div> : null}
        <div className="acts"><button className="btn" type="submit" disabled={busy}>{busy ? "Invio…" : "Invia la richiesta"}</button></div>
      </form>
      <aside className="summary">
        <h3>Come funziona</h3>
        <ol style={{ margin: 0, paddingLeft: 18, display: "flex", flexDirection: "column", gap: 6 }}>
          <li>Pubblichi i prodotti: il sistema verifica subito le regole UE (max 290 € per unità) e i divieti doganali.</li>
          <li>Il cliente paga in rubli, tu ricevi euro ogni due settimane, meno la commissione (12–18 %).</li>
          <li>Confermi l&apos;ordine e lo spedisci al nostro hub di Milano entro 3 giorni lavorativi.</li>
        </ol>
      </aside>
    </div>
  );
}

function Gate({ children }: { children: ReactNode }) {
  const { call, username, logout } = useAuth();
  const path = usePathname();
  const [seller, setSeller] = useState<Seller | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    call<Seller>("/api/seller/me")
      .then(setSeller)
      .catch((e) => (e instanceof ApiError && e.status === 404 ? setSeller(null) : setError("Servizio non disponibile. Riprova tra un minuto.")));
  }, [call]);

  useEffect(load, [load]);

  if (error) return <div className="empty">{error}</div>;
  if (seller === undefined) return <div className="empty">Caricamento…</div>;
  if (seller === null || seller.status === "REJECTED") return <Application previous={seller} onDone={setSeller} />;
  if (seller.status === "PENDING") {
    return (
      <div className="panel">
        <h1>Richiesta in verifica</h1>
        <p style={{ margin: 0 }}>
          Grazie, {seller.legalName}. Verifichiamo Partita IVA e IBAN entro 3 giorni lavorativi e ti scriviamo a {seller.contactEmail}.
        </p>
        <div className="acts"><button className="btn ghost sm" type="button" onClick={load}>Aggiorna lo stato</button></div>
      </div>
    );
  }

  const tabs = [
    { href: "/seller", label: "Prodotti" },
    { href: "/seller/orders", label: "Ordini" },
    { href: "/seller/payouts", label: "Pagamenti" },
  ];
  return (
    <SellerContext.Provider value={seller}>
      <div className="cabinet">
        <nav className="side" aria-label="Area venditori">
          {tabs.map((t) => (
            <Link key={t.href} href={t.href} aria-current={path === t.href ? "page" : undefined}>{t.label}</Link>
          ))}
          <span className="who">{seller.legalName} · {username}</span>
          <button className="btn ghost sm" type="button" onClick={logout}>Esci</button>
        </nav>
        <div className="stack">{children}</div>
      </div>
    </SellerContext.Provider>
  );
}

export default function SellerLayout({ children }: { children: ReactNode }) {
  return (
    <div lang="it">
      <AuthProvider locale="it">
        <Gate>{children}</Gate>
      </AuthProvider>
    </div>
  );
}
