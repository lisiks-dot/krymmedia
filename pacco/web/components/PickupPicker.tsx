"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useMemo, useRef, useState } from "react";
import type { Map as LMap, CircleMarker } from "leaflet";
import { api } from "@/lib/client";
import type { PickupPoint } from "@/lib/types";

/**
 * Выбор пункта выдачи: город → карта и список, синхронно. Карта — Leaflet с тайлами OpenStreetMap
 * (для продакшена в России — Яндекс Карты или 2ГИС с ключом API). Без карты список работает сам по себе.
 */
export function PickupPicker({ value, onChange }: { value: number | null; onChange: (p: PickupPoint) => void }) {
  const [cities, setCities] = useState<string[]>([]);
  const [city, setCity] = useState<string | null>(null);
  const [points, setPoints] = useState<PickupPoint[]>([]);
  const [error, setError] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<LMap | null>(null);
  const markers = useRef<Map<number, CircleMarker>>(new Map());

  useEffect(() => {
    Promise.all([api<string[]>("/api/pickup-points/cities"), api<PickupPoint[]>("/api/pickup-points")])
      .then(([c, all]) => {
        setCities(c);
        const chosen = all.find((p) => p.id === value);
        const start = chosen?.city ?? c[0] ?? null;
        setCity(start);
        setPoints(all);
        const initial = chosen ?? all.find((p) => p.city === start) ?? all[0];
        if (initial) onChange(initial);
      })
      .catch(() => setError(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const inCity = useMemo(() => points.filter((p) => p.city === city), [points, city]);

  // карта: создаём один раз, маркеры — при смене города
  useEffect(() => {
    if (!box.current || !inCity.length) return;
    let cancelled = false;
    void import("leaflet").then((L) => {
      if (cancelled || !box.current) return;
      if (!map.current) {
        map.current = L.map(box.current, { scrollWheelZoom: false, attributionControl: true });
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          maxZoom: 18,
          attribution: "© участники OpenStreetMap",
        }).addTo(map.current);
      }
      markers.current.forEach((m) => m.remove());
      markers.current.clear();
      for (const p of inCity) {
        const m = L.circleMarker([p.lat, p.lng], { radius: 9, weight: 3, color: "#ffffff", fillColor: "#0b5fd6", fillOpacity: 1 })
          .addTo(map.current)
          .bindTooltip(`${p.carrier} · ${p.address}`, { direction: "top", offset: [0, -8] })
          .on("click", () => onChange(p));
        markers.current.set(p.id, m);
      }
      map.current.fitBounds(L.latLngBounds(inCity.map((p) => [p.lat, p.lng] as [number, number])).pad(0.25), { maxZoom: 14 });
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inCity]);

  // выделение выбранного пункта
  useEffect(() => {
    markers.current.forEach((m, id) => m.setStyle(id === value
      ? { radius: 12, fillColor: "#ff6b1a", color: "#1c1206" }
      : { radius: 9, fillColor: "#0b5fd6", color: "#ffffff" }));
    const chosen = points.find((p) => p.id === value);
    if (chosen && map.current && chosen.city === city) map.current.panTo([chosen.lat, chosen.lng]);
  }, [value, points, city]);

  useEffect(() => () => {
    map.current?.remove();
    map.current = null;
  }, []);

  if (error) return <div className="note bad">Не удалось загрузить пункты выдачи. Обновите страницу.</div>;

  return (
    <div className="picker">
      <div className="chips-row" role="radiogroup" aria-label="Город">
        {cities.map((c) => (
          <button key={c} type="button" className="chip" role="radio" aria-checked={c === city} aria-pressed={c === city}
            onClick={() => { setCity(c); const first = points.find((p) => p.city === c); if (first) onChange(first); }}>
            {c}
          </button>
        ))}
      </div>
      <div className="picker-body">
        <div ref={box} className="picker-map" aria-label="Карта пунктов выдачи" />
        <div className="options picker-list">
          {inCity.map((p) => (
            <label key={p.id} className="option pvz" htmlFor={`pvz-${p.id}`}>
              <input type="radio" id={`pvz-${p.id}`} name="pvz" value={p.id} checked={p.id === value} onChange={() => onChange(p)} />
              <span>
                <b>{p.carrier} · {p.address}</b>
                <span className="small muted">{p.hours}{p.note ? ` · ${p.note}` : ""}</span>
                {p.fitting ? <span className="badge ok">Есть примерочная</span> : null}
              </span>
            </label>
          ))}
        </div>
      </div>
    </div>
  );
}
