"use client";

import { useState } from "react";
import type { Category, MediaItem } from "@/lib/types";
import { ProductArt } from "./ProductArt";

/** Галерея карточки: фото и видео продавца; если их нет — фирменная иллюстрация. */
export function Gallery({ id, title, category, media }: { id: number; title: string; category: Category; media: MediaItem[] }) {
  const [current, setCurrent] = useState(0);
  if (!media.length) {
    return (
      <div className="pdp-gallery single">
        <ProductArt id={id} title={title} category={category} size="hero" />
      </div>
    );
  }
  const m = media[Math.min(current, media.length - 1)];
  return (
    <div className={`pdp-gallery${media.length === 1 ? " single" : ""}`}>
      {media.length > 1 ? (
        <div className="thumbs" role="tablist" aria-label="Фото и видео товара">
          {media.map((x, i) => (
            <button key={x.id} type="button" role="tab" aria-selected={i === current} className="thumb"
              onClick={() => setCurrent(i)} aria-label={x.kind === "VIDEO" ? `Видео ${i + 1}` : `Фото ${i + 1}`}>
              {x.kind === "VIDEO" ? (
                <span className="thumb-video"><video src={`${x.url}#t=0.5`} preload="metadata" muted playsInline /><span aria-hidden="true">▶</span></span>
              ) : (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={x.url} alt="" loading="lazy" />
              )}
            </button>
          ))}
        </div>
      ) : null}
      <div className="stage">
        {m.kind === "VIDEO" ? (
          <video key={m.id} src={m.url} controls playsInline preload="metadata" aria-label={`${title}: видео`} />
        ) : (
          // eslint-disable-next-line @next/next/no-img-element
          <img key={m.id} src={m.url} alt={`${title}, фото ${current + 1} из ${media.length}`} />
        )}
        {media.length > 1 ? (
          <div className="stage-dots" aria-hidden="true">
            {media.map((x, i) => <span key={x.id} className={i === current ? "on" : ""} />)}
          </div>
        ) : null}
      </div>
    </div>
  );
}
