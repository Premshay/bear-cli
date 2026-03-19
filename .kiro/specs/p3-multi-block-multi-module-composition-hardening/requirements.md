# Requirements: P3 Multi-Block Multi-Module Composition Hardening

## Introduction

BEAR already supports multi-block repos via `bear.blocks.yaml` and `--all` mode commands.
However, the current test coverage only exercises single-projectRoot layouts (all blocks in
one Gradle module). Real repos span multiple Gradle modules (multiple `projectRoot` values)
and multiple blocks per module. This feature hardens BEAR's deterministic behavior for those
layouts without changing any existing contracts.

Three areas need hardening:

1. **Cross-block dependency constraints** — block-port edges that cross `projectRoot`
   boundaries must be validated and enforced with the same determinism as same-root edges.
2. **Repo-wide drift and reach reporting** — `check --all` output ordering and summary
   fields must be stable and reproducible when blocks span multiple roots.
3. **Repo-wide boundary-expansion reporting** — `pr-check --all` delta and governance
   output must be stable and ordered across composed projects.

No IR schema changes, no new exit codes, no new CLI flags, and no relaxation of existing
deterministic contracts are in scope.

## Glossary

- **Block**: A single governed logic unit declared in `bear.blocks.yaml` with a name, IR
  file path, and `projectRoot`.
- **Block-port edge**: A cross-block dependency declared in IR via
  `effects.allow[].kind: block` and `targetBlock: <name>`. Governs which operations of
  another block this block may call.
- **Multi-root layout**: A `bear.blocks.yaml` where two or more blocks have different
  `projectRoot` values (i.e., they live in separate Gradle modules).
- **Multi-block layout**: A `bear.blocks.yaml` where two or more blocks share the same
  `projectRoot`.
- **Composed project**: A repo that uses both multi-root and multi-block layouts
  simultaneously.
- **BlockPortGraph**: The in-memory directed graph of block-port edges resolved from
  `bear.blocks.yaml` and the referenced IR files.
- **Inbound target wrapper set**: The set of generated wrapper FQCNs that are inbound
  targets for a given set of blocks, used by the boundary-bypass scanner.
- **REPO_MULTI_BLOCK_FAILED**: The aggregated failure code emitted by `--all` commands
  when one or more blocks fail.
- **Repo_Reporter**: The `--all` mode output layer (`AllModeRenderer`, `AllModeAggregation`,
  `AllModeBlockDiscovery`) responsible for deterministic ordering and summary fields.
- **Composition_Validator**: The `BlockPortGraphResolver` component responsible for
  validating block-port edges across the full index.
- **Check_All_Service**: `CheckAllCommandService` — orchestrates `check --all`.
- **PrCheck_All_Service**: `PrCheckAllCommandService` — orchestrates `pr-check --all`.

---

## Requirements

### Requirement 1: Cross-block dependency validation is deterministic for multi-root layouts

**User Story:** As a developer, I want BEAR to validate cross-block dependencies the same
way regardless of whether the referenced blocks live in the same Gradle module or different
ones, so that I can trust governance signals in multi-module repos.

#### Acceptance Criteria

1. WHEN `bear check --all` is run on a repo where block A (in `projectRoot` X) declares a
   block-port edge to block B (in `projectRoot` Y), THE Composition_Validator SHALL resolve
   the inbound target wrapper FQCN set for both roots correctly and pass it to the
   boundary-bypass scanner for each root.

2. WHEN `bear check --all` is run on a repo where a block-port edge references a block name
   that does not exist in `bear.blocks.yaml`, THE Composition_Validator SHALL fail with
   `exit 2` and `CODE=IR_VALIDATION`, regardless of whether the referencing block and the
   missing block would have been in the same or different `projectRoot` values.

3. WHEN `bear check --all` is run on a repo where block-port edges form a cycle across
   multiple `projectRoot` values, THE Composition_Validator SHALL fail with `exit 2` and
   `CODE=IR_VALIDATION` using the canonical least-rotation cycle representation.

4. WHEN `bear pr-check --all` is run on a repo with a multi-root block-port graph, THE
   PrCheck_All_Service SHALL classify IR deltas for each block independently and emit
   delta lines in deterministic sorted order across all blocks.

5. THE Composition_Validator SHALL produce identical validation results for the same
   `bear.blocks.yaml` and IR content regardless of the order in which block entries appear
   in the index file.

### Requirement 2: Block selection ordering is stable and reproducible

**User Story:** As a developer running `bear check --all` or `bear pr-check --all`, I want
the output block order to be deterministic so that CI diffs are stable and agent loops
produce reproducible reports.

#### Acceptance Criteria

1. THE Repo_Reporter SHALL emit block sections in lexicographic order by block name for
   both `check --all` and `pr-check --all`, regardless of the order blocks appear in
   `bear.blocks.yaml`.

2. WHEN `bear check --all` is run twice on the same repo state with no file changes, THE
   Check_All_Service SHALL produce byte-identical stdout output both times (idempotence
   property).

3. WHEN `bear pr-check --all` is run twice on the same repo state with no file changes,
   THE PrCheck_All_Service SHALL produce byte-identical stdout and stderr output both times
   (idempotence property).

4. WHEN `bear check --all` is run on a multi-root layout, THE Repo_Reporter SHALL emit
   `check-all: ROOT_TEST_START project=<root>` and `check-all: ROOT_TEST_DONE project=<root>`
   lines for each distinct `projectRoot` that has at least one passing block, in
   lexicographic order by normalized `projectRoot` token.

### Requirement 3: Repo-wide drift reporting is correct for multi-root layouts

**User Story:** As a developer, I want `bear check --all` to correctly report drift,
undeclared reach, and test failures per root when blocks span multiple Gradle modules, so
that I can identify which module is failing without ambiguity.

#### Acceptance Criteria

1. WHEN `bear check --all` is run on a multi-root layout where one root has drift and
   another root is clean, THE Check_All_Service SHALL report the drifted blocks as FAIL
   and the clean blocks as PASS, with the SUMMARY `failed` count reflecting only the
   drifted root's blocks.

2. WHEN `bear check --all` is run on a multi-root layout where one root has undeclared
   reach, THE Check_All_Service SHALL set `ROOT_REACH_FAILED` to 1 in the SUMMARY and
   SHALL mark all blocks in that root as FAIL with `BLOCK_CODE=UNDECLARED_REACH`, while
   blocks in other roots that pass remain PASS.

3. WHEN `bear check --all` is run on a multi-root layout where one root's project tests
   fail, THE Check_All_Service SHALL set `ROOT_TEST_FAILED` to 1 in the SUMMARY and SHALL
   mark all blocks in that root as FAIL with `BLOCK_CODE=TEST_FAILURE`, while blocks in
   other roots that pass remain PASS.

4. IF a `projectRoot` has both undeclared reach and a test failure, THEN THE
   Check_All_Service SHALL report `ROOT_REACH_FAILED` for that root (reach takes priority
   over test failure) and SHALL set `ROOT_TEST_SKIPPED_DUE_TO_REACH` to 1 in the SUMMARY.

5. THE Check_All_Service SHALL aggregate the final exit code across all roots using the
   frozen severity rank order (`70 > 74 > 64 > 2 > 3 > 7 > 6 > 4 > 0`), not numeric max.

### Requirement 4: Repo-wide boundary-expansion reporting is stable across composed projects

**User Story:** As a developer reviewing a PR that touches multiple blocks across multiple
modules, I want `bear pr-check --all` to produce a stable, ordered governance report so
that I can review boundary changes without noise from non-deterministic output.

#### Acceptance Criteria

1. WHEN `bear pr-check --all` is run on a multi-root layout, THE PrCheck_All_Service SHALL
   emit per-block `pr-delta:` lines in deterministic sorted order (by class, category,
   change, key) within each block section.

2. WHEN `bear pr-check --all` is run on a multi-root layout where each root has a
   `bear-policy/_shared.policy.yaml`, THE PrCheck_All_Service SHALL emit a `REPO DELTA:`
   section that aggregates shared-policy deltas from all roots in deterministic sorted
   order, with no per-block duplication.

3. WHEN `bear pr-check --all` is run on a multi-root layout with no boundary-expanding
   changes, THE PrCheck_All_Service SHALL exit `0` and emit `pr-check: OK: NO_BOUNDARY_EXPANSION`
   for each block.

4. WHEN `bear pr-check --all` is run on a multi-root layout where one block has a
   boundary-expanding delta and another block is ordinary, THE PrCheck_All_Service SHALL
   exit `5` and the SUMMARY SHALL show `BOUNDARY_EXPANDING: 1`.

5. THE PrCheck_All_Service SHALL aggregate the final exit code across all blocks using the
   frozen pr severity rank order (`70 > 74 > 64 > 2 > 7 > 5 > 0`), not numeric max.

### Requirement 5: Test coverage for representative multi-block and multi-module cases

**User Story:** As a BEAR maintainer, I want integration tests that exercise multi-root and
multi-block layouts so that regressions in composition behavior are caught before release.

#### Acceptance Criteria

1. THE test suite SHALL include at least one `check --all` test that exercises a two-root
   layout (two blocks in different `projectRoot` values) and verifies that both blocks pass
   independently.

2. THE test suite SHALL include at least one `check --all` test that exercises a two-root
   layout where one root fails (drift or reach) and verifies that the other root's blocks
   remain PASS in the output.

3. THE test suite SHALL include at least one `pr-check --all` test that exercises a
   two-root layout and verifies that delta lines from both blocks appear in deterministic
   sorted order in the output.

4. THE test suite SHALL include at least one test that verifies `BlockPortGraphResolver`
   correctly resolves inbound target wrapper FQCNs for a multi-root block-port graph (block
   A in root X references block B in root Y).

5. THE test suite SHALL include at least one test that verifies block selection ordering
   is lexicographic by name regardless of the order entries appear in `bear.blocks.yaml`.

6. WHERE the repo layout includes blocks that share a `projectRoot`, THE test suite SHALL
   verify that `ROOT_TEST_START` / `ROOT_TEST_DONE` progress lines are emitted exactly once
   per distinct root, not once per block.

### Requirement 6: No regression to existing deterministic contracts

**User Story:** As a BEAR user, I want the hardening work to leave all existing exit codes,
failure envelopes, and IR schemas unchanged so that my CI pipelines are not broken.

#### Acceptance Criteria

1. THE Check_All_Service SHALL continue to emit `CODE=REPO_MULTI_BLOCK_FAILED` /
   `PATH=bear.blocks.yaml` as the aggregated failure footer for any non-zero `check --all`
   exit, unchanged from current behavior.

2. THE PrCheck_All_Service SHALL continue to emit `CODE=REPO_MULTI_BLOCK_FAILED` /
   `PATH=bear.blocks.yaml` as the aggregated failure footer for any non-zero `pr-check --all`
   exit, unchanged from current behavior.

3. THE Check_All_Service SHALL NOT introduce new exit codes beyond the frozen set
   (`0, 2, 3, 4, 6, 7, 64, 70, 74`).

4. THE PrCheck_All_Service SHALL NOT introduce new exit codes beyond the frozen set
   (`0, 2, 5, 7, 64, 70, 74`).

5. WHEN `bear check --all` or `bear pr-check --all` is run on a single-block single-root
   repo (the existing baseline), THE commands SHALL produce output identical to pre-hardening
   behavior (no regression).
