# Tasks: Phase C — Node Target (Runtime Execution)

## Implementation Tasks

- [x] 1. `NodeManifestParser`
  - **Context:** Read `design.md` §NodeManifestParser — Node wiring manifest schema, field mapping table, error codes. Reference `TargetManifestParsers` for `extractRequiredString()` / `extractRequiredArrayPayload()` utility methods.
  - **Execution:** New class; independent of tasks 2–9. Tasks 2 and 8 depend on this.
  - **Verification:** Run tests after completion. Confirm `NodeManifestParserTest` and `ManifestParsingProperties` all pass.

  - [x] 1.1 Create `NodeManifestParser.java`
    - Static `parse(Path)` method; regex-based JSON parsing (no Jackson)
    - Validate `version == "1"`, require `blockKey`; map Node JSON fields to `WiringManifest` record
    - Throw `ManifestParseException` with codes: `MALFORMED_JSON`, `MISSING_KEY_version`, `MISSING_KEY_blockKey`, `UNSUPPORTED_WIRING_SCHEMA_VERSION`
    - _Requirements: 1.1–1.6_

  - [x] 1.2 Write `NodeManifestParserTest.java`
    - Valid manifest → all fields populated; `version` missing → `MISSING_KEY_version`; `blockKey` missing → `MISSING_KEY_blockKey`; `version="2"` → `UNSUPPORTED_WIRING_SCHEMA_VERSION`; malformed JSON → `MALFORMED_JSON`; empty `wrappers`/`ports` → `List.of()`
    - _Requirements: 1.1–1.6_

  - [x] 1.3 Write `ManifestParsingProperties.java` (property-style tests)
    - **Property 1: Manifest parsing populates all fields** — Validates: Requirements 1.1, 1.2
    - **Property 2: Manifest round-trip stability** — Validates: Requirement 1.9
    - **Property 3: Malformed JSON rejected** — Validates: Requirement 1.5
    - **Property 4: Unsupported version rejected** — Validates: Requirement 1.6
    - **Property 18: `NodeTarget.parseWiringManifest()` delegates to `NodeManifestParser`** — Validates: Requirements 1.7, 1.8

  - Commit: `feat(node): add NodeManifestParser`

- [x] 2. `NodeTarget` stub resolution + `parseWiringManifest()` + `prepareCheckWorkspace()`
  - **Context:** Read `design.md` §NodeTarget — stub replacement table, `prepareCheckWorkspace()` implementation, `parseWiringManifest()` delegation. Reference `ReactTarget` and `PythonTarget` for structural pattern.
  - **Execution:** Depends on task 1 (`NodeManifestParser` exists). Task 4 depends on this.
  - **Verification:** Run `NodeTargetTest` after completion. Confirm all 8 stubs return expected values without throwing.

  - [x] 2.1 Replace all 8 `UnsupportedOperationException` stubs in `NodeTarget` with `null`/`List.of()` returns
    - `containmentSkipInfoLine()` → `null`; `preflightContainmentIfRequired()` → `null`; `verifyContainmentMarkersIfRequired()` → `null`
    - `scanPortImplContainmentBypass()` → `List.of()`; `scanBlockPortBindings()` → `List.of()`; `scanMultiBlockPortImplAllowedSignals()` → `List.of()`; `scanUndeclaredReach()` → `List.of()`; `scanForbiddenReflectionDispatch()` → `List.of()`
    - _Requirements: 3.1–3.9_

  - [x] 2.2 Implement `NodeTarget.parseWiringManifest()` delegating to `NodeManifestParser.parse()`
    - _Requirements: 1.7, 1.8_

  - [x] 2.3 Implement `NodeTarget.prepareCheckWorkspace(projectRoot, tempRoot)`
    - Copy `src/blocks/_shared/` to `tempRoot/src/blocks/_shared/` if present; no-op if absent
    - Follow same pattern as `PythonTarget.prepareCheckWorkspace()` and `ReactTarget.prepareCheckWorkspace()`
    - _Requirements: 2.1–2.3_

  - [x] 2.4 Update `NodeTargetTest.java`
    - All 8 stub methods return expected value, no exception; `prepareCheckWorkspace()` with `_shared` → directory created; `prepareCheckWorkspace()` without `_shared` → no error; `parseWiringManifest()` on Node manifest → `schemaVersion="1"`
    - _Requirements: 1.7, 1.8, 2.1–2.3, 3.1–3.9_

  - [x] 2.5 Write property tests in `ManifestParsingProperties.java` (add to task 1.3 file)
    - **Property 5: Check workspace preparation with `_shared`** — Validates: Requirement 2.1
    - **Property 6: JVM stub methods never throw** — Validates: Requirements 3.1–3.9
    - **Property 21: `prepareCheckWorkspace()` no-op when `_shared` absent** — Validates: Requirement 2.2

  - Commit: `feat(node): resolve NodeTarget stubs, wire parseWiringManifest and prepareCheckWorkspace`

- [x] 3. `NodeProjectVerificationRunner`
  - **Context:** Read `design.md` §NodeProjectVerificationRunner — `run()` algorithm, `isTscMissing()` patterns, exit code mapping. Reference `ReactProjectVerificationRunner` as the exact structural model.
  - **Execution:** Independent of tasks 1–2. Task 4 depends on this.
  - **Verification:** Run `NodeProjectVerificationRunnerTest` and `ProjectVerificationProperties` after completion.

  - [x] 3.1 Create `NodeProjectVerificationRunner.java`
    - `isPnpmAvailable()` via `which pnpm` / `where pnpm`, 10-second timeout
    - `run(projectRoot)`: `pnpm exec tsc --noEmit`; stdout+stderr captured via daemon thread; 300-second timeout with `destroyForcibly()`
    - Exit code mapping: `0` → `PASSED`; non-zero → `FAILED`; pnpm missing → `BOOTSTRAP_IO`; `isTscMissing()` → `BOOTSTRAP_IO`; timeout → `TIMEOUT`
    - `phase` field always `"tsc"` on returned `ProjectTestResult`
    - Follow `ReactProjectVerificationRunner` structure exactly
    - _Requirements: 4.1–4.8_

  - [x] 3.2 Write `NodeProjectVerificationRunnerTest.java`
    - `isTscMissing()` with `"Cannot find module"` + typescript reference → `true`; `"command \"tsc\" not found"` → `true`; normal tsc error → `false`; pnpm unavailable → `BOOTSTRAP_IO`; result phase always `"tsc"`
    - _Requirements: 4.1–4.8_

  - [x] 3.3 Write `ProjectVerificationProperties.java` (property-style tests)
    - **Property 7: BOOTSTRAP_IO on tsc-missing output patterns** — Validates: Requirement 4.5
    - **Property 19: `NodeProjectVerificationRunner` phase tag** — Validates: Requirement 4.9

  - Commit: `feat(node): add NodeProjectVerificationRunner`

- [x] 4. `NodeTarget.runProjectVerification()` wiring
  - **Context:** Read `design.md` §NodeTarget `runProjectVerification()` — delegation pattern; `initScriptRelativePath` is JVM-specific and ignored for Node.
  - **Execution:** Depends on tasks 2 and 3. Task 8 depends on this.
  - **Verification:** Run `NodeTargetTest` after completion. Confirm `runProjectVerification()` returns result with `phase="tsc"`.

  - [x] 4.1 Implement `NodeTarget.runProjectVerification()` delegating to `NodeProjectVerificationRunner.run()`
    - Ignore `initScriptRelativePath` parameter (JVM-specific)
    - _Requirements: 4.9_

  - [x] 4.2 Update `NodeTargetTest.java`
    - `runProjectVerification()` returns result with `phase="tsc"`
    - _Requirements: 4.9_

  - Commit: `feat(node): wire NodeTarget.runProjectVerification to NodeProjectVerificationRunner`

- [x] 5. Dynamic import enforcement in `NodeImportContainmentScanner`
  - **Context:** Read `design.md` §Dynamic Import Enforcement — change in `scan()` loop; `NodeDynamicImportDetector` itself is unchanged.
  - **Execution:** Independent of tasks 1–4. Task 8 depends on this.
  - **Verification:** Run `NodeImportContainmentScannerTest` and `DynamicImportEnforcementProperties` after completion.

  - [x] 5.1 Update `NodeImportContainmentScanner.scan()` to promote `NodeDynamicImportDetector` results from advisory to enforced
    - Each `DynamicImport` produces a `BoundaryBypassFinding` with `rule="DYNAMIC_IMPORT_FORBIDDEN"`, repo-relative `path`, and `detail` containing the specifier
    - Collect all violations before returning (no early exit)
    - _Requirements: 5.1–5.6_

  - [x] 5.2 Update `NodeImportContainmentScannerTest.java`
    - File with `import('./other')` → `DYNAMIC_IMPORT_FORBIDDEN` finding; no dynamic imports → no such findings; 3 dynamic imports → 3 findings; static bypass + dynamic import → both findings present; `path` is repo-relative, `detail` contains specifier
    - _Requirements: 5.1–5.6_

  - [x] 5.3 Write `DynamicImportEnforcementProperties.java` (property-style tests)
    - **Property 8: Dynamic import findings include path and specifier** — Validates: Requirements 5.1, 5.4
    - **Property 9: No dynamic import findings for clean files** — Validates: Requirement 5.2
    - **Property 10: All dynamic imports collected before reporting** — Validates: Requirement 5.6
    - **Property 17: Deterministic scan output** — Validates: Requirement 7.8
    - **Property 22: Both static bypass and dynamic import reported independently** — Validates: Requirement 5.5

  - Commit: `feat(node): enforce dynamic imports in NodeImportContainmentScanner`

- [x] 6. `NodePathAliasResolver`
  - **Context:** Read `design.md` §NodePathAliasResolver — resolution algorithm, caching behavior, `getAlias()` regex pattern. No Jackson; regex-based tsconfig parsing.
  - **Execution:** Independent of tasks 1–5. Task 7 depends on this.
  - **Verification:** Run `NodePathAliasResolverTest` and `PathAliasResolutionProperties` after completion.

  - [x] 6.1 Create `NodePathAliasResolver.java`
    - `resolve(specifier, projectRoot)`: returns `Optional.empty()` for non-`@/` specifiers; reads `tsconfig.json` `compilerOptions.paths["@/*"][0]`; resolves `@/<path>` to `projectRoot/<alias-prefix>/<path>`
    - `getAlias(projectRoot)`: cached per instance; regex `"@/\\*"\\s*:\\s*\\[\\s*"([^"]+)"` on tsconfig content
    - Returns `Optional.empty()` when tsconfig missing, unreadable, or no `@/*` entry; uses first array element only
    - _Requirements: 6.1, 6.6–6.10_

  - [x] 6.2 Write `NodePathAliasResolverTest.java`
    - `@/*` → `["./src/*"]` → resolves `@/foo/bar` to `projectRoot/src/foo/bar`; missing tsconfig → `Optional.empty()`; no `compilerOptions.paths` → `Optional.empty()`; multi-element array → first element only; only `#utils` entry → `Optional.empty()` for `@/` specifiers; second call reads file only once
    - _Requirements: 6.1, 6.6–6.10_

  - [x] 6.3 Write `PathAliasResolutionProperties.java` (property-style tests)
    - **Property 11: `@/*` alias resolves to `src/` subtree** — Validates: Requirement 6.1
    - **Property 16: Non-`@/*` alias entries ignored** — Validates: Requirement 6.7
    - **Property 20: `NodePathAliasResolver` caches tsconfig read** — Validates: Requirement 6.10

  - Commit: `feat(node): add NodePathAliasResolver`

- [x] 7. `NodeImportBoundaryResolver` `@/*` alias resolution
  - **Context:** Read `design.md` §NodeImportBoundaryResolver — updated `resolve()` logic; `NodePathAliasResolver` injected via constructor; `NodeImportContainmentScanner` constructs one resolver per scan and passes it in.
  - **Execution:** Depends on task 6 (`NodePathAliasResolver` exists). Task 8 depends on this.
  - **Verification:** Run `NodeImportBoundaryResolverTest` after completion. Confirm all existing tests still pass.

  - [x] 7.1 Update `NodeImportBoundaryResolver` to accept `NodePathAliasResolver` as a constructor parameter
    - Insert `@/*` alias resolution before the existing bare-specifier check
    - `@/` specifier with alias configured: resolve to path, apply same boundary rules as relative imports (same block → allowed; `_shared` → allowed; generated dir → allowed; otherwise → `fail("BOUNDARY_BYPASS")`)
    - `@/` specifier with no alias configured → `fail("BOUNDARY_BYPASS")`
    - _Requirements: 6.2–6.5_

  - [x] 7.2 Update `NodeImportContainmentScanner` to construct one `NodePathAliasResolver` per scan and pass it to `NodeImportBoundaryResolver`
    - _Requirements: 6.10_

  - [x] 7.3 Update `NodeImportBoundaryResolverTest.java`
    - `@/blocks/my-block/foo` with alias, same block → `allowed()`; `@/blocks/other-block/foo` with alias → `fail("BOUNDARY_BYPASS")`; `@/blocks/_shared/utils` with alias → `allowed()`; `@/outside/path` with alias → `fail("BOUNDARY_BYPASS")`; `@/foo` with no alias → `fail("BOUNDARY_BYPASS")`; `@/foo` with missing tsconfig → `fail("BOUNDARY_BYPASS")`
    - Confirm all existing Phase B resolver tests still pass
    - _Requirements: 6.2–6.5_

  - [x] 7.4 Add alias boundary properties to `PathAliasResolutionProperties.java`
    - **Property 12: `@/*` alias to same block passes** — Validates: Requirement 6.2
    - **Property 13: `@/*` alias to sibling block fails** — Validates: Requirement 6.3
    - **Property 14: `@/*` alias to `_shared` passes** — Validates: Requirement 6.4
    - **Property 15: `@/*` alias to ungoverned path fails** — Validates: Requirement 6.5

  - Commit: `feat(node): add @/* alias resolution to NodeImportBoundaryResolver`

- [x] 8. Integration test fixtures + `NodeCheckIntegrationTest`
  - **Context:** Read `design.md` §Integration Test Fixtures — fixture structures for all 6 new scenarios. Reference `kernel/src/test/resources/fixtures/node/` existing fixtures for layout pattern.
  - **Execution:** Depends on tasks 1–7 (all components complete). Task 9 depends on this.
  - **Verification:** Run full integration test suite. Confirm all 6 new fixtures produce expected exit codes.

  - [x] 8.1 Create `node/check-clean/` fixture
    - `package.json`, `pnpm-lock.yaml`, `tsconfig.json` (with `@/*` alias), Node wiring manifest, `src/blocks/my-block/` with clean static imports only
    - _Requirements: 7.1_

  - [x] 8.2 Create `node/check-dynamic-import/` fixture
    - Same base as `check-clean`; `src/blocks/my-block/index.ts` contains `import('./other')`
    - _Requirements: 7.2_

  - [x] 8.3 Create `node/check-project-verification-failure/` fixture
    - Same base; governed file contains a TypeScript type error
    - _Requirements: 7.3_

  - [x] 8.4 Create `node/check-alias-same-block/` fixture
    - `tsconfig.json` with `@/*` alias; `index.ts` imports `@/blocks/my-block/utils`; `utils.ts` exists in same block
    - _Requirements: 7.6_

  - [x] 8.5 Create `node/check-alias-sibling-block/` fixture
    - Two blocks; `index.ts` imports `@/blocks/other-block/utils`
    - _Requirements: 7.7_

  - [x] 8.6 Create `node/check-alias-no-tsconfig-paths/` fixture
    - `tsconfig.json` present but no `compilerOptions.paths`; governed file uses `@/` import
    - _Requirements: 7.5_

  - [x] 8.7 Write `NodeCheckIntegrationTest.java`
    - `check-clean` → exit 0; `check-dynamic-import` → exit 7, `DYNAMIC_IMPORT_FORBIDDEN`; `check-project-verification-failure` → exit 4; `check-alias-same-block` → exit 0; `check-alias-sibling-block` → exit 7, `BOUNDARY_BYPASS`; `check-alias-no-tsconfig-paths` → exit 7, `BOUNDARY_BYPASS`
    - _Requirements: 7.1–7.8_

  - Commit: `feat(node): add Phase C integration test fixtures and NodeCheckIntegrationTest`

- [x] 9. Regression verification — full test suite
  - **Context:** Read `docs/context/start-here.md` — session close protocol requires docs guard test. All existing Phase B, JVM, Python, React tests must pass without modification.
  - **Execution:** Depends on task 8 (all work complete).
  - **Verification:** Zero failures across all modules; all 22 correctness properties pass (100+ iterations each).

  - [x] 9.1 Run `./gradlew :kernel:test --no-daemon` and confirm zero failures
    - Verify all 22 correctness properties pass
    - Confirm all existing Phase B Node tests pass without modification
    - _Requirements: 7.8_

  - [x] 9.2 Run `./gradlew :app:test --no-daemon` and confirm zero failures
    - Confirm all existing JVM, Python, React tests pass without modification

  - [x] 9.3 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs are within budget before updating `state.md`

  - Commit: no commit needed (verification only); if any fixes required: `fix(node): <description>`

- [x] 10. Session-end: update state, partner model, and roadmap
  - Read: `.agent/skills/session-end/SKILL.md` — follow the BEAR-specific section
  - Read: `docs/context/start-here.md` — session close protocol checklist
  - Depends on: task 9

  - [x] 10.1 If `docs/context/state.md` Session Notes is approaching the `ContextDocsConsistencyTest` cap, move oldest notes to `docs/context/archive/archive-state-history.md` first

  - [x] 10.2 Update `docs/context/state.md`: `Last Updated`, `Current Focus` → Phase C complete, `Next Concrete Task` from `roadmap/board.md`, brief session note

  - [x] 10.3 Update `.agent/Knowledge/partner_model.md`: add dated calibration note summarizing what shipped (NodeManifestParser, NodeProjectVerificationRunner, NodePathAliasResolver, dynamic import enforcement, `@/*` alias resolution)

  - [x] 10.4 Update `roadmap/features/multi-target-foundation-phases.md`: add Phase C entry with completion date

  - [x] 10.5 Update `roadmap/board.md`: move Phase C to Completed; promote next Ready Queue item to Active

  - [x] 10.6 Run `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` to confirm docs still within budget after updates

  - Commit: `chore: session-end — update state, partner model, and roadmap for Phase C`
