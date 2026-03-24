// Clean TypeScript file — @/* alias to same block (allowed)
import { helper } from "@/blocks/my-block/utils.js";

export function run(input: string): string {
  return helper(input);
}
