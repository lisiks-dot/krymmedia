const nf = new Intl.NumberFormat("ru-RU");

export const rub = (n: number) => `${nf.format(n)} ₽`;

export const eur = (cents: number) =>
  `${(cents / 100).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} €`;

export const kg = (grams: number) => `${(grams / 1000).toLocaleString("ru-RU", { maximumFractionDigits: 1 })} кг`;

/** Цена в рублях так же, как на бэкенде: курс × (1 + буфер), вверх до 10 ₽. */
export const toRub = (eurAmount: number, rate: number, buffer: number) =>
  Math.ceil((eurAmount * rate * (1 + buffer)) / 10 - 1e-9) * 10;
