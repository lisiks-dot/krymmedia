"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { ApiError, api } from "@/lib/client";
import { fmtDay } from "@/lib/order";
import { optionalToken, session } from "@/lib/session";
import type { ProductReviews, Review, ReviewEligibility } from "@/lib/types";
import { Stars } from "./Stars";

const plural = (n: number) => {
  const m10 = n % 10, m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return "отзыв";
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "отзыва";
  return "отзывов";
};

/** Отзывы на карточке товара. Написать может только тот, кто получил заказ с этим товаром. */
export function Reviews({ productId }: { productId: number }) {
  const [data, setData] = useState<ProductReviews | null>(null);
  const [me, setMe] = useState<ReviewEligibility | null | undefined>(undefined);

  const load = useCallback(() => {
    api<ProductReviews>(`/api/products/${productId}/reviews`).then(setData).catch(() => setData({ summary: { count: 0, average: 0, distribution: [0, 0, 0, 0, 0] }, reviews: [] }));
    void (async () => {
      const token = await optionalToken();
      if (!token) return setMe(null);
      setMe(await api<ReviewEligibility>(`/api/products/${productId}/reviews/me`, { headers: { Authorization: `Bearer ${token}` } }).catch(() => null));
    })();
  }, [productId]);

  useEffect(load, [load]);

  const s = data?.summary;
  return (
    <section className="reviews" id="reviews" aria-labelledby="reviews-h">
      <div className="reviews-head">
        <h2 id="reviews-h">Отзывы</h2>
        {s && s.count ? <span className="muted">{s.count} {plural(s.count)} от покупателей, получивших заказ</span> : null}
      </div>
      <div className="reviews-grid">
        <aside className="reviews-side">
          {s && s.count ? (
            <div className="rating-box">
              <b className="num">{s.average.toFixed(1).replace(".", ",")}</b>
              <Stars value={s.average} size="lg" />
              <ul className="dist" aria-label="Распределение оценок">
                {[5, 4, 3, 2, 1].map((star) => {
                  const n = s.distribution[star - 1] ?? 0;
                  return (
                    <li key={star}>
                      <span className="num">{star}</span>
                      <span className="bar"><span style={{ width: `${s.count ? (n / s.count) * 100 : 0}%` }} /></span>
                      <span className="num muted">{n}</span>
                    </li>
                  );
                })}
              </ul>
            </div>
          ) : data ? (
            <p className="muted" style={{ margin: 0 }}>Отзывов пока нет. Первый оставит покупатель, который получит этот товар.</p>
          ) : <div className="skel" style={{ height: 120 }} />}
          <Write productId={productId} me={me} onDone={load} />
        </aside>
        <div className="reviews-list">
          {data?.reviews.map((r) => <ReviewItem key={r.id} r={r} />)}
        </div>
      </div>
    </section>
  );
}

export function ReviewItem({ r, children }: { r: Review; children?: ReactNode }) {
  return (
    <article className={`review${r.hidden ? " is-hidden" : ""}`}>
      <header>
        <Stars value={r.rating} />
        <b>{r.author}</b>
        <span className="small muted">{fmtDay(r.createdAt)}{r.size ? `, размер ${r.size}` : ""}</span>
        {r.verified ? <span className="badge ok">Покупка подтверждена</span> : null}
      </header>
      <p>{r.text}</p>
      {r.replies.map((x) => (
        <div key={x.id} className={`reply ${x.role === "ADMIN" ? "staff" : ""}`}>
          <b>{x.role === "ADMIN" ? "Ответ ilpacco" : `Ответ продавца, ${x.author}`}</b>
          <span className="small muted">{fmtDay(x.createdAt)}</span>
          <p>{x.text}</p>
        </div>
      ))}
      {children}
    </article>
  );
}

function Write({ productId, me, onDone }: { productId: number; me: ReviewEligibility | null | undefined; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (me === undefined) return null;
  if (me === null) {
    return (
      <p className="small muted" style={{ margin: 0 }}>
        Отзывы пишут покупатели, получившие этот товар.{" "}
        <button className="linklike" type="button" onClick={() => void session().then(({ kc }) => kc.login())}>Войдите</button>, если уже забрали заказ.
      </p>
    );
  }
  if (me.mine) return <div className="note ok small">Спасибо, ваш отзыв опубликован.</div>;
  if (!me.canReview) return <p className="small muted" style={{ margin: 0 }}>{me.reason}</p>;

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!rating) return setError("Поставьте оценку");
    setBusy(true);
    setError(null);
    try {
      const token = await optionalToken();
      await api(`/api/products/${productId}/reviews`, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: JSON.stringify({ rating, text: new FormData(e.currentTarget).get("text") }),
      });
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.messages.join("; ") : "Не удалось отправить отзыв");
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <div className="stack" style={{ gap: 6 }}>
        <button className="btn" type="button" onClick={() => setOpen(true)}>Написать отзыв</button>
        <span className="small muted">По заказу {me.orderNumber}</span>
      </div>
    );
  }
  return (
    <form className="review-form" onSubmit={submit}>
      <fieldset className="plain">
        <legend>Ваша оценка</legend>
        <div className="star-input">
          {[5, 4, 3, 2, 1].map((n) => (
            <label key={n}>
              <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} />
              <span aria-hidden="true">★</span>
              <span className="sr-only">{n} из 5</span>
            </label>
          ))}
        </div>
      </fieldset>
      <div className="field">
        <label htmlFor="review-text">Что понравилось, что нет, как с размером</label>
        <textarea id="review-text" name="text" rows={5} minLength={10} maxLength={2000} required />
      </div>
      <p className="hint">Отзыв увидят все покупатели. Имя покажем как «Имя Ф.». Продавец и ilpacco смогут ответить.</p>
      {error ? <div className="note bad" role="alert">{error}</div> : null}
      <div className="controls">
        <button className="btn" type="submit" disabled={busy}>{busy ? "Отправляем…" : "Опубликовать"}</button>
        <button className="btn ghost" type="button" onClick={() => setOpen(false)}>Отмена</button>
      </div>
      <Link className="small" href="/help#reviews">Правила отзывов</Link>
    </form>
  );
}
