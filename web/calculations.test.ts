import { describe, expect, it } from "vitest";
import { DEFAULT_MATERIAL_PRICES } from "./config";
import { analyzeItem, expectedManosCost, manosSuccessChance } from "./calculations";
import type { CalculationSettings, MarketItem } from "./types";

const settings: CalculationSettings = {
  taxRate: 0.845,
  materialPrices: { ...DEFAULT_MATERIAL_PRICES },
};

const fetchedAt = "2026-09-15T00:00:00.000Z";
const quote = (price: number | null, kind: "listing" | "preorder" | "unavailable" = price === null ? "unavailable" : "listing") => ({
  price,
  sellersAtLowest: kind === "listing" ? 1 : 0,
  totalSellers: kind === "listing" ? 1 : 0,
  buyersAtPrice: kind === "preorder" ? 10 : 0,
  totalBuyers: kind === "preorder" ? 10 : 0,
  kind,
  state: price === null ? "unlisted" as const : "fresh" as const,
  fetchedAt,
  source: "test",
});

describe("Manos enhancement model", () => {
  it("keeps the verified fixed-rate table", () => {
    expect(Array.from({ length: 20 }, (_, level) => manosSuccessChance(level))).toEqual([
      100, 100, 100, 100, 100, 100, 100, 70, 60, 50,
      40, 30, 20, 15, 10, 30, 25, 20, 15, 6,
    ]);
  });

  it("computes finite downgrade and rebuild expectations", () => {
    const expectation = expectedManosCost(4, 120_000_000, settings);
    expect(expectation.cost).toBeGreaterThan(120_000_000);
    expect(Number.isFinite(expectation.cost)).toBe(true);
    expect(expectation.items).toBe(1);
  });

  it("uses only the configured Manos materials", () => {
    const freeMaterials: CalculationSettings = {
      ...settings,
      materialPrices: { blackGem: 0, concentratedBlackGem: 0, memoryFragment: 0 },
    };
    expect(expectedManosCost(3, 100_000_000, settings).cost)
      .toBeGreaterThan(expectedManosCost(3, 100_000_000, freeMaterials).cost);
  });

  it("does not calculate profit when a current target listing is missing", () => {
    const item: MarketItem = {
      id: 705037,
      name: "Manos Cook's Clothes",
      levels: { "0": quote(259_000_000), "2": quote(null), "3": quote(null), "4": quote(null) },
    };
    const result = analyzeItem(item, settings).results.find((entry) => entry.level === 2);
    expect(result?.status).toBe("unavailable");
    expect(result?.profit).toBeNull();
  });

  it("calculates with the BASE preorder maximum when a target is listed", () => {
    const item: MarketItem = {
      id: 705047,
      name: "Manos Alchemist's Clothes",
      levels: {
        "0": quote(259_000_000, "preorder"),
        "2": quote(null),
        "3": quote(2_990_000_000),
        "4": quote(7_800_000_000),
      },
    };
    const result = analyzeItem(item, settings).results.find((entry) => entry.level === 3);
    expect(result?.status).toBe("ok");
    expect(result?.avgCost).toBeGreaterThan(259_000_000);
    expect(result?.salePrice).toBe(2_990_000_000);
  });

  it("does not calculate with a missing or expired material price", () => {
    const item: MarketItem = {
      id: 705047,
      name: "Manos Alchemist's Clothes",
      levels: {
        "0": quote(259_000_000, "preorder"),
        "2": quote(null),
        "3": quote(2_990_000_000),
        "4": quote(7_800_000_000),
      },
    };
    const missingMaterial: CalculationSettings = {
      ...settings,
      materialPrices: { ...settings.materialPrices, memoryFragment: Number.NaN },
    };
    const result = analyzeItem(item, missingMaterial).results.find((entry) => entry.level === 3);
    expect(result).toMatchObject({ status: "unavailable", unavailableReason: "material", profit: null });
  });

  it("does not calculate with an invalid net-sale rate", () => {
    const item: MarketItem = {
      id: 705047,
      name: "Manos Alchemist's Clothes",
      levels: {
        "0": quote(259_000_000, "preorder"),
        "2": quote(null),
        "3": quote(2_990_000_000),
        "4": quote(7_900_000_000),
      },
    };
    const invalidTax = { ...settings, taxRate: Number.NaN };
    const result = analyzeItem(item, invalidTax).results.find((entry) => entry.level === 3);
    expect(result).toMatchObject({ status: "unavailable", unavailableReason: "tax", profit: null });
  });
});
