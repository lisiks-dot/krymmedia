"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "@/lib/auth";
import { ApiError, upload } from "@/lib/client";
import { eur } from "@/lib/format";
import { CATEGORY_LABEL, type Category } from "@/lib/types";

type Job = {
  id: number; source: string; kind: "PAGE" | "FEED"; url: string | null; maxItems: number; status: "RUNNING" | "DONE" | "FAILED";
  found: number; imported: number; failed: number; message: string | null; startedBy: string | null; createdAt: string; finishedAt: string | null;
};
type Item = {
  id: number; url: string; sku: string | null; brand: string | null; title: string; color: string | null;
  priceEurCents: number | null; suggestedPriceEurCents: number | null; sizes: string[]; available: boolean; imageUrl: string | null;
  status: "NEW" | "APPROVED" | "REJECTED"; productId: number | null; productStatus: string | null; rejectReason: string | null;
  hintCategory: Category; hintHs: string; hintWeight: number; hintKind: string; fetchedAt: string;
};

const TABS = [
  { key: "NEW", label: "На модерации" },
  { key: "APPROVED", label: "В каталоге" },
  { key: "REJECTED", label: "Отклонены" },
] as const;

export default function ImportPage() {
  const { call, bearer } = useAuth();
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [items, setItems] = useState<Item[] | null>(null);
  const [tab, setTab] = useState<(typeof TABS)[number]["key"]>("NEW");
  const [message, setMessage] = useState<{ tone: string; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const loadJobs = useCallback(() => call<Job[]>("/api/admin/import/jobs").then(setJobs).catch(() => setJobs([])), [call]);
  const loadItems = useCallback(() => {
    call<Item[]>(`/api/admin/import/items?status=${tab}`).then(setItems).catch(() => setItems([]));
  }, [call, tab]);
  useEffect(() => { void loadJobs(); }, [loadJobs]);
  useEffect(loadItems, [loadItems]);

  // пока идёт загрузка — обновляем прогресс
  const running = jobs?.some((j) => j.status === "RUNNING");
  useEffect(() => {
    if (!running) return;
    const t = window.setInterval(() => { void loadJobs(); loadItems(); }, 4000);
    return () => window.clearInterval(t);
  }, [running, loadJobs, loadItems]);

  async function start(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    setMessage(null);
    try {
      await call("/api/admin/import/jobs", { method: "POST", body: JSON.stringify({ url: f.get("url"), maxItems: Number(f.get("max")) || 48 }) });
      setMessage({ tone: "ok", text: "Загрузка началась. Пауза между запросами — 3 секунды, 48 товаров — около 3 минут." });
      void loadJobs();
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Не удалось начать загрузку" });
    } finally {
      setBusy(false);
    }
  }

  async function feed(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const file = (new FormData(e.currentTarget).get("file") as File | null);
    if (!file || !file.size) return;
    setBusy(true);
    setMessage(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const job = await upload<Job>("/api/admin/import/feed", form, await bearer());
      setMessage({ tone: "ok", text: `Фид загружен: товаров ${job.found}, добавлено ${job.imported}${job.failed ? `, с ошибками ${job.failed}` : ""}.` });
      void loadJobs();
      loadItems();
    } catch (err) {
      setMessage({ tone: "bad", text: err instanceof ApiError ? err.messages.join("; ") : "Не удалось загрузить фид" });
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>Импорт с Zalando</h1>
        <p className="muted">Товары площадки: при заказе ilpacco выкупает их у Zalando и проверяет на хабе. Сохраняем бренд, название, цвет, цену и размеры; фото и описания Zalando на витрину не попадают. Цены и наличие обновляются каждую ночь.</p>
      </div>
      {message ? <div className={`note ${message.tone}`} role="status">{message.text}</div> : null}

      <div className="import-sources">
        <form className="panel" onSubmit={start}>
          <h3>По адресу страницы</h3>
          <div className="field"><label htmlFor="imp-url">Каталог, бренд, поиск или карточка товара</label>
            <input id="imp-url" name="url" type="url" required placeholder="https://www.zalando.it/scarpe-uomo/" /></div>
          <div className="row-form">
            <label className="small" htmlFor="imp-max">Не больше</label>
            <input id="imp-max" name="max" className="inline-input" type="number" min={1} max={200} defaultValue={48} />
            <span className="small muted">товаров</span>
            <button className="btn sm" type="submit" disabled={busy}>Загрузить</button>
          </div>
          <p className="hint">Робот соблюдает robots.txt и не обходит защиту сайта. Если Zalando ограничит доступ, загрузка остановится — тогда используйте фид.</p>
        </form>
        <form className="panel" onSubmit={feed}>
          <h3>Фид партнёрской программы</h3>
          <div className="field"><label htmlFor="imp-feed">CSV из Zalando Partner Program (Awin) или аналогичный</label>
            <input id="imp-feed" name="file" type="file" accept=".csv,text/csv,text/plain" className="file-in" required /></div>
          <button className="btn sm ghost" type="submit" disabled={busy}>Загрузить фид</button>
          <p className="hint">Надёжный и согласованный с Zalando способ: в фиде цены, размеры и наличие, а условия использования данных прописаны в договоре.</p>
        </form>
      </div>

      {jobs && jobs.length ? (
        <section className="panel">
          <h3>Загрузки</h3>
          <div className="scroll">
            <table className="t">
              <thead><tr><th>Когда</th><th>Источник</th><th className="r">Найдено</th><th className="r">Добавлено</th><th className="r">Ошибки</th><th>Статус</th></tr></thead>
              <tbody>
                {jobs.slice(0, 8).map((j) => (
                  <tr key={j.id}>
                    <td className="num small">{new Date(j.createdAt).toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</td>
                    <td className="small">{j.kind === "FEED" ? "Фид CSV" : <a href={j.url ?? "#"} target="_blank" rel="noreferrer">{(j.url ?? "").replace("https://www.zalando.it", "")}</a>}<div className="muted">{j.startedBy}</div></td>
                    <td className="r num">{j.found}</td>
                    <td className="r num">{j.imported}</td>
                    <td className="r num">{j.failed}</td>
                    <td><span className={`pill ${j.status === "DONE" ? "ok" : j.status === "FAILED" ? "bad" : "warn"}`}>{j.status === "RUNNING" ? "Идёт" : j.status === "DONE" ? "Готово" : "Ошибка"}</span>{j.message ? <div className="small muted">{j.message}</div> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      ) : null}

      <div className="tabs" role="tablist">
        {TABS.map((t) => <button key={t.key} type="button" role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>{t.label}</button>)}
      </div>
      {items === null ? <div className="skel" style={{ height: 200 }} /> : items.length === 0 ? (
        <div className="empty">{tab === "NEW" ? "Новых товаров нет. Загрузите страницу каталога или фид." : "Пусто."}</div>
      ) : (
        <div className="stack">
          {items.map((i) => <ItemCard key={i.id} item={i} onDone={(text, tone = "ok") => { setMessage({ tone, text }); loadItems(); }} />)}
        </div>
      )}
    </>
  );
}

function ItemCard({ item, onDone }: { item: Item; onDone: (text: string, tone?: string) => void }) {
  const { call } = useAuth();
  const [error, setError] = useState<string | null>(null);

  async function post(path: string, body: unknown, ok: string) {
    setError(null);
    try {
      await call(`/api/admin/import/items/${item.id}/${path}`, { method: "POST", body: JSON.stringify(body ?? {}) });
      onDone(ok);
    } catch (e) {
      setError(e instanceof ApiError ? e.messages.join("; ") : "Не получилось");
    }
  }

  function approve(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    void post("approve", {
      title: f.get("title"),
      category: f.get("category"),
      hsCode: f.get("hs"),
      weightGrams: Number(f.get("weight")) || null,
      markupPercent: Number(f.get("markup")),
      sizes: String(f.get("sizes") ?? "").split(",").map((s) => s.trim()).filter(Boolean),
    }, `${item.brand ?? ""} ${item.title}: опубликован в каталоге`);
  }

  return (
    <article className="qcard import-item">
      <div className="import-main">
        {item.imageUrl ? <img src={item.imageUrl} alt="" referrerPolicy="no-referrer" loading="lazy" className="import-thumb" /> : <span className="import-thumb" />}
        <div className="stack tight">
          <header>
            <b>{item.brand ?? "Без бренда"}</b>
            <span>{item.title}</span>
            {item.color ? <span className="small muted">{item.color}</span> : null}
          </header>
          <span className="small">
            {item.priceEurCents !== null ? <>Zalando: <b className="num">{eur(item.priceEurCents)}</b></> : "Цена не найдена"}
            {item.suggestedPriceEurCents !== null ? <> · у нас: <b className="num">{eur(item.suggestedPriceEurCents)}</b></> : null}
            {" · "}{item.available ? (item.sizes.length ? `размеры: ${item.sizes.join(", ")}` : "в наличии") : <span className="bad-text">нет в наличии</span>}
          </span>
          <span className="small muted"><a href={item.url} target="_blank" rel="noreferrer">Открыть на Zalando</a>{item.sku ? ` · ${item.sku}` : ""} · обновлено {new Date(item.fetchedAt).toLocaleString("ru-RU", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })}</span>
          {item.status === "APPROVED" ? <span className={`pill ${item.productStatus === "PUBLISHED" ? "ok" : "warn"}`}>{item.productStatus === "PUBLISHED" ? `В каталоге, товар №${item.productId}` : `Снят с продажи (№${item.productId})`}</span> : null}
          {item.status === "REJECTED" ? <span className="small muted">Отклонён: {item.rejectReason}</span> : null}
        </div>
      </div>

      {item.status === "NEW" ? (
        <form className="ops-form import-form" onSubmit={approve}>
          <div className="field"><label htmlFor={`t-${item.id}`}>Название на витрине</label><input id={`t-${item.id}`} name="title" defaultValue={item.title} /></div>
          <div className="field"><label htmlFor={`c-${item.id}`}>Категория</label>
            <select id={`c-${item.id}`} name="category" defaultValue={item.hintCategory}>
              {(Object.keys(CATEGORY_LABEL) as Category[]).map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
            </select></div>
          <div className="field"><label htmlFor={`h-${item.id}`}>ТН ВЭД, 8 цифр <span className="muted">— подсказка: {item.hintHs} ({item.hintKind})</span></label>
            <input id={`h-${item.id}`} name="hs" inputMode="numeric" required pattern="[0-9 ]{8,11}" placeholder={`${item.hintHs} …`} /></div>
          <div className="field"><label htmlFor={`w-${item.id}`}>Вес, г</label><input id={`w-${item.id}`} name="weight" type="number" min={50} defaultValue={item.hintWeight} /></div>
          <div className="field"><label htmlFor={`m-${item.id}`}>Наценка, %</label><input id={`m-${item.id}`} name="markup" type="number" min={0} max={100} step={1} defaultValue={10} /></div>
          <div className="field"><label htmlFor={`s-${item.id}`}>Размеры</label><input id={`s-${item.id}`} name="sizes" defaultValue={item.sizes.join(", ")} placeholder="40, 41, 42" /></div>
          {error ? <div className="note bad" role="alert">{error}</div> : null}
          <div className="acts">
            <button className="btn sm" type="submit" disabled={!item.available}>Опубликовать</button>
            <button className="btn sm ghost" type="button" onClick={() => post("reject", { reason: "Не подходит для ilpacco" }, `${item.title}: отклонён`)}>Отклонить</button>
          </div>
        </form>
      ) : (
        <div className="acts">
          {item.status === "REJECTED" ? <button className="btn sm ghost" type="button" onClick={() => post("reopen", null, `${item.title}: снова на модерации`)}>Вернуть на модерацию</button> : null}
          {item.status === "APPROVED" ? <button className="btn sm ghost" type="button" onClick={() => post("refresh", null, `${item.title}: цена и наличие обновлены`)}>Обновить с Zalando</button> : null}
          {error ? <span className="small bad-text">{error}</span> : null}
        </div>
      )}
    </article>
  );
}
