import assert from "node:assert/strict";
import test from "node:test";
import { decodeMarketHuffman } from "./huffman.mjs";

test("decodes a deterministic market Huffman fixture", () => {
  const fixture = Buffer.from(
    "swAAAAAAAAAMAAAAHgAAAC0AAABRAAAAMAAAAAsAAAAxAAAABQAAADIAAAADAAAAMwAAAAoAAAA0AAAACwAAADUAAAACAAAANgAAAAEAAAA3AAAAAQAAADgAAAAKAAAAOQAAAA8AAAB8AAAA2AEAADsAAAC0AAAAmM8PXbeAfHbMQHrtmJD123Vh83beLD322B8u26B8u2YwHrttYfHbMZB67Ziw9dsxmD12zGcHrtmKDu0=",
    "base64",
  );
  assert.equal(
    decodeMarketHuffman(fixture),
    "4980000-1-0|5200000-2-0|4990000-1-0|4940000-1-0|5150000-6-0|5250000-5-0|5000000-3-0|5100000-3-0|4920000-1-0|5050000-2-0|4930000-1-0|4950000-1-0|4960000-1-0|4970000-1-0|4910000-0-0|",
  );
});
