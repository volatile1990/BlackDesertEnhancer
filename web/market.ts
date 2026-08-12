import {
  API_BASE,
  CACHE_MAX_STALE_MS,
  CACHE_TTL_MS,
  CATALOG_SEEDS,
  CATEGORY_ENDPOINTS,
  MATERIALS,
  TARGET_LEVELS,
  cacheKey,
  classifyItem,
  marketSid,
} from "./config";
import type {
  Category,
  MarketItem,
  MarketQuote,
  MarketSnapshot,
  MaterialKey,
  MaterialQuote,
  PriceState,
  Region,
} from "./types";

interface CatalogRow {
  id: number;
  name: string;
  basePrice?: number;
}

export interface OrderRow {
  price: number;
  sellers: number;
  buyers?: number;
}

export interface OrderBook {
  id: number;
  sid: number;
  name?: string;
  orders: OrderRow[];
}

export interface MarketLoadResult {
  snapshot: MarketSnapshot;
  status: "fresh" | "partial" | "cached" | "snapshot";
  warnings: string[];
}

interface FetchOptions {
  attempts?: number;
  timeoutMs?: number;
  deadlineAt?: number;
  fetcher?: typeof fetch;
}

const RETRYABLE_STATUS = new Set([408, 425, 429, 500, 502, 503, 504]);
const REQUEST_CONCURRENCY = 3;
const MAX_RESPONSE_BYTES = 2_000_000;
const TOTAL_REFRESH_MS = 45_000;
let consecutiveFailures = 0;
let circuitOpenUntil = 0;

class NonRetryableFetchError extends Error {}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function safeInteger(value: unknown, field: string): number {
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value < 0) {
    throw new Error(`Ungültiges Feld ${field}`);
  }
  return value;
}

function validateCatalog(value: unknown): CatalogRow[] {
  if (!Array.isArray(value) || value.length > 500) throw new Error("Ungültiger Marktkatalog");
  return value.map((entry) => {
    if (!isRecord(entry) || typeof entry.name !== "string" || entry.name.length > 160) {
      throw new Error("Ungültiger Katalogeintrag");
    }
    return {
      id: safeInteger(entry.id, "id"),
      name: entry.name,
      basePrice: typeof entry.basePrice === "number" ? safeInteger(entry.basePrice, "basePrice") : undefined,
    };
  });
}

function validateQuote(value: unknown): MarketQuote {
  if (!isRecord(value) || (value.price !== null && typeof value.price !== "number")) {
    throw new Error("Ungültige Marktquote");
  }
  const states = new Set<PriceState>(["fresh", "snapshot", "cached", "unlisted", "error"]);
  const kinds = new Set(["listing", "preorder", "unavailable"]);
  if (typeof value.state !== "string" || !states.has(value.state as PriceState)) throw new Error("Ungültiger Preisstatus");
  if (typeof value.kind !== "string" || !kinds.has(value.kind)) throw new Error("Ungültige Preisart");
  if (typeof value.fetchedAt !== "string" || !Number.isFinite(Date.parse(value.fetchedAt))) throw new Error("Ungültiger Abrufzeitpunkt");
  if (typeof value.source !== "string" || value.source.length > 180) throw new Error("Ungültige Preisquelle");
  const price = value.price === null ? null : safeInteger(value.price, "price");
  const sellersAtLowest = safeInteger(value.sellersAtLowest, "sellersAtLowest");
  const totalSellers = safeInteger(value.totalSellers, "totalSellers");
  const buyersAtPrice = safeInteger(value.buyersAtPrice, "buyersAtPrice");
  const totalBuyers = safeInteger(value.totalBuyers, "totalBuyers");
  if (sellersAtLowest > totalSellers) throw new Error("Ungültige Verkäuferzahlen");
  if (buyersAtPrice > totalBuyers) throw new Error("Ungültige Vorbestellungszahlen");
  if (price === null && !["unlisted", "error"].includes(value.state as string)) throw new Error("Preisloser Eintrag ohne Fehlerstatus");
  if (price !== null && ["unlisted", "error"].includes(value.state as string)) throw new Error("Preis trotz Fehlerstatus");
  if (price === null && value.kind !== "unavailable") throw new Error("Preisloser Eintrag mit falscher Preisart");
  if (price !== null && value.kind === "unavailable") throw new Error("Verfügbarer Preis mit falscher Preisart");
  if (value.kind === "listing" && sellersAtLowest === 0) throw new Error("Listing ohne Verkäufer");
  if (value.kind === "preorder" && (sellersAtLowest !== 0 || totalSellers !== 0)) throw new Error("Preorder mit Verkäufern");
  return {
    price,
    sellersAtLowest,
    totalSellers,
    buyersAtPrice,
    totalBuyers,
    kind: value.kind as MarketQuote["kind"],
    state: value.state as PriceState,
    fetchedAt: value.fetchedAt,
    source: value.source,
  };
}

export function validateMarketSnapshot(value: unknown, expectedRegion: Region): MarketSnapshot {
  if (!isRecord(value) || value.schemaVersion !== 2 || value.region !== expectedRegion) {
    throw new Error("Ungültige Snapshot-Version oder Region");
  }
  if (typeof value.fetchedAt !== "string" || !Number.isFinite(Date.parse(value.fetchedAt))) throw new Error("Ungültiger Snapshot-Zeitpunkt");
  if (typeof value.source !== "string" || value.source.length > 180) throw new Error("Ungültige Snapshot-Quelle");
  if (!Array.isArray(value.items) || value.items.length > 500) throw new Error("Ungültige Snapshot-Itemliste");

  const seenIds = new Set<number>();
  const items = value.items.map((entry): MarketItem => {
    if (!isRecord(entry) || typeof entry.name !== "string" || entry.name.length > 160 || !isRecord(entry.levels)) {
      throw new Error("Ungültiges Snapshot-Item");
    }
    const levelRecord = entry.levels;
    const id = safeInteger(entry.id, "id");
    if (seenIds.has(id)) throw new Error("Doppelte Snapshot-ID");
    seenIds.add(id);
    if (!["accessory", "silver", "manos"].includes(entry.category as string)) throw new Error("Ungültige Snapshot-Kategorie");
    const levels = Object.fromEntries(
      ["0", "2", "3", "4"].map((level) => {
        if (!(level in levelRecord)) throw new Error("Fehlende Snapshot-Preisstufe");
        const quote = validateQuote(levelRecord[level]);
        if (level !== "0" && quote.kind === "preorder") throw new Error("Preorder ist nur für BASE zulässig");
        return [level, quote];
      }),
    );
    return { id, name: entry.name, category: entry.category as Category, levels };
  });

  if (!isRecord(value.materials)) throw new Error("Ungültige Snapshot-Materialien");
  const materialRecord = value.materials;
  const materialEntries = MATERIALS.map((material): [MaterialKey, MaterialQuote] => {
    const entry = materialRecord[material.key];
    if (!isRecord(entry) || entry.key !== material.key || entry.label !== material.label) throw new Error("Ungültiges Snapshot-Material");
    if (safeInteger(entry.id, "material.id") !== material.id) throw new Error("Falsche Snapshot-Material-ID");
    const quote = validateQuote(entry);
    if (quote.kind === "preorder") throw new Error("Material darf keinen Preorder-Preis verwenden");
    return [material.key, { ...material, ...quote }];
  });

  return {
    schemaVersion: 2,
    region: expectedRegion,
    fetchedAt: value.fetchedAt,
    source: value.source,
    items,
    materials: Object.fromEntries(materialEntries) as Record<MaterialKey, MaterialQuote>,
  };
}

export function validateOrderBooks(value: unknown): OrderBook[] {
  const books = Array.isArray(value) ? value : [value];
  if (books.length > 100) throw new Error("Zu viele Orderbücher in einer Antwort");

  return books.map((entry) => {
    if (!isRecord(entry) || !Array.isArray(entry.orders) || entry.orders.length > 500) {
      throw new Error("Ungültiges Orderbuch");
    }
    return {
      id: safeInteger(entry.id, "id"),
      sid: safeInteger(entry.sid, "sid"),
      name: typeof entry.name === "string" ? entry.name : undefined,
      orders: entry.orders.map((order) => {
        if (!isRecord(order)) throw new Error("Ungültige Preisstufe");
        return {
          price: safeInteger(order.price, "price"),
          sellers: safeInteger(order.sellers, "sellers"),
          buyers: order.buyers === undefined ? undefined : safeInteger(order.buyers, "buyers"),
        };
      }),
    };
  });
}

export function lowestListedPrice(orders: OrderRow[]): {
  price: number | null;
  sellersAtLowest: number;
  totalSellers: number;
} {
  const asks = orders.filter((order) => order.sellers > 0);
  const totalSellers = asks.reduce((sum, order) => sum + order.sellers, 0);
  if (asks.length === 0) return { price: null, sellersAtLowest: 0, totalSellers: 0 };
  const lowest = asks.reduce((best, order) => (order.price < best.price ? order : best));
  return { price: lowest.price, sellersAtLowest: lowest.sellers, totalSellers };
}

export function highestPreorderPrice(orders: OrderRow[]): {
  price: number | null;
  buyersAtPrice: number;
  totalBuyers: number;
} {
  const totalBuyers = orders.reduce((sum, order) => sum + (order.buyers ?? 0), 0);
  if (orders.length === 0) return { price: null, buyersAtPrice: 0, totalBuyers };
  const highestPrice = Math.max(...orders.map((order) => order.price));
  const buyersAtPrice = orders
    .filter((order) => order.price === highestPrice)
    .reduce((sum, order) => sum + (order.buyers ?? 0), 0);
  return { price: highestPrice, buyersAtPrice, totalBuyers };
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => globalThis.setTimeout(resolve, ms));
}

export async function fetchJsonWithRetry<T>(url: string, options: FetchOptions = {}): Promise<T> {
  if (Date.now() < circuitOpenUntil) throw new Error("Markt-API ist vorübergehend pausiert");

  const attempts = options.attempts ?? 3;
  const timeoutMs = options.timeoutMs ?? 9_000;
  const fetcher = options.fetcher ?? fetch;
  let lastError: unknown;

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const remaining = options.deadlineAt === undefined ? timeoutMs : options.deadlineAt - Date.now();
    if (remaining <= 0) {
      lastError = new Error("Gesamtzeit für Markt-Aktualisierung überschritten");
      break;
    }
    const controller = new AbortController();
    const timeout = globalThis.setTimeout(() => controller.abort(), Math.min(timeoutMs, remaining));
    try {
      const response = await fetcher(url, {
        method: "GET",
        mode: "cors",
        cache: "no-store",
        headers: { Accept: "application/json" },
        signal: controller.signal,
      });
      if (!response.ok) {
        const error = new Error(`Markt-API: HTTP ${response.status}`);
        if (!RETRYABLE_STATUS.has(response.status)) throw new NonRetryableFetchError(error.message);
        if (attempt === attempts - 1) throw error;
        const retryAfter = Number(response.headers.get("retry-after"));
        const retryDelay = Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter * 1_000 : 0;
        const delay = Math.max(retryDelay, 350 * 2 ** attempt + Math.random() * 300);
        const deadlineDelay = options.deadlineAt === undefined ? delay : Math.max(0, options.deadlineAt - Date.now());
        if (deadlineDelay <= 0) throw new Error("Gesamtzeit für Markt-Aktualisierung überschritten");
        await wait(Math.min(delay, deadlineDelay));
        continue;
      }

      const contentType = response.headers.get("content-type") ?? "";
      if (!contentType.includes("json")) throw new Error("Markt-API lieferte kein JSON");
      const announcedLength = Number(response.headers.get("content-length"));
      if (Number.isFinite(announcedLength) && announcedLength > MAX_RESPONSE_BYTES) {
        throw new NonRetryableFetchError("Markt-Antwort ist unerwartet groß");
      }
      const body = await response.text();
      if (body.length > MAX_RESPONSE_BYTES) throw new NonRetryableFetchError("Markt-Antwort ist unerwartet groß");
      const parsed = JSON.parse(body) as T;
      consecutiveFailures = 0;
      return parsed;
    } catch (error) {
      lastError = error;
      if (error instanceof NonRetryableFetchError || attempt === attempts - 1) break;
      const delay = 350 * 2 ** attempt + Math.random() * 300;
      const deadlineDelay = options.deadlineAt === undefined ? delay : Math.max(0, options.deadlineAt - Date.now());
      if (deadlineDelay <= 0) break;
      await wait(Math.min(delay, deadlineDelay));
    } finally {
      globalThis.clearTimeout(timeout);
    }
  }

  consecutiveFailures += 1;
  if (consecutiveFailures >= 3) circuitOpenUntil = Date.now() + 20_000;
  throw lastError instanceof Error ? lastError : new Error("Markt-API nicht erreichbar");
}

async function mapLimit<T, R>(
  values: T[],
  limit: number,
  task: (value: T) => Promise<R>,
): Promise<PromiseSettledResult<R>[]> {
  const results: PromiseSettledResult<R>[] = new Array(values.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, values.length) }, async () => {
    while (cursor < values.length) {
      const index = cursor;
      cursor += 1;
      try {
        results[index] = { status: "fulfilled", value: await task(values[index]!) };
      } catch (reason) {
        results[index] = { status: "rejected", reason };
      }
    }
  });
  await Promise.all(workers);
  return results;
}

function buildOrderUrl(region: Region, pairs: ReadonlyArray<{ id: number; sid: number }>): string {
  const params = new URLSearchParams();
  for (const pair of pairs) {
    params.append("id", String(pair.id));
    params.append("sid", String(pair.sid));
  }
  return `${API_BASE}/${region}/GetBiddingInfoList?${params.toString()}`;
}

export function quoteFromBook(
  book: OrderBook,
  fetchedAt: string,
  state: PriceState = "fresh",
  allowPreorder = false,
): MarketQuote {
  const listing = lowestListedPrice(book.orders);
  const totalBuyers = book.orders.reduce((sum, order) => sum + (order.buyers ?? 0), 0);
  if (listing.price !== null) {
    const buyersAtPrice = book.orders
      .filter((order) => order.price === listing.price)
      .reduce((sum, order) => sum + (order.buyers ?? 0), 0);
    return {
      ...listing,
      buyersAtPrice,
      totalBuyers,
      kind: "listing",
      state,
      fetchedAt,
      source: "Arsha order book",
    };
  }

  const preorder = allowPreorder ? highestPreorderPrice(book.orders) : null;
  if (preorder && preorder.price !== null) {
    return {
      price: preorder.price,
      sellersAtLowest: 0,
      totalSellers: 0,
      buyersAtPrice: preorder.buyersAtPrice,
      totalBuyers: preorder.totalBuyers,
      kind: "preorder",
      state,
      fetchedAt,
      source: "Arsha order book · höchste zulässige Preorder-Preisstufe",
    };
  }

  return {
    ...listing,
    buyersAtPrice: 0,
    totalBuyers,
    kind: "unavailable",
    state: "unlisted",
    fetchedAt,
    source: "Arsha order book",
  };
}

function unavailableQuote(fetchedAt: string): MarketQuote {
  return {
    price: null,
    sellersAtLowest: 0,
    totalSellers: 0,
    buyersAtPrice: 0,
    totalBuyers: 0,
    kind: "unavailable",
    state: "error",
    fetchedAt,
    source: "Arsha order book",
  };
}

function quoteAsFallback<T extends MarketQuote>(quote: T, state: "cached" | "snapshot"): T {
  return { ...quote, state: quote.price === null ? quote.state : state };
}

function cloneWithState(snapshot: MarketSnapshot, state: "cached" | "snapshot"): MarketSnapshot {
  return {
    ...snapshot,
    source: state === "cached" ? "Lokaler Last-known-good-Cache" : "Gebündelter GitHub-Snapshot",
    items: snapshot.items.map((item) => ({
      ...item,
      levels: Object.fromEntries(
        Object.entries(item.levels).map(([level, quote]) => [level, quoteAsFallback(quote, state)]),
      ),
    })),
    materials: Object.fromEntries(
      Object.entries(snapshot.materials).map(([key, quote]) => [key, quoteAsFallback(quote, state)]),
    ) as Record<MaterialKey, MaterialQuote>,
  };
}

function readCache(region: Region): MarketSnapshot | null {
  try {
    if (!("localStorage" in globalThis)) return null;
    const raw = globalThis.localStorage.getItem(cacheKey(region));
    if (!raw) return null;
    const parsed = validateMarketSnapshot(JSON.parse(raw), region);
    if (Date.now() - Date.parse(parsed.fetchedAt) > CACHE_MAX_STALE_MS) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeCache(snapshot: MarketSnapshot): void {
  try {
    if ("localStorage" in globalThis) {
      globalThis.localStorage.setItem(cacheKey(snapshot.region), JSON.stringify(snapshot));
    }
  } catch {
    // Storage quotas and private browsing must not break calculations.
  }
}

async function readBundledSnapshot(): Promise<MarketSnapshot> {
  const response = await fetch(`${import.meta.env.BASE_URL}data/market-eu.json`, { cache: "no-store" });
  if (!response.ok) throw new Error("Gebündelter Snapshot fehlt");
  return validateMarketSnapshot(await response.json(), "eu");
}

async function fetchCatalog(region: Region, deadlineAt: number): Promise<{
  rows: Array<{ id: number; name: string; category: Category }>;
  failedCategories: number;
}> {
  const responses = await Promise.allSettled(
    CATEGORY_ENDPOINTS.map(async (endpoint) => {
      const url = `${API_BASE}/${region}/GetWorldMarketList?mainCategory=${endpoint.main}&subCategory=${endpoint.sub}`;
      const payload = await fetchJsonWithRetry<unknown>(url, { deadlineAt });
      return validateCatalog(payload)
        .map((row) => ({ ...row, category: classifyItem(row.name, endpoint.category) }))
        .filter((row): row is CatalogRow & { category: Category } => row.category !== null);
    }),
  );

  const rows = responses.flatMap((result) => (result.status === "fulfilled" ? result.value : []));
  const fallbackRows = CATALOG_SEEDS.filter((seed) => !rows.some((row) => row.id === seed.id));
  rows.push(...fallbackRows);
  if (rows.length === 0) throw new Error("Kein Marktkatalog verfügbar");
  return {
    rows: Array.from(new Map(rows.map((row) => [row.id, row])).values()),
    failedCategories: responses.filter((result) => result.status === "rejected").length,
  };
}

async function fetchOrderBooks(
  region: Region,
  pairs: Array<{ id: number; sid: number }>,
  deadlineAt: number,
): Promise<{ books: Map<string, OrderBook>; failedKeys: Set<string> }> {
  const chunks: Array<Array<{ id: number; sid: number }>> = [];
  for (let index = 0; index < pairs.length; index += 8) chunks.push(pairs.slice(index, index + 8));

  const fetchChunk = async (chunk: Array<{ id: number; sid: number }>): Promise<{
    books: OrderBook[];
    failed: Array<{ id: number; sid: number }>;
  }> => {
    try {
      const payload = await fetchJsonWithRetry<unknown>(buildOrderUrl(region, chunk), {
        attempts: 2,
        timeoutMs: 7_000,
        deadlineAt,
      });
      const chunkBooks = validateOrderBooks(payload);
      const expected = new Set(chunk.map(({ id, sid }) => `${id}:${sid}`));
      for (const book of chunkBooks) {
        if (!expected.has(`${book.id}:${book.sid}`)) throw new Error("Orderbuch-ID stimmt nicht mit Anfrage überein");
      }
      const returned = new Set(chunkBooks.map((book) => `${book.id}:${book.sid}`));
      return {
        books: chunkBooks,
        failed: chunk.filter(({ id, sid }) => !returned.has(`${id}:${sid}`)),
      };
    } catch {
      if (chunk.length === 1 || Date.now() >= deadlineAt) return { books: [], failed: chunk };
      const midpoint = Math.ceil(chunk.length / 2);
      const left = await fetchChunk(chunk.slice(0, midpoint));
      const right = await fetchChunk(chunk.slice(midpoint));
      return { books: [...left.books, ...right.books], failed: [...left.failed, ...right.failed] };
    }
  };

  const settled = await mapLimit(chunks, REQUEST_CONCURRENCY, fetchChunk);

  const books = new Map<string, OrderBook>();
  const failedKeys = new Set<string>();
  for (let index = 0; index < settled.length; index += 1) {
    const result = settled[index]!;
    if (result.status === "fulfilled") {
      result.value.books.forEach((book) => books.set(`${book.id}:${book.sid}`, book));
      result.value.failed.forEach(({ id, sid }) => failedKeys.add(`${id}:${sid}`));
    } else {
      chunks[index]!.forEach(({ id, sid }) => failedKeys.add(`${id}:${sid}`));
    }
  }
  return { books, failedKeys };
}

function fallbackQuote(
  fallback: MarketSnapshot | null,
  fallbackState: "cached" | "snapshot",
  id: number,
  resultLevel: number,
  fetchedAt: string,
): MarketQuote {
  const old = fallback?.items.find((item) => item.id === id)?.levels[String(resultLevel)];
  if (!old) return unavailableQuote(fetchedAt);
  return quoteAsFallback(old, fallbackState);
}

async function fetchFreshSnapshot(
  region: Region,
  fallback: MarketSnapshot | null,
  fallbackState: "cached" | "snapshot",
): Promise<MarketLoadResult> {
  const fetchedAt = new Date().toISOString();
  const deadlineAt = Date.now() + TOTAL_REFRESH_MS;
  const catalogResult = await fetchCatalog(region, deadlineAt);
  const pairs = catalogResult.rows.flatMap((item) =>
    [0, ...TARGET_LEVELS.map((level) => marketSid(item.category, level))].map((sid) => ({ id: item.id, sid })),
  );
  pairs.push(...MATERIALS.map((material) => ({ id: material.id, sid: 0 })));

  const { books, failedKeys } = await fetchOrderBooks(region, pairs, deadlineAt);
  const items: MarketItem[] = catalogResult.rows.map((item) => ({
    id: item.id,
    name: item.name,
    category: item.category,
    levels: Object.fromEntries(
      [0, ...TARGET_LEVELS].map((level) => {
        const sid = marketSid(item.category, level);
        const book = books.get(`${item.id}:${sid}`);
        return [String(level), book ? quoteFromBook(book, fetchedAt, "fresh", level === 0) : fallbackQuote(fallback, fallbackState, item.id, level, fetchedAt)];
      }),
    ),
  }));

  if (catalogResult.failedCategories > 0 && fallback) {
    const freshIds = new Set(items.map((item) => item.id));
    items.push(
      ...fallback.items
        .filter((item) => !freshIds.has(item.id))
        .map((item) => ({
          ...item,
          levels: Object.fromEntries(
            Object.entries(item.levels).map(([level, quote]) => [level, quoteAsFallback(quote, fallbackState)]),
          ),
        })),
    );
  }

  const materials = Object.fromEntries(
    MATERIALS.map((material) => {
      const book = books.get(`${material.id}:0`);
      const old = fallback?.materials[material.key];
      const quote = book
        ? quoteFromBook(book, fetchedAt)
        : old
          ? quoteAsFallback(old, fallbackState)
          : unavailableQuote(fetchedAt);
      return [material.key, { ...material, ...quote }];
    }),
  ) as Record<MaterialKey, MaterialQuote>;

  const snapshot: MarketSnapshot = {
    schemaVersion: 2,
    region,
    fetchedAt,
    source: "Arsha order books",
    items,
    materials,
  };
  const warnings: string[] = [];
  if (catalogResult.failedCategories > 0) {
    warnings.push(`${catalogResult.failedCategories} Marktkategorien waren nicht erreichbar.`);
  }
  if (failedKeys.size > 0) warnings.push(`${failedKeys.size} Orderbücher waren nach mehreren Versuchen nicht erreichbar.`);

  const usableQuotes = items.reduce(
    (count, item) => count + Object.values(item.levels).filter((entry) => entry.state !== "error").length,
    0,
  );
  if (usableQuotes === 0) throw new Error("Keine gültigen Orderbücher verfügbar");
  writeCache(snapshot);
  return { snapshot, status: warnings.length ? "partial" : "fresh", warnings };
}

export async function loadMarket(region: Region, force = false): Promise<MarketLoadResult> {
  const cached = readCache(region);
  if (!force && cached && Date.now() - Date.parse(cached.fetchedAt) < CACHE_TTL_MS) {
    return { snapshot: cloneWithState(cached, "cached"), status: "cached", warnings: [] };
  }

  let bundled: MarketSnapshot | null = null;
  if (!cached && region === "eu") {
    try {
      bundled = await readBundledSnapshot();
    } catch {
      bundled = null;
    }
  }
  const fallback = cached ?? bundled;

  try {
    return await fetchFreshSnapshot(region, fallback, cached ? "cached" : "snapshot");
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unbekannter API-Fehler";
    if (fallback) {
      const kind = cached ? "cached" : "snapshot";
      return {
        snapshot: cloneWithState(fallback, kind),
        status: kind,
        warnings: [`Live-Abruf fehlgeschlagen (${message}). Letzte gültige Daten werden angezeigt.`],
      };
    }
    throw error;
  }
}
