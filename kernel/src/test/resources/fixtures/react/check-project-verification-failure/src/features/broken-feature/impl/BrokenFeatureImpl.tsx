// User-owned — BEAR will not overwrite this file
// This file contains a deliberate TypeScript type error for testing

// TypeScript type error: assigning string to number
const x: number = "string";

export class BrokenFeatureImpl {
  getValue(): number {
    return x;
  }
}
