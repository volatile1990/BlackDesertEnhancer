import marketConfig from "../shared/manos-market.json";
import type { MaterialKey, Region, ResultLevel } from "./types";

export const API_BASE = "https://api.arsha.io/v2";
export const CACHE_TTL_MS = 10 * 60 * 1_000;
export const CACHE_MAX_STALE_MS = 24 * 60 * 60 * 1_000;

export const MANOS_ITEMS: ReadonlyArray<{ id: number; name: string }> = marketConfig.items;

export const MATERIALS = marketConfig.materials as ReadonlyArray<{
  id: number;
  key: MaterialKey;
  label: string;
  defaultPrice: number;
}>;

export const DEFAULT_MATERIAL_PRICES = Object.fromEntries(
  MATERIALS.map(({ key, defaultPrice }) => [key, defaultPrice]),
) as Record<MaterialKey, number>;

export const TARGET_LEVELS: ResultLevel[] = [2, 3, 4];

export const LEVEL_LABELS: Record<ResultLevel, string> = {
  2: "DUO",
  3: "TRI",
  4: "TET",
};

export function marketSid(resultLevel: number): number {
  return resultLevel > 0 ? resultLevel + 15 : 0;
}

export function cacheKey(region: Region): string {
  return `bdo-enhancer-manos-market-v3:${region}`;
}
