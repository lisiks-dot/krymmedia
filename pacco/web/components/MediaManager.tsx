"use client";

import { useRef, useState, type DragEvent } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError, upload } from "@/lib/client";
import type { MediaItem } from "@/lib/types";

const ACCEPT = "image/jpeg,image/png,image/webp,video/mp4,video/webm";

/**
 * Foto e video del prodotto (area venditori): caricamento multiplo, ordine con le frecce o trascinando, eliminazione.
 * La prima immagine diventa la copertina nel catalogo.
 */
export function MediaManager({ productId, media, onChange }: { productId: number; media: MediaItem[]; onChange: (m: MediaItem[]) => void }) {
  const { call, bearer } = useAuth();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [over, setOver] = useState(false);
  const [drag, setDrag] = useState<number | null>(null);
  const base = `/api/seller/products/${productId}/media`;

  async function add(files: FileList | File[]) {
    const list = Array.from(files);
    if (!list.length) return;
    setError(null);
    const h = await bearer();
    let next = [...media];
    for (const [i, file] of list.entries()) {
      setBusy(`Caricamento ${i + 1} di ${list.length}: ${file.name}`);
      try {
        const form = new FormData();
        form.append("file", file);
        next = [...next, await upload<MediaItem>(base, form, h)];
        onChange(next);
      } catch (e) {
        setError(`${file.name}: ${e instanceof ApiError ? e.messages.join("; ") : "caricamento non riuscito"}`);
      }
    }
    setBusy(null);
    if (input.current) input.current.value = "";
  }

  async function remove(m: MediaItem) {
    setError(null);
    try {
      await call(`${base}/${m.id}`, { method: "DELETE" });
      onChange(media.filter((x) => x.id !== m.id));
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Eliminazione non riuscita");
    }
  }

  async function reorder(ids: number[]) {
    const byId = new Map(media.map((m) => [m.id, m]));
    onChange(ids.map((id) => byId.get(id)!).filter(Boolean));
    try {
      onChange(await call<MediaItem[]>(`${base}/order`, { method: "PUT", body: JSON.stringify({ ids }) }));
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Ordine non salvato");
      onChange(media);
    }
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= media.length || from === to) return;
    const ids = media.map((m) => m.id);
    const [x] = ids.splice(from, 1);
    ids.splice(to, 0, x);
    void reorder(ids);
  }

  function dropFiles(e: DragEvent) {
    e.preventDefault();
    setOver(false);
    if (e.dataTransfer.files.length) void add(e.dataTransfer.files);
  }

  const images = media.filter((m) => m.kind === "IMAGE").length;
  const videos = media.length - images;
  return (
    <div className="media-manager">
      <ol className="media-grid" aria-label="Foto e video, in ordine di visualizzazione">
        {media.map((m, i) => (
          <li key={m.id} draggable onDragStart={() => setDrag(i)} onDragEnd={() => setDrag(null)}
            onDragOver={(e) => drag !== null && e.preventDefault()}
            onDrop={(e) => { if (drag !== null) { e.preventDefault(); e.stopPropagation(); move(drag, i); setDrag(null); } }}
            className={drag === i ? "dragging" : ""}>
            {m.kind === "VIDEO" ? (
              <video src={`${m.url}#t=0.5`} preload="metadata" muted playsInline />
            ) : (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={m.url} alt="" />
            )}
            {i === 0 ? <span className="media-tag">Copertina</span> : m.kind === "VIDEO" ? <span className="media-tag">Video</span> : null}
            <div className="media-acts">
              <button type="button" onClick={() => move(i, i - 1)} disabled={i === 0} aria-label={`Sposta indietro ${i + 1}`}>←</button>
              <button type="button" onClick={() => move(i, i + 1)} disabled={i === media.length - 1} aria-label={`Sposta avanti ${i + 1}`}>→</button>
              <button type="button" className="del" onClick={() => void remove(m)} aria-label={`Elimina ${i + 1}`}>Elimina</button>
            </div>
          </li>
        ))}
        <li className={`media-drop${over ? " over" : ""}`}
          onDragOver={(e) => { if (drag === null) { e.preventDefault(); setOver(true); } }}
          onDragLeave={() => setOver(false)} onDrop={dropFiles}>
          <button type="button" onClick={() => input.current?.click()} disabled={busy !== null}>
            <b>+ Aggiungi</b>
            <span className="small muted">Trascina qui o scegli i file</span>
          </button>
          <input ref={input} type="file" accept={ACCEPT} multiple hidden onChange={(e) => e.target.files && void add(e.target.files)} />
        </li>
      </ol>
      <p className="small muted" style={{ margin: 0 }}>
        Foto JPEG, PNG o WebP fino a 10 MB ({images}/12), video MP4 o WebM fino a 60 MB ({videos}/2). Sfondo chiaro, prodotto intero, senza loghi di altri negozi. La prima foto è la copertina nel catalogo.
      </p>
      {busy ? <div className="note info" role="status">{busy}</div> : null}
      {error ? <div className="note bad" role="alert">{error}</div> : null}
    </div>
  );
}
