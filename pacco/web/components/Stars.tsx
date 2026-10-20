/** Оценка звёздами: только отображение. */
export function Stars({ value, size = "sm" }: { value: number; size?: "sm" | "lg" }) {
  const full = Math.round(value);
  return (
    <span className={`stars ${size}`} role="img" aria-label={`Оценка ${value.toFixed(1).replace(".", ",")} из 5`}>
      {[1, 2, 3, 4, 5].map((i) => <span key={i} className={i <= full ? "on" : ""} aria-hidden="true">★</span>)}
    </span>
  );
}
