import { decodeMarketHuffman } from "./huffman.mjs";

// Velia Inn documents this Pearl Abyss wire format but does not proxy or originate the prices.
export const OFFICIAL_MARKET_BASES = Object.freeze({
  eu: "https://eu-trade.naeu.playblackdesert.com",
  na: "https://na-trade.naeu.playblackdesert.com",
});

function isRecord(value) {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function unsignedInteger(text, field, allowZero = true) {
  if (typeof text !== "string" || !/^(0|[1-9]\d*)$/.test(text)) throw new Error(`Invalid ${field}`);
  const value = Number(text);
  if (!Number.isSafeInteger(value) || (!allowZero && value === 0)) throw new Error(`Invalid ${field}`);
  return value;
}

function nonNegativeSafeInteger(value, field, allowZero = true) {
  if (!Number.isSafeInteger(value) || value < 0 || (!allowZero && value === 0)) throw new Error(`Invalid ${field}`);
  return value;
}

export function parseVeliaInnJsonEnvelope(text) {
  let envelope;
  try {
    envelope = JSON.parse(text);
  } catch {
    throw new Error("Velia Inn documented market fallback returned invalid JSON");
  }
  if (!isRecord(envelope) || !Number.isSafeInteger(envelope.resultCode)) {
    throw new Error("Velia Inn documented market fallback returned an invalid envelope");
  }
  if (envelope.resultCode !== 0) {
    throw new Error(`Pearl Abyss market resultCode ${envelope.resultCode}`);
  }
  if (typeof envelope.resultMsg !== "string" || envelope.resultMsg.length > 2_000_000) {
    throw new Error("Pearl Abyss market resultMsg is invalid");
  }
  return envelope.resultMsg;
}

export function decodeVeliaInnMarketResponse(input, contentType = "") {
  const data = Buffer.isBuffer(input) ? input : Buffer.from(input);
  if (data.length === 0 || data.length > 2_000_000) throw new Error("Invalid market fallback response size");
  const normalizedType = contentType.toLowerCase();
  if (normalizedType.includes("json") || data[0] === 0x7b) {
    return parseVeliaInnJsonEnvelope(data.toString("utf8"));
  }
  if (!normalizedType.includes("octet-stream")) {
    throw new Error(`Unexpected market fallback content type: ${normalizedType || "missing"}`);
  }
  return decodeMarketHuffman(data);
}

function resultEntries(result) {
  if (typeof result !== "string" || result.length > 2_000_000) throw new Error("Invalid Pearl Abyss market result");
  const normalized = result.endsWith("|") ? result.slice(0, -1) : result;
  if (normalized === "") return [];
  const entries = normalized.split("|");
  if (entries.some((entry) => entry === "")) throw new Error("Invalid empty Pearl Abyss market row");
  return entries;
}

export function parseVeliaInnOrderBook(result, id, sid) {
  nonNegativeSafeInteger(id, "order-book item id", false);
  nonNegativeSafeInteger(sid, "order-book enhancement id");
  const orders = resultEntries(result).map((entry) => {
    const fields = entry.split("-");
    if (fields.length !== 3) throw new Error("Invalid Pearl Abyss order-book row");
    return {
      price: unsignedInteger(fields[0], "order-book price", false),
      sellers: unsignedInteger(fields[1], "order-book sellers"),
      buyers: unsignedInteger(fields[2], "order-book buyers"),
    };
  });
  return {
    id,
    sid,
    orders,
    source: "Velia Inn-documented Pearl Abyss order book (build fallback)",
  };
}
