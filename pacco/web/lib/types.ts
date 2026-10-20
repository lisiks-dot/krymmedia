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
  media?: MediaItem[];
}

export interface MediaItem {
  id: number;
  kind: "IMAGE" | "VIDEO";
  contentType: string;
  url: string;
}

export interface ReviewReply {
  id: number;
  role: "SELLER" | "ADMIN";
  author: string;
  text: string;
  createdAt: string;
}

export interface Review {
  id: number;
  productId: number;
  productTitle: string;
  sellerId: number | null;
  author: string;
  size: string | null;
  rating: number;
  text: string;
  verified: boolean;
  hidden: boolean;
  hiddenReason: string | null;
  orderNumber: string | null;
  createdAt: string;
  replies: ReviewReply[];
}

export interface ProductReviews {
  summary: { count: number; average: number; distribution: number[] };
  reviews: Review[];
}

export interface ReviewEligibility {
  canReview: boolean;
  reason: string | null;
  orderNumber: string | null;
  mine: Review | null;
}

export interface Conversation {
  id: number;
  buyerName: string;
  sellerId: number | null;
  sellerName: string;
  productId: number | null;
  productTitle: string | null;
  orderNumber: string | null;
  lastMessageAt: string;
  lastMessage: string | null;
  unread: number;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  role: "BUYER" | "SELLER" | "ADMIN";
  author: string;
  text: string;
  masked: boolean;
  createdAt: string;
}

export interface ChatThread {
  conversation: Conversation;
  messages: ChatMessage[];
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

export interface PhotoView {
  id: number;
  lineNo: number | null;
  kind: "HUB" | "DISPUTE";
  createdAt: string;
}

export interface PickupPoint {
  id: number;
  carrier: string;
  city: string;
  address: string;
  lat: number;
  lng: number;
  hours: string;
  note: string | null;
  fitting: boolean;
}

export interface BuyerProfile {
  name: string | null;
  phone: string | null;
  email: string | null;
  pickupPointId: number | null;
  notifySms: boolean;
  notifyEmail: boolean;
}

export interface NotificationView {
  id: number;
  orderId: number | null;
  channel: "SMS" | "EMAIL";
  recipient: string;
  subject: string | null;
  body: string;
  status: "SENT" | "LOGGED" | "FAILED";
  provider: string;
  error: string | null;
  createdAt: string;
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
  photos: PhotoView[];
  buyerEmail: string | null;
  pickupPointId: number | null;
  linkedToAccount: boolean;
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
