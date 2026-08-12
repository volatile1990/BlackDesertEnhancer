function readUint32(buffer, offset) {
  if (offset + 4 > buffer.length) throw new Error("Truncated Huffman response");
  return buffer.readUInt32LE(offset);
}

function compareNodes(left, right) {
  return left.frequency - right.frequency;
}

// Mirrors java.util.PriorityQueue's comparator heap behavior, including equal-frequency nodes.
class NodeHeap {
  constructor() {
    this.values = [];
  }

  add(node) {
    let index = this.values.length;
    this.values.push(node);
    while (index > 0) {
      const parentIndex = (index - 1) >>> 1;
      const parent = this.values[parentIndex];
      if (compareNodes(node, parent) >= 0) break;
      this.values[index] = parent;
      index = parentIndex;
    }
    this.values[index] = node;
  }

  poll() {
    if (this.values.length === 0) return undefined;
    const result = this.values[0];
    const replacement = this.values.pop();
    if (this.values.length > 0 && replacement) {
      let index = 0;
      const half = this.values.length >>> 1;
      while (index < half) {
        let childIndex = (index << 1) + 1;
        let child = this.values[childIndex];
        const rightIndex = childIndex + 1;
        if (rightIndex < this.values.length && compareNodes(child, this.values[rightIndex]) > 0) {
          childIndex = rightIndex;
          child = this.values[rightIndex];
        }
        if (compareNodes(replacement, child) <= 0) break;
        this.values[index] = child;
        index = childIndex;
      }
      this.values[index] = replacement;
    }
    return result;
  }

  get size() {
    return this.values.length;
  }
}

export function decodeMarketHuffman(input) {
  const buffer = Buffer.isBuffer(input) ? input : Buffer.from(input);
  if (buffer.length < 24 || buffer.length > 2_000_000) throw new Error("Invalid Huffman response size");
  const characterCount = readUint32(buffer, 8);
  if (characterCount === 0 || characterCount > 256) throw new Error("Invalid Huffman character table");

  let offset = 12;
  const heap = new NodeHeap();
  for (let index = 0; index < characterCount; index += 1) {
    const frequency = readUint32(buffer, offset);
    const character = buffer[offset + 4];
    if (frequency === 0 || character === undefined) throw new Error("Invalid Huffman frequency entry");
    heap.add({ character, frequency, left: null, right: null });
    offset += 8;
  }

  while (heap.size > 1) {
    const left = heap.poll();
    const right = heap.poll();
    if (!left || !right) throw new Error("Invalid Huffman tree");
    heap.add({ character: null, frequency: left.frequency + right.frequency, left, right });
  }
  const root = heap.poll();
  if (!root) throw new Error("Empty Huffman tree");

  const bitCount = readUint32(buffer, offset);
  const encodedBytes = readUint32(buffer, offset + 4);
  const expectedBytes = readUint32(buffer, offset + 8);
  offset += 12;
  if (encodedBytes !== buffer.length - offset || bitCount > encodedBytes * 8 || expectedBytes > 2_000_000) {
    throw new Error("Invalid Huffman payload sizes");
  }

  const output = Buffer.allocUnsafe(expectedBytes);
  let outputOffset = 0;
  let current = root;
  for (let bitOffset = 0; bitOffset < bitCount; bitOffset += 1) {
    const byte = buffer[offset + (bitOffset >>> 3)];
    if (byte === undefined) throw new Error("Truncated Huffman payload");
    const bit = (byte >> (7 - (bitOffset & 7))) & 1;
    current = bit === 0 ? current.left : current.right;
    if (!current) throw new Error("Invalid Huffman branch");
    if (current.left === null && current.right === null) {
      if (outputOffset >= expectedBytes || current.character === null) throw new Error("Huffman output exceeds expected size");
      output[outputOffset] = current.character;
      outputOffset += 1;
      current = root;
    }
  }
  if (outputOffset !== expectedBytes) throw new Error(`Huffman output length mismatch (${outputOffset}/${expectedBytes})`);
  return output.toString("utf8");
}
