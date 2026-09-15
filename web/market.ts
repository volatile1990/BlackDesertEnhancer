import {
  API_BASE,
  CACHE_MAX_STALE_MS,
  CACHE_TTL_MS,
  MANOS_ITEMS,
  MATERIALS,
  TARGET_LEVELS,
  cacheKey,
  marketSid,
} from "./config";
import type {
  MarketItem,
  MarketQuote,
  MarketSnapshot,
  MaterialKey,
  MaterialQuote,
  PriceState,
  Region,
} from "./types";

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
  refreshRecommended: boolean;
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
const TOTAL_REFRESH_MS = 35_000;
const MAX_FUTURE_CLOCK_SKEW_MS = 5 * 60 * 1_000;

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

function safePositiveInteger(value: unknown, field: string): number {
  const result = safeInteger(value, field);
  if (result === 0) throw new Error(`Ungültiges Feld ${field}`);
  return result;
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
  if (typeof value.source !== "string" || value.source.length > 240) throw new Error("Ungültige Preisquelle");
  const price = value.price === null ? null : safePositiveInteger(value.price, "price");
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
  if (!isRecord(value) || value.schemaVersion !== 3 || value.region !== expectedRegion) {
    throw new Error("Ungültige Snapshot-Version oder Region");
  }
  if (typeof value.fetchedAt !== "string" || !Number.isFinite(Date.parse(value.fetchedAt))) throw new Error("Ungültiger Snapshot-Zeitpunkt");
  if (typeof value.source !== "string" || value.source.length > 240) throw new Error("Ungültige Snapshot-Quelle");
  if (!Array.isArray(value.items) || value.items.length !== MANOS_ITEMS.length) throw new Error("Ungültige Manos-Itemliste");

  const expectedItems = new Map(MANOS_ITEMS.map((item) => [item.id, item]));
  const seenIds = new Set<number>();
  const items = value.items.map((entry): MarketItem => {
    if (!isRecord(entry) || typeof entry.name !== "string" || !isRecord(entry.levels)) {
      throw new Error("Ungültiges Snapshot-Item");
    }
    const levelRecord = entry.levels;
    const id = safeInteger(entry.id, "id");
    const definition = expectedItems.get(id);
    if (!definition || definition.name !== entry.name || seenIds.has(id)) throw new Error("Unbekanntes oder doppeltes Manos-Item");
    seenIds.add(id);
    const levels = Object.fromEntries(
      [0, ...TARGET_LEVELS].map((level) => {
        const key = String(level);
        if (!(key in levelRecord)) throw new Error("Fehlende Snapshot-Preisstufe");
        const quote = validateQuote(levelRecord[key]);
        if (level !== 0 && quote.kind === "preorder") throw new Error("Preorder ist nur für BASE zulässig");
        return [key, quote];
      }),
    );
    return { id, name: entry.name, levels };
  });

  if (!isRecord(value.materials) || Object.keys(value.materials).length !== MATERIALS.length) {
    throw new Error("Ungültige Snapshot-Materialien");
  }
  const materialRecord = value.materials;
  const materialEntries = MATERIALS.map((material): [MaterialKey, MaterialQuote] => {
    const entry = materialRecord[material.key];
    if (!isRecord(entry) || entry.key !== material.key || entry.label !== material.label) throw new Error("Ungültiges Snapshot-Material");
    if (safeInteger(entry.id, "material.id") !== material.id) throw new Error("Falsche Snapshot-Material-ID");
    const quote = validateQuote(entry);
    if (quote.kind === "preorder") throw new Error("Material darf keinen Preorder-Preis verwenden");
    return [material.key, { id: material.id, key: material.key, label: material.label, ...quote }];
  });

  return {
    schemaVersion: 3,
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
          price: safePositiveInteger(order.price, "price"),
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

export function isQuoteWithinMaxAge(quote: MarketQuote, now = Date.now()): boolean {
  const timestamp = Date.parse(quote.fetchedAt);
  if (!Number.isFinite(timestamp)) return false;
  const age = now - timestamp;
  return age >= -MAX_FUTURE_CLOCK_SKEW_MS && age <= CACHE_MAX_STALE_MS;
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => globalThis.setTimeout(resolve, ms));
}

export async function fetchJsonWithRetry<T>(url: string, options: FetchOptions = {}): Promise<T> {
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
        const availableDelay = options.deadlineAt === undefined ? delay : Math.max(0, options.deadlineAt - Date.now());
        if (availableDelay <= 0) throw new Error("Gesamtzeit für Markt-Aktualisierung überschritten");
        await wait(Math.min(delay, availableDelay));
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
      return parsed;
    } catch (error) {
      lastError = error;
      if (error instanceof NonRetryableFetchError || attempt === attempts - 1) break;
      const delay = 350 * 2 ** attempt + Math.random() * 300;
      const availableDelay = options.deadlineAt === undefined ? delay : Math.max(0, options.deadlineAt - Date.now());
      if (availableDelay <= 0) break;
      await wait(Math.min(delay, availableDelay));
    } finally {
      globalThis.clearTimeout(timeout);
    }
  }

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

function unavailableQuote(fetchedAt: string, source = "Arsha order book"): MarketQuote {
  return {
    price: null,
    sellersAtLowest: 0,
    totalSellers: 0,
    buyersAtPrice: 0,
    totalBuyers: 0,
    kind: "unavailable",
    state: "error",
    fetchedAt,
    source,
  };
}

function quoteAsFallback<T extends MarketQuote>(quote: T, state: "cached" | "snapshot"): T {
  return { ...quote, state: quote.price === null ? quote.state : state };
}

interface FallbackCandidate {
  snapshot: MarketSnapshot;
  state: "cached" | "snapshot";
}

function newestFallbackQuote(
  candidates: FallbackCandidate[],
  getQuote: (snapshot: MarketSnapshot) => MarketQuote | undefined,
  failedAt: string,
): MarketQuote {
  const current = candidates
    .flatMap((candidate) => {
      const quote = getQuote(candidate.snapshot);
      return quote && quote.state !== "error" && isQuoteWithinMaxAge(quote)
        ? [{ quote, state: candidate.state }]
        : [];
    })
    .sort((left, right) => Date.parse(right.quote.fetchedAt) - Date.parse(left.quote.fetchedAt))[0];
  return current
    ? quoteAsFallback(current.quote, current.state)
    : unavailableQuote(failedAt, "Kein Fallback-Orderbuch unter 24 Stunden");
}

export function mergeFallbacks(candidates: FallbackCandidate[], region: Region): {
  snapshot: MarketSnapshot;
  state: "cached" | "snapshot";
  missingQuotes: number;
} | null {
  if (candidates.length === 0) return null;
  const newestCandidate = [...candidates]
    .sort((left, right) => Date.parse(right.snapshot.fetchedAt) - Date.parse(left.snapshot.fetchedAt))[0]!;
  const failedAt = newestCandidate.snapshot.fetchedAt;
  const items: MarketItem[] = MANOS_ITEMS.map((item) => ({
    id: item.id,
    name: item.name,
    levels: Object.fromEntries([0, ...TARGET_LEVELS].map((level) => [
      String(level),
      newestFallbackQuote(
        candidates,
        (snapshot) => snapshot.items.find((candidate) => candidate.id === item.id)?.levels[String(level)],
        failedAt,
      ),
    ])),
  }));
  const materials = Object.fromEntries(MATERIALS.map((material) => [
    material.key,
    {
      id: material.id,
      key: material.key,
      label: material.label,
      ...newestFallbackQuote(candidates, (snapshot) => snapshot.materials[material.key], failedAt),
    },
  ])) as Record<MaterialKey, MaterialQuote>;
  const missingQuotes = [
    ...items.flatMap((item) => Object.values(item.levels)),
    ...Object.values(materials),
  ].filter((quote) => quote.state === "error").length;
  return {
    snapshot: {
      schemaVersion: 3,
      region,
      fetchedAt: newestCandidate.snapshot.fetchedAt,
      source: candidates.length > 1 ? "Lokaler Cache und GitHub-Snapshot" : newestCandidate.snapshot.source,
      items,
      materials,
    },
    state: newestCandidate.state,
    missingQuotes,
  };
}

function readCache(region: Region): MarketSnapshot | null {
  try {
    if (!("localStorage" in globalThis)) return null;
    const raw = globalThis.localStorage.getItem(cacheKey(region));
    if (!raw) return null;
    const parsed = validateMarketSnapshot(JSON.parse(raw), region);
    const age = Date.now() - Date.parse(parsed.fetchedAt);
    if (age < -MAX_FUTURE_CLOCK_SKEW_MS || age > CACHE_MAX_STALE_MS) return null;
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
      const returned = new Set<string>();
      for (const book of chunkBooks) {
        const key = `${book.id}:${book.sid}`;
        if (!expected.has(key)) throw new Error("Orderbuch-ID stimmt nicht mit Anfrage überein");
        if (returned.has(key)) throw new Error("Doppeltes Orderbuch in Markt-Antwort");
        returned.add(key);
      }
      if (returned.size !== chunk.length) throw new Error("Markt-Antwort enthält nicht alle angefragten Orderbücher");
      return {
        books: chunkBooks,
        failed: [],
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
  fallbacks: FallbackCandidate[],
  id: number,
  resultLevel: number,
  fetchedAt: string,
): MarketQuote {
  return newestFallbackQuote(
    fallbacks,
    (snapshot) => snapshot.items.find((item) => item.id === id)?.levels[String(resultLevel)],
    fetchedAt,
  );
}

async function fetchFreshSnapshot(
  region: Region,
  fallbacks: FallbackCandidate[],
): Promise<MarketLoadResult> {
  const fetchedAt = new Date().toISOString();
  const deadlineAt = Date.now() + TOTAL_REFRESH_MS;
  const pairs = MANOS_ITEMS.flatMap((item) =>
    [0, ...TARGET_LEVELS.map((level) => marketSid(level))].map((sid) => ({ id: item.id, sid })),
  );
  pairs.push(...MATERIALS.map((material) => ({ id: material.id, sid: 0 })));

  const { books, failedKeys } = await fetchOrderBooks(region, pairs, deadlineAt);
  if (books.size === 0) throw new Error("Arsha lieferte kein einziges Manos-Orderbuch");
  if (![...books.values()].some((book) => book.orders.length > 0)) {
    throw new Error("Arsha lieferte ausschließlich leere Manos-Orderbücher");
  }

  const items: MarketItem[] = MANOS_ITEMS.map((item) => ({
    id: item.id,
    name: item.name,
    levels: Object.fromEntries(
      [0, ...TARGET_LEVELS].map((level) => {
        const sid = marketSid(level);
        const book = books.get(`${item.id}:${sid}`);
        return [String(level), book
          ? quoteFromBook(book, fetchedAt, "fresh", level === 0)
          : fallbackQuote(fallbacks, item.id, level, fetchedAt)];
      }),
    ),
  }));

  const materials = Object.fromEntries(
    MATERIALS.map((material) => {
      const book = books.get(`${material.id}:0`);
      const quote = book
        ? quoteFromBook(book, fetchedAt)
        : newestFallbackQuote(fallbacks, (snapshot) => snapshot.materials[material.key], fetchedAt);
      return [material.key, { id: material.id, key: material.key, label: material.label, ...quote }];
    }),
  ) as Record<MaterialKey, MaterialQuote>;

  const warnings: string[] = [];
  if (failedKeys.size > 0) {
    warnings.push(`${failedKeys.size} von ${pairs.length} Manos-Orderbüchern waren nach mehreren Versuchen nicht erreichbar.`);
  }
  const snapshot: MarketSnapshot = {
    schemaVersion: 3,
    region,
    fetchedAt,
    source: warnings.length ? "Arsha order books (partial)" : "Arsha order books",
    items,
    materials,
  };
  writeCache(snapshot);
  return { snapshot, status: warnings.length ? "partial" : "fresh", warnings, refreshRecommended: false };
}

export async function loadMarket(region: Region, force = false): Promise<MarketLoadResult> {
  const cached = readCache(region);
  let bundled: MarketSnapshot | null = null;
  if (region === "eu") {
    try {
      bundled = await readBundledSnapshot();
    } catch {
      bundled = null;
    }
  }
  const fallbacks: FallbackCandidate[] = [
    ...(cached ? [{ snapshot: cached, state: "cached" as const }] : []),
    ...(bundled ? [{ snapshot: bundled, state: "snapshot" as const }] : []),
  ];
  const mergedFallback = mergeFallbacks(fallbacks, region);
  if (!force && mergedFallback) {
    const warnings = mergedFallback.missingQuotes > 0
      ? [`${mergedFallback.missingQuotes} Fallback-Preise fehlen oder sind älter als 24 Stunden.`]
      : [];
    const cachedAge = cached ? Date.now() - Date.parse(cached.fetchedAt) : Number.POSITIVE_INFINITY;
    return {
      snapshot: mergedFallback.snapshot,
      status: mergedFallback.state,
      warnings,
      refreshRecommended: mergedFallback.state === "snapshot" || cachedAge >= CACHE_TTL_MS,
    };
  }

  try {
    return await fetchFreshSnapshot(region, fallbacks);
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unbekannter API-Fehler";
    if (mergedFallback) {
      return {
        snapshot: mergedFallback.snapshot,
        status: mergedFallback.state,
        warnings: [
          `Live-Abruf fehlgeschlagen (${message}). Der letzte Snapshot wird verwendet; Einzelpreise über 24 Stunden werden verworfen.`,
          ...(mergedFallback.missingQuotes > 0 ? [`${mergedFallback.missingQuotes} Preise sind deshalb nicht verfügbar.`] : []),
        ],
        refreshRecommended: false,
      };
    }
    throw error;
  }
}
