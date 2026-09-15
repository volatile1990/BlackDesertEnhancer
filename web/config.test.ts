import { describe, expect, it } from "vitest";
import { MANOS_ITEMS, MATERIALS, marketSid } from "./config";

describe("Manos-only market configuration", () => {
  it("contains exactly the eight supported Manos clothes with unique IDs", () => {
    expect(MANOS_ITEMS).toHaveLength(8);
    expect(new Set(MANOS_ITEMS.map((item) => item.id))).toHaveLength(8);
    expect(MANOS_ITEMS.every((item) => /^Manos .+Clothes$/.test(item.name))).toBe(true);
  });

  it("requests only the three materials used by the Manos calculation", () => {
    expect(MATERIALS.map((material) => material.key)).toEqual([
      "blackGem",
      "concentratedBlackGem",
      "memoryFragment",
    ]);
  });

  it("maps result levels to the Manos market enhancement IDs", () => {
    expect(marketSid(0)).toBe(0);
    expect(marketSid(2)).toBe(17);
    expect(marketSid(3)).toBe(18);
    expect(marketSid(4)).toBe(19);
  });

  it("has exactly 35 unique order-book pairs", () => {
    const pairs = [
      ...MANOS_ITEMS.flatMap((item) => [0, 2, 3, 4].map((level) => `${item.id}:${marketSid(level)}`)),
      ...MATERIALS.map((material) => `${material.id}:0`),
    ];
    expect(pairs).toHaveLength(35);
    expect(new Set(pairs).size).toBe(35);
  });
});
