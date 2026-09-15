import { afterEach, describe, expect, it, vi } from "vitest";
import bundledSnapshot from "../public/data/market-eu.json";
import { CACHE_MAX_STALE_MS, CACHE_TTL_MS, MANOS_ITEMS, MATERIALS, cacheKey } from "./config";
import {
  fetchJsonWithRetry,
  highestPreorderPrice,
  isQuoteWithinMaxAge,
  lowestListedPrice,
  loadMarket,
  mergeFallbacks,
  quoteFromBook,
  validateMarketSnapshot,
  validateOrderBooks,
} from "./market";
import type { MarketQuote } from "./types";

afterEach(() => {
  vi.unstubAllGlobals();
});

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

  it("uses the highest allowed order-book tier for a missing BASE listing", () => {
    const orders = [
      { price: 90, sellers: 0, buyers: 20 },
      { price: 110, sellers: 0, buyers: 0 },
      { price: 100, sellers: 0, buyers: 7 },
    ];
    expect(highestPreorderPrice(orders)).toEqual({ price: 110, buyersAtPrice: 0, totalBuyers: 27 });
    expect(quoteFromBook({ id: 1, sid: 0, orders }, "2026-09-15T00:00:00.000Z", "fresh", true)).toMatchObject({
      price: 110,
      kind: "preorder",
      state: "fresh",
      buyersAtPrice: 0,
      totalBuyers: 27,
    });
  });

  it("still prefers the lowest seller listing when BASE has sellers", () => {
    const quote = quoteFromBook({
      id: 1,
      sid: 0,
      orders: [
        { price: 90, sellers: 2, buyers: 0 },
        { price: 110, sellers: 0, buyers: 10 },
        { price: 100, sellers: 1, buyers: 0 },
      ],
    }, "2026-09-15T00:00:00.000Z", "fresh", true);
    expect(quote).toMatchObject({ price: 90, kind: "listing", sellersAtLowest: 2 });
  });

  it("keeps an empty BASE book and a buyers-only target unavailable", () => {
    expect(quoteFromBook({ id: 1, sid: 0, orders: [] }, fetchedAt, "fresh", true)).toMatchObject({
      price: null,
      kind: "unavailable",
      state: "unlisted",
    });
    expect(quoteFromBook({
      id: 1,
      sid: 18,
      orders: [{ price: 500, sellers: 0, buyers: 20 }],
    }, fetchedAt, "fresh", false)).toMatchObject({ price: null, kind: "unavailable", state: "unlisted" });
  });

  it("rejects unsafe market integers and malformed schemas", () => {
    expect(() => validateOrderBooks({
      id: 1,
      sid: 0,
      orders: [{ price: Number.MAX_SAFE_INTEGER + 1, sellers: 1 }],
    })).toThrow(/price/);
    expect(() => validateOrderBooks({ id: 1, sid: 0, orders: [{ price: 0, sellers: 1 }] })).toThrow(/price/);
    expect(() => validateOrderBooks({ id: 1, sid: 0, orders: "not-an-array" })).toThrow(/Orderbuch/);
  });
});

const fetchedAt = "2026-09-15T00:00:00.000Z";

describe("Manos snapshot validation and cache age", () => {
  it("accepts a bundled snapshot containing only Manos clothes and required materials", () => {
    const snapshot = validateMarketSnapshot(bundledSnapshot, "eu");
    expect(snapshot.items.map((item) => item.id).sort()).toEqual(MANOS_ITEMS.map((item) => item.id).sort());
    expect(Object.keys(snapshot.materials).sort()).toEqual(MATERIALS.map((material) => material.key).sort());
    expect(snapshot.items.flatMap((item) => [item.levels["2"], item.levels["3"], item.levels["4"]])
      .every((quote) => quote?.kind !== "preorder")).toBe(true);
    expect(Object.values(snapshot.materials).every((quote) => quote.kind !== "preorder")).toBe(true);
  });

  it("rejects old schemas and unknown non-Manos items", () => {
    const oldSchema = structuredClone(bundledSnapshot) as Record<string, unknown>;
    oldSchema.schemaVersion = 2;
    expect(() => validateMarketSnapshot(oldSchema, "eu")).toThrow(/Version/);

    const unknownItem = structuredClone(bundledSnapshot);
    unknownItem.items[0]!.id = 1;
    expect(() => validateMarketSnapshot(unknownItem, "eu")).toThrow(/Manos-Item/);
  });

  it("expires each quote by its own timestamp instead of the outer cache timestamp", () => {
    const quote = bundledSnapshot.items[0]!.levels["0"] as MarketQuote;
    const quoteTime = Date.parse("2026-09-15T00:00:00.000Z");
    expect(isQuoteWithinMaxAge({ ...quote, fetchedAt: new Date(quoteTime - CACHE_MAX_STALE_MS + 1).toISOString() }, quoteTime)).toBe(true);
    expect(isQuoteWithinMaxAge({ ...quote, fetchedAt: new Date(quoteTime - CACHE_MAX_STALE_MS - 1).toISOString() }, quoteTime)).toBe(false);
    expect(isQuoteWithinMaxAge({ ...quote, fetchedAt: new Date(quoteTime + 5 * 60 * 1_000).toISOString() }, quoteTime)).toBe(true);
    expect(isQuoteWithinMaxAge({ ...quote, fetchedAt: new Date(quoteTime + 5 * 60 * 1_000 + 1).toISOString() }, quoteTime)).toBe(false);
  });

  it("merges fallback data per quote and ignores a newer failed cache entry", () => {
    const now = Date.now();
    const cached = structuredClone(validateMarketSnapshot(bundledSnapshot, "eu"));
    const bundled = structuredClone(validateMarketSnapshot(bundledSnapshot, "eu"));
    cached.fetchedAt = new Date(now).toISOString();
    bundled.fetchedAt = new Date(now - 60 * 60 * 1_000).toISOString();
    const cachedQuote = cached.items[0]!.levels["0"]!;
    Object.assign(cachedQuote, {
      price: null,
      sellersAtLowest: 0,
      totalSellers: 0,
      buyersAtPrice: 0,
      totalBuyers: 0,
      kind: "unavailable",
      state: "error",
      fetchedAt: cached.fetchedAt,
    });
    const bundledQuote = bundled.items[0]!.levels["0"]!;
    bundledQuote.fetchedAt = bundled.fetchedAt;
    const merged = mergeFallbacks([
      { snapshot: cached, state: "cached" },
      { snapshot: bundled, state: "snapshot" },
    ], "eu");
    expect(merged?.snapshot.items[0]!.levels["0"]).toMatchObject({
      price: bundledQuote.price,
      state: "snapshot",
      fetchedAt: bundled.fetchedAt,
    });
  });

  it("shows the bundled snapshot immediately and requests background refresh", async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(bundledSnapshot), {
      status: 200,
      headers: { "content-type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetcher);
    const result = await loadMarket("eu", false);
    expect(result.status).toBe("snapshot");
    expect(result.refreshRecommended).toBe(true);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("keeps the bundled snapshot when Arsha returns only empty books", async () => {
    const storage = { getItem: vi.fn(() => null), setItem: vi.fn() };
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("data/market-eu.json")) {
        return new Response(JSON.stringify(bundledSnapshot), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }
      const parsed = new URL(url);
      const ids = parsed.searchParams.getAll("id");
      const sids = parsed.searchParams.getAll("sid");
      return new Response(JSON.stringify(ids.map((id, index) => ({
        id: Number(id),
        sid: Number(sids[index]),
        orders: [],
      }))), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    });
    vi.stubGlobal("localStorage", storage);
    vi.stubGlobal("fetch", fetcher);

    const result = await loadMarket("eu", true);
    expect(result).toMatchObject({ status: "snapshot", refreshRecommended: false });
    expect(result.warnings.join(" ")).toMatch(/ausschließlich leere/);
    expect(storage.setItem).not.toHaveBeenCalled();
  });

  it("stores a complete refresh with one genuinely empty order book as unlisted", async () => {
    const storage = { getItem: vi.fn(() => null), setItem: vi.fn() };
    const firstPair = `${MANOS_ITEMS[0]!.id}:0`;
    const fetcher = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("data/market-eu.json")) {
        return new Response(JSON.stringify(bundledSnapshot), {
          status: 200,
          headers: { "content-type": "application/json" },
        });
      }
      const parsed = new URL(url);
      const ids = parsed.searchParams.getAll("id");
      const sids = parsed.searchParams.getAll("sid");
      return new Response(JSON.stringify(ids.map((id, index) => {
        const sid = Number(sids[index]);
        return {
          id: Number(id),
          sid,
          orders: `${id}:${sid}` === firstPair ? [] : [{ price: 100, sellers: 1, buyers: 0 }],
        };
      })), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    });
    vi.stubGlobal("localStorage", storage);
    vi.stubGlobal("fetch", fetcher);

    const result = await loadMarket("eu", true);
    expect(result).toMatchObject({ status: "fresh", refreshRecommended: false });
    expect(result.snapshot.items[0]!.levels["0"]).toMatchObject({
      price: null,
      kind: "unavailable",
      state: "unlisted",
    });
    expect(storage.setItem).toHaveBeenCalledTimes(1);
  });

  it("honors the ten-minute browser-cache TTL", async () => {
    const now = Date.now();
    const cached = structuredClone(validateMarketSnapshot(bundledSnapshot, "eu"));
    cached.fetchedAt = new Date(now - CACHE_TTL_MS + 1_000).toISOString();
    for (const quote of cached.items.flatMap((item) => Object.values(item.levels))) quote.fetchedAt = cached.fetchedAt;
    for (const quote of Object.values(cached.materials)) quote.fetchedAt = cached.fetchedAt;
    const storage = { getItem: vi.fn((key: string) => key === cacheKey("eu") ? JSON.stringify(cached) : null), setItem: vi.fn() };
    const oldBundle = structuredClone(cached);
    oldBundle.fetchedAt = new Date(now - 2 * CACHE_TTL_MS).toISOString();
    vi.stubGlobal("localStorage", storage);
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(oldBundle), {
      status: 200,
      headers: { "content-type": "application/json" },
    })));
    await expect(loadMarket("eu", false)).resolves.toMatchObject({ status: "cached", refreshRecommended: false });

    cached.fetchedAt = new Date(now - CACHE_TTL_MS - 1_000).toISOString();
    for (const quote of cached.items.flatMap((item) => Object.values(item.levels))) quote.fetchedAt = cached.fetchedAt;
    for (const quote of Object.values(cached.materials)) quote.fetchedAt = cached.fetchedAt;
    await expect(loadMarket("eu", false)).resolves.toMatchObject({ status: "cached", refreshRecommended: true });
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
