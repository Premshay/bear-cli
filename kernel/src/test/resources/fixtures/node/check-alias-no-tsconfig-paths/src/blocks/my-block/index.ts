// Bad TypeScript file — @/* import with no compilerOptions.paths configured
import { helper } from "@/blocks/my-block/utils.js";

export function run(input: string): string {
  return helper(input);
}
