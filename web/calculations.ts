import { LEVEL_LABELS, STACK_OPTIONS, TARGET_LEVELS } from "./config";
import type {
  CalculationResult,
  CalculationSettings,
  Category,
  ItemAnalysis,
  MarketItem,
  ResultLevel,
} from "./types";

interface Expectation {
  cost: number;
  items: number;
}

interface ChanceProfile {
  base: number;
  beforeSoftcap: number;
  softcap: number;
  afterSoftcap: number;
}

const ACCESSORY_PROFILES: readonly ChanceProfile[] = [
  { base: 25, beforeSoftcap: 2.5, softcap: 18, afterSoftcap: 0.5 },
  { base: 10, beforeSoftcap: 1, softcap: 40, afterSoftcap: 0.2 },
  { base: 7.5, beforeSoftcap: 0.75, softcap: 44, afterSoftcap: 0.15 },
  { base: 2.5, beforeSoftcap: 0.25, softcap: 110, afterSoftcap: 0.05 },
];

const SILVER_PRI_PROFILE: ChanceProfile = {
  base: 30,
  beforeSoftcap: 3,
  softcap: 14,
  afterSoftcap: 0.6,
};

const ACCESSORY_PITY = [5, 6, 8, 10] as const;
const STACK_ACQUISITION: Readonly<Record<number, { material: "blackStone" | "crystallizedDespair" | "primordialBlackStone"; count: number }>> = {
  10: { material: "blackStone", count: 12 },
  15: { material: "blackStone", count: 21 },
  20: { material: "blackStone", count: 33 },
  25: { material: "blackStone", count: 53 },
  30: { material: "blackStone", count: 84 },
  35: { material: "blackStone", count: 136 },
  40: { material: "blackStone", count: 230 },
  45: { material: "blackStone", count: 406 },
  50: { material: "crystallizedDespair", count: 4 },
  60: { material: "crystallizedDespair", count: 8 },
  70: { material: "crystallizedDespair", count: 15 },
  80: { material: "crystallizedDespair", count: 25 },
  90: { material: "crystallizedDespair", count: 35 },
  100: { material: "crystallizedDespair", count: 50 },
  110: { material: "primordialBlackStone", count: 25 },
};

// Current fixed-rate data for Manos life-skill clothes (+1 through PEN).
const MANOS_CHANCES = [
  100, 100, 100, 100, 100,
  100, 100, 70, 60, 50,
  40, 30, 20, 15, 10,
  30, 25, 20, 15, 6,
] as const;

const MANOS_PITY: Readonly<Record<number, number>> = {
  7: 3,
  8: 4,
  9: 4,
  10: 5,
  11: 7,
  12: 10,
  13: 14,
  14: 20,
  15: 7,
  16: 8,
  17: 10,
  18: 15,
  19: 35,
};

const MANOS_BLACK_GEMS = [1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5] as const;

export function successChance(category: "accessory" | "silver", stage: number, failstack: number): number {
  const profile = category === "silver" && stage === 0 ? SILVER_PRI_PROFILE : ACCESSORY_PROFILES[stage];
  if (!profile) throw new Error(`Nicht unterstützte Enhancement-Stufe ${stage}`);
  const stack = Math.max(0, failstack);
  const before = Math.min(stack, profile.softcap);
  const after = Math.max(0, stack - profile.softcap);
  return Math.min(90, profile.base + before * profile.beforeSoftcap + after * profile.afterSoftcap);
}

export function manosSuccessChance(currentLevel: number): number {
  const chance = MANOS_CHANCES[currentLevel];
  if (chance === undefined) throw new Error(`Nicht unterstützte Manos-Stufe ${currentLevel}`);
  return chance;
}

export function stackAcquisitionCost(stack: number, settings: CalculationSettings): number {
  if (settings.stackCostMode === "owned") return 0;
  const recipe = STACK_ACQUISITION[stack];
  if (!recipe) throw new Error(`Unbekannter Failstack ${stack}`);
  return recipe.count * settings.materialPrices[recipe.material];
}

function accessoryStageExpectation(
  category: "accessory" | "silver",
  stage: number,
  stack: number,
  basePrice: number,
  settings: CalculationSettings,
  rebuild: Expectation,
): Expectation {
  const threshold = ACCESSORY_PITY[stage];
  if (threshold === undefined) throw new Error(`Nicht unterstützte Accessoire-Stufe ${stage}`);
  const materialCost = stage === 0 ? basePrice * 2 : basePrice;
  const materialItems = stage === 0 ? 2 : 1;
  const stackCost = stackAcquisitionCost(stack, settings);

  let next: Expectation = { cost: materialCost, items: materialItems };
  for (let failures = threshold - 1; failures >= 0; failures -= 1) {
    const probability = successChance(category, stage, stack + failures) / 100;
    next = {
      cost: materialCost + probability * stackCost + (1 - probability) * (rebuild.cost + next.cost),
      items: materialItems + (1 - probability) * (rebuild.items + next.items),
    };
  }
  return next;
}

export function expectedAccessoryCost(
  category: "accessory" | "silver",
  targetLevel: ResultLevel,
  basePrice: number,
  settings: CalculationSettings,
): Expectation {
  let total: Expectation = { cost: 0, items: 0 };
  for (let stage = 0; stage < targetLevel; stage += 1) {
    const stack = settings.stacks[stage];
    if (stack === undefined) throw new Error(`Failstack für Stufe ${stage} fehlt`);
    const increment = accessoryStageExpectation(
      category,
      stage,
      stack,
      basePrice,
      settings,
      total,
    );
    total = { cost: total.cost + increment.cost, items: total.items + increment.items };
  }
  return total;
}

function manosAttemptCost(currentLevel: number, settings: CalculationSettings): number {
  if (currentLevel < 15) {
    const count = MANOS_BLACK_GEMS[currentLevel];
    if (count === undefined) throw new Error(`Fehlende Black-Gem-Menge für +${currentLevel + 1}`);
    return count * settings.materialPrices.blackGem;
  }
  return settings.materialPrices.concentratedBlackGem;
}

function manosStageExpectation(
  currentLevel: number,
  settings: CalculationSettings,
  rebuildAfterDowngrade: Expectation,
): Expectation {
  const attemptCost = manosAttemptCost(currentLevel, settings);
  const repairCost = (currentLevel < 15 ? 5 : 10) * settings.materialPrices.memoryFragment;
  const chance = manosSuccessChance(currentLevel) / 100;
  if (chance >= 1) return { cost: attemptCost, items: 0 };

  const threshold = MANOS_PITY[currentLevel];
  if (threshold === undefined) throw new Error(`Fehlende Agris-Schwelle für Manos +${currentLevel + 1}`);
  let next: Expectation = { cost: attemptCost, items: 0 };
  for (let failures = threshold - 1; failures >= 0; failures -= 1) {
    next = {
      cost: attemptCost + (1 - chance) * (repairCost + rebuildAfterDowngrade.cost + next.cost),
      items: (1 - chance) * (rebuildAfterDowngrade.items + next.items),
    };
  }
  return next;
}

export function expectedManosCost(
  targetLevel: ResultLevel,
  basePrice: number,
  settings: CalculationSettings,
): Expectation {
  const actualTarget = targetLevel + 15;
  let total: Expectation = { cost: basePrice, items: 1 };
  const increments: Expectation[] = [];

  for (let currentLevel = 0; currentLevel < actualTarget; currentLevel += 1) {
    const rebuild = currentLevel >= 17 ? increments[currentLevel - 1] : undefined;
    const increment = manosStageExpectation(currentLevel, settings, rebuild ?? { cost: 0, items: 0 });
    increments[currentLevel] = increment;
    total = { cost: total.cost + increment.cost, items: total.items + increment.items };
  }
  return total;
}

function unavailableResult(category: Category, level: ResultLevel, salePrice: number | null): CalculationResult {
  return {
    level,
    label: LEVEL_LABELS[category][level],
    status: "unavailable",
    avgCost: null,
    expectedItems: null,
    salePrice,
    profit: null,
    margin: null,
  };
}

export function analyzeItem(item: MarketItem, settings: CalculationSettings): ItemAnalysis {
  const basePrice = item.levels["0"]?.price ?? null;
  const results = TARGET_LEVELS.map((level): CalculationResult => {
    const salePrice = item.levels[String(level)]?.price ?? null;
    if (basePrice === null || salePrice === null) return unavailableResult(item.category, level, salePrice);

    const expectation = item.category === "manos"
      ? expectedManosCost(level, basePrice, settings)
      : expectedAccessoryCost(item.category, level, basePrice, settings);
    const netSale = salePrice * settings.taxRate;
    const profit = netSale - expectation.cost;
    return {
      level,
      label: LEVEL_LABELS[item.category][level],
      status: "ok",
      avgCost: expectation.cost,
      expectedItems: expectation.items,
      salePrice,
      profit,
      margin: netSale === 0 ? null : profit / netSale,
    };
  });
  const profits = results.flatMap((result) => (result.profit === null ? [] : [result.profit]));
  return { item, results, bestProfit: profits.length ? Math.max(...profits) : null };
}

export function analyzeItems(items: MarketItem[], settings: CalculationSettings): ItemAnalysis[] {
  return items.map((item) => analyzeItem(item, settings));
}

export interface StackOptimization {
  stacks: [number, number, number];
  avgCost: number;
  profit: number;
}

export function optimizeTriStacks(item: MarketItem, settings: CalculationSettings): StackOptimization | null {
  if (item.category === "manos") return null;
  const basePrice = item.levels["0"]?.price ?? null;
  const salePrice = item.levels["3"]?.price ?? null;
  if (basePrice === null || salePrice === null) return null;

  const candidates = STACK_OPTIONS.filter((stack) => stack <= 60);
  let best: StackOptimization | null = null;
  for (const pri of candidates) {
    for (const duo of candidates) {
      if (duo < pri) continue;
      for (const tri of candidates) {
        if (tri < duo) continue;
        const candidateSettings: CalculationSettings = {
          ...settings,
          stacks: { ...settings.stacks, 0: pri, 1: duo, 2: tri },
        };
        const expectation = expectedAccessoryCost(item.category, 3, basePrice, candidateSettings);
        const profit = salePrice * settings.taxRate - expectation.cost;
        if (!best || profit > best.profit) {
          best = { stacks: [pri, duo, tri], avgCost: expectation.cost, profit };
        }
      }
    }
  }
  return best;
}
