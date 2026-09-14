import assert from "node:assert/strict";
import test from "node:test";
import {
  decodeVeliaInnMarketResponse,
  parseVeliaInnCatalog,
  parseVeliaInnJsonEnvelope,
  parseVeliaInnOrderBook,
} from "./velia-inn-market.mjs";

test("accepts successful legacy JSON envelopes and rejects API errors", () => {
  const success = '{"resultCode":0,"resultMsg":"100-1-0|"}';
  assert.equal(parseVeliaInnJsonEnvelope(success), "100-1-0|");
  assert.equal(decodeVeliaInnMarketResponse(Buffer.from(success), "application/json"), "100-1-0|");
  assert.throws(
    () => parseVeliaInnJsonEnvelope('{"resultCode":8,"resultMsg":"Invalid item"}'),
    /resultCode 8/,
  );
});

test("decodes the current octet-stream response and rejects unrelated content", () => {
  const compressed = Buffer.from(
    "swAAAAAAAAAMAAAAHgAAAC0AAABRAAAAMAAAAAsAAAAxAAAABQAAADIAAAADAAAAMwAAAAoAAAA0AAAACwAAADUAAAACAAAANgAAAAEAAAA3AAAAAQAAADgAAAAKAAAAOQAAAA8AAAB8AAAA2AEAADsAAAC0AAAAmM8PXbeAfHbMQHrtmJD123Vh83beLD322B8u26B8u2YwHrttYfHbMZB67Ziw9dsxmD12zGcHrtmKDu0=",
    "base64",
  );
  assert.match(decodeVeliaInnMarketResponse(compressed, "application/octet-stream"), /^4980000-1-0\|/);
  assert.throws(() => decodeVeliaInnMarketResponse(Buffer.from("not market data"), "text/html"), /content type/);
});

test("parses the Velia Inn documented catalog and unordered order-book format", () => {
  assert.deepEqual(parseVeliaInnCatalog("11653-1-200-140000000|14021-3-400-5900000|", "accessory"), [
    { id: 11653, category: "accessory" },
    { id: 14021, category: "accessory" },
  ]);
  assert.deepEqual(parseVeliaInnOrderBook("6350000-1-0|5900000-17-2|6100000-0-4|", 12031, 0), {
    id: 12031,
    sid: 0,
    orders: [
      { price: 6350000, sellers: 1, buyers: 0 },
      { price: 5900000, sellers: 17, buyers: 2 },
      { price: 6100000, sellers: 0, buyers: 4 },
    ],
    source: "Velia Inn-documented Pearl Abyss order book (build fallback)",
  });
});

test("rejects malformed or unsafe fallback order-book rows", () => {
  assert.throws(() => parseVeliaInnOrderBook("100-1|", 1, 0), /row/);
  assert.throws(() => parseVeliaInnOrderBook("-1-1-0|", 1, 0), /row/);
  assert.throws(() => parseVeliaInnOrderBook("100--1|", 1, 0), /sellers/);
  assert.throws(() => parseVeliaInnOrderBook("100-1-0||110-1-0|", 1, 0), /empty/);
  assert.throws(() => parseVeliaInnOrderBook("1e3-1-0|", 1, 0), /price/);
  assert.throws(() => parseVeliaInnOrderBook("9007199254740992-1-0|", 1, 0), /price/);
  assert.throws(() => parseVeliaInnCatalog("11653-1-200|", "accessory"), /catalog row/);
});
