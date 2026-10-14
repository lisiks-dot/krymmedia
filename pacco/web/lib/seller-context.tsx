"use client";

import { createContext, useContext } from "react";
import type { Seller } from "./cabinet";

export const SellerContext = createContext<Seller | null>(null);

export function useSeller(): Seller {
  const s = useContext(SellerContext);
  if (!s) throw new Error("useSeller fuori dal cabinet");
  return s;
}
