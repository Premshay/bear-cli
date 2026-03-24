# Tasks: P3 Capability Templates

## Execution Notes for Agents

### Repo standards
- Read: `AGENTS.md` — repo-level guardrails, session hygiene, safety rules
- Read: `.agent/Knowledge/partner_model.md` — collaboration style, BEAR-specific patterns, calibration notes
- Read: `docs/context/start-here.md` — session close protocol (mandatory before ending)
- Read: `docs/context/state.md` — current handoff state

### Commit discipline
- Commit after each top-level task completes (not after each sub-task)
- Commit message format: `feat(templates): <short description of what was added>`
- Example: `feat(templates): add CapabilityTemplateRegistry and ReadStoreTemplate`
- Push after Task 5 (full suite green) — not after individual tasks

### Parallel execution guidance
- Tasks 1 and 2 are independent and CAN be executed in parallel by separate subagents
  - Task 1 creates new files in `kernel/src/main/java/com/bear/kernel/template/`
  - Task 2 extends Task 1 (adds `ReadStoreTemplate` to the same package) — depends on Task 1
  - Task 3 creates new files in `app/` — no overlap with Task 1 or 2 until wiring in Task 4
- Task 4 depends on Tasks 1, 2, 3 (wires scaffold into `BearCli`)
- Task 5 depends on Task 4 (full suite verification)
- Task 6 depends on Task 5 (session-end after all work is verified)

### State update cadence
- Update `docs/context/state.md` after Task 5 (full suite green)
- If `Session Notes` in `state.md` approaches the `ContextDocsConsistencyTest` cap, move oldest notes to `docs/context/archive/archive-state-history.md` first
- Update `.agent/Knowledge/partner_model.md` in Task 6 (session-end)
- Update `roadmap/board.md` and `roadmap/features/p3-capability-templates.md` in Task 6

---

## Context Files

Spec:
- `.kiro/specs/p3-capability-templates/requirements.md`
- `.kiro/specs/p3-capability-templates/design.md`

Primary files to create:
- `kernel/src/main/java/com/bear/kernel/template/CapabilityTemplate.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/template/TemplateParams.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/template/TemplatePack.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/template/CapabilityTemplateRegistry.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/template/ReadStoreTemplate.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/template/CapabilityTemplateRegistryTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/template/ReadStoreTemplateTest.java` *(new)*
- `app/src/main/java/com/bear/app/BlocksYamlUpdater.java` *(new)*
- `app/src/main/java/com/bear/app/ScaffoldCommandService.java` *(new)*
- `app/src/test/java/com/bear/app/ScaffoldCommandServiceTest.java` *(new)*
- `app/src/test/java/com/bear/app/ScaffoldIntegrationTest.java` *(new)*

Primary files to modify:
- `app/src/main/java/com/bear/app/CliCodes.java` *(add two string constants)*
- `app/src/main/java/com/bear/app/BearCli.java` *(add "scaffold" to COMMAND_HANDLERS)*
- `app/src/main/java/com/bear/app/BearCliCommandHandlers.java` *(add runScaffold method)*

Reference (read, do not edit):
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` — `TargetId` type used in `TemplateParams`
- `kernel/src/main/java/com/bear/kernel/target/Target.java` — `target.compile(ir, projectRoot, blockKey)` signature
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` — `TargetRegistry.defaultRegistry()` and `resolve(projectRoot)`
- `kernel/src/main/java/com/bear/kernel/ir/BearIrParser.java` — IR parsing for `ReadStoreTemplateTest`
- `kernel/src/main/java/com/bear/kernel/ir/BearIrValidator.java` — IR validation for `ReadStoreTemplateTest`
- `app/src/main/java/com/bear/app/BearCli.java` — `COMMAND_HANDLERS` map, `emitCompileResult` pattern
- `app/src/main/java/com/bear/app/BearCliCommandHandlers.java` — existing handler method signatures to mirror
- `app/src/main/java/com/bear/app/CliCodes.java` — frozen exit codes and existing string constants
- `app/src/main/java/com/bear/app/BlockIndexParser.java` — `BlockIndexParser.parse()` for reading `bear.blocks.yaml`
- `app/src/main/java/com/bear/app/DefaultIrPipeline.java` — `IR_PIPELINE.parseValidateNormalize(irPath)` pattern
- `app/src/test/java/com/bear/app/MultiRootCompositionTest.java` — `@TempDir`, `runCli`, `writeProjectWrapper`, `writeWorkingImpl`, git helpers for integration test patterns
- `app/src/test/java/com/bear/app/AllModeContractTest.java` — `createSingleBlockFixture`, `writeProjectWrapper`, `writeWorkingWithdrawImpl` fixture helpers
- `app/src/test/java/com/bear/app/TestRepoPaths.java` — `TestRepoPaths.repoRoot()` for fixture IR paths

Docs to update:
- `docs/context/state.md`
- `docs/context/archive/archive-state-history.md` (if state.md Session Notes approaches cap)
- `.agent/Knowledge/partner_model.md`
- `roadmap/features/p3-capability-templates.md`
- `roadmap/board.md`

---

## Task List

- [x] 1. Create kernel template infrastructure (`CapabilityTemplate`, `TemplateParams`, `TemplatePack`, `CapabilityTemplateRegistry`)
  - Read: `.kiro/specs/p3-capability-templates/design.md` §Components and Interfaces — `CapabilityTemplate` interface, `TemplateParams` record, `TemplatePack` record, `CapabilityTemplateRegistry` class signatures
  - Read: `kernel/src/main/java/com/bear/kernel/target/TargetId.java` — `TargetId` type used in `TemplateParams`
  - New package: `com.bear.kernel.template` under `kernel/src/main/java/`
  - No new kernel dependencies; no I/O in registry; no agent logic

  - [x] 1.1 Create `CapabilityTemplate` interface, `TemplateParams` record, and `TemplatePack` record in `com.bear.kernel.template`
    - `CapabilityTemplate`: `id()`, `description()`, `emit(TemplateParams, Path) throws IOException`
    - `TemplateParams`: `blockName` (String), `targetId` (TargetId)
    - `TemplatePack`: `irPath` (Path), `implStubPath` (Path), `generatedPaths` (List<Path>)
    - Read: `.kiro/specs/p3-capability-templates/design.md` §Components and Interfaces for exact field names and types
    - Read: `kernel/src/main/java/com/bear/kernel/target/TargetId.java` — import path for `TargetId`
    - _Requirements: 1.5, 2.1, 2.5_

  - [x] 1.2 Create `CapabilityTemplateRegistry` with `list()` (sorted, unmodifiable) and `find()` (Optional, never throws)
    - `list()` returns templates sorted by `id()`, stable across calls
    - `find(String id)` returns `Optional.empty()` for unknown ids — no unchecked exceptions
    - Initially register zero templates (placeholder `List.of()`); `ReadStoreTemplate` added in Task 2
    - Read: `.kiro/specs/p3-capability-templates/design.md` §CapabilityTemplateRegistry for exact class structure
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

  - [x] 1.3 Create `CapabilityTemplateRegistryTest` — sorted+stable list, lookup round-trip, not-found returns empty, find never throws
    - `listIsSortedAndStable`: call `list()` twice, assert both results are equal and sorted by id
    - `findKnownTemplateReturnsPresent`: for each id in `list()`, assert `find(id)` returns non-empty Optional whose `id()` equals the queried id (parameterized, min 1 input once ReadStoreTemplate is registered)
    - `findUnknownTemplateReturnsEmpty`: assert `find("unknown-xyz")` returns `Optional.empty()`
    - `findNeverThrowsForArbitraryInput`: parameterized over `["", " ", "null-like", "read-store", "UNKNOWN", "!@#$"]` — assert no exception thrown
    - Read: `.kiro/specs/p3-capability-templates/design.md` §Testing Strategy (Kernel unit tests table) and §Correctness Properties P1–P3
    - Property 1: Registry enumeration is stable and sorted — Validates: Requirements 1.1, 1.4
    - Property 2: Registry lookup round-trip — Validates: Requirements 1.2
    - Property 3: Registry not-found is explicit — Validates: Requirements 1.3

  - [x] 1.4 Run `./gradlew :kernel:test --tests "*.CapabilityTemplateRegistryTest"` and confirm all tests pass

  - Commit: `feat(templates): add kernel template infrastructure (CapabilityTemplate, TemplateParams, TemplatePack, CapabilityTemplateRegistry)`

- [x] 2. Implement `ReadStoreTemplate` and register it
  - Read: `.kiro/specs/p3-capability-templates/design.md` §ReadStoreTemplate — exact IR YAML shape to emit (version, block name, operations, effects, uses.allow)
  - Read: `kernel/src/main/java/com/bear/kernel/ir/BearIrParser.java` — `BearIrParser.parse(Path)` for test validation
  - Read: `kernel/src/main/java/com/bear/kernel/ir/BearIrValidator.java` — `BearIrValidator.validate(BearIr)` for test validation
  - Depends on: Task 1 (CapabilityTemplate interface and CapabilityTemplateRegistry exist)

  - [x] 2.1 Create `ReadStoreTemplate` implementing `CapabilityTemplate` in `com.bear.kernel.template`
    - `id()` returns `"read-store"`
    - `description()` returns a one-line human description
    - `emit(params, projectRoot)`: creates `spec/` dir if absent, writes `spec/<blockName>.ir.yaml` with the v1 IR shape from the design, returns `TemplatePack` with `irPath` set and `generatedPaths` empty
    - IR shape: `version: v1`, block name from params, one `Get<BlockName>` operation with `id`/`result` string fields, one `<blockName>Store` effect port with `ops: [get]`, `uses.allow` referencing the port
    - Block name in IR must use PascalCase for the block `name` field (e.g., `MyBlock`) while the port uses camelCase (e.g., `myBlockStore`)
    - Read: `.kiro/specs/p3-capability-templates/design.md` §ReadStoreTemplate for the exact YAML template
    - _Requirements: 2.1, 4.1, 4.2, 7.1_

  - [x] 2.2 Register `ReadStoreTemplate` in `CapabilityTemplateRegistry`
    - Replace the placeholder `List.of()` with `List.of(new ReadStoreTemplate())`
    - Read: `kernel/src/main/java/com/bear/kernel/template/CapabilityTemplateRegistry.java` — the TEMPLATES constant to update
    - _Requirements: 1.1, 4.1_

  - [x] 2.3 Create `ReadStoreTemplateTest` — emitted IR parses+validates, has data-store port, emit is deterministic, IR path is under `spec/`
    - `emittedIrParsesAndValidates`: emit to a `@TempDir`, parse with `BearIrParser`, validate with `BearIrValidator`, assert no exceptions
    - `emittedIrHasDataStorePort`: assert emitted IR contains at least one effect port and at least one `uses.allow` entry referencing that port
    - `emitIsDeterministic`: parameterized over `["my-block", "read-store-test", "foo-bar", "abc", "x1"]` — emit twice to separate temp dirs, assert IR file contents are byte-identical
    - `emittedIrPathIsUnderSpecDir`: assert `TemplatePack.irPath()` is under `projectRoot/spec/`
    - Read: `.kiro/specs/p3-capability-templates/design.md` §ReadStoreTemplate and §Correctness Properties P4, P6, P8
    - Property 4: Emitted IR is valid v1 — Validates: Requirements 2.1, 4.2, 5.1
    - Property 6: Emission determinism — Validates: Requirements 2.5
    - Property 8: File placement invariant (IR under spec/) — Validates: Requirements 3.2

  - [x] 2.4 Run `./gradlew :kernel:test --tests "*.ReadStoreTemplateTest" --tests "*.CapabilityTemplateRegistryTest"` and confirm all tests pass (registry tests now have a real template to exercise)

  - Commit: `feat(templates): add ReadStoreTemplate and register in CapabilityTemplateRegistry`

- [x] 3. App layer — `BlocksYamlUpdater` and `ScaffoldCommandService`
  - Read: `.kiro/specs/p3-capability-templates/design.md` §ScaffoldCommandService, §BlocksYamlUpdater, §Error Handling
  - Read: `app/src/main/java/com/bear/app/CliCodes.java` — frozen exit codes; add `USAGE_UNKNOWN_TEMPLATE` and `BLOCK_ALREADY_EXISTS` string constants here
  - Read: `app/src/main/java/com/bear/app/BlockIndexParser.java` — `BlockIndexParser.parse(repoRoot, indexPath)` for reading existing `bear.blocks.yaml`
  - Read: `app/src/main/java/com/bear/app/BearCli.java` — `executeCompile` and `emitCompileResult` patterns; `IR_PIPELINE` usage
  - Read: `app/src/main/java/com/bear/app/DefaultIrPipeline.java` — `IR_PIPELINE.parseValidateNormalize(irPath)` pattern

  - [x] 3.1 Add `USAGE_UNKNOWN_TEMPLATE` and `BLOCK_ALREADY_EXISTS` string constants to `CliCodes`
    - These are string failure codes only — no new integer exit codes
    - Both map to `EXIT_USAGE` (64) per the error handling table in the design
    - Read: `app/src/main/java/com/bear/app/CliCodes.java` — add alongside existing `USAGE_INVALID_ARGS` and `USAGE_UNKNOWN_COMMAND`
    - _Requirements: 6.2, 6.3, 7.2_

  - [x] 3.2 Create `BlocksYamlUpdater` in `com.bear.app`
    - If `bear.blocks.yaml` does not exist: create it with `version: v1` header and the new block entry
    - If it exists: read with `BlockIndexParser`, verify block name not already present (double-check), then append the new entry as raw YAML text and write back
    - Entry format: `- name: <blockName>\n  ir: <irRelPath>\n  projectRoot: .\n`
    - Use `projectRoot`-relative paths for `ir` field; `projectRoot` field is always `.` for single-root scaffold
    - Simple append — no full YAML re-serialization to preserve existing formatting
    - Read: `.kiro/specs/p3-capability-templates/design.md` §BlocksYamlUpdater for exact append behavior
    - Read: `app/src/main/java/com/bear/app/BlockIndexParser.java` — `BlockIndex` and `BlockIndexEntry` types for the double-check read
    - _Requirements: 3.5, 6.1_

  - [x] 3.3 Create `ScaffoldCommandService` in `com.bear.app`
    - Handles `--template <id> --block <name> [--project <path>]` and `--list` subcommands
    - `--list` flow: call `CapabilityTemplateRegistry.list()`, print each `id()`, exit 0
    - Emit flow (10 steps from design): parse args → resolve projectRoot → find template (unknown → EXIT_USAGE/USAGE_UNKNOWN_TEMPLATE) → check bear.blocks.yaml for existing block (exists → EXIT_USAGE/BLOCK_ALREADY_EXISTS) → resolve target → emit IR → parse+validate IR → compile → update bear.blocks.yaml → print "scaffold: OK" + exit 0
    - Missing `--template` or `--block` flags → EXIT_USAGE/USAGE_INVALID_ARGS
    - Returns a `ScaffoldResult` record (mirrors `CompileResult` pattern: `exitCode`, `stdoutLines`, `stderrLines`, `failureCode`, `failurePath`, `failureRemediation`)
    - Read: `.kiro/specs/p3-capability-templates/design.md` §ScaffoldCommandService for the full 10-step emit flow and §ScaffoldResult data model
    - Read: `app/src/main/java/com/bear/app/BearCli.java` — `executeCompile` for the compile-after-emit pattern; `TargetRegistry.defaultRegistry().resolve(projectRoot)` for target resolution
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 7.2_

  - [x] 3.4 Create `ScaffoldCommandServiceTest` — unknown template exits 64, existing block exits 64, `--list` prints sorted ids, missing flags exit 64
    - `unknownTemplateExitsUsage`: call service with `--template unknown-xyz --block my-block`, assert exitCode == 64 and stderr contains `USAGE_UNKNOWN_TEMPLATE`
    - `existingBlockExitsUsage`: write a `bear.blocks.yaml` with block `my-block` already present, call service with `--template read-store --block my-block`, assert exitCode == 64 and stderr contains `BLOCK_ALREADY_EXISTS`
    - `listPrintsSortedTemplateIds`: call service with `--list`, assert exitCode == 0 and stdout contains `read-store`
    - `missingTemplateFlagExitsUsage`: call with only `--block my-block`, assert exitCode == 64
    - `missingBlockFlagExitsUsage`: call with only `--template read-store`, assert exitCode == 64
    - Read: `.kiro/specs/p3-capability-templates/design.md` §Testing Strategy (App unit tests table) and §Correctness Properties P12, P13
    - Property 12: Scaffold refuses unknown template — Validates: Requirements 6.2
    - Property 13: Scaffold refuses existing block — Validates: Requirements 6.3

  - [x] 3.5 Run `./gradlew :app:test --tests "*.ScaffoldCommandServiceTest"` and confirm all tests pass

  - Commit: `feat(templates): add BlocksYamlUpdater and ScaffoldCommandService`

- [x] 4. Wire scaffold command into `BearCli` and add integration tests
  - Read: `.kiro/specs/p3-capability-templates/design.md` §Architecture, §BearCli changes, §Testing Strategy (Integration tests table)
  - Read: `app/src/main/java/com/bear/app/BearCli.java` — `COMMAND_HANDLERS` map (add `"scaffold"` entry); `run()` method for dispatch pattern
  - Read: `app/src/main/java/com/bear/app/BearCliCommandHandlers.java` — existing handler method signatures; add `runScaffold` following the same pattern as `runCompile`
  - Read: `app/src/test/java/com/bear/app/MultiRootCompositionTest.java` — `@TempDir`, `runCli`, `writeProjectWrapper`, `writeWorkingImpl`, git helpers to reuse for integration tests
  - Read: `app/src/test/java/com/bear/app/AllModeContractTest.java` — `createSingleBlockFixture`, `writeProjectWrapper`, `writeWorkingWithdrawImpl` for single-root fixture patterns

  - [x] 4.1 Add `"scaffold"` to `BearCli.COMMAND_HANDLERS` and implement `BearCliCommandHandlers.runScaffold()`
    - In `BearCli`: add `"scaffold", BearCliCommandHandlers::runScaffold` to the `COMMAND_HANDLERS` map
    - In `BearCliCommandHandlers`: add `static int runScaffold(String[] args, PrintStream out, PrintStream err)` that delegates to `ScaffoldCommandService`; emit result via a new `BearCli.emitScaffoldResult()` helper following the same pattern as `emitCompileResult`
    - Read: `app/src/main/java/com/bear/app/BearCli.java` — `COMMAND_HANDLERS` is a `Map.of(...)` — must be replaced with a map that includes the new entry (Map.of supports up to 10 entries; if at limit, switch to `Map.ofEntries`)
    - Read: `app/src/main/java/com/bear/app/BearCliCommandHandlers.java` — `runCompile` as the template for `runScaffold`
    - _Requirements: 6.1, 6.4, 7.3_

  - [x] 4.2 Create `ScaffoldIntegrationTest` with `@TempDir` — happy path, file placement, scaffold-then-check, compile-equivalence, stub preservation, pr-check boundary-expanding, existing commands unaffected
    - `scaffoldHappyPathExitsZero`: run `bear scaffold --template read-store --block my-block --project <tempDir>`, assert exitCode == 0 and stdout contains `scaffold: OK`
    - `scaffoldWritesIrAndBlocksYaml`: after scaffold, assert `spec/my-block.ir.yaml` exists and `bear.blocks.yaml` contains `name: my-block`; parse `bear.blocks.yaml` with `BlockIndexParser` and assert entry has correct `name`, `ir`, `projectRoot` fields
    - `scaffoldFilePlacementInvariant`: after scaffold, assert all generated artifact paths are under `build/generated/bear/`, impl stub is under `src/main/java/blocks/my-block/impl/`, IR is under `spec/` — no files outside these three trees
    - `scaffoldThenCheckExitsZero`: scaffold, then run `bear check spec/my-block.ir.yaml --project <tempDir>`, assert exitCode == 0
    - `scaffoldThenCompileIsEquivalent`: scaffold to dir A; separately write the same IR to dir B and run `bear compile`; assert generated artifact contents are byte-identical between A and B
    - `scaffoldPreservesExistingImplStub`: write a custom impl stub at the expected path, scaffold, assert stub content is unchanged
    - `scaffoldThenPrCheckIsBoundaryExpanding`: init git repo, commit empty state, scaffold, commit, run `bear pr-check --base HEAD~1`, assert exitCode == 5
    - `existingCommandsUnaffectedByScaffoldAddition`: run `bear validate`, `bear compile`, `bear check` on an existing fixture and assert they produce the same exit codes as before scaffold was added
    - Read: `.kiro/specs/p3-capability-templates/design.md` §Testing Strategy (Integration tests table) and §Correctness Properties P5, P7, P8, P9, P10, P11
    - Read: `app/src/test/java/com/bear/app/MultiRootCompositionTest.java` — `initGitRepo`, `gitCommitAll`, `git` helpers for pr-check test; `runCli` pattern
    - Property 5: Compile-equivalence round-trip — Validates: Requirements 2.2, 3.4, 5.2
    - Property 7: Impl stub preservation — Validates: Requirements 2.6
    - Property 8: File placement invariant — Validates: Requirements 3.1, 3.2, 7.4
    - Property 9: bear.blocks.yaml entry correctness — Validates: Requirements 3.5
    - Property 10: bear check passes on freshly scaffolded project — Validates: Requirements 3.3, 5.3
    - Property 11: Scaffold command happy path — Validates: Requirements 6.1

  - [x] 4.3 Run `./gradlew :app:test --tests "*.ScaffoldIntegrationTest"` and confirm all tests pass

  - Commit: `feat(templates): wire scaffold command into BearCli and add integration tests`

- [-] 5. Full test suite verification
  - Read: `docs/context/start-here.md` — session close protocol requires docs guard test
  - Read: `app/src/test/java/com/bear/app/AllModeContractTest.java` — regression guard; must remain green

  - [x] 5.1 Run `./gradlew test --no-daemon` and verify zero regressions across all modules; pay particular attention to `AllModeContractTest`, `ScaffoldCommandServiceTest`, `ScaffoldIntegrationTest`, `CapabilityTemplateRegistryTest`, and `ReadStoreTemplateTest`
  - [x] 5.2 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are within budget before updating state.md

  - Commit: no commit needed (verification only); if any fixes were required, commit with: `fix(templates): <description of what was fixed>`

- [x] 6. Session-end: update partner model, project state, and roadmap
  - Read: `.agent/skills/session-end/SKILL.md` — follow the BEAR-specific section (no devlogs; update `state.md` and `partner_model.md`)
  - Read: `docs/context/start-here.md` — session close protocol checklist
  - Read: `.agent/Knowledge/partner_model.md` — add a dated calibration note for this session
  - Read: `docs/context/state.md` — update handoff fields; check Session Notes length against `ContextDocsConsistencyTest` cap
  - Read: `docs/context/archive/archive-state-history.md` — if `state.md` Session Notes is approaching cap, move oldest notes here first
  - Read: `roadmap/board.md` and `roadmap/features/p3-capability-templates.md` — update status

  - [x] 6.1 If `docs/context/state.md` Session Notes is approaching the `ContextDocsConsistencyTest` cap, move the oldest session notes to `docs/context/archive/archive-state-history.md` before adding new notes
    - Read: `docs/context/state.md` — check current Session Notes length
    - Read: `docs/context/archive/archive-state-history.md` — append oldest notes at the end

  - [x] 6.2 Update `docs/context/state.md`: set `Last Updated` to today's date, `Current Focus` to `p3-capability-templates complete`, `Next Concrete Task` to next queued item from `roadmap/board.md`, add a brief session note summarizing what shipped
    - Read: `roadmap/board.md` — identify next item in Ready Queue

  - [x] 6.3 Update `.agent/Knowledge/partner_model.md`: add a dated entry under Calibration Notes summarizing what shipped this session, any new patterns observed (e.g., template emit + compile-equivalence pattern, `BlocksYamlUpdater` append strategy), and any corrections received
    - Read: `.agent/Knowledge/partner_model.md` — add entry below the most recent dated section

  - [x] 6.4 Update `roadmap/features/p3-capability-templates.md`: set `status: completed`
    - Read: `roadmap/features/p3-capability-templates.md` — change `status: queued` to `status: completed`

  - [x] 6.5 Update `roadmap/board.md`: move `p3-capability-templates` from Active to Completed; promote next Ready Queue item to Active if applicable
    - Read: `roadmap/board.md` — move item between sections

  - [x] 6.6 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are still within budget after updates

  - Commit: `chore: session-end — update state, partner model, and roadmap for capability templates`
  - Push: push all commits to the feature branch after this final commit
