import { describe, expect, it } from "vitest";
import { classifyItem, marketSid } from "./config";

describe("category profiles", () => {
  it("separates current Silver Embroidered and Manos clothing", () => {
    expect(classifyItem("Silver Embroidered Farmer's Clothes", "functional")).toBe("silver");
    expect(classifyItem("Silver Embroidered Trader's Clothes", "functional")).toBe("silver");
    expect(classifyItem("Manos Cook's Clothes", "functional")).toBe("manos");
  });

  it("does not silently run Manos life accessories through the standard profile", () => {
    expect(classifyItem("Manos Necklace", "accessory")).toBeNull();
  });

  it("maps Manos clothing result levels to the correct market enhancement id", () => {
    expect(marketSid("accessory", 3)).toBe(3);
    expect(marketSid("silver", 3)).toBe(3);
    expect(marketSid("manos", 2)).toBe(17);
    expect(marketSid("manos", 4)).toBe(19);
  });
});
