// Clean TypeScript file — only static imports within block
import { MyBlockImpl } from "./impl/MyBlockImpl.js";

export function run(input: string): string {
  const impl = new MyBlockImpl();
  return impl.execute(input);
}
