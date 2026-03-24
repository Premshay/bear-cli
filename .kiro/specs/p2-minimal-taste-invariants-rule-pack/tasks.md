# Tasks: P2 Minimal Taste-Invariants Rule Pack

## Execution Notes for Agents

### Repo standards
- Read: `AGENTS.md` — repo-level guardrails, session hygiene, safety rules
- Read: `.agent/Knowledge/partner_model.md` — collaboration style, BEAR-specific patterns, calibration notes
- Read: `docs/context/start-here.md` — session close protocol (mandatory before ending)
- Read: `docs/context/state.md` — current handoff state

### Commit discipline
- Commit after each top-level task completes (not after each sub-task)
- Commit message format: `feat(taste-invariants): <short description of what was added>`
- Example: `feat(taste-invariants): add TasteInvariantScanner with 6 rule IDs`
- Push after Task 6 (full suite green) — not after individual tasks

### Parallel execution guidance
- Tasks 1 and 2 are independent and CAN be executed in parallel
  - Task 1 creates `TasteInvariantScanner.java` in `app/`
  - Task 2 updates `GovernanceRuleRegistry.java` — no overlap with Task 1
- Task 3 depends on Task 1 (integrates scanner into `CheckCommandService`)
- Task 4 depends on Task 1 (unit tests for the scanner)
- Task 5 depends on Tasks 1, 3, 4 (property tests require scanner and integration)
- Task 6 depends on Task 5 (full suite verification)
- Task 7 depends on Task 6 (session-end after all work is verified)

### State update cadence
- Update `docs/context/state.md` after Task 6 (full suite green)
- If `Session Notes` in `state.md` approaches the `ContextDocsConsistencyTest` cap, move oldest notes to `docs/context/archive/archive-state-history.md` first
- Update `.agent/Knowledge/partner_model.md` in Task 7 (session-end)
- Update `roadmap/board.md` and relevant roadmap files in Task 7

---

## Context Files

Spec:
- `.kiro/specs/p2-minimal-taste-invariants-rule-pack/requirements.md`
- `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md`

Primary files to create:
- `app/src/main/java/com/bear/app/TasteInvariantScanner.java` *(new)*
- `app/src/test/java/com/bear/app/TasteInvariantScannerTest.java` *(new)*
- `app/src/test/java/com/bear/app/TasteInvariantScannerProperties.java` *(new)*

Primary files to modify:
- `app/src/main/java/com/bear/app/GovernanceRuleRegistry.java` *(add 6 rule IDs)*
- `app/src/main/java/com/bear/app/CheckCommandService.java` *(add taste-invariant scanning)*

Reference (read, do not edit):
- `app/src/main/java/com/bear/app/HygieneScanner.java` — pattern to follow for scanner structure
- `app/src/main/java/com/bear/app/PolicyAllowlistParser.java` — allowlist parsing pattern
- `kernel/src/main/java/com/bear/kernel/target/BoundaryBypassFinding.java` — finding record to reuse
- `app/src/main/java/com/bear/app/CliCodes.java` — `EXIT_UNDECLARED_REACH` (exit code 6)

Docs to update:
- `docs/context/state.md`
- `docs/context/archive/archive-state-history.md` (if state.md Session Notes approaches cap)
- `.agent/Knowledge/partner_model.md`
- `roadmap/board.md`

---

## Task List

- [x] 1. Create `TasteInvariantScanner` with 6 rule implementations
  - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Components and Interfaces, §Algorithms
  - Read: `app/src/main/java/com/bear/app/HygieneScanner.java` — follow this pattern for scanner structure
  - Read: `kernel/src/main/java/com/bear/kernel/target/BoundaryBypassFinding.java` — reuse this record for findings
  - New file: `app/src/main/java/com/bear/app/TasteInvariantScanner.java`

  - [x] 1.1 Create `TasteInvariantScanner` class skeleton with 6 rule ID constants and allowlist path constant
    - Add constants: `GENERATED_SURFACE_NAMING_VIOLATION`, `GENERATED_WIRING_NAMING_VIOLATION`, `GENERATED_ZONE_UNEXPECTED_PATH`, `GENERATED_SOURCE_STRUCTURE_VIOLATION`, `GENERATED_SOURCE_UNEXPECTED_FILE`, `GENERATED_FORBIDDEN_DEPENDENCY`
    - Add constant: `TASTE_INVARIANTS_ALLOWLIST_PATH = "bear-policy/taste-invariants-allowlist.txt"`
    - Private constructor (utility class pattern)
    - _Requirements: 6.1, 7.1_

  - [x] 1.2 Implement `scanTasteInvariants(Path projectRoot, Set<String> allowlist)` main entry point
    - Return empty list if `build/generated/bear/` does not exist
    - Call each rule validation method and aggregate findings
    - Sort findings by path, then rule, then detail for determinism
    - _Requirements: 8.2, 8.4_

  - [x] 1.3 Implement surface naming validation (Rule 1)
    - Scan `build/generated/bear/surfaces/` for files not matching `^[a-z][a-z0-9-]*\.surface\.json$`
    - Emit `GENERATED_SURFACE_NAMING_VIOLATION` for violations
    - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Rule 1: Surface Naming Validation
    - _Requirements: 1.1, 1.3_

  - [x] 1.4 Implement wiring naming validation (Rule 2)
    - Scan `build/generated/bear/wiring/` for files not matching `^[a-z][a-z0-9-]*\.wiring\.json$`
    - Emit `GENERATED_WIRING_NAMING_VIOLATION` for violations
    - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Rule 2: Wiring Naming Validation
    - _Requirements: 1.2, 1.4_

  - [x] 1.5 Implement zone sprawl detection (Rule 3)
    - Enumerate all paths under `build/generated/bear/`
    - Verify each path belongs to known categories: surfaces, wiring, src, gradle
    - Emit `GENERATED_ZONE_UNEXPECTED_PATH` for unexpected paths
    - Skip paths in allowlist
    - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Rule 3: Zone Sprawl Detection
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 1.6 Implement source structure validation (Rule 4)
    - Scan `build/generated/bear/src/main/java/` for Java files
    - Verify files are under `com/bear/generated/{block}/` package structure
    - Emit `GENERATED_SOURCE_STRUCTURE_VIOLATION` for violations
    - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Rule 4: Source Structure Validation
    - _Requirements: 3.1, 3.2_

  - [x] 1.7 Implement source file type validation (Rule 5)
    - Verify generated Java files match expected naming: `{Block}Logic.java`, `{Block}_{Op}.java`, `{Block}_{Port}BlockClient.java`, `Bear*.java` (runtime)
    - Emit `GENERATED_SOURCE_UNEXPECTED_FILE` for unexpected files
    - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Rule 5: Source File Type Validation
    - _Requirements: 3.3, 3.4_

  - [x] 1.8 Implement forbidden dependency detection (Rule 6)
    - Parse import statements from generated Java files
    - Detect imports from `blocks.*.impl.*` packages
    - Allow imports from `blocks._shared.pure.*` and `blocks._shared.state.*`
    - Emit `GENERATED_FORBIDDEN_DEPENDENCY` with specific import in detail
    - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Rule 6: Forbidden Dependency Detection
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - Commit: `feat(taste-invariants): add TasteInvariantScanner with 6 rule implementations`

- [x] 2. Register rule IDs in `GovernanceRuleRegistry`
  - Read: `app/src/main/java/com/bear/app/GovernanceRuleRegistry.java` — add 6 new rule IDs to `PUBLIC_RULE_IDS`
  - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Integration with GovernanceRuleRegistry

  - [x] 2.1 Add 6 taste-invariant rule IDs to `PUBLIC_RULE_IDS` set
    - Add: `GENERATED_SURFACE_NAMING_VIOLATION`, `GENERATED_WIRING_NAMING_VIOLATION`, `GENERATED_ZONE_UNEXPECTED_PATH`, `GENERATED_SOURCE_STRUCTURE_VIOLATION`, `GENERATED_SOURCE_UNEXPECTED_FILE`, `GENERATED_FORBIDDEN_DEPENDENCY`
    - _Requirements: 6.1, 6.2_

  - Commit: `feat(taste-invariants): register 6 rule IDs in GovernanceRuleRegistry`

- [x] 3. Integrate scanner into `CheckCommandService`
  - Read: `app/src/main/java/com/bear/app/CheckCommandService.java` — add taste-invariant scanning after boundary bypass checks
  - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Integration with CheckCommandService
  - Depends on: Task 1 (TasteInvariantScanner exists)

  - [x] 3.1 Add taste-invariant scanning after boundary bypass checks in `executeCheck`
    - Parse allowlist using `PolicyAllowlistParser.parseExactPathAllowlist(projectRoot, TasteInvariantScanner.TASTE_INVARIANTS_ALLOWLIST_PATH)`
    - Call `TasteInvariantScanner.scanTasteInvariants(projectRoot, tasteAllowlist)`
    - Report findings with `EXIT_UNDECLARED_REACH` (exit code 6)
    - Format: `check: BOUNDARY_BYPASS: RULE=<rule>: <path>: <detail>`
    - _Requirements: 5.1, 5.2, 5.4, 5.5, 8.1, 8.3_

  - [x] 3.2 Ensure findings are combined with existing governance findings in deterministic order
    - Existing findings first, then taste findings sorted by path
    - _Requirements: 8.3_

  - Commit: `feat(taste-invariants): integrate TasteInvariantScanner into CheckCommandService`

- [x] 4. Add unit tests for `TasteInvariantScanner`
  - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Testing Strategy
  - New file: `app/src/test/java/com/bear/app/TasteInvariantScannerTest.java`
  - Depends on: Task 1 (TasteInvariantScanner exists)

  - [x] 4.1 Create `TasteInvariantScannerTest` class with `@TempDir` setup
    - Helper methods to create generated zone structure
    - _Requirements: 9.1, 9.2_

  - [x] 4.2 Add positive test for `GENERATED_SURFACE_NAMING_VIOLATION`
    - Create file `build/generated/bear/surfaces/BadName.json` (uppercase, wrong pattern)
    - Assert scanner emits finding with rule ID `GENERATED_SURFACE_NAMING_VIOLATION`
    - _Requirements: 9.1_

  - [x] 4.3 Add negative test for surface naming (valid structure)
    - Create file `build/generated/bear/surfaces/my-block.surface.json`
    - Assert scanner emits no findings for this file
    - _Requirements: 9.2_

  - [x] 4.4 Add positive test for `GENERATED_WIRING_NAMING_VIOLATION`
    - Create file `build/generated/bear/wiring/BadWiring.json`
    - Assert scanner emits finding with rule ID `GENERATED_WIRING_NAMING_VIOLATION`
    - _Requirements: 9.1_

  - [x] 4.5 Add negative test for wiring naming (valid structure)
    - Create file `build/generated/bear/wiring/my-block.wiring.json`
    - Assert scanner emits no findings for this file
    - _Requirements: 9.2_

  - [x] 4.6 Add positive test for `GENERATED_ZONE_UNEXPECTED_PATH`
    - Create unexpected directory `build/generated/bear/unexpected/`
    - Assert scanner emits finding with rule ID `GENERATED_ZONE_UNEXPECTED_PATH`
    - _Requirements: 9.1_

  - [x] 4.7 Add negative test for zone sprawl (valid structure)
    - Create only expected directories: surfaces/, wiring/, src/, gradle/
    - Assert scanner emits no zone sprawl findings
    - _Requirements: 9.2_

  - [x] 4.8 Add positive test for `GENERATED_SOURCE_STRUCTURE_VIOLATION`
    - Create file `build/generated/bear/src/main/java/wrong/package/Foo.java`
    - Assert scanner emits finding with rule ID `GENERATED_SOURCE_STRUCTURE_VIOLATION`
    - _Requirements: 9.1_

  - [x] 4.9 Add negative test for source structure (valid structure)
    - Create file `build/generated/bear/src/main/java/com/bear/generated/myblock/MyblockLogic.java`
    - Assert scanner emits no source structure findings
    - _Requirements: 9.2_

  - [x] 4.10 Add positive test for `GENERATED_SOURCE_UNEXPECTED_FILE`
    - Create file `build/generated/bear/src/main/java/com/bear/generated/myblock/RandomFile.java`
    - Assert scanner emits finding with rule ID `GENERATED_SOURCE_UNEXPECTED_FILE`
    - _Requirements: 9.1_

  - [x] 4.11 Add negative test for source file types (valid files)
    - Create files matching expected patterns: `MyblockLogic.java`, `Myblock_GetData.java`
    - Assert scanner emits no unexpected file findings
    - _Requirements: 9.2_

  - [x] 4.12 Add positive test for `GENERATED_FORBIDDEN_DEPENDENCY`
    - Create Java file with `import blocks.myblock.impl.SomeClass;`
    - Assert scanner emits finding with rule ID `GENERATED_FORBIDDEN_DEPENDENCY`
    - _Requirements: 9.1_

  - [x] 4.13 Add negative test for forbidden dependencies (allowed imports)
    - Create Java file with `import blocks._shared.pure.SomeType;`
    - Assert scanner emits no forbidden dependency findings
    - _Requirements: 9.2, 4.5_

  - [x] 4.14 Add test for allowlist suppression
    - Create violation, add path to allowlist
    - Assert scanner emits no findings for allowlisted path
    - _Requirements: 9.4_

  - [x] 4.15 Add test for deterministic output
    - Scan same generated zone twice
    - Assert findings are identical in both order and content
    - _Requirements: 9.3_

  - [x] 4.16 Add scope lock test for exactly 6 rule IDs
    - Assert `GovernanceRuleRegistry.PUBLIC_RULE_IDS` contains exactly the 6 taste-invariant rule IDs (plus existing rules)
    - Verify no additional taste-invariant rules exist
    - _Requirements: 9.5_

  - [x] 4.17 Add test for stable remediation text
    - Verify remediation text contains no timestamps, random IDs, or non-deterministic content
    - _Requirements: 9.6_

  - [x] 4.18 Run `./gradlew :app:test --tests "*.TasteInvariantScannerTest"` and confirm all tests pass

  - Commit: `test(taste-invariants): add unit tests for TasteInvariantScanner`

- [x] 5. Add property-based tests for correctness properties
  - Read: `.kiro/specs/p2-minimal-taste-invariants-rule-pack/design.md` §Correctness Properties
  - New file: `app/src/test/java/com/bear/app/TasteInvariantScannerProperties.java`
  - Depends on: Tasks 1, 3, 4 (scanner, integration, and unit tests complete)

  - [x] 5.1 Create `TasteInvariantScannerProperties` class with jqwik configuration
    - Tag format: `Feature: p2-minimal-taste-invariants-rule-pack, Property {number}: {property_text}`
    - Minimum 100 iterations per property
    - _Requirements: 9.1_

  - [x] 5.2 Write property test for Property 1: Surface/Wiring Naming Pattern Validation
    - **Property 1: Surface/Wiring Naming Pattern Validation**
    - **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
    - Generate arbitrary filenames; verify scanner emits finding iff filename doesn't match pattern

  - [x] 5.3 Write property test for Property 2: Zone Sprawl Detection
    - **Property 2: Zone Sprawl Detection**
    - **Validates: Requirements 2.1, 2.2**
    - Generate arbitrary paths under generated zone; verify scanner emits finding iff path not in known categories

  - [x] 5.4 Write property test for Property 3: Source Package Structure Validation
    - **Property 3: Source Package Structure Validation**
    - **Validates: Requirements 3.1, 3.2**
    - Generate arbitrary package paths; verify scanner emits finding iff not under `com.bear.generated.{block}/`

  - [x] 5.5 Write property test for Property 4: Source File Type Validation
    - **Property 4: Source File Type Validation**
    - **Validates: Requirements 3.3, 3.4**
    - Generate arbitrary Java filenames; verify scanner emits finding iff not matching expected patterns

  - [x] 5.6 Write property test for Property 5: Forbidden Dependency Detection
    - **Property 5: Forbidden Dependency Detection**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**
    - Generate arbitrary import statements; verify scanner emits finding iff import matches forbidden pattern

  - [x] 5.7 Write property test for Property 6: Allowlist Suppression
    - **Property 6: Allowlist Suppression**
    - **Validates: Requirements 2.3, 2.4, 7.1, 7.2**
    - Generate violations with matching allowlist entries; verify no findings emitted

  - [x] 5.8 Write property test for Property 7: Scan Determinism
    - **Property 7: Scan Determinism**
    - **Validates: Requirements 1.5, 2.5, 3.5, 5.3, 8.3**
    - Generate arbitrary generated zones; verify scanning twice produces identical findings

  - [x] 5.9 Write property test for Property 8: Failure Envelope Format
    - **Property 8: Failure Envelope Format**
    - **Validates: Requirements 5.1**
    - Verify all findings follow CODE/PATH/REMEDIATION format with deterministic content

  - [x] 5.10 Write property test for Property 9: Read-Only Scan
    - **Property 9: Read-Only Scan**
    - **Validates: Requirements 8.4**
    - Verify scanner does not modify any files during scan

  - [x] 5.11 Run `./gradlew :app:test --tests "*.TasteInvariantScannerProperties"` and confirm all property tests pass

  - Commit: `test(taste-invariants): add property-based tests for correctness properties`

- [x] 6. Full test suite verification
  - Read: `docs/context/start-here.md` — session close protocol requires docs guard test
  - Depends on: Tasks 1-5 (all implementation and tests complete)

  - [x] 6.1 Run `./gradlew test --no-daemon` and verify zero regressions across all modules
    - Pay particular attention to `TasteInvariantScannerTest`, `TasteInvariantScannerProperties`, and existing governance tests

  - [x] 6.2 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are within budget before updating state.md

  - Commit: no commit needed (verification only); if any fixes were required, commit with: `fix(taste-invariants): <description of what was fixed>`

- [-] 7. Session-end: update partner model, project state, and roadmap
  - Read: `.agent/skills/session-end/SKILL.md` — follow the BEAR-specific section (no devlogs; update `state.md` and `partner_model.md`)
  - Read: `docs/context/start-here.md` — session close protocol checklist
  - Read: `.agent/Knowledge/partner_model.md` — add a dated calibration note for this session
  - Read: `docs/context/state.md` — update handoff fields; check Session Notes length against `ContextDocsConsistencyTest` cap
  - Read: `docs/context/archive/archive-state-history.md` — if `state.md` Session Notes is approaching cap, move oldest notes here first
  - Read: `roadmap/board.md` — update status
  - Depends on: Task 6 (full suite green)

  - [x] 7.1 If `docs/context/state.md` Session Notes is approaching the `ContextDocsConsistencyTest` cap, move the oldest session notes to `docs/context/archive/archive-state-history.md` before adding new notes
    - Read: `docs/context/state.md` — check current Session Notes length
    - Read: `docs/context/archive/archive-state-history.md` — append oldest notes at the end

  - [x] 7.2 Update `docs/context/state.md`: set `Last Updated` to today's date, `Current Focus` to `p2-minimal-taste-invariants-rule-pack complete`, `Next Concrete Task` to next queued item from `roadmap/board.md`, add a brief session note summarizing what shipped
    - Read: `roadmap/board.md` — identify next item in Ready Queue

  - [x] 7.3 Update `.agent/Knowledge/partner_model.md`: add a dated entry under Calibration Notes summarizing what shipped this session, any new patterns observed (e.g., taste-invariant scanner pattern, zone sprawl detection), and any corrections received
    - Read: `.agent/Knowledge/partner_model.md` — add entry below the most recent dated section

  - [x] 7.4 Update `roadmap/board.md`: move `p2-minimal-taste-invariants-rule-pack` from Active to Completed; promote next Ready Queue item to Active if applicable
    - Read: `roadmap/board.md` — move item between sections

  - [x] 7.5 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are still within budget after updates

  - Commit: `chore: session-end — update state, partner model, and roadmap for taste-invariants rule pack`
  - Push: push all commits to the feature branch after this final commit
