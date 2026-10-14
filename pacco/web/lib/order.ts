import type { DisputeReason, LineState, OrderStatus, OrderView } from "./types";

/**
 * 12 статусов заказа покупателю показываем шестью понятными этапами,
 * а подробности — в истории событий.
 */
export const STAGES: { key: string; label: string; statuses: OrderStatus[] }[] = [
  { key: "paid", label: "Оплачен", statuses: ["CREATED", "PAID"] },
  { key: "seller", label: "У продавца", statuses: ["CONFIRMED", "TO_HUB"] },
  { key: "hub", label: "Хаб в Милане", statuses: ["AT_HUB", "CONSOLIDATED"] },
  { key: "road", label: "В пути и на таможне", statuses: ["HANDED_TO_CARRIER", "IN_CUSTOMS", "RELEASED"] },
  { key: "pickup", label: "В пункте выдачи", statuses: ["AT_PICKUP_POINT"] },
  { key: "done", label: "Получен", statuses: ["RECEIVED", "COMPLETED"] },
];

export function stageIndex(status: OrderStatus): number {
  return STAGES.findIndex((s) => s.statuses.includes(status));
}

/** Что происходит сейчас и чего ждать — по статусу. */
export const NEXT_STEP: Record<OrderStatus, string> = {
  CREATED: "Заказ ждёт оплаты. Пока он не оплачен, товар не резервируем.",
  PAID: "Продавец подтвердит наличие в течение 24 часов. Если товара не окажется, деньги за него вернутся сразу.",
  CONFIRMED: "Продавец упаковывает товар и отправит его на наш хаб в Милане за 3 рабочих дня.",
  TO_HUB: "Товар едет на хаб в Милане. Там мы сверим его с заказом и сфотографируем.",
  AT_HUB: "Товар на хабе: проверили и собираем посылку.",
  CONSOLIDATED: "Посылка собрана и ждёт передачи перевозчику.",
  HANDED_TO_CARRIER: "Посылка у перевозчика и едет в Россию.",
  IN_CUSTOMS: "Посылка на таможне. Декларацию подаёт наш таможенный представитель — от вас ничего не нужно.",
  RELEASED: "Таможня выпустила посылку, она едет в пункт выдачи.",
  AT_PICKUP_POINT: "Посылка ждёт вас. Назовите код получения в пункте выдачи.",
  RECEIVED: "Посылка у вас. Если с товаром что-то не так, откройте спор — вернём деньги без пересылки товара обратно.",
  COMPLETED: "Заказ завершён. Спасибо, что выбрали ilpacco.",
  CANCELLED: "Заказ отменён.",
};

export const LINE_TONE: Record<LineState, "ok" | "warn" | "bad" | "neutral"> = {
  PENDING: "warn",
  CONFIRMED: "neutral",
  SHIPPED: "neutral",
  ACCEPTED: "ok",
  DECLINED: "bad",
  REJECTED_AT_HUB: "bad",
  RETURNED: "bad",
};

export const INACTIVE: LineState[] = ["DECLINED", "REJECTED_AT_HUB", "RETURNED"];

export const DISPUTE_REASONS: { value: DisputeReason; label: string }[] = [
  { value: "DAMAGED", label: "Повреждён" },
  { value: "NOT_AS_DESCRIBED", label: "Не соответствует описанию" },
  { value: "WRONG_ITEM", label: "Пришёл другой товар" },
  { value: "MISSING", label: "Товара нет в посылке" },
  { value: "SIZE", label: "Не подошёл размер" },
];

export function statusTone(status: OrderStatus): "ok" | "warn" | "bad" | "neutral" | "brand" {
  if (status === "CANCELLED") return "bad";
  if (status === "COMPLETED" || status === "RECEIVED") return "ok";
  if (status === "AT_PICKUP_POINT") return "brand";
  if (status === "CREATED") return "warn";
  return "neutral";
}

/** Ориентир доставки: от даты оплаты, по сроку способа доставки. */
export function eta(o: OrderView): string | null {
  if (["CREATED", "RECEIVED", "COMPLETED", "CANCELLED", "AT_PICKUP_POINT"].includes(o.status)) return null;
  const paid = o.events.find((e) => e.status === "PAID")?.at ?? o.createdAt;
  const [from, to] = o.mode === "AIR" ? [10, 18] : [16, 24];
  const d = (days: number) => new Date(new Date(paid).getTime() + days * 86_400_000)
    .toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
  return `${d(from)} — ${d(to)}`;
}

export const fmtDay = (iso: string) => new Date(iso).toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
export const fmtTime = (iso: string, locale = "ru-RU") =>
  new Date(iso).toLocaleString(locale, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

export const ACTOR_LABEL: Record<string, string> = {
  buyer: "Вы",
  seller: "Продавец",
  hub: "Хаб в Милане",
  carrier: "Перевозчик",
  customs: "Таможня",
  pickup: "Пункт выдачи",
  support: "Поддержка",
  system: "ilpacco",
};
