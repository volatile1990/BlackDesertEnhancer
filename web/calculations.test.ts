import { describe, expect, it } from "vitest";
import { DEFAULT_MATERIAL_PRICES, DEFAULT_STACKS } from "./config";
import {
  analyzeItem,
  expectedAccessoryCost,
  expectedManosCost,
  manosSuccessChance,
  stackAcquisitionCost,
  successChance,
} from "./calculations";
import type { CalculationSettings, MarketItem } from "./types";

const settings: CalculationSettings = {
  taxRate: 0.845,
  stackCostMode: "market",
  stacks: { ...DEFAULT_STACKS },
  materialPrices: { ...DEFAULT_MATERIAL_PRICES },
};

describe("piecewise enhancement chances", () => {
  it("crosses the standard PRI softcap without lowering the chance", () => {
    expect(successChance("accessory", 0, 18)).toBe(70);
    expect(successChance("accessory", 0, 19)).toBe(70.5);
    expect(successChance("accessory", 0, 15 + 4)).toBe(70.5);
  });

  it("uses the separate Silver Embroidered +1 profile", () => {
    expect(successChance("silver", 0, 14)).toBe(72);
    expect(successChance("silver", 0, 15)).toBeCloseTo(72.6);
    expect(successChance("silver", 1, 40)).toBe(50);
  });

  it("is monotonic and capped at 90 percent for every accessory stage", () => {
    for (const category of ["accessory", "silver"] as const) {
      for (let stage = 0; stage < 4; stage += 1) {
        let previous = 0;
        for (let stack = 0; stack <= 500; stack += 1) {
          const chance = successChance(category, stage, stack);
          expect(chance).toBeGreaterThanOrEqual(previous);
          expect(chance).toBeLessThanOrEqual(90);
          previous = chance;
        }
      }
    }
  });

  it("keeps the verified current Manos fixed-rate table", () => {
    expect(Array.from({ length: 20 }, (_, level) => manosSuccessChance(level))).toEqual([
      100, 100, 100, 100, 100, 100, 100, 70, 60, 50,
      40, 30, 20, 15, 10, 30, 25, 20, 15, 6,
    ]);
  });
});

describe("cost model", () => {
  it("prices direct stack acquisition with the configured live materials", () => {
    expect(stackAcquisitionCost(50, settings)).toBe(4 * settings.materialPrices.crystallizedDespair);
    expect(stackAcquisitionCost(110, settings)).toBe(25 * settings.materialPrices.primordialBlackStone);
    expect(stackAcquisitionCost(110, { ...settings, stackCostMode: "owned" })).toBe(0);
  });

  it("keeps fractional expected item counts", () => {
    const expectation = expectedAccessoryCost("accessory", 2, 100_000_000, settings);
    expect(expectation.items).toBeGreaterThan(2);
    expect(Number.isInteger(expectation.items)).toBe(false);
    expect(expectation.cost).toBeGreaterThan(0);
  });

  it("computes finite Manos downgrade and rebuild expectations", () => {
    const expectation = expectedManosCost(4, 120_000_000, settings);
    expect(expectation.cost).toBeGreaterThan(120_000_000);
    expect(Number.isFinite(expectation.cost)).toBe(true);
    expect(expectation.items).toBe(1);
  });

  it("does not calculate profit when a current sell listing is missing", () => {
    const fetchedAt = "2026-08-12T00:00:00.000Z";
    const item: MarketItem = {
      id: 1,
      name: "Test Ring",
      category: "accessory",
      levels: {
        "0": { price: 10_000_000, sellersAtLowest: 1, totalSellers: 1, state: "fresh", fetchedAt, source: "test" },
        "2": { price: null, sellersAtLowest: 0, totalSellers: 0, state: "unlisted", fetchedAt, source: "test" },
      },
    };
    const result = analyzeItem(item, settings).results.find((entry) => entry.level === 2);
    expect(result?.status).toBe("unavailable");
    expect(result?.profit).toBeNull();
  });
});
