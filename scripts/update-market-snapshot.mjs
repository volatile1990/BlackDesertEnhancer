import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import {
  OFFICIAL_MARKET_BASES,
  decodeVeliaInnMarketResponse,
  parseVeliaInnOrderBook,
} from "./velia-inn-market.mjs";
import { quoteFromBook, sidFor } from "./build-market.mjs";
import { addExpectedOrderBooks, expectedOrderBookCoverage, hasMarketSignal } from "./snapshot-quality.mjs";

const API_BASE = "https://api.arsha.io/v2";
const region = process.env.MARKET_REGION === "na" ? "na" : "eu";
const outputPath = resolve(process.argv[2] ?? `public/data/market-${region}.json`);
const configPath = new URL("../shared/manos-market.json", import.meta.url);
const marketConfig = JSON.parse(await readFile(configPath, "utf8"));
const items = marketConfig.items;
const materials = marketConfig.materials.map(({ defaultPrice: _defaultPrice, ...material }) => material);
const targetLevels = [2, 3, 4];
const retryable = new Set([408, 425, 429, 500, 502, 503, 504]);
const snapshotDeadlineAt = Date.now() + 240_000;
const MAX_RESPONSE_BYTES = 2_000_000;

if (!Array.isArray(items) || items.length !== 8 || !Array.isArray(materials) || materials.length !== 3) {
  throw new Error("Invalid Manos market configuration");
}

const wait = (milliseconds) => new Promise((resolveWait) => setTimeout(resolveWait, milliseconds));
class NonRetryableFetchError extends Error {}

async function fetchVeliaInnFallback(endpoint, payload) {
  let lastError;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    let retryAfterMs = 0;
    const remaining = snapshotDeadlineAt - Date.now();
    if (remaining <= 0) throw new Error("Snapshot deadline exceeded");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Math.min(10_000, remaining));
    try {
      const marketBase = OFFICIAL_MARKET_BASES[region];
      const response = await fetch(`${marketBase}/Trademarket/${endpoint}`, {
        method: "POST",
        headers: {
          Accept: "application/octet-stream, application/json",
          "Content-Type": "application/json",
          "User-Agent": "BlackDesert",
        },
        body: JSON.stringify({ keyType: 0, ...payload }),
        signal: controller.signal,
      });
      if (!response.ok) {
        const error = new Error(`Velia Inn documented market fallback HTTP ${response.status}`);
        if (!retryable.has(response.status)) throw new NonRetryableFetchError(error.message);
        const retryAfter = Number(response.headers.get("retry-after"));
        retryAfterMs = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1_000 : 0;
        throw error;
      }
      const data = Buffer.from(await response.arrayBuffer());
      return decodeVeliaInnMarketResponse(data, response.headers.get("content-type") ?? "");
    } catch (error) {
      lastError = error;
      if (error instanceof NonRetryableFetchError) break;
      if (attempt < 2) await wait(Math.max(retryAfterMs, 700 * 2 ** attempt + Math.random() * 350));
    } finally {
      clearTimeout(timeout);
    }
  }
  throw lastError;
}

async function fetchJson(url, attempts = 3, timeoutMs = 8_000) {
  let lastError;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const remaining = snapshotDeadlineAt - Date.now();
    if (remaining <= 0) throw new Error("Snapshot deadline exceeded");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Math.min(timeoutMs, remaining));
    try {
      const response = await fetch(url, {
        cache: "no-store",
        headers: { Accept: "application/json" },
        signal: controller.signal,
      });
      if (!response.ok) {
        const error = new Error(`HTTP ${response.status} for ${url}`);
        if (!retryable.has(response.status)) throw new NonRetryableFetchError(error.message);
        if (attempt === attempts - 1) throw error;
        const retryAfter = Number(response.headers.get("retry-after"));
        await wait(Number.isFinite(retryAfter) && retryAfter > 0
          ? retryAfter * 1_000
          : 600 * 2 ** attempt + Math.random() * 500);
        continue;
      }
      const contentType = response.headers.get("content-type") ?? "";
      if (!contentType.includes("json")) throw new NonRetryableFetchError(`Unexpected content type for ${url}`);
      const announcedLength = Number(response.headers.get("content-length"));
      if (Number.isFinite(announcedLength) && announcedLength > MAX_RESPONSE_BYTES) {
        throw new NonRetryableFetchError(`Response too large for ${url}`);
      }
      const body = await response.text();
      if (body.length > MAX_RESPONSE_BYTES) throw new NonRetryableFetchError(`Response too large for ${url}`);
      return JSON.parse(body);
    } catch (error) {
      lastError = error;
      if (error instanceof NonRetryableFetchError || attempt === attempts - 1) break;
      await wait(600 * 2 ** attempt + Math.random() * 500);
    } finally {
      clearTimeout(timeout);
    }
  }
  throw lastError;
}

function orderUrl(pairs) {
  const params = new URLSearchParams();
  for (const { id, sid } of pairs) {
    params.append("id", String(id));
    params.append("sid", String(sid));
  }
  return `${API_BASE}/${region}/GetBiddingInfoList?${params}`;
}

function nonNegativeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function positiveInteger(value) {
  return Number.isSafeInteger(value) && value > 0;
}

function normalizeBooks(payload) {
  const rawBooks = Array.isArray(payload) ? payload : [payload];
  if (rawBooks.length > 100) throw new Error("Too many Arsha order books");
  return rawBooks.map((book) => {
    if (!nonNegativeInteger(book?.id) || !nonNegativeInteger(book?.sid) || !Array.isArray(book?.orders)) {
      throw new Error("Invalid Arsha order book");
    }
    const orders = book.orders.map((order) => {
      if (!positiveInteger(order?.price) || !nonNegativeInteger(order?.sellers) ||
          (order?.buyers !== undefined && !nonNegativeInteger(order.buyers))) {
        throw new Error("Invalid Arsha order-book price row");
      }
      return { price: order.price, sellers: order.sellers, buyers: order.buyers ?? 0 };
    });
    return { id: book.id, sid: book.sid, orders, source: "Arsha order book" };
  });
}

function missingQuote(fetchedAt) {
  return {
    price: null,
    sellersAtLowest: 0,
    totalSellers: 0,
    buyersAtPrice: 0,
    totalBuyers: 0,
    kind: "unavailable",
    state: "error",
    fetchedAt,
    source: "Snapshot refresh failed",
  };
}

async function mapLimit(values, limit, task) {
  const results = new Array(values.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, values.length) }, async () => {
    while (cursor < values.length) {
      const index = cursor;
      cursor += 1;
      try {
        results[index] = { status: "fulfilled", value: await task(values[index]) };
      } catch (reason) {
        results[index] = { status: "rejected", reason };
      }
    }
  });
  await Promise.all(workers);
  return results;
}

async function fetchChunkResilient(chunk) {
  if (Date.now() >= snapshotDeadlineAt) return [];
  try {
    const books = normalizeBooks(await fetchJson(orderUrl(chunk), 2, 7_000));
    const expected = new Set(chunk.map(({ id, sid }) => `${id}:${sid}`));
    if (books.some((book) => !expected.has(`${book.id}:${book.sid}`))) throw new Error("Unexpected Arsha order book");
    return books;
  } catch {
    if (chunk.length === 1 || Date.now() >= snapshotDeadlineAt) return [];
    const midpoint = Math.ceil(chunk.length / 2);
    const left = await fetchChunkResilient(chunk.slice(0, midpoint));
    const right = await fetchChunkResilient(chunk.slice(midpoint));
    return [...left, ...right];
  }
}

async function main() {
  const pairs = items.flatMap((item) => [0, ...targetLevels.map(sidFor)].map((sid) => ({ id: item.id, sid })));
  pairs.push(...materials.map(({ id }) => ({ id, sid: 0 })));
  const books = new Map();

  try {
    const probeChunk = pairs.slice(0, 4);
    const probeBooks = normalizeBooks(await fetchJson(orderUrl(probeChunk), 2, 6_000));
    addExpectedOrderBooks(books, pairs, probeBooks);
    const remainingPairs = pairs.filter(({ id, sid }) => !books.has(`${id}:${sid}`));
    const chunks = [];
    for (let index = 0; index < remainingPairs.length; index += 8) chunks.push(remainingPairs.slice(index, index + 8));
    const chunkResults = await mapLimit(chunks, 2, (chunk) => fetchChunkResilient(chunk));
    chunkResults.forEach((result) => {
      if (result.status === "fulfilled") addExpectedOrderBooks(books, pairs, result.value);
    });
  } catch {
    process.stdout.write("Arsha order books unavailable; switching to Velia Inn documented build fallback\n");
  }
  if (books.size > 0 && !hasMarketSignal(books)) {
    books.clear();
    process.stdout.write("Arsha returned only empty order books; switching to the build fallback\n");
  }
  process.stdout.write(`Order-book Arsha: ${books.size}/${pairs.length}\n`);

  const fallbackPairs = pairs.filter(({ id, sid }) => {
    const book = books.get(`${id}:${sid}`);
    return !book || book.orders.length === 0;
  });
  const fallbackResults = await mapLimit(fallbackPairs, 3, async (pair) => {
    if (Date.now() >= snapshotDeadlineAt) return null;
    try {
      return parseVeliaInnOrderBook(await fetchVeliaInnFallback("GetBiddingInfoList", {
        mainKey: pair.id,
        subKey: pair.sid,
      }), pair.id, pair.sid);
    } catch {
      return null;
    }
  });
  fallbackResults.forEach((result, index) => {
    const pair = fallbackPairs[index];
    if (result.status === "fulfilled" && result.value) {
      addExpectedOrderBooks(books, pairs, [result.value]);
    } else if (pair) {
      const key = `${pair.id}:${pair.sid}`;
      if (books.get(key)?.orders.length === 0) books.delete(key);
    }
  });
  process.stdout.write(`Order-book Velia Inn documented fallback: ${books.size}/${pairs.length}\n`);

  const coverage = expectedOrderBookCoverage(pairs, books);
  if (coverage < 1 || !hasMarketSignal(books)) {
    throw new Error(`Only ${(coverage * 100).toFixed(1)}% plausible order-book coverage; existing snapshot was not touched`);
  }

  const fetchedAt = new Date().toISOString();
  const snapshotItems = items.map((item) => ({
    id: item.id,
    name: item.name,
    levels: Object.fromEntries([0, ...targetLevels].map((level) => {
      const sid = sidFor(level);
      const book = books.get(`${item.id}:${sid}`);
      return [String(level), book ? quoteFromBook(book, fetchedAt, level === 0) : missingQuote(fetchedAt)];
    })),
  }));
  const materialQuotes = Object.fromEntries(materials.map((material) => {
    const book = books.get(`${material.id}:0`);
    return [material.key, { ...material, ...(book ? quoteFromBook(book, fetchedAt) : missingQuote(fetchedAt)) }];
  }));
  const snapshot = {
    schemaVersion: 3,
    region,
    fetchedAt,
    source: "Manos order-book snapshot (Arsha / Velia Inn-documented Pearl Abyss fallback)",
    items: snapshotItems,
    materials: materialQuotes,
  };

  await mkdir(dirname(outputPath), { recursive: true });
  const temporaryPath = `${outputPath}.${process.pid}.tmp`;
  try {
    await writeFile(temporaryPath, `${JSON.stringify(snapshot, null, 2)}\n`, "utf8");
    await rename(temporaryPath, outputPath);
  } catch (error) {
    await rm(temporaryPath, { force: true });
    throw error;
  }
  process.stdout.write(`Wrote ${snapshotItems.length} Manos items and ${books.size}/${pairs.length} order books to ${outputPath}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack : String(error)}\n`);
  process.exitCode = 1;
});
