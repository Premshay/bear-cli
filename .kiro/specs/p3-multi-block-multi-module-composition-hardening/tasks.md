# Tasks: P3 Multi-Block Multi-Module Composition Hardening

## Execution Notes for Agents

### Repo standards
- Read: `AGENTS.md` — repo-level guardrails, session hygiene, safety rules
- Read: `.agent/Knowledge/partner_model.md` — collaboration style, BEAR-specific patterns, calibration notes
- Read: `docs/context/start-here.md` — session close protocol (mandatory before ending)
- Read: `docs/context/state.md` — current handoff state

### Commit discipline
- Commit after each top-level task completes (not after each sub-task)
- Commit message format: `test(composition): <short description of what was added>`
- Example: `test(composition): add multi-root unit tests to BlockPortGraphResolverTest`
- Push after Task 4 (full suite green) — not after individual tasks

### Parallel execution guidance
- Tasks 1 and 2 are independent and CAN be executed in parallel by separate subagents
  - Task 1 edits `BlockPortGraphResolverTest.java` only
  - Task 2 creates a new file `MultiRootCompositionTest.java`
  - No file overlap — safe for parallel execution
- Task 3 depends on Task 2 (extends `MultiRootCompositionTest.java` created in Task 2)
- Task 4 depends on Tasks 1, 2, 3 (full suite verification)
- Task 5 depends on Task 4 (session-end after all work is verified)

### State update cadence
- Update `docs/context/state.md` after Task 4 (full suite green)
- If `Session Notes` in `state.md` approaches the `ContextDocsConsistencyTest` cap, move oldest notes to `docs/context/archive/archive-state-history.md` first
- Update `.agent/Knowledge/partner_model.md` in Task 5 (session-end)
- Update `roadmap/board.md` and `roadmap/features/p3-multi-block-multi-module-composition-hardening.md` in Task 5

---

## Context Files

Spec:
- `.kiro/specs/p3-multi-block-multi-module-composition-hardening/requirements.md`
- `.kiro/specs/p3-multi-block-multi-module-composition-hardening/design.md`

Primary test files to edit or create:
- `app/src/test/java/com/bear/app/BlockPortGraphResolverTest.java` *(extend)*
- `app/src/test/java/com/bear/app/MultiRootCompositionTest.java` *(new)*

Reference (read, do not edit):
- `app/src/test/java/com/bear/app/AllModeContractTest.java` — in-process CLI invocation pattern, fixture helpers (`createSingleBlockFixture`, `writeProjectWrapper`, `writeWorkingWithdrawImpl`, `runCli`, `assertOrderedSubsequence`)
- `app/src/test/java/com/bear/app/BlockPortGraphResolverTest.java` — existing resolver test structure (IR YAML helpers, `resolveAndValidate`, `inboundTargetWrapperFqcns`)
- `app/src/main/java/com/bear/app/BlockPortGraphResolver.java` — `inboundTargetWrapperFqcns(graph, sourceBlockKeys)` overload; `wrapperFqcn(blockKey, opName)` for expected FQCN construction
- `app/src/main/java/com/bear/app/AllModeBlockDiscovery.java` — `selectBlocks(index, onlyNames)` returns list sorted by `BlockIndexEntry::name`
- `app/src/main/java/com/bear/app/AllModeRenderer.java` — output line format: `BLOCK:`, `STATUS:`, `SUMMARY:`, `ROOT_REACH_FAILED:`, `ROOT_TEST_FAILED:`, `BOUNDARY_EXPANDING:`, `REPO DELTA:`
- `app/src/test/java/com/bear/app/TestRepoPaths.java` — `TestRepoPaths.repoRoot()` for fixture IR paths
- `app/src/test/java/com/bear/app/TargetSeamParityTest.java` — `initGitRepo` / `gitCommitAll` / `git` private helpers to copy for pr-check tests
- `app/src/test/java/com/bear/app/PythonSeamParityTest.java` — alternative source for git helpers

Docs to update:
- `docs/context/state.md`
- `docs/context/archive/archive-state-history.md` (if state.md Session Notes approaches cap)
- `.agent/Knowledge/partner_model.md`
- `roadmap/features/p3-multi-block-multi-module-composition-hardening.md`
- `roadmap/board.md`

---

## Task List

- [x] 1. Add multi-root unit tests to `BlockPortGraphResolverTest` (Workstreams 1 + 5)
  - Read: `app/src/test/java/com/bear/app/BlockPortGraphResolverTest.java` — follow existing IR YAML helper pattern (`accountIr()`, `txIr()`); add new test methods to the same class
  - Read: `app/src/main/java/com/bear/app/BlockPortGraphResolver.java` — use `BlockPortGraphResolver.inboundTargetWrapperFqcns(graph, sourceBlockKeys)` with a `Set.of(blockName)` argument; use `BlockPortGraphResolver.wrapperFqcn(blockKey, opName)` to construct expected FQCNs
  - Read: `.kiro/specs/p3-multi-block-multi-module-composition-hardening/design.md` §Workstream 1 and §Workstream 5 testing table
  - Parallel: YES — this task has no file overlap with Task 2; can run as a parallel subagent

  - [x] 1.1 Add `inboundWrapperFqcnsAreScopedToSourceRoot`: write a two-block `bear.blocks.yaml` where `account` has `projectRoot: module-a` and `transaction-log` has `projectRoot: module-b`; block `account` declares a block-port edge to `transaction-log` (ops: `AppendTransaction`, `GetTransactions`); call `resolveAndValidate`, then assert `inboundTargetWrapperFqcns(graph, Set.of("account"))` returns exactly `{wrapperFqcn("transaction-log","AppendTransaction"), wrapperFqcn("transaction-log","GetTransactions")}` and `inboundTargetWrapperFqcns(graph, Set.of("transaction-log"))` returns an empty set
    - Read: `app/src/test/java/com/bear/app/BlockPortGraphResolverTest.java` — look at how `accountIr()` and `txIr()` build IR YAML strings; look at how `resolveAndValidate` parses index + IR files and returns a `BlockPortGraph`
    - Read: `app/src/main/java/com/bear/app/BlockPortGraphResolver.java` — `wrapperFqcn(blockKey, opName)` returns the expected FQCN string
    - _Requirements: 1.1, 5.4_

  - [x] 1.2 Add `inboundWrapperFqcnsForTargetRootAreEmpty`: reuse the same two-root fixture from 1.1 (inline or via a shared helper); assert that calling `inboundTargetWrapperFqcns(graph, Set.of("transaction-log"))` returns an empty `TreeSet` (target block has no outbound block-port edges)
    - Read: same files as 1.1
    - _Requirements: 1.1, 5.4_

  - [x] 1.3 Add `graphResolutionIsOrderIndependent` (property-style, plain JUnit 5): build the same two-root two-block `bear.blocks.yaml` content in two orderings — `[account, transaction-log]` and `[transaction-log, account]`; call `resolveAndValidate` for each; assert both produce identical edge lists (same size, same `sourceBlockKey`/`targetBlockKey`/`targetOps` for each edge) and identical `inboundTargetWrapperFqcns(graph, Set.of("account"))` sets
    - Read: same files as 1.1
    - Property 10: Index entry order does not affect graph resolution
    - Validates: Requirements 1.5, 5.5

  - [x] 1.4 Run `./gradlew :app:test --tests "*.BlockPortGraphResolverTest"` and confirm all new tests pass with no regressions to existing tests

  - Commit: `test(composition): add multi-root unit tests to BlockPortGraphResolverTest`

- [x] 2. Create `MultiRootCompositionTest` with `check --all` integration tests (Workstreams 2 + 3)
  - Read: `app/src/test/java/com/bear/app/AllModeContractTest.java` — copy `runCli`, `normalizeLf`, `stdoutLines`, `assertOrderedSubsequence`, `writeProjectWrapper`, `writeWorkingWithdrawImpl` patterns verbatim; use `BearCli.run(args, out, err)` for in-process invocation
  - Read: `app/src/main/java/com/bear/app/AllModeRenderer.java` — output format: `BLOCK: <name>`, `STATUS: PASS`, `STATUS: FAIL`, `BLOCK_CODE: <code>`, `ROOT_REACH_FAILED: <n>`, `ROOT_TEST_FAILED: <n>`, `SUMMARY:`
  - Read: `bear-ir/fixtures/withdraw.bear.yaml` — use as the IR fixture for both blocks in multi-root tests (copy via `TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml")`)
  - Read: `.kiro/specs/p3-multi-block-multi-module-composition-hardening/design.md` §Workstream 2, §Workstream 3, §Testing Strategy
  - Parallel: YES — this task creates a new file with no overlap with Task 1; can run as a parallel subagent

  - [x] 2.1 Create `app/src/test/java/com/bear/app/MultiRootCompositionTest.java` with package declaration, imports, and private `runCli` / `normalizeLf` / `stdoutLines` / `assertOrderedSubsequence` helpers (mirror `AllModeContractTest` exactly)
    - Read: `app/src/test/java/com/bear/app/AllModeContractTest.java` — copy the private helper methods verbatim; note the `BearCli.run(args, out, err)` invocation pattern and `@TempDir` usage
    - _Requirements: 5.1_

  - [x] 2.2 Implement `blockSelectionIsLexicographicRegardlessOfIndexOrder` (property-style, plain JUnit 5): write a `bear.blocks.yaml` with blocks listed in reverse-lexicographic order (`[zeta, beta, alpha]`); run `check --all`; assert stdout contains `BLOCK: alpha` before `BLOCK: beta` before `BLOCK: zeta` using `assertOrderedSubsequence`
    - Read: `app/src/main/java/com/bear/app/AllModeBlockDiscovery.java` — confirm `selectBlocks` sorts by `BlockIndexEntry::name`
    - Read: `app/src/main/java/com/bear/app/AllModeRenderer.java` — confirm `BLOCK:` line format
    - Property 2: Block selection is lexicographic regardless of index order
    - Validates: Requirements 2.1, 5.5

  - [x] 2.3 Implement `twoRootLayoutBothBlocksPass`: create two project roots (`module-a/`, `module-b/`) each with a `gradlew`/`gradlew.bat` stub and a `build.gradle`; write `bear.blocks.yaml` with `block-a` pointing to `module-a` and `block-b` pointing to `module-b`; compile both; write working impls; run `check --all`; assert `exitCode == 0`, stdout contains `STATUS: PASS` for both blocks, and `SUMMARY:` shows `2 passed`
    - Read: `app/src/test/java/com/bear/app/AllModeContractTest.java` — look at `createSingleBlockFixture`, `writeProjectWrapper`, `writeWorkingWithdrawImpl` for fixture setup patterns; adapt for two-root layout
    - Read: `bear-ir/fixtures/withdraw.bear.yaml` — the IR fixture to use for both blocks
    - Read: `app/src/test/java/com/bear/app/TestRepoPaths.java` — `TestRepoPaths.repoRoot()` for resolving fixture paths
    - _Requirements: 5.1, 3.1_

  - [x] 2.4 Implement `twoRootLayoutOneRootDriftOtherPasses`: same two-root fixture as 2.3; after compile, delete the wiring manifest for `block-a` to induce drift; run `check --all`; assert `exitCode != 0`, stdout contains `STATUS: FAIL` for `block-a` and `STATUS: PASS` for `block-b`, and `SUMMARY:` shows `failed` count of 1
    - Read: `app/src/main/java/com/bear/app/AllModeRenderer.java` — confirm `STATUS: FAIL` and `SUMMARY:` format for failed count
    - Property 6: Per-root drift isolation
    - Validates: Requirements 3.1, 5.2

  - [x] 2.5 Implement `twoRootLayoutRootTestStartDoneAreOrdered`: same two-root fixture as 2.3 (both passing); run `check --all`; assert stdout contains `check-all: ROOT_TEST_START project=module-a` before `check-all: ROOT_TEST_START project=module-b` using `assertOrderedSubsequence`; also assert each root emits exactly one `ROOT_TEST_START` and one `ROOT_TEST_DONE` line
    - Read: `app/src/main/java/com/bear/app/CheckAllCommandService.java` — confirm `ROOT_TEST_START` / `ROOT_TEST_DONE` emission format and `normalizePathToken` usage
    - Property 5: ROOT_TEST_START/DONE lines are lexicographically ordered by root
    - Validates: Requirements 2.4, 5.6

  - [x] 2.6 Implement `checkAllIsIdempotentOnSameRepoState`: same two-root fixture as 2.3 (both passing); run `check --all` twice; assert both stdout strings are equal (byte-identical after LF normalization)
    - Property 3: Check --all idempotence
    - Validates: Requirements 2.2, 6.5

  - [x] 2.7 Run `./gradlew :app:test --tests "*.MultiRootCompositionTest"` and confirm all tests added so far pass

  - Commit: `test(composition): add MultiRootCompositionTest with check --all integration tests`

- [x] 3. Add `pr-check --all` integration tests to `MultiRootCompositionTest` (Workstream 4)
  - Read: `app/src/test/java/com/bear/app/TargetSeamParityTest.java` — copy `initGitRepo` / `gitCommitAll` / `git` private helpers for git operations in pr-check tests
  - Read: `app/src/test/java/com/bear/app/PythonSeamParityTest.java` — alternative source for the same git helpers
  - Read: `app/src/main/java/com/bear/app/AllModeRenderer.java` — pr-check output format: `BLOCK:`, `STATUS:`, `DELTA:`, `pr-delta:` lines, `REPO DELTA:`, `BOUNDARY_EXPANDING:`, `SUMMARY:`
  - Read: `.kiro/specs/p3-multi-block-multi-module-composition-hardening/design.md` §Workstream 4 and §Testing Strategy
  - Depends on: Task 2 (extends `MultiRootCompositionTest.java` created there)

  - [x] 3.1 Add `initGitRepo`, `gitCommitAll`, and `git` private helpers to `MultiRootCompositionTest` (copy from `TargetSeamParityTest` or `PythonSeamParityTest`)
    - Read: `app/src/test/java/com/bear/app/TargetSeamParityTest.java` — find the `initGitRepo`, `gitCommitAll`, `git` private static methods; copy verbatim
    - _Requirements: 5.3_

  - [x] 3.2 Implement `twoRootPrCheckDeltaLinesAreSorted`: init git repo; write `bear.blocks.yaml` with two blocks in two roots; compile both; commit; modify both IR files to add a new operation to each block; recompile; run `pr-check --all --base HEAD~1`; assert `exitCode == 5`; collect all `pr-delta:` lines from stdout; assert they are in lexicographic sorted order within each block section
    - Read: `app/src/main/java/com/bear/app/PrCheckAllCommandService.java` — confirm `repoDeltaLines` sort and per-block delta emission
    - Read: `app/src/main/java/com/bear/app/AllModeRenderer.java` — confirm `pr-delta:` line format
    - Property 9: Pr-check delta lines are deterministically sorted across roots
    - Validates: Requirements 4.1, 5.3

  - [x] 3.3 Implement `prCheckAllIsIdempotentOnSameRepoState`: same git fixture as 3.2 (with boundary-expanding delta); run `pr-check --all --base HEAD~1` twice; assert both stdout strings are equal (byte-identical after LF normalization)
    - Property 4: Pr-check --all idempotence
    - Validates: Requirements 2.3, 6.5

  - [x] 3.4 Run `./gradlew :app:test --tests "*.MultiRootCompositionTest"` and confirm all pr-check tests pass

  - Commit: `test(composition): add pr-check --all integration tests to MultiRootCompositionTest`

- [x] 4. Full test suite verification
  - Read: `app/src/test/java/com/bear/app/AllModeContractTest.java` — the `checkAllEmitsProgressLinesAsOrderedSubsequence` test is the single-root regression guard; it must remain green
  - Read: `docs/context/start-here.md` — session close protocol requires docs guard test

  - [x] 4.1 Run `./gradlew test` and confirm zero regressions across all modules; pay particular attention to `AllModeContractTest`, `BlockPortGraphResolverTest`, and `MultiRootCompositionTest`
  - [x] 4.2 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are within budget before updating state.md

  - Commit: no commit needed (verification only); if any fixes were required, commit with: `fix(tests): <description of what was fixed>`

- [x] 5. Session-end: update partner model, project state, and roadmap
  - Read: `.agent/skills/session-end/SKILL.md` — follow the BEAR-specific section (no devlogs; update `state.md` and `partner_model.md`)
  - Read: `docs/context/start-here.md` — session close protocol checklist
  - Read: `.agent/Knowledge/partner_model.md` — add a dated calibration note for this session
  - Read: `docs/context/state.md` — update handoff fields; check Session Notes length against `ContextDocsConsistencyTest` cap
  - Read: `docs/context/archive/archive-state-history.md` — if `state.md` Session Notes is approaching cap, move oldest notes here first
  - Read: `roadmap/board.md` and `roadmap/features/p3-multi-block-multi-module-composition-hardening.md` — update status

  - [x] 5.1 If `docs/context/state.md` Session Notes is approaching the `ContextDocsConsistencyTest` cap, move the oldest session notes to `docs/context/archive/archive-state-history.md` before adding new notes
    - Read: `docs/context/state.md` — check current Session Notes length
    - Read: `docs/context/archive/archive-state-history.md` — append oldest notes at the end

  - [x] 5.2 Update `docs/context/state.md`: set `Last Updated` to today's date, `Current Focus` to `p3-multi-block-multi-module-composition-hardening complete`, `Next Concrete Task` to next queued item from `roadmap/board.md` (likely `p3-capability-templates`), add a brief session note summarizing what shipped
    - Read: `roadmap/board.md` — identify next item in Ready Queue

  - [x] 5.3 Update `.agent/Knowledge/partner_model.md`: add a dated entry under Calibration Notes summarizing what shipped this session, any new patterns observed (e.g., multi-root fixture patterns, parallel task execution results), and any corrections received
    - Read: `.agent/Knowledge/partner_model.md` — add entry below the most recent dated section

  - [x] 5.4 Update `roadmap/features/p3-multi-block-multi-module-composition-hardening.md`: set `status: completed`
    - Read: `roadmap/features/p3-multi-block-multi-module-composition-hardening.md` — change `status: queued` to `status: completed`

  - [x] 5.5 Update `roadmap/board.md`: move `p3-multi-block-multi-module-composition-hardening` from Active to Completed; promote next Ready Queue item to Active
    - Read: `roadmap/board.md` — move item between sections

  - [x] 5.6 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are still within budget after updates

  - Commit: `chore: session-end — update state, partner model, and roadmap for composition hardening`
  - Push: push all commits to the feature branch after this final commit
