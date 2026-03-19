# Design: P3 Multi-Block Multi-Module Composition Hardening

## Overview

Four parallel workstreams, all additive or corrective changes to existing files:

1. **Workstream 1 — Cross-root FQCN resolution** (`BlockPortGraphResolver`): ensure
   `inboundTargetWrapperFqcns` correctly scopes wrapper sets per root when blocks span
   multiple `projectRoot` values.
2. **Workstream 2 — Deterministic block selection ordering** (`AllModeBlockDiscovery`,
   `AllModeRenderer`): verify and harden lexicographic block ordering in `--all` mode.
3. **Workstream 3 — Per-root isolation in `check --all`** (`CheckAllCommandService`):
   verify that drift, reach, and test failures in one root do not contaminate results
   for other roots; harden `ROOT_TEST_START`/`ROOT_TEST_DONE` emission ordering.
4. **Workstream 4 — Stable boundary-expansion reporting in `pr-check --all`**
   (`PrCheckAllCommandService`, `AllModeRenderer`): verify delta line sort order and
   REPO DELTA aggregation across multiple roots.
5. **Workstream 5 — Integration tests**: two-root and multi-block fixtures exercising
   all four workstreams.

No new classes, no new exit codes, no new CLI flags, no IR schema changes.

---

## Architecture

The composition hardening sits entirely in the `app/` module. The kernel is unchanged.

```
bear.blocks.yaml
      │
      ▼
BlockIndexParser ──► BlockIndex (entries sorted by AllModeBlockDiscovery)
      │
      ▼
BlockPortGraphResolver ──► BlockPortGraph
      │                         │
      │                         ▼
      │              inboundTargetWrapperFqcns(graph, rootBlockKeys)
      │                         │ (scoped per root)
      ▼                         ▼
CheckAllCommandService ──► per-root scan loop
      │                         │
      ▼                         ▼
AllModeAggregation ──► RepoAggregationResult
      │
      ▼
AllModeRenderer ──► ordered output lines
```

The key invariant: **block selection order is established once by `AllModeBlockDiscovery.selectBlocks`
(lexicographic by name) and never re-sorted downstream**. All rendering and aggregation
consume the list in that order.

---

## Components and Interfaces

### Workstream 1: `BlockPortGraphResolver`

**File:** `app/src/main/java/com/bear/app/BlockPortGraphResolver.java`

**Current behavior:** `inboundTargetWrapperFqcns(graph, sourceBlockKeys)` filters edges
by `sourceBlockKey` membership. In a multi-root layout, `CheckAllCommandService` calls
this with `rootBlockKeys` — the set of block names whose `projectRoot` matches the current
root being scanned. This is already structurally correct.

**Gap identified:** The existing `BlockPortGraphResolverTest` only exercises single-root
layouts (all blocks share `projectRoot: .`). There is no test that verifies the resolver
correctly handles a two-root layout where block A (root X) has a block-port edge to
block B (root Y). The resolver logic itself is correct; the gap is test coverage and
the absence of a multi-root fixture in `CheckAllCommandService`'s root-scan loop.

**Verification needed:** Confirm that when `CheckAllCommandService` builds `rootBlockKeys`
for root X, it only includes blocks whose `projectRoot` resolves to X, so the wrapper
FQCN set passed to `scanBlockPortBindings` is correctly scoped to cross-root inbound
targets only.

**No code changes required** to `BlockPortGraphResolver` itself. The fix is:
- Add a multi-root test to `BlockPortGraphResolverTest` (Workstream 5).
- Confirm the `rootBlockKeys` construction in `CheckAllCommandService` is correct
  (it is — `rootPassIndexes` keys on `blockResult.project()` which is `block.projectRoot()`).

### Workstream 2: `AllModeBlockDiscovery` + `AllModeRenderer`

**File:** `app/src/main/java/com/bear/app/AllModeBlockDiscovery.java`

**Current behavior:** `selectBlocks` already sorts by `BlockIndexEntry::name` before
filtering. This is correct.

**File:** `app/src/main/java/com/bear/app/AllModeRenderer.java`

**Current behavior:** `renderCheckAllOutput` and `renderPrAllOutput` iterate the
`results` list in the order provided. Since `selectBlocks` returns a sorted list and
`CheckAllCommandService`/`PrCheckAllCommandService` preserve that order, the output
is already lexicographically ordered.

**Gap identified:** No test verifies that a `bear.blocks.yaml` with entries in
non-lexicographic order produces output in lexicographic order. The existing
`AllModeRendererTest` uses pre-sorted input.

**No code changes required.** The fix is a targeted test (Workstream 5).

**`ROOT_TEST_START`/`ROOT_TEST_DONE` ordering:** `CheckAllCommandService` iterates
`rootPassIndexes` (a `TreeMap<String, List<Integer>>`) which is already sorted by
`projectRoot` string. The `normalizePathToken` call converts backslashes to forward
slashes before emission. This is correct for lexicographic ordering.

**Gap identified:** No test verifies that two distinct roots emit their
`ROOT_TEST_START`/`ROOT_TEST_DONE` lines in lexicographic order. Add to Workstream 5.

### Workstream 3: `CheckAllCommandService`

**File:** `app/src/main/java/com/bear/app/CheckAllCommandService.java`

**Current behavior:** The root-scan loop iterates `rootPassIndexes.entrySet()` (a
`TreeMap`, so sorted). For each root, it runs reach scan, reflection scan, boundary
bypass scan, and project tests. Failures mark all blocks in that root as FAIL via
`BearCli.rootFailure`. Blocks in other roots are unaffected.

**Gap identified:** No integration test exercises a two-root layout where one root
fails and the other passes. The isolation logic is correct but untested for multi-root.

**Priority rule (Req 3.4):** When a root has undeclared reach, the code increments
`rootReachFailed` and `rootTestSkippedDueToReach` and `continue`s — skipping the test
runner. This correctly implements reach-over-test priority. No code change needed.

**Exit code aggregation:** `AllModeAggregation.aggregateCheckResults` uses
`severityRankCheck` which implements the frozen rank order. No change needed.

**No code changes required.** The fix is integration tests (Workstream 5).

### Workstream 4: `PrCheckAllCommandService` + `AllModeRenderer`

**File:** `app/src/main/java/com/bear/app/PrCheckAllCommandService.java`

**Current behavior:** `repoDeltaLines` is built by iterating `sharedByRoot.values()`
(a `TreeMap`, so sorted by root) and collecting delta lines, then calling
`repoDeltaLines.sort(String::compareTo)`. This is correct.

**Per-block delta lines:** Each block's `deltaLines` come from
`PrCheckCommandService.executePrCheck` which sorts them internally. The renderer
emits them in the order stored in `BlockExecutionResult.deltaLines()`. This is correct.

**Gap identified:** No test verifies that a two-root pr-check run produces delta lines
in sorted order across both blocks. Add to Workstream 5.

**No code changes required.** The fix is integration tests (Workstream 5).

### Workstream 5: Integration Tests

New test class: `app/src/test/java/com/bear/app/MultiRootCompositionTest.java`

This class contains all integration tests for multi-root and multi-block layouts.
It follows the pattern established in `AllModeContractTest` (in-process CLI invocation,
`@TempDir`, fixture helpers).

---

## Data Models

No new data models. Existing models are sufficient:

- `BlockPortGraph` — already holds the full index and edge list; multi-root is implicit
  in the `BlockIndexEntry.projectRoot()` field.
- `BlockExecutionResult` — `project()` field carries the `projectRoot` string, used as
  the grouping key in `CheckAllCommandService`.
- `RepoAggregationResult` — `rootReachFailed`, `rootTestFailed`,
  `rootTestSkippedDueToReach` counters already exist.
- `AllCheckOptions` / `AllPrCheckOptions` — unchanged.

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid
executions of a system — essentially, a formal statement about what the system should do.
Properties serve as the bridge between human-readable specifications and
machine-verifiable correctness guarantees.*

### Property 1: Cross-root inbound wrapper FQCN resolution

*For any* two-block layout where block A (in root X) declares a block-port edge to
block B (in root Y), calling `BlockPortGraphResolver.inboundTargetWrapperFqcns(graph, {A})`
should return exactly the wrapper FQCNs derived from block B's operations referenced
in the edge, and calling it with `{B}` should return an empty set (B has no outbound
block-port edges in this layout).

**Validates: Requirements 1.1**

### Property 2: Block selection is lexicographic regardless of index order

*For any* `bear.blocks.yaml` with blocks listed in arbitrary order,
`AllModeBlockDiscovery.selectBlocks` should return a list whose block names form a
non-decreasing lexicographic sequence, regardless of the order entries appear in the
index file.

**Validates: Requirements 2.1, 5.5**

### Property 3: Check --all idempotence

*For any* repo state with no file changes between runs, running `bear check --all`
twice should produce byte-identical stdout output both times.

**Validates: Requirements 2.2, 6.5**

### Property 4: Pr-check --all idempotence

*For any* repo state with no file changes between runs, running `bear pr-check --all`
twice should produce byte-identical stdout and stderr output both times.

**Validates: Requirements 2.3, 6.5**

### Property 5: ROOT_TEST_START/DONE lines are lexicographically ordered by root

*For any* multi-root layout where multiple roots have passing blocks,
the `check-all: ROOT_TEST_START project=<root>` lines should appear in the stdout
output in lexicographic order by the normalized `projectRoot` token.

**Validates: Requirements 2.4**

### Property 6: Per-root drift isolation

*For any* two-root layout where one root has drift (wiring manifest mismatch) and
the other root is clean, the blocks in the drifted root should be FAIL and the blocks
in the clean root should be PASS in the `check --all` output, with the SUMMARY
`failed` count reflecting only the drifted root's blocks.

**Validates: Requirements 3.1, 5.2**

### Property 7: Per-root reach isolation

*For any* two-root layout where one root has undeclared reach and the other root is
clean, `check --all` should set `ROOT_REACH_FAILED: 1` in the SUMMARY and mark only
the failing root's blocks as FAIL with `BLOCK_CODE=UNDECLARED_REACH`, while the clean
root's blocks remain PASS.

**Validates: Requirements 3.2**

### Property 8: Per-root test failure isolation

*For any* two-root layout where one root's project tests fail and the other root is
clean, `check --all` should set `ROOT_TEST_FAILED: 1` in the SUMMARY and mark only
the failing root's blocks as FAIL with `BLOCK_CODE=TEST_FAILURE`, while the clean
root's blocks remain PASS.

**Validates: Requirements 3.3**

### Property 9: Pr-check delta lines are deterministically sorted across roots

*For any* multi-root pr-check run, the `pr-delta:` lines emitted within each block
section should be in lexicographic sorted order, and the `REPO DELTA:` section lines
should also be in lexicographic sorted order across all roots.

**Validates: Requirements 1.4, 4.1, 4.2**

### Property 10: Index entry order does not affect graph resolution

*For any* `bear.blocks.yaml` content, shuffling the order of block entries should
produce an identical `BlockPortGraph` (same edges, same FQCN sets, same cycle
detection result).

**Validates: Requirements 1.5**

---

## Error Handling

All error paths are unchanged from current behavior. This hardening spec adds no new
error conditions. The existing error contracts are:

| Condition | Exit | CODE |
|---|---|---|
| Missing block-port target | 2 | `IR_VALIDATION` |
| Block-port cycle detected | 2 | `IR_VALIDATION` |
| Undeclared reach in root | 7 | `UNDECLARED_REACH` |
| Project test failure | 4 | `TEST_FAILURE` |
| Aggregated multi-block failure | per severity rank | `REPO_MULTI_BLOCK_FAILED` |

The frozen severity rank orders are unchanged:
- check: `70 > 74 > 64 > 2 > 3 > 7 > 6 > 4 > 0`
- pr-check: `70 > 74 > 64 > 2 > 7 > 5 > 0`

---

## Testing Strategy

### Dual testing approach

Unit tests verify specific examples and edge cases. Property tests verify universal
properties across many generated inputs. Both are required.

### Workstream 1: `BlockPortGraphResolver` — multi-root coverage

**File:** `app/src/test/java/com/bear/app/BlockPortGraphResolverTest.java`

| Test | Kind | Validates |
|---|---|---|
| `inboundWrapperFqcnsAreScopedToSourceRoot` | unit | P1, Req 1.1 |
| `inboundWrapperFqcnsForTargetRootAreEmpty` | unit | P1, Req 1.1 |
| `graphResolutionIsOrderIndependent` | property | P10, Req 1.5 |
| `missingTargetBlockFailsWithIrValidation` (existing) | unit | Req 1.2 |
| `cycleDetectionUsesCanonicalLeastRotation` (existing) | unit | Req 1.3 |

### Workstream 2: `AllModeBlockDiscovery` — ordering

**File:** `app/src/test/java/com/bear/app/MultiRootCompositionTest.java`

| Test | Kind | Validates |
|---|---|---|
| `blockSelectionIsLexicographicRegardlessOfIndexOrder` | property | P2, Req 2.1 |

### Workstream 3: `CheckAllCommandService` — per-root isolation

**File:** `app/src/test/java/com/bear/app/MultiRootCompositionTest.java`

| Test | Kind | Validates |
|---|---|---|
| `twoRootLayoutBothBlocksPass` | integration | Req 5.1 |
| `twoRootLayoutOneRootDriftOtherPasses` | integration | P6, Req 3.1, 5.2 |
| `twoRootLayoutRootTestStartDoneAreOrdered` | integration | P5, Req 2.4, 5.6 |
| `checkAllIsIdempotentOnSameRepoState` | integration | P3, Req 2.2 |

### Workstream 4: `PrCheckAllCommandService` — delta ordering

**File:** `app/src/test/java/com/bear/app/MultiRootCompositionTest.java`

| Test | Kind | Validates |
|---|---|---|
| `twoRootPrCheckDeltaLinesAreSorted` | integration | P9, Req 4.1, 5.3 |
| `prCheckAllIsIdempotentOnSameRepoState` | integration | P4, Req 2.3 |

### Workstream 5: `BlockPortGraphResolverTest` — multi-root unit

**File:** `app/src/test/java/com/bear/app/BlockPortGraphResolverTest.java`

| Test | Kind | Validates |
|---|---|---|
| `inboundWrapperFqcnsAreScopedToSourceRoot` | unit | P1, Req 1.1, 5.4 |
| `graphResolutionIsOrderIndependent` | property | P10, Req 1.5, 5.5 |

### Property-based testing

Property-style tests use plain JUnit 5 with specific example orderings (not randomized
generators). They verify ordering and order-independence properties by constructing
multiple concrete inputs and asserting identical outputs.

Tag format: `Feature: p3-multi-block-multi-module-composition-hardening, Property N: <text>`

| Property | Test method | Kind |
|---|---|---|
| P2: Block selection is lexicographic | `blockSelectionIsLexicographicRegardlessOfIndexOrder` | plain JUnit 5 |
| P10: Index order independence | `graphResolutionIsOrderIndependent` | plain JUnit 5 |

Properties P3, P4, P5, P6, P7, P8, P9 are validated by integration tests (deterministic
fixtures, not randomized) because they require a compiled project on disk. They are
single-example integration tests, not property-based tests.

### Unit test balance

- Unit tests: specific examples, error conditions, edge cases (reach+test priority).
- Property tests: ordering and order-independence properties where input space is large.
- Integration tests: end-to-end CLI invocations with real compiled fixtures.
- Avoid duplicating coverage already provided by `AllModeAggregationTest` (severity rank)
  and `AllModeContractTest` (single-root baseline).

### Regression guard

The existing `AllModeContractTest.checkAllEmitsProgressLinesAsOrderedSubsequence` test
exercises the single-root baseline. It must remain green after all changes. No new
exit codes, no new failure envelope fields, no IR schema changes.
