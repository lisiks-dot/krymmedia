import type { Category, OrderView, Product } from "./types";

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

export interface SellerOrder {
  number: string;
  status: string;
  statusLabel: string;
  createdAt: string;
  lines: { productId: number; title: string; size: string | null; qty: number; priceEurCents: number; confirmed: boolean }[];
  confirmedBySeller: boolean;
  goodsEurCents: number;
}

export interface Payouts {
  payableGrossEurCents: number;
  payableCommissionEurCents: number;
  payableNetEurCents: number;
  pendingGrossEurCents: number;
  pendingCommissionEurCents: number;
  pendingNetEurCents: number;
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
