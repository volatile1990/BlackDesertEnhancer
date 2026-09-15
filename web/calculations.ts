import { LEVEL_LABELS, TARGET_LEVELS } from "./config";
import type {
  CalculationResult,
  CalculationSettings,
  ItemAnalysis,
  MarketItem,
  ResultLevel,
} from "./types";

interface Expectation {
  cost: number;
  items: number;
}

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

export function manosSuccessChance(currentLevel: number): number {
  const chance = MANOS_CHANCES[currentLevel];
  if (chance === undefined) throw new Error(`Nicht unterstützte Manos-Stufe ${currentLevel}`);
  return chance;
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

function unavailableResult(
  level: ResultLevel,
  salePrice: number | null,
  unavailableReason: "base" | "target" | "material" | "tax",
): CalculationResult {
  return {
    level,
    label: LEVEL_LABELS[level],
    status: "unavailable",
    avgCost: null,
    expectedItems: null,
    salePrice,
    profit: null,
    margin: null,
    unavailableReason,
  };
}

export function analyzeItem(item: MarketItem, settings: CalculationSettings): ItemAnalysis {
  const basePrice = item.levels["0"]?.price ?? null;
  const materialsAvailable = Object.values(settings.materialPrices)
    .every((price) => Number.isFinite(price) && price >= 0);
  const taxAvailable = Number.isFinite(settings.taxRate) && settings.taxRate > 0 && settings.taxRate <= 1;
  const results = TARGET_LEVELS.map((level): CalculationResult => {
    const salePrice = item.levels[String(level)]?.price ?? null;
    if (basePrice === null) return unavailableResult(level, salePrice, "base");
    if (salePrice === null) return unavailableResult(level, salePrice, "target");
    if (!taxAvailable) return unavailableResult(level, salePrice, "tax");
    if (!materialsAvailable) return unavailableResult(level, salePrice, "material");

    const expectation = expectedManosCost(level, basePrice, settings);
    const netSale = salePrice * settings.taxRate;
    const profit = netSale - expectation.cost;
    return {
      level,
      label: LEVEL_LABELS[level],
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
