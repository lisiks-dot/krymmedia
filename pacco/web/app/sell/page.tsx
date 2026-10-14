import type { Metadata } from "next";
import Link from "next/link";
import { ProductArt } from "@/components/ProductArt";
import { WorkHeader, WorkShell } from "@/components/Work";

export const metadata: Metadata = {
  title: "Vendi su ilpacco.ru",
  description: "Produttori italiani: vendete ai clienti in Russia. Spedite all'hub di Milano, al resto pensiamo noi.",
};

const STEPS = [
  { t: "Invia la richiesta", d: "Ragione sociale, Partita IVA e IBAN. Verifichiamo entro 3 giorni lavorativi." },
  { t: "Pubblica i prodotti", d: "Il sistema controlla subito le regole UE e doganali: vedi l'esito prima di inviare la scheda." },
  { t: "Conferma l'ordine", d: "Il cliente ha già pagato. Hai 24 ore per confermare o segnalare un articolo non disponibile." },
  { t: "Spedisci all'hub di Milano", d: "Entro 3 giorni lavorativi, con il foglio di accompagnamento. Verifichiamo, fotografiamo e imballiamo noi." },
  { t: "Ricevi i pagamenti in euro", d: "14 giorni dopo la consegna al cliente, ogni due settimane sul tuo IBAN, al netto della commissione." },
];

export default function SellPage() {
  return (
    <WorkShell>
      <WorkHeader area="Per i produttori" home="/sell" right={<Link className="btn sm on-dark" href="/seller">Accedi</Link>} />
      <main className="sell" id="content" lang="it">
        <section className="sell-hero">
          <div className="wrap">
            <div className="copy">
              <h1>I tuoi prodotti, ai clienti in Russia. Senza pensare a spedizione e dogana.</h1>
              <p>Tu spedisci all&apos;hub di Milano come a un cliente italiano. Noi verifichiamo la merce, la consegniamo in Russia, sdoganiamo e incassiamo in rubli. Tu ricevi euro.</p>
              <div className="controls">
                <Link className="btn" href="/seller">Invia la richiesta</Link>
                <a className="btn ghost light" href="#regole">Regole e commissioni</a>
              </div>
            </div>
            <div className="sell-arts" aria-hidden="true">
              <ProductArt id={0} title="mocassino" category="SHOES" palette={0} />
              <ProductArt id={0} title="cardigan" category="CLOTHES" palette={3} />
              <ProductArt id={0} title="crema" category="BEAUTY" palette={5} />
            </div>
          </div>
        </section>

        <div className="wrap sell-body">
          <section className="stack">
            <h2>Come funziona</h2>
            <ol className="flow">
              {STEPS.map((s, i) => (
                <li key={s.t}><span className="n num">{i + 1}</span><b>{s.t}</b><span className="muted">{s.d}</span></li>
              ))}
            </ol>
          </section>

          <section className="sell-cols" id="regole">
            <div className="stack">
              <h2>Commissioni</h2>
              <p className="muted">Solo sulle vendite consegnate. Nessun canone, nessun costo di pubblicazione.</p>
              <table className="t compact">
                <tbody>
                  <tr><td>Abbigliamento e calzature</td><td className="r num"><b>18 %</b></td></tr>
                  <tr><td>Cosmetica</td><td className="r num"><b>15 %</b></td></tr>
                  <tr><td>Casa e arredo</td><td className="r num"><b>12 %</b></td></tr>
                  <tr><td>Alimentari (solo in ordini misti)</td><td className="r num"><b>12 %</b></td></tr>
                </tbody>
              </table>
              <p className="small muted">La spedizione nazionale fino all&apos;hub è a tuo carico. Da Milano in poi la paga il cliente.</p>
            </div>
            <div className="stack">
              <h2>Cosa si può vendere</h2>
              <ul className="rules">
                <li><b>Massimo 290 € per articolo</b> per moda, calzature, cosmetica e casa: il Reg. UE 833/2014 vieta l&apos;export di beni di lusso sopra 300 €.</li>
                <li><b>Niente alcolici</b>, carne, pesce, latticini, frutta e verdura fresche.</li>
                <li><b>Niente aerosol</b> e liquidi infiammabili: non viaggiano in aereo.</li>
                <li>Codice doganale (HS) a 8 cifre su ogni scheda. Il controllo è automatico: se una regola cambia, i prodotti interessati vengono sospesi e te lo segnaliamo.</li>
              </ul>
            </div>
          </section>

          <section className="sell-cta">
            <h2>Pronto a vendere?</h2>
            <p className="muted">La richiesta richiede cinque minuti. Ti serve la Partita IVA e l&apos;IBAN aziendale.</p>
            <Link className="btn" href="/seller">Invia la richiesta</Link>
          </section>
        </div>
        <footer className="sell-foot"><div className="wrap"><Link href="/" className="small">ilpacco.ru — il negozio per i clienti</Link><span className="small muted">partner@ilpacco.ru</span></div></footer>
      </main>
    </WorkShell>
  );
}
