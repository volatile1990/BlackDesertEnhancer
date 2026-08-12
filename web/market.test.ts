import { describe, expect, it, vi } from "vitest";
import bundledSnapshot from "../public/data/market-eu.json";
import { fetchJsonWithRetry, lowestListedPrice, validateMarketSnapshot, validateOrderBooks } from "./market";

describe("listing-first market price", () => {
  it("takes the cheapest unordered price level that has sellers", () => {
    expect(lowestListedPrice([
      { price: 120, sellers: 4 },
      { price: 90, sellers: 0, buyers: 11 },
      { price: 100, sellers: 2 },
      { price: 110, sellers: 3 },
    ])).toEqual({ price: 100, sellersAtLowest: 2, totalSellers: 9 });
  });

  it("returns null for empty or buyers-only order books", () => {
    expect(lowestListedPrice([]).price).toBeNull();
    expect(lowestListedPrice([{ price: 500, sellers: 0, buyers: 20 }])).toEqual({
      price: null,
      sellersAtLowest: 0,
      totalSellers: 0,
    });
  });

  it("rejects unsafe market integers and malformed schemas", () => {
    expect(() => validateOrderBooks({
      id: 1,
      sid: 0,
      orders: [{ price: Number.MAX_SAFE_INTEGER + 1, sellers: 1 }],
    })).toThrow(/price/);
    expect(() => validateOrderBooks({ id: 1, sid: 0, orders: "not-an-array" })).toThrow(/Orderbuch/);
  });
});

describe("persisted snapshot validation", () => {
  it("accepts the bundled EU last-known-good snapshot with all three profiles", () => {
    const snapshot = validateMarketSnapshot(bundledSnapshot, "eu");
    expect(new Set(snapshot.items.map((item) => item.category))).toEqual(new Set(["accessory", "silver", "manos"]));
    expect(Object.values(snapshot.materials).every((material) => material.price !== null)).toBe(true);
  });

  it("rejects a cached quote that disguises a missing listing as a price", () => {
    expect(() => validateMarketSnapshot({
      schemaVersion: 1,
      region: "eu",
      fetchedAt: "2026-08-12T00:00:00.000Z",
      source: "test",
      items: [{
        id: 1,
        name: "Test",
        category: "accessory",
        levels: Object.fromEntries(["0", "2", "3", "4"].map((level) => [level, {
          price: level === "0" ? 100 : null,
          sellersAtLowest: level === "0" ? 1 : 0,
          totalSellers: level === "0" ? 1 : 0,
          state: "cached",
          fetchedAt: "2026-08-12T00:00:00.000Z",
          source: "test",
        }])),
      }],
      materials: {},
    }, "eu")).toThrow(/Preisloser Eintrag/);
  });
});

describe("resilient JSON fetch", () => {
  it("retries a transient 503 and then returns JSON", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(new Response("temporary", { status: 503 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }));
    await expect(fetchJsonWithRetry<{ ok: boolean }>("https://example.test", {
      attempts: 2,
      timeoutMs: 2_000,
      fetcher: fetcher as typeof fetch,
    })).resolves.toEqual({ ok: true });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("does not retry a permanent 404", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response("missing", { status: 404 }));
    await expect(fetchJsonWithRetry("https://example.test", {
      attempts: 3,
      timeoutMs: 2_000,
      fetcher: fetcher as typeof fetch,
    })).rejects.toThrow(/404/);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("rejects successful HTML instead of parsing it as market data", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response("<html></html>", {
      status: 200,
      headers: { "content-type": "text/html" },
    }));
    await expect(fetchJsonWithRetry("https://example.test", {
      attempts: 1,
      fetcher: fetcher as typeof fetch,
    })).rejects.toThrow(/kein JSON/);
  });
});
