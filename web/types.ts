export type Region = "eu" | "na";
export type PriceState = "fresh" | "snapshot" | "cached" | "unlisted" | "error";
export type PriceKind = "listing" | "preorder" | "unavailable";
export type ResultLevel = 2 | 3 | 4;

export interface MarketQuote {
  price: number | null;
  sellersAtLowest: number;
  totalSellers: number;
  buyersAtPrice: number;
  totalBuyers: number;
  kind: PriceKind;
  state: PriceState;
  fetchedAt: string;
  source: string;
}

export interface MarketItem {
  id: number;
  name: string;
  levels: Record<string, MarketQuote>;
}

export type MaterialKey = "blackGem" | "concentratedBlackGem" | "memoryFragment";

export interface MaterialQuote extends MarketQuote {
  id: number;
  key: MaterialKey;
  label: string;
}

export interface MarketSnapshot {
  schemaVersion: 3;
  region: Region;
  fetchedAt: string;
  source: string;
  items: MarketItem[];
  materials: Record<MaterialKey, MaterialQuote>;
}

export interface CalculationSettings {
  taxRate: number;
  materialPrices: Record<MaterialKey, number>;
}

export interface CalculationResult {
  level: ResultLevel;
  label: string;
  status: "ok" | "unavailable";
  avgCost: number | null;
  expectedItems: number | null;
  salePrice: number | null;
  profit: number | null;
  margin: number | null;
  unavailableReason?: "base" | "target" | "material" | "tax";
}

export interface ItemAnalysis {
  item: MarketItem;
  results: CalculationResult[];
  bestProfit: number | null;
}
