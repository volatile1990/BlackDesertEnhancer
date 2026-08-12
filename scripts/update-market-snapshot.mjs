import { mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { decodeMarketHuffman } from "./huffman.mjs";

const API_BASE = "https://api.arsha.io/v2";
const region = process.env.MARKET_REGION === "na" ? "na" : "eu";
const officialBase = `https://${region}-trade.naeu.playblackdesert.com/TradeMarket`;
const outputPath = resolve(process.argv[2] ?? `public/data/market-${region}.json`);
const categories = [
  { main: 20, sub: 1, source: "accessory" },
  { main: 20, sub: 2, source: "accessory" },
  { main: 20, sub: 3, source: "accessory" },
  { main: 20, sub: 4, source: "accessory" },
  { main: 15, sub: 5, source: "functional" },
];
const materials = [
  { id: 16001, key: "blackStone", label: "Black Stone" },
  { id: 8411, key: "crystallizedDespair", label: "Crystallized Despair" },
  { id: 820934, key: "primordialBlackStone", label: "Primordial Black Stone" },
  { id: 5000, key: "blackGem", label: "Black Gem" },
  { id: 4987, key: "concentratedBlackGem", label: "Concentrated Magical Black Gem" },
  { id: 44195, key: "memoryFragment", label: "Memory Fragment" },
];
const targetLevels = [2, 3, 4];
const retryable = new Set([408, 425, 429, 500, 502, 503, 504]);
const snapshotDeadlineAt = Date.now() + 300_000;

const wait = (milliseconds) => new Promise((resolveWait) => setTimeout(resolveWait, milliseconds));

async function fetchOfficial(endpoint, payload) {
  let lastError;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const remaining = snapshotDeadlineAt - Date.now();
    if (remaining <= 0) throw new Error("Snapshot deadline exceeded");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Math.min(10_000, remaining));
    try {
      const response = await fetch(`${officialBase}/${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "User-Agent": "BlackDesert" },
        body: JSON.stringify({ keyType: 0, ...payload }),
        signal: controller.signal,
      });
      if (!response.ok) throw new Error(`Official market HTTP ${response.status}`);
      const data = Buffer.from(await response.arrayBuffer());
      return decodeMarketHuffman(data);
    } catch (error) {
      lastError = error;
      if (attempt < 2) await wait(700 * 2 ** attempt + Math.random() * 350);
    } finally {
      clearTimeout(timeout);
    }
  }
  throw lastError;
}

function parseOfficialCatalog(result, category) {
  return result.split("|").flatMap((entry) => {
    if (!entry) return [];
    const [id] = entry.split("-").map(Number);
    return Number.isSafeInteger(id) ? [{ id, category }] : [];
  });
}

function parseOfficialBook(result, id, sid) {
  const orders = result.split("|").flatMap((entry) => {
    if (!entry) return [];
    const [price, sellers, buyers] = entry.split("-").map(Number);
    return Number.isSafeInteger(price) && Number.isSafeInteger(sellers) && Number.isSafeInteger(buyers)
      ? [{ price, sellers, buyers }]
      : [];
  });
  return { id, sid, orders, source: "Pearl Abyss order book (build snapshot)" };
}

async function fetchNames(ids) {
  const names = new Map();
  const chunks = [];
  for (let index = 0; index < ids.length; index += 80) chunks.push(ids.slice(index, index + 80));
  for (const chunk of chunks) {
    const params = new URLSearchParams({ lang: "en" });
    chunk.forEach((id) => params.append("id", String(id)));
    try {
      const payload = await fetchJson(`https://api.arsha.io/util/db?${params}`, 3, 8_000);
      const rows = Array.isArray(payload) ? payload : [payload];
      rows.forEach((row) => {
        if (Number.isSafeInteger(row?.id) && typeof row?.name === "string") names.set(row.id, row.name);
      });
    } catch {
      // Unknown names are skipped instead of assigning an incorrect enhancement profile.
    }
  }
  return names;
}

async function fetchJson(url, attempts = 4, timeoutMs = 10_000) {
  let lastError;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const remaining = snapshotDeadlineAt - Date.now();
    if (remaining <= 0) throw new Error("Snapshot deadline exceeded");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), Math.min(timeoutMs, remaining));
    try {
      const response = await fetch(url, {
        headers: { Accept: "application/json" },
        signal: controller.signal,
      });
      if (!response.ok) {
        const error = new Error(`HTTP ${response.status} for ${url}`);
        if (!retryable.has(response.status) || attempt === attempts - 1) throw error;
        const retryAfter = Number(response.headers.get("retry-after"));
        await wait(Number.isFinite(retryAfter) && retryAfter > 0
          ? retryAfter * 1_000
          : 600 * 2 ** attempt + Math.random() * 500);
        continue;
      }
      const contentType = response.headers.get("content-type") ?? "";
      if (!contentType.includes("json")) throw new Error(`Unexpected content type for ${url}`);
      return await response.json();
    } catch (error) {
      lastError = error;
      if (attempt < attempts - 1) await wait(600 * 2 ** attempt + Math.random() * 500);
    } finally {
      clearTimeout(timeout);
    }
  }
  throw lastError;
}

function classify(name, source) {
  if (source === "accessory") {
    if (/manos|preonne|geranoa|loggia/i.test(name)) return null;
    if (/diamond necklace of fortitude|emerald necklace of tranquility|topaz necklace of regeneration|sapphire necklace of storms|corrupt ruby necklace/i.test(name)) return null;
    return "accessory";
  }
  if (/^silver embroidered /i.test(name)) return "silver";
  if (/^manos .+clothes$/i.test(name)) return "manos";
  return null;
}

function sidFor(category, level) {
  return category === "manos" && level > 0 ? level + 15 : level;
}

function orderUrl(pairs) {
  const params = new URLSearchParams();
  for (const { id, sid } of pairs) {
    params.append("id", String(id));
    params.append("sid", String(sid));
  }
  return `${API_BASE}/${region}/GetBiddingInfoList?${params}`;
}

function normalizeBooks(payload) {
  const books = Array.isArray(payload) ? payload : [payload];
  return books
    .filter((book) => Number.isSafeInteger(book?.id) && Number.isSafeInteger(book?.sid) && Array.isArray(book?.orders))
    .map((book) => ({ ...book, source: "Arsha order book" }));
}

function quoteFromBook(book, fetchedAt) {
  const asks = book.orders.filter((order) => Number.isSafeInteger(order.price) && order.price >= 0 && Number.isSafeInteger(order.sellers) && order.sellers > 0);
  const totalSellers = asks.reduce((sum, order) => sum + order.sellers, 0);
  if (asks.length === 0) {
    return { price: null, sellersAtLowest: 0, totalSellers: 0, state: "unlisted", fetchedAt, source: book.source ?? "Order book" };
  }
  const lowest = asks.reduce((best, order) => order.price < best.price ? order : best);
  return { price: lowest.price, sellersAtLowest: lowest.sellers, totalSellers, state: "snapshot", fetchedAt, source: book.source ?? "Order book" };
}

function missingQuote(fetchedAt) {
  return { price: null, sellersAtLowest: 0, totalSellers: 0, state: "error", fetchedAt, source: "Snapshot refresh failed" };
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
    return normalizeBooks(await fetchJson(orderUrl(chunk), 2, 8_000));
  } catch {
    if (chunk.length === 1) return [];
    const midpoint = Math.ceil(chunk.length / 2);
    await wait(350);
    const left = await fetchChunkResilient(chunk.slice(0, midpoint));
    await wait(350);
    const right = await fetchChunkResilient(chunk.slice(midpoint));
    return [...left, ...right];
  }
}

async function main() {
  const categoryResults = await mapLimit(categories, 1, async (category) => {
    try {
      const url = `${API_BASE}/${region}/GetWorldMarketList?mainCategory=${category.main}&subCategory=${category.sub}`;
      const rows = await fetchJson(url, 5, 10_000);
      if (!Array.isArray(rows)) throw new Error(`Invalid catalog response for ${category.main}/${category.sub}`);
      return rows.flatMap((row) => {
        const itemCategory = typeof row?.name === "string" ? classify(row.name, category.source) : null;
        return itemCategory && Number.isSafeInteger(row.id)
          ? [{ id: row.id, name: row.name, category: itemCategory }]
          : [];
      });
    } finally {
      await wait(500);
    }
  });
  const failedCategoryIndexes = categoryResults.flatMap((result, index) => result.status === "rejected" ? [index] : []);
  const officialCatalogRows = [];
  for (const index of failedCategoryIndexes) {
    const category = categories[index];
    if (!category) continue;
    try {
      const result = await fetchOfficial("GetWorldMarketList", {
        mainCategory: category.main,
        subCategory: category.sub,
      });
      officialCatalogRows.push(...parseOfficialCatalog(result, category.source));
    } catch {
      // Existing bundled snapshot remains untouched if no complete refresh is possible.
    }
  }
  const unnamedCatalog = Array.from(new Map([
    ...categoryResults.flatMap((result) => result.status === "fulfilled" ? result.value.map((item) => ({ ...item, sourceName: item.name })) : []),
    ...officialCatalogRows,
  ].map((item) => [item.id, item])).values());
  const missingNameIds = unnamedCatalog.filter((item) => !("sourceName" in item)).map((item) => item.id);
  const fetchedNames = await fetchNames(missingNameIds);
  const catalog = unnamedCatalog.flatMap((item) => {
    const name = "sourceName" in item ? item.sourceName : fetchedNames.get(item.id);
    if (!name) return [];
    const category = classify(name, item.category === "accessory" ? "accessory" : "functional");
    return category ? [{ id: item.id, name, category }] : [];
  });
  if (catalog.length === 0) throw new Error("No usable catalog responses; existing snapshot was not touched");
  for (const requiredCategory of ["accessory", "silver", "manos"]) {
    if (!catalog.some((item) => item.category === requiredCategory)) {
      throw new Error(`Missing ${requiredCategory} catalog; existing snapshot was not touched`);
    }
  }

  const pairs = catalog.flatMap((item) => [0, ...targetLevels.map((level) => sidFor(item.category, level))].map((sid) => ({ id: item.id, sid })));
  pairs.push(...materials.map(({ id }) => ({ id, sid: 0 })));
  const books = new Map();
  try {
    const probeChunk = pairs.slice(0, 4);
    const probeBooks = await normalizeBooks(await fetchJson(orderUrl(probeChunk), 2, 6_000));
    probeBooks.forEach((book) => books.set(`${book.id}:${book.sid}`, book));

    const chunks = [];
    const remainingPairs = pairs.filter(({ id, sid }) => !books.has(`${id}:${sid}`));
    for (let index = 0; index < remainingPairs.length; index += 8) chunks.push(remainingPairs.slice(index, index + 8));
    const chunkResults = await mapLimit(chunks, 2, async (chunk) => {
      if (Date.now() >= snapshotDeadlineAt) return [];
      try {
        return await fetchChunkResilient(chunk);
      } finally {
        await wait(250);
      }
    });
    chunkResults.forEach((result) => {
      if (result.status === "fulfilled") result.value.forEach((book) => books.set(`${book.id}:${book.sid}`, book));
    });
  } catch {
    process.stdout.write("Arsha order books unavailable; switching to build-time official fallback\n");
  }
  process.stdout.write(`Order-book Arsha: ${books.size}/${pairs.length}\n`);

  const missingPairs = pairs.filter(({ id, sid }) => !books.has(`${id}:${sid}`));
  const officialResults = await mapLimit(missingPairs, 3, async (pair) => {
    if (Date.now() >= snapshotDeadlineAt) return null;
    try {
      return parseOfficialBook(await fetchOfficial("GetBiddingInfoList", {
        mainKey: pair.id,
        subKey: pair.sid,
      }), pair.id, pair.sid);
    } catch {
      return null;
    }
  });
  officialResults.forEach((result) => {
    if (result.status === "fulfilled" && result.value) books.set(`${result.value.id}:${result.value.sid}`, result.value);
  });
  process.stdout.write(`Order-book official fallback: ${books.size}/${pairs.length}\n`);
  const coverage = books.size / pairs.length;
  if (coverage < 0.7) throw new Error(`Only ${(coverage * 100).toFixed(1)}% order-book coverage; existing snapshot was not touched`);

  const fetchedAt = new Date().toISOString();
  const items = catalog.map((item) => ({
    ...item,
    levels: Object.fromEntries([0, ...targetLevels].map((level) => {
      const sid = sidFor(item.category, level);
      const book = books.get(`${item.id}:${sid}`);
      return [String(level), book ? quoteFromBook(book, fetchedAt) : missingQuote(fetchedAt)];
    })),
  }));
  const materialQuotes = Object.fromEntries(materials.map((material) => {
    const book = books.get(`${material.id}:0`);
    return [material.key, { ...material, ...(book ? quoteFromBook(book, fetchedAt) : missingQuote(fetchedAt)) }];
  }));
  const snapshot = {
    schemaVersion: 1,
    region,
    fetchedAt,
    source: "Scheduled order-book snapshot (Arsha / Pearl Abyss build fallback)",
    items,
    materials: materialQuotes,
  };
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(snapshot, null, 2)}\n`, "utf8");
  process.stdout.write(`Wrote ${items.length} items and ${books.size}/${pairs.length} order books to ${outputPath}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack : String(error)}\n`);
  process.exitCode = 1;
});
