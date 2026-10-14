export type Category = "CLOTHES" | "SHOES" | "BEAUTY" | "HOME" | "FOOD";
export type DeliveryMode = "GROUND" | "AIR";

export const CATEGORY_LABEL: Record<Category, string> = {
  CLOTHES: "Одежда",
  SHOES: "Обувь",
  BEAUTY: "Косметика",
  HOME: "Дом",
  FOOD: "Гастрономия",
};

export const MODE_LABEL: Record<DeliveryMode, { name: string; days: string }> = {
  GROUND: { name: "Наземная", days: "16–24 дня" },
  AIR: { name: "Экспресс-авиа", days: "10–18 дней" },
};

export interface Product {
  id: number;
  title: string;
  sellerName: string;
  region: string | null;
  category: Category;
  hsCode: string;
  priceEurCents: number;
  priceRub: number;
  weightGrams: number;
  sizes: string[];
  description: string | null;
  status: string;
  blockReason: string | null;
}

export interface Quote {
  mode: DeliveryMode;
  goodsEurCents: number;
  weightGrams: number;
  deliveryEurCents: number;
  dutyEurCents: number;
  vatEurCents: number;
  goodsRub: number;
  deliveryRub: number;
  dutyRub: number;
  vatRub: number;
  totalRub: number;
  dutyFree: boolean;
  foodOnly: boolean;
  removalHintProductId: number | null;
  deliveryRubByMode: Record<DeliveryMode, number>;
  rubPerEur: number;
}

export interface CartItem {
  productId: number;
  title: string;
  sellerName: string;
  category: Category;
  size: string | null;
  qty: number;
  unitPriceEurCents: number;
  unitPriceRub: number;
}

export interface CartView {
  id: string;
  items: CartItem[];
  quote: Quote | null;
}

export interface OrderView {
  number: string;
  status: string;
  statusLabel: string;
  statusStep: number;
  mode: DeliveryMode;
  buyerName: string;
  pickupPoint: string;
  items: { productId: number; title: string; sellerName: string; category: Category; size: string | null; qty: number; priceEurCents: number }[];
  goodsRub: number;
  deliveryRub: number;
  dutyRub: number;
  vatRub: number;
  totalRub: number;
  createdAt: string;
  updatedAt: string;
}

export interface Tariffs {
  rubPerEur: number;
  fxBuffer: number;
  dutyFreeEurCents: number;
  packagingGrams: number;
  ground: { perParcelEur: number; perKgEur: number };
  air: { perParcelEur: number; perKgEur: number };
}

export interface Problem {
  title?: string;
  detail?: string;
  errors?: string[];
}

export const ORDER_STEPS = [
  "Создан",
  "Оплачен",
  "Подтверждён продавцом",
  "Едет на хаб",
  "Принят на хабе",
  "Упакован в посылку",
  "Передан перевозчику",
  "На таможне",
  "Выпущен",
  "В ПВЗ или у курьера",
  "Получен",
  "Завершён",
];
