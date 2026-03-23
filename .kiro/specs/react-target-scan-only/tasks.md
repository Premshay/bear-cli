# React Target — Full Pipeline: Implementation Tasks

## Execution Notes for Agents

### Repo standards
- Read: `AGENTS.md` — repo-level guardrails, session hygiene, safety rules
- Read: `.agent/Knowledge/partner_model.md` — collaboration style, BEAR-specific patterns, calibration notes
- Read: `docs/context/start-here.md` — session close protocol (mandatory before ending)
- Read: `docs/context/state.md` — current handoff state

### Commit discipline
- Commit after each top-level task completes (not after each sub-task)
- Commit message format: `feat(react): <short description of what was added>`
- Example: `feat(react): add ReactTargetDetector and ReactProjectShape enum`
- Push to `feature/multi-target-expansion` branch after Task 8 (full suite green)

### Testing documentation
- Read: `docs/context/architecture.md` — BEAR core principles and governance guarantees
- Read: `.kiro/specs/phase-p2-python-checking/design.md` — reference for PBT property patterns
- Read: `.kiro/specs/p3-capability-templates/tasks.md` — reference for task structure and session-end patterns

### Parallel execution guidance
- Tasks 1 and 2 are independent and CAN be executed in parallel
  - Task 1 creates detection infrastructure in `kernel/src/main/java/com/bear/kernel/target/react/`
  - Task 2 creates artifact generation in the same package — depends on Task 1 for `ReactProjectShape`
- Tasks 3, 4, 5, 6 depend on Tasks 1 and 2
- Task 7 depends on Tasks 3–6 (integration tests)
- Task 8 depends on Task 7 (full suite verification)
- Task 9 depends on Task 8 (session-end after all work is verified)

### State update cadence
- Update `docs/context/state.md` after Task 8 (full suite green)
- If `Session Notes` in `state.md` approaches the `ContextDocsConsistencyTest` cap, move oldest notes to `docs/context/archive/archive-state-history.md` first
- Update `.agent/Knowledge/partner_model.md` in Task 9 (session-end)
- Update `roadmap/board.md` and `roadmap/features/multi-target-foundation-phases.md` in Task 9

---

## Context Files

Spec:
- `.kiro/specs/react-target-scan-only/requirements.md`
- `.kiro/specs/react-target-scan-only/design.md`

Primary files to create:
- `kernel/src/main/java/com/bear/kernel/target/react/ReactProjectShape.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactTargetDetector.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactTarget.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactArtifactGenerator.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactManifestGenerator.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactImportSpecifierExtractor.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactImportBoundaryResolver.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactImportContainmentScanner.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactApiBoundaryScanner.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactProjectVerificationRunner.java` *(new)*
- `kernel/src/main/java/com/bear/kernel/target/react/ReactPrCheckContributor.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactTargetDetectorTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactTargetTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactArtifactGeneratorTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactImportSpecifierExtractorTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactImportBoundaryResolverTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactImportContainmentScannerTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactApiBoundaryScannerTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactProjectVerificationRunnerTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactPrCheckContributorTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/ReactCheckIntegrationTest.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/properties/ReactDetectionProperties.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/properties/ReactArtifactGenerationProperties.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/properties/ReactGovernedRootsProperties.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/properties/ReactImportContainmentProperties.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/properties/ReactDriftGateProperties.java` *(new)*
- `kernel/src/test/java/com/bear/kernel/target/react/properties/ReactProjectVerificationProperties.java` *(new)*

Primary files to modify:
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java` *(add react/react-dom exclusion)*
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` *(add ReactTarget + ReactTargetDetector)*

Test fixtures to create:
- `kernel/src/test/resources/fixtures/react/vite-react-single-block/`
- `kernel/src/test/resources/fixtures/react/vite-react-multi-block/`
- `kernel/src/test/resources/fixtures/react/vite-react-with-shared/`
- `kernel/src/test/resources/fixtures/react/nextjs-single-block/`
- `kernel/src/test/resources/fixtures/react/nextjs-use-client/`
- `kernel/src/test/resources/fixtures/react/invalid-workspace/`
- `kernel/src/test/resources/fixtures/react/invalid-ambiguous-shape/`
- `kernel/src/test/resources/fixtures/react/boundary-bypass-escape/`
- `kernel/src/test/resources/fixtures/react/boundary-bypass-sibling/`
- `kernel/src/test/resources/fixtures/react/boundary-bypass-bare-import/`
- `kernel/src/test/resources/fixtures/react/boundary-bypass-fetch/`
- `kernel/src/test/resources/fixtures/react/nextjs-alias-sibling/`
- `kernel/src/test/resources/fixtures/react/check-project-verification-failure/`
- `kernel/src/test/resources/fixtures/react/check-pr-check-boundary-expanding/`

Reference (read, do not edit):
- `kernel/src/main/java/com/bear/kernel/target/Target.java` — `Target` interface to implement
- `kernel/src/main/java/com/bear/kernel/target/TargetDetector.java` — `TargetDetector` interface
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` — `TargetId.REACT` enum value
- `kernel/src/main/java/com/bear/kernel/target/TargetManifestParsers.java` — shared wiring manifest parser
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java` — reference for detection patterns
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportSpecifierExtractor.java` — reuse for import extraction
- `kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java` — reference for non-JVM target patterns
- `kernel/src/main/java/com/bear/kernel/target/python/PythonProjectVerificationRunner.java` — reference for verification runner

Docs to update:
- `docs/context/state.md`
- `docs/context/archive/archive-state-history.md` (if state.md Session Notes approaches cap)
- `.agent/Knowledge/partner_model.md`
- `roadmap/features/multi-target-foundation-phases.md`
- `roadmap/board.md`

---

## Task List

- [x] 1. `ReactProjectShape` enum + `ReactTargetDetector`
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactTargetDetector — detection algorithm and sub-profile handling
  - Read: `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java` — reference for detection patterns
  - Read: `kernel/src/main/java/com/bear/kernel/target/TargetDetector.java` — interface to implement
  - New package: `com.bear.kernel.target.react` under `kernel/src/main/java/`

  - [x] 1.1 Create `ReactProjectShape.java` enum with values `VITE_REACT`, `NEXTJS_APP_ROUTER`
  - [x] 1.2 Create `ReactTargetDetector.java` implementing `TargetDetector`
  - [x] 1.3 Detection: parse `package.json` for `type:module`, `packageManager` starting with `pnpm`, `react`+`react-dom` in `dependencies`
  - [x] 1.4 Detection: check `pnpm-lock.yaml`, `tsconfig.json` presence
  - [x] 1.5 Detection: check `pnpm-workspace.yaml` → UNSUPPORTED
  - [x] 1.6 Detection: check `vite.config.ts` + `next.config.*` presence → sub-profile or UNSUPPORTED on ambiguous
  - [x] 1.7 Update `NodeTargetDetector` to return `NONE` when `react` or `react-dom` in `package.json` dependencies
  - [x] 1.8 Write `ReactTargetDetectorTest.java` covering all 11 acceptance criteria
  - [x] 1.9 Write `ReactDetectionProperties.java` (P1–P6)
    - Property 1: Detection completeness (Vite+React) — Validates: Requirement 1.1
    - Property 2: Detection exclusivity (Next.js) — Validates: Requirement 1.2
    - Property 3: Detection NONE on missing react dep — Validates: Requirement 1.7
    - Property 4: Detection UNSUPPORTED on workspace — Validates: Requirement 1.3
    - Property 5: Detection UNSUPPORTED on ambiguous shape — Validates: Requirement 1.11
    - Property 6: Node exclusion — Validates: Requirement 3.1, 3.2
  - [x] 1.10 Create fixture directories: `vite-react-single-block`, `nextjs-single-block`, `invalid-workspace`, `invalid-ambiguous-shape`
  - [x] 1.11 Run `./gradlew :kernel:test --tests "*.ReactTargetDetectorTest" --tests "*.ReactDetectionProperties"` and confirm all tests pass

  - Commit: `feat(react): add ReactProjectShape enum and ReactTargetDetector`

- [x] 2. `ReactArtifactGenerator` + `ReactManifestGenerator`
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactArtifactGenerator — generated file layout and naming conventions
  - Read: `kernel/src/main/java/com/bear/kernel/target/TargetManifestParsers.java` — wiring manifest schema
  - Depends on: Task 1 (ReactProjectShape enum exists)

  - [x] 2.1 Create `ReactArtifactGenerator.java` with `generatePorts()`, `generateLogic()`, `generateWrapper()`, `generateUserImplSkeleton()`
  - [x] 2.2 Implement kebab-case → PascalCase block name derivation (`ReactLexicalSupport` or inline helper)
  - [x] 2.3 Use React-flavored suffixes: `FeaturePorts`, `FeatureLogic`, `FeatureWrapper`; impl skeleton as `.tsx`
  - [x] 2.4 Implement `writeIfDifferent()` with `StandardOpenOption.SYNC` for byte-stable writes
  - [x] 2.5 Create `ReactManifestGenerator.java` delegating to `TargetManifestParsers` schema
  - [x] 2.6 Write `ReactArtifactGeneratorTest.java` covering all 10 acceptance criteria from Requirement 4
  - [x] 2.7 Write `ReactArtifactGenerationProperties.java` (P7–P10)
    - Property 7: Round-trip stability — Validates: Requirement 5.3
    - Property 8: PascalCase naming — Validates: Requirement 4.10
    - Property 9: Impl preservation — Validates: Requirement 4.6
    - Property 10: Wiring manifest present — Validates: Requirement 4.4
  - [x] 2.8 Populate `vite-react-single-block` fixture with generated artifacts and IR file
  - [x] 2.9 Run `./gradlew :kernel:test --tests "*.ReactArtifactGeneratorTest" --tests "*.ReactArtifactGenerationProperties"` and confirm all tests pass

  - Commit: `feat(react): add ReactArtifactGenerator and ReactManifestGenerator`

- [x] 3. `ReactTarget` skeleton + `TargetRegistry` wiring
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactTarget — interface method mapping table
  - Read: `kernel/src/main/java/com/bear/kernel/target/Target.java` — interface to implement
  - Read: `kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java` — reference for non-JVM target patterns
  - Depends on: Tasks 1 and 2

  - [x] 3.1 Create `ReactTarget.java` implementing all `Target` interface methods
  - [x] 3.2 `targetId()` → `TargetId.REACT`; `defaultProfile()` → `GovernanceProfile.of(TargetId.REACT, "feature-ui")`
  - [x] 3.3 `compile()` delegates to `ReactArtifactGenerator` + `ReactManifestGenerator`
  - [x] 3.4 `generateWiringOnly()` delegates to `ReactManifestGenerator` only
  - [x] 3.5 `parseWiringManifest()` delegates to `TargetManifestParsers.parseWiringManifest()`
  - [x] 3.6 `prepareCheckWorkspace()` creates `src/shared/` in tempRoot if present in projectRoot
  - [x] 3.7 `ownedGeneratedPrefixes()` returns `build/generated/bear/types/<blockKey>/` + wiring path
  - [x] 3.8 JVM-specific stubs: `containmentSkipInfoLine()` → null, `preflightContainmentIfRequired()` → null, `verifyContainmentMarkersIfRequired()` → null
  - [x] 3.9 No-op stubs: `scanUndeclaredReach()` → `List.of()`, `scanForbiddenReflectionDispatch()` → `List.of()`, `scanPortImplContainmentBypass()` → `List.of()`, `scanBlockPortBindings()` → `List.of()`, `scanMultiBlockPortImplAllowedSignals()` → `List.of()`
  - [x] 3.10 `blockDeclaresAllowedDeps()` parses IR, returns true if `impl.allowedDeps` non-empty
  - [x] 3.11 `checkDrift()` public method: compile to temp dir, byte-compare generated artifacts, exclude user impl
  - [x] 3.12 Add `ReactTarget` + `ReactTargetDetector` to `TargetRegistry.defaultRegistry()`
  - [x] 3.13 Write `ReactTargetTest.java` covering interface contract, allowedDeps guard, drift gate
  - [x] 3.14 Write `ReactGovernedRootsProperties.java` (governed roots computation)
  - [x] 3.15 Write `ReactDriftGateProperties.java` (P19–P20)
    - Property 19: No drift on fresh compile — Validates: Requirement 10.1
    - Property 20: Drift detected on modification — Validates: Requirement 10.2
  - [x] 3.16 Verify all existing JVM, Node, Python tests still pass
  - [x] 3.17 Run `./gradlew :kernel:test --tests "*.ReactTargetTest" --tests "*.ReactGovernedRootsProperties" --tests "*.ReactDriftGateProperties"` and confirm all tests pass

  - Commit: `feat(react): add ReactTarget skeleton and TargetRegistry wiring`

- [x] 4. `ReactImportContainmentScanner`
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactImportContainmentScanner — scan algorithm and boundary resolution
  - Read: `kernel/src/main/java/com/bear/kernel/target/node/NodeImportSpecifierExtractor.java` — reuse for import extraction
  - Depends on: Task 3

  - [x] 4.1 Create `ReactImportSpecifierExtractor.java` (thin wrapper reusing `NodeImportSpecifierExtractor`, extended for `.tsx`)
  - [x] 4.2 Create `ReactImportBoundaryResolver.java` with full resolution logic: relative, bare, `@/`, `#`, URL-like specifiers
  - [x] 4.3 Implement `@/*` alias resolution for `nextjs-app-router` sub-profile (replace `@/` with `./src/`)
  - [x] 4.4 Implement `detectSubProfile(projectRoot)` — lightweight re-detection via `next.config.*` presence
  - [x] 4.5 Implement `detectDirective(content)` — detect `"use client"` / `"use server"` as first statement
  - [x] 4.6 Create `ReactImportContainmentScanner.java` orchestrating governed roots, file collection, per-file scanning
  - [x] 4.7 Wire `scanBoundaryBypass()` in `ReactTarget` to call both `ReactImportContainmentScanner` and `ReactApiBoundaryScanner`, merge and sort findings
  - [x] 4.8 Write `ReactImportSpecifierExtractorTest.java` (static imports, export re-exports, side-effect imports, JSX files)
  - [x] 4.9 Write `ReactImportBoundaryResolverTest.java` covering all specifier types and both sub-profiles
  - [x] 4.10 Write `ReactImportContainmentScannerTest.java` covering all 12 acceptance criteria from Requirement 7 + Requirement 8
  - [x] 4.11 Write `ReactImportContainmentProperties.java` (P11–P18)
    - Property 11: Intra-feature imports allowed — Validates: Requirement 7.1
    - Property 12: Shared imports allowed — Validates: Requirement 7.2
    - Property 13: react/react-dom allowed — Validates: Requirement 7.4, 7.5
    - Property 14: Sibling feature imports fail — Validates: Requirement 7.7
    - Property 15: Bare package imports fail — Validates: Requirement 7.8
    - Property 16: Next.js @/* alias within same feature allowed — Validates: Requirement 8.2
    - Property 17: Next.js @/* alias to sibling fails — Validates: Requirement 8.3
    - Property 18: Vite @/* alias always fails — Validates: Requirement 8.5
  - [x] 4.12 Create fixtures: `vite-react-multi-block`, `vite-react-with-shared`, `nextjs-use-client`, `boundary-bypass-escape`, `boundary-bypass-sibling`, `boundary-bypass-bare-import`, `nextjs-alias-sibling`
  - [x] 4.13 Run `./gradlew :kernel:test --tests "*.ReactImport*"` and confirm all tests pass

  - Commit: `feat(react): add ReactImportContainmentScanner and boundary resolution`

- [x] 5. `ReactApiBoundaryScanner`
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactApiBoundaryScanner — PARTIAL scanner for fetch/XHR detection
  - Depends on: Task 4

  - [x] 5.1 Create `ReactApiBoundaryScanner.java` scanning `.tsx` files for `fetch(` and `new XMLHttpRequest(`
  - [x] 5.2 Exclude `.ts` files (non-component) from API boundary scanning
  - [x] 5.3 Exclude test files (`*.test.tsx`, `*.spec.tsx`) from scanning
  - [x] 5.4 Write `ReactApiBoundaryScannerTest.java` covering all 6 acceptance criteria from Requirement 12
  - [x] 5.5 Create fixture: `boundary-bypass-fetch`
  - [x] 5.6 Run `./gradlew :kernel:test --tests "*.ReactApiBoundaryScannerTest"` and confirm all tests pass

  - Commit: `feat(react): add ReactApiBoundaryScanner for fetch/XHR detection`

- [x] 6. `ReactProjectVerificationRunner`
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactProjectVerificationRunner — pnpm exec tsc verification
  - Read: `kernel/src/main/java/com/bear/kernel/target/python/PythonProjectVerificationRunner.java` — reference for verification runner pattern
  - Depends on: Task 3

  - [x] 6.1 Create `ReactProjectVerificationRunner.java` running `pnpm exec tsc --noEmit -p tsconfig.json`
  - [x] 6.2 Implement `isPnpmAvailable()` check via `which pnpm` / `where pnpm`
  - [x] 6.3 Capture stdout+stderr via background thread; 300-second timeout with `destroyForcibly()`
  - [x] 6.4 Map exit codes: 0 → PASSED, non-zero → FAILED, pnpm missing → BOOTSTRAP_IO, tsc missing → BOOTSTRAP_IO, timeout → TIMEOUT
  - [x] 6.5 Wire `runProjectVerification()` in `ReactTarget` to delegate to `ReactProjectVerificationRunner`
  - [x] 6.6 Write `ReactProjectVerificationRunnerTest.java` covering all 7 acceptance criteria from Requirement 14
  - [x] 6.7 Write `ReactProjectVerificationProperties.java` (P21–P23)
    - Property 21: PASSED on zero tsc exit — Validates: Requirement 14.2
    - Property 22: FAILED on non-zero tsc exit — Validates: Requirement 14.3
    - Property 23: BOOTSTRAP_IO on missing pnpm — Validates: Requirement 14.4
  - [x] 6.8 Create fixture: `check-project-verification-failure`
  - [x] 6.9 Run `./gradlew :kernel:test --tests "*.ReactProjectVerification*"` and confirm all tests pass

  - Commit: `feat(react): add ReactProjectVerificationRunner for tsc verification`

- [x] 7. `ReactPrCheckContributor` + integration tests
  - Read: `.kiro/specs/react-target-scan-only/design.md` §ReactPrCheckContributor — dependency governance delta
  - Depends on: Tasks 3–6

  - [x] 7.1 Create `ReactPrCheckContributor.java` computing `package.json` + `pnpm-lock.yaml` delta between base and head snapshots
  - [x] 7.2 Classify any diff in either file as `BOUNDARY_EXPANDING`
  - [x] 7.3 Write `ReactPrCheckContributorTest.java` covering all 5 acceptance criteria from Requirement 15
  - [x] 7.4 Create fixture: `check-pr-check-boundary-expanding`
  - [x] 7.5 Write `ReactCheckIntegrationTest.java` exercising full `bear check` flow against `vite-react-single-block` and `nextjs-single-block` fixtures
  - [x] 7.6 Verify exit codes: clean → 0, boundary bypass → 7, drift → 5, allowedDeps → 64
  - [x] 7.7 Run `./gradlew :kernel:test --tests "*.ReactPrCheckContributorTest" --tests "*.ReactCheckIntegrationTest"` and confirm all tests pass

  - Commit: `feat(react): add ReactPrCheckContributor and integration tests`

- [x] 8. Full test suite verification
  - Read: `docs/context/start-here.md` — session close protocol requires docs guard test
  - Read: `kernel/src/test/java/com/bear/kernel/target/jvm/` — JVM tests must remain green
  - Read: `kernel/src/test/java/com/bear/kernel/target/node/` — Node tests must remain green
  - Read: `kernel/src/test/java/com/bear/kernel/target/python/` — Python tests must remain green
  - Depends on: Task 7

  - [x] 8.1 Run `./gradlew test --no-daemon` and verify zero regressions across all modules
  - [x] 8.2 Confirm all 23 correctness properties pass (100+ iterations each)
  - [x] 8.3 Confirm all existing JVM, Node, Python tests pass without modification
  - [x] 8.4 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are within budget before updating state.md

  - Commit: no commit needed (verification only); if any fixes were required, commit with: `fix(react): <description of what was fixed>`

- [x] 9. Session-end: update partner model, project state, and roadmap
  - Read: `.agent/skills/session-end/SKILL.md` — follow the BEAR-specific section (no devlogs; update `state.md` and `partner_model.md`)
  - Read: `docs/context/start-here.md` — session close protocol checklist
  - Read: `.agent/Knowledge/partner_model.md` — add a dated calibration note for this session
  - Read: `docs/context/state.md` — update handoff fields; check Session Notes length against `ContextDocsConsistencyTest` cap
  - Read: `docs/context/archive/archive-state-history.md` — if `state.md` Session Notes is approaching cap, move oldest notes here first
  - Read: `roadmap/board.md` and `roadmap/features/multi-target-foundation-phases.md` — update status
  - Depends on: Task 8

  - [x] 9.1 If `docs/context/state.md` Session Notes is approaching the `ContextDocsConsistencyTest` cap, move the oldest session notes to `docs/context/archive/archive-state-history.md` before adding new notes
    - Read: `docs/context/state.md` — check current Session Notes length
    - Read: `docs/context/archive/archive-state-history.md` — append oldest notes at the end

  - [x] 9.2 Update `docs/context/state.md`: set `Last Updated` to today's date, `Current Focus` to `Phase R (React full pipeline) complete`, `Next Concrete Task` to next queued item from `roadmap/board.md`, add a brief session note summarizing what shipped
    - Read: `roadmap/board.md` — identify next item in Ready Queue

  - [x] 9.3 Update `.agent/Knowledge/partner_model.md`: add a dated entry under Calibration Notes summarizing what shipped this session, any new patterns observed (e.g., React sub-profile detection, @/* alias resolution, API boundary scanning), and any corrections received
    - Read: `.agent/Knowledge/partner_model.md` — add entry below the most recent dated section

  - [x] 9.4 Update `roadmap/features/multi-target-foundation-phases.md`: add Phase R (React full pipeline) entry with completion date
    - Read: `roadmap/features/multi-target-foundation-phases.md` — add new phase entry

  - [x] 9.5 Update `roadmap/board.md`: move Phase R to Completed if applicable; promote next Ready Queue item to Active
    - Read: `roadmap/board.md` — move item between sections

  - [x] 9.6 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are still within budget after updates

  - Commit: `chore: session-end — update state, partner model, and roadmap for React target`
  - Push: push all commits to `feature/multi-target-expansion` branch after this final commit
