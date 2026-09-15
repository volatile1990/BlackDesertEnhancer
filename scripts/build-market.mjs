export function sidFor(level) {
  return level > 0 ? level + 15 : 0;
}

export function quoteFromBook(book, fetchedAt, allowPreorder = false) {
  const asks = book.orders.filter((order) => order.sellers > 0);
  const totalSellers = asks.reduce((sum, order) => sum + order.sellers, 0);
  const totalBuyers = book.orders.reduce((sum, order) => sum + order.buyers, 0);
  if (asks.length > 0) {
    const lowest = asks.reduce((best, order) => order.price < best.price ? order : best);
    const buyersAtPrice = book.orders
      .filter((order) => order.price === lowest.price)
      .reduce((sum, order) => sum + order.buyers, 0);
    return {
      price: lowest.price,
      sellersAtLowest: lowest.sellers,
      totalSellers,
      buyersAtPrice,
      totalBuyers,
      kind: "listing",
      state: "snapshot",
      fetchedAt,
      source: book.source ?? "Order book",
    };
  }

  if (allowPreorder && book.orders.length > 0) {
    const highestPrice = Math.max(...book.orders.map((order) => order.price));
    const buyersAtPrice = book.orders
      .filter((order) => order.price === highestPrice)
      .reduce((sum, order) => sum + order.buyers, 0);
    return {
      price: highestPrice,
      sellersAtLowest: 0,
      totalSellers: 0,
      buyersAtPrice,
      totalBuyers,
      kind: "preorder",
      state: "snapshot",
      fetchedAt,
      source: `${book.source ?? "Order book"} · höchste zulässige Preorder-Preisstufe`,
    };
  }

  return {
    price: null,
    sellersAtLowest: 0,
    totalSellers: 0,
    buyersAtPrice: 0,
    totalBuyers,
    kind: "unavailable",
    state: "unlisted",
    fetchedAt,
    source: book.source ?? "Order book",
  };
}
