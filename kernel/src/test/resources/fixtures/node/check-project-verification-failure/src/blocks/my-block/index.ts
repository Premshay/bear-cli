// TypeScript file with type error — will fail tsc --noEmit
import { MyBlockImpl } from "./impl/MyBlockImpl.js";

export function run(input: string): string {
  const impl = new MyBlockImpl();
  // Type error: number is not assignable to string
  const result: string = 42;
  return impl.execute(result);
}
