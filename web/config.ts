import type { Category, MaterialKey, Region, ResultLevel } from "./types";

export const API_BASE = "https://api.arsha.io/v2";
export const CACHE_TTL_MS = 30 * 60 * 1_000;
export const CACHE_MAX_STALE_MS = 7 * 24 * 60 * 60 * 1_000;

export const CATEGORY_ENDPOINTS = [
  { main: 20, sub: 1, category: "accessory" as const },
  { main: 20, sub: 2, category: "accessory" as const },
  { main: 20, sub: 3, category: "accessory" as const },
  { main: 20, sub: 4, category: "accessory" as const },
  { main: 15, sub: 5, category: "functional" as const },
] as const;

// Stable IDs for the catalog group that the public API intermittently fails to enumerate.
// Names remain English because every market request uses the English catalog.
export const CATALOG_SEEDS: ReadonlyArray<{ id: number; name: string; category: Category }> = [
  { id: 12031, name: "Ring of Crescent Guardian", category: "accessory" },
  { id: 12061, name: "Tungrad Ring", category: "accessory" },
  { id: 12229, name: "Centaurus Belt", category: "accessory" },
  { id: 11607, name: "Ogre Ring", category: "accessory" },
];

export const MATERIALS: ReadonlyArray<{ id: number; key: MaterialKey; label: string }> = [
  { id: 16001, key: "blackStone", label: "Black Stone" },
  { id: 8411, key: "crystallizedDespair", label: "Crystallized Despair" },
  { id: 820934, key: "primordialBlackStone", label: "Primordial Black Stone" },
  { id: 5000, key: "blackGem", label: "Black Gem" },
  { id: 4987, key: "concentratedBlackGem", label: "Concentrated Magical Black Gem" },
  { id: 44195, key: "memoryFragment", label: "Memory Fragment" },
] as const;

export const DEFAULT_MATERIAL_PRICES: Record<MaterialKey, number> = {
  blackStone: 141_000,
  crystallizedDespair: 49_200_000,
  primordialBlackStone: 59_000_000,
  blackGem: 463_000,
  concentratedBlackGem: 14_500_000,
  memoryFragment: 2_570_000,
};

export const TARGET_LEVELS: ResultLevel[] = [2, 3, 4];

export const LEVEL_LABELS: Record<Category, Record<ResultLevel, string>> = {
  accessory: { 2: "DUO", 3: "TRI", 4: "TET" },
  silver: { 2: "+2", 3: "+3", 4: "+4" },
  manos: { 2: "DUO", 3: "TRI", 4: "TET" },
};

export const DEFAULT_STACKS: Record<number, number> = {
  0: 30,
  1: 40,
  2: 45,
  3: 110,
};

export const STACK_OPTIONS = [10, 15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100, 110];

export function marketSid(category: Category, resultLevel: number): number {
  if (category === "manos" && resultLevel > 0) return resultLevel + 15;
  return resultLevel;
}

export function classifyItem(
  name: string,
  sourceCategory: "accessory" | "functional",
): Category | null {
  if (sourceCategory === "accessory") {
    if (/manos|preonne|geranoa|loggia/i.test(name)) return null;
    if (/diamond necklace of fortitude|emerald necklace of tranquility|topaz necklace of regeneration|sapphire necklace of storms|corrupt ruby necklace/i.test(name)) {
      return null;
    }
    return "accessory";
  }

  if (/^silver embroidered /i.test(name)) return "silver";
  if (/^manos .+clothes$/i.test(name)) return "manos";
  return null;
}

export function cacheKey(region: Region): string {
  return `bdo-enhancer-market-v2:${region}`;
}
