import assert from "node:assert/strict";
import test from "node:test";
import { quoteFromBook, sidFor } from "./build-market.mjs";

const fetchedAt = "2026-09-15T12:00:00.000Z";

test("maps Manos enhancement levels to market SIDs", () => {
  assert.deepEqual(
    [0, 2, 3, 4].map((level) => sidFor(level)),
    [0, 17, 18, 19],
  );
});

test("selects the lowest listed ask from an unsorted order book", () => {
  const quote = quoteFromBook({
    source: "Test book",
    orders: [
      { price: 300, sellers: 4, buyers: 1 },
      { price: 100, sellers: 2, buyers: 7 },
      { price: 200, sellers: 3, buyers: 5 },
      { price: 50, sellers: 0, buyers: 9 },
    ],
  }, fetchedAt);

  assert.equal(quote.price, 100);
  assert.equal(quote.kind, "listing");
  assert.equal(quote.sellersAtLowest, 2);
  assert.equal(quote.totalSellers, 9);
  assert.equal(quote.buyersAtPrice, 7);
});

test("BASE fallback uses the highest price tier even with no buyers", () => {
  const quote = quoteFromBook({
    source: "Test book",
    orders: [
      { price: 100, sellers: 0, buyers: 12 },
      { price: 300, sellers: 0, buyers: 0 },
      { price: 200, sellers: 0, buyers: 4 },
    ],
  }, fetchedAt, true);

  assert.equal(quote.price, 300);
  assert.equal(quote.kind, "preorder");
  assert.equal(quote.buyersAtPrice, 0);
  assert.equal(quote.totalBuyers, 16);
});

test("listed sellers take priority over the BASE preorder fallback", () => {
  const quote = quoteFromBook({
    source: "Test book",
    orders: [
      { price: 300, sellers: 0, buyers: 20 },
      { price: 200, sellers: 1, buyers: 0 },
    ],
  }, fetchedAt, true);

  assert.equal(quote.price, 200);
  assert.equal(quote.kind, "listing");
  assert.equal(quote.sellersAtLowest, 1);
});

test("non-BASE equipment and materials without sellers stay unavailable", () => {
  const book = {
    source: "Test book",
    orders: [{ price: 300, sellers: 0, buyers: 8 }],
  };

  for (const quote of [
    quoteFromBook(book, fetchedAt, false),
    quoteFromBook(book, fetchedAt),
  ]) {
    assert.equal(quote.price, null);
    assert.equal(quote.kind, "unavailable");
    assert.equal(quote.state, "unlisted");
  }
});
