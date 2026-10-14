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

export type OrderStatus =
  | "CREATED" | "PAID" | "CONFIRMED" | "TO_HUB" | "AT_HUB" | "CONSOLIDATED" | "HANDED_TO_CARRIER"
  | "IN_CUSTOMS" | "RELEASED" | "AT_PICKUP_POINT" | "RECEIVED" | "COMPLETED" | "CANCELLED";

export type LineState = "PENDING" | "CONFIRMED" | "SHIPPED" | "ACCEPTED" | "DECLINED" | "REJECTED_AT_HUB" | "RETURNED";
export type DisputeReason = "NOT_AS_DESCRIBED" | "DAMAGED" | "WRONG_ITEM" | "MISSING" | "SIZE";
export type DisputeStatus = "OPEN" | "REFUNDED" | "REJECTED";
export type PaymentMethod = "MIR" | "SBP";

export interface OrderLine {
  lineNo: number;
  productId: number;
  title: string;
  sellerName: string;
  sellerId: number | null;
  category: Category;
  size: string | null;
  qty: number;
  priceEurCents: number;
  lineRub: number;
  state: LineState;
  stateLabel: string;
  sellerTracking: string | null;
  note: string | null;
}

export interface OrderEvent {
  status: OrderStatus;
  title: string;
  note: string | null;
  actor: string;
  at: string;
}

export interface DisputeView {
  id: number;
  lines: number[];
  reason: DisputeReason;
  reasonLabel: string;
  comment: string | null;
  status: DisputeStatus;
  statusLabel: string;
  refundRub: number;
  resolution: string | null;
  createdAt: string;
  resolvedAt: string | null;
}

export interface OrderView {
  number: string;
  status: OrderStatus;
  statusLabel: string;
  statusStep: number;
  mode: DeliveryMode;
  buyerName: string;
  buyerPhone: string | null;
  pickupPoint: string;
  items: OrderLine[];
  goodsRub: number;
  deliveryRub: number;
  dutyRub: number;
  vatRub: number;
  totalRub: number;
  refundRub: number;
  paymentMethod: PaymentMethod | null;
  cancelReason: string | null;
  parcelWeightG: number | null;
  carrierTracking: string | null;
  pickupCode: string | null;
  storageUntil: string | null;
  receivedAt: string | null;
  disputeUntil: string | null;
  canPay: boolean;
  canCancel: boolean;
  canDispute: boolean;
  accessKey: string | null;
  events: OrderEvent[];
  dispute: DisputeView | null;
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
