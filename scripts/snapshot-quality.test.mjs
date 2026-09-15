import assert from "node:assert/strict";
import test from "node:test";
import { addExpectedOrderBooks, expectedOrderBookCoverage, hasMarketSignal } from "./snapshot-quality.mjs";

test("counts only unique order books that were actually requested", () => {
  const pairs = [{ id: 1, sid: 0 }, { id: 1, sid: 17 }, { id: 2, sid: 0 }];
  const books = new Map();
  addExpectedOrderBooks(books, pairs, [
    { id: 1, sid: 0, orders: [] },
    { id: 1, sid: 0, orders: [] },
    { id: 999, sid: 999, orders: [] },
  ]);
  assert.equal(books.size, 1);
  assert.equal(expectedOrderBookCoverage(pairs, books), 1 / 3);
  assert.equal(hasMarketSignal(books), false);

  addExpectedOrderBooks(books, pairs, [
    { id: 1, sid: 0, orders: [{ price: 100, sellers: 0, buyers: 1 }] },
    { id: 1, sid: 17, orders: [{ price: 200, sellers: 0, buyers: 0 }] },
    { id: 2, sid: 0, orders: [{ price: 300, sellers: 1, buyers: 0 }] },
  ]);
  assert.equal(expectedOrderBookCoverage(pairs, books), 1);
  assert.equal(hasMarketSignal(books), true);
});
