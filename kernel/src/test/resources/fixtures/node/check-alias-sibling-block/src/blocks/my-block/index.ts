// Bad TypeScript file — @/* alias to sibling block (boundary bypass)
import { helper } from "@/blocks/other-block/utils.js";

export function run(input: string): string {
  return helper(input);
}
