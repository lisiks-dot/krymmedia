"use client";

import { useEffect, useRef, useState } from "react";
import { blobUrl } from "@/lib/client";
import { fmtTime } from "@/lib/order";
import type { PhotoView } from "@/lib/types";

/**
 * Фото заказа: миниатюры и просмотр крупно. Картинки закрыты (ключ заказа или токен),
 * поэтому грузим их fetch-ем с заголовками и показываем как blob-URL.
 */
export function Photos({ photos, src, headers, caption }: {
  photos: PhotoView[];
  src: (id: number) => string;
  headers: () => Promise<Record<string, string>>;
  caption?: (p: PhotoView) => string;
}) {
  const [urls, setUrls] = useState<Record<number, string>>({});
  const [open, setOpen] = useState<PhotoView | null>(null);
  const dialog = useRef<HTMLDialogElement>(null);
  const ids = photos.map((p) => p.id).join(",");

  useEffect(() => {
    let alive = true;
    const made: string[] = [];
    void (async () => {
      const h = await headers();
      for (const p of photos) {
        try {
          const u = await blobUrl(src(p.id), h);
          made.push(u);
          if (alive) setUrls((prev) => ({ ...prev, [p.id]: u }));
        } catch {
          /* фото недоступно — оставляем плейсхолдер */
        }
      }
    })();
    return () => {
      alive = false;
      made.forEach((u) => URL.revokeObjectURL(u));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ids]);

  useEffect(() => {
    if (open) dialog.current?.showModal();
    else dialog.current?.close();
  }, [open]);

  if (!photos.length) return null;
  return (
    <>
      <div className="photos">
        {photos.map((p) => (
          <button key={p.id} type="button" className="photo" onClick={() => setOpen(p)} aria-label={`Открыть фото${caption ? `: ${caption(p)}` : ""}`}>
            {urls[p.id] ? <img src={urls[p.id]} alt="" /> : <span className="skel" />}
            {caption ? <span className="photo-cap">{caption(p)}</span> : null}
          </button>
        ))}
      </div>
      <dialog ref={dialog} className="lightbox" onClose={() => setOpen(null)} onClick={(e) => e.target === dialog.current && setOpen(null)}>
        {open ? (
          <figure>
            {urls[open.id] ? <img src={urls[open.id]} alt={caption ? caption(open) : "Фото заказа"} /> : null}
            <figcaption>{caption ? `${caption(open)} · ` : ""}{fmtTime(open.createdAt)}</figcaption>
            <button type="button" className="btn sm ghost" onClick={() => setOpen(null)}>Закрыть</button>
          </figure>
        ) : null}
      </dialog>
    </>
  );
}
