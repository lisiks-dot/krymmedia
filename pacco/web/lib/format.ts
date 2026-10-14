const nf = new Intl.NumberFormat("ru-RU");

export const rub = (n: number) => `${nf.format(n)} ₽`;

export const eur = (cents: number) =>
  `${(cents / 100).toLocaleString("ru-RU", { maximumFractionDigits: 2 })} €`;

export const kg = (grams: number) => `${(grams / 1000).toLocaleString("ru-RU", { maximumFractionDigits: 1 })} кг`;

/** Цена в рублях так же, как на бэкенде: курс × (1 + буфер), вверх до 10 ₽. */
export const toRub = (eurAmount: number, rate: number, buffer: number) =>
  Math.ceil((eurAmount * rate * (1 + buffer)) / 10 - 1e-9) * 10;

/** 1 товар, 2 товара, 5 товаров. */
export const goods = (n: number) =>
  `${n} ${n % 10 === 1 && n % 100 !== 11 ? "товар" : [2, 3, 4].includes(n % 10) && ![12, 13, 14].includes(n % 100) ? "товара" : "товаров"}`;
