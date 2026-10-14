import type { Category, LineState, OrderStatus, OrderView, Product } from "./types";

export type SellerStatus = "PENDING" | "ACTIVE" | "REJECTED";

export interface Seller {
  id: number;
  legalName: string;
  partitaIva: string;
  iban: string;
  region: string | null;
  contactEmail: string;
  status: SellerStatus;
  rejectReason: string | null;
  createdAt: string;
}

export interface Rules {
  thresholdEurCents: number;
  thresholdCategories: Category[];
  embargoPrefixes: string[];
  alcoholPrefixes: string[];
  updatedAt: string;
}

export interface Violation {
  code: string;
  message: string;
}

export interface SellerOrderLine {
  lineNo: number;
  productId: number;
  title: string;
  size: string | null;
  qty: number;
  priceEurCents: number;
  confirmed: boolean;
  state: LineState;
  tracking: string | null;
  note: string | null;
}

export interface SellerOrder {
  number: string;
  status: OrderStatus;
  statusLabel: string;
  createdAt: string;
  lines: SellerOrderLine[];
  confirmedBySeller: boolean;
  goodsEurCents: number;
  needsAnswer: boolean;
  needsShipping: boolean;
  confirmBy: string;
}

export interface Payouts {
  payableGrossEurCents: number;
  payableCommissionEurCents: number;
  payableNetEurCents: number;
  pendingGrossEurCents: number;
  pendingCommissionEurCents: number;
  pendingNetEurCents: number;
  completedLines: number;
}

export type { OrderView, Product };

export const PRODUCT_STATUS_RU: Record<string, { label: string; tone: string }> = {
  PENDING: { label: "На модерации", tone: "warn" },
  PUBLISHED: { label: "В продаже", tone: "ok" },
  BLOCKED: { label: "Снят правилами", tone: "bad" },
  REJECTED: { label: "Отклонён", tone: "bad" },
};

export const PRODUCT_STATUS_IT: Record<string, { label: string; tone: string }> = {
  PENDING: { label: "In revisione", tone: "warn" },
  PUBLISHED: { label: "Pubblicato", tone: "ok" },
  BLOCKED: { label: "Sospeso dalle regole", tone: "bad" },
  REJECTED: { label: "Respinto", tone: "bad" },
};

export const SELLER_STATUS_RU: Record<SellerStatus, { label: string; tone: string }> = {
  PENDING: { label: "Ждёт проверки", tone: "warn" },
  ACTIVE: { label: "Активен", tone: "ok" },
  REJECTED: { label: "Отклонён", tone: "bad" },
};

export const fmtDate = (iso: string, locale = "ru-RU") =>
  new Date(iso).toLocaleString(locale, { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });

export interface PayoutSummary {
  id: number;
  number: string;
  sellerId: number;
  sellerName: string;
  grossEurCents: number;
  commissionEurCents: number;
  netEurCents: number;
  linesCount: number;
  status: "READY" | "PAID";
  bankRef: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface PayoutLine {
  orderNumber: string;
  lineNo: number;
  title: string;
  size: string | null;
  qty: number;
  category: Category;
  grossEurCents: number;
  rate: number;
  commissionEurCents: number;
  completedAt: string;
}

export interface PayoutDetail {
  payout: PayoutSummary;
  seller: Seller;
  lines: PayoutLine[];
}
