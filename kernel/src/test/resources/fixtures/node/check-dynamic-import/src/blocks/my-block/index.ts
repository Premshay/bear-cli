// Bad TypeScript file — contains dynamic import
import { MyBlockImpl } from "./impl/MyBlockImpl.js";

export async function run(input: string): Promise<string> {
  const impl = new MyBlockImpl();
  const mod = await import('./other.js');
  return impl.execute(input) + mod.helper();
}
