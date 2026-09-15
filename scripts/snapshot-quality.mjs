export function orderBookKey({ id, sid }) {
  return `${id}:${sid}`;
}

export function addExpectedOrderBooks(target, pairs, candidates) {
  const expected = new Set(pairs.map(orderBookKey));
  for (const book of candidates) {
    const key = orderBookKey(book);
    if (expected.has(key) && Array.isArray(book.orders)) target.set(key, book);
  }
}

export function hasMarketSignal(books) {
  return [...books.values()].some((book) => book.orders.length > 0);
}

export function expectedOrderBookCoverage(pairs, books) {
  if (pairs.length === 0) return 0;
  const expected = new Set(pairs.map(orderBookKey));
  const received = [...expected].filter((key) => {
    return books.has(key);
  }).length;
  return received / expected.size;
}
