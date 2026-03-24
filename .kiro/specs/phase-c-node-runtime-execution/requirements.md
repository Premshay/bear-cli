# Phase C: Node Target — Runtime Execution

Phase C completes the Node/TypeScript target by implementing the runtime execution capabilities
that were explicitly deferred from Phase B (scan-only). After this phase, a Node/TypeScript
project can run the full `bear check` pipeline including project verification, dynamic import
enforcement, and TypeScript path alias resolution.

Builds on Phase B: `NodeTarget`, `NodeTargetDetector`, `NodeImportContainmentScanner`,
`NodeDynamicImportDetector`, `NodeImportBoundaryResolver`, `TypeScriptArtifactGenerator`.

Follows the patterns established by Phase P2 (Python full pipeline) and Phase R (React full
pipeline).

## Anchoring Constraints

- IR v1 is the boundary source of truth. No target-specific IR extensions.
- Exit code registry is frozen: `0`, `2`, `3`, `4`, `5`, `6`, `7`, `64`, `70`, `74`.
- `CODE/PATH/REMEDIATION` envelope is frozen.
- JVM, Python, and React behavior must remain byte-identical. Node work arrives behind the Target seam.
- No runtime policy engine additions.
- `TargetId.NODE` already exists. Do not re-add it.
- All existing tests must pass without modification.

## Glossary

- **NodeTarget** — Target implementation for Node/TypeScript projects (extended in Phase C).
- **NodeProjectVerificationRunner** — Runs `pnpm exec tsc --noEmit` as the Node project verification step.
- **NodeManifestParser** — Parses Node-specific wiring manifest JSON (distinct schema from JVM `TargetManifestParsers`).
- **NodeDynamicImportDetector** — Identifies `import()` expressions (Phase B: advisory; Phase C: enforced).
- **NodeImportBoundaryResolver** — Classifies resolved import paths; extended in Phase C with `tsconfig.json` path alias support.
- **NodePathAliasResolver** — Reads `tsconfig.json` `compilerOptions.paths` and resolves `@/*`-style aliases to concrete paths.
- **Node wiring manifest** — JSON file at `build/generated/bear/wiring/<blockKey>.wiring.json` with Node-specific schema (`version`, `blockKey`, `targetId`, `generatedPackage`, `implPackage`, `wrappers`, `ports`).
- **JVM wiring manifest** — JSON file with JVM-specific schema (`schemaVersion`, `entrypointFqcn`, `logicInterfaceFqcn`, etc.) parsed by `TargetManifestParsers`.
- **governed root** — Source directories under BEAR governance: `src/blocks/<blockKey>/` and `src/blocks/_shared/`.
- **`_shared` directory** — Optional shared source root at `src/blocks/_shared/`; copied to temp check workspace when present.
- **dynamic import** — A `import(...)` expression in TypeScript source (as opposed to a static `import ... from` statement).
- **path alias** — A `tsconfig.json` `compilerOptions.paths` entry mapping a specifier prefix (e.g., `@/*`) to a concrete path array.
- **`@/*` alias** — The common TypeScript path alias mapping `@/*` to `./src/*`.
- **check workspace** — Temporary directory used during `bear check` to stage files before scanning.
- **CanonicalLocator** — Structured finding locator from Phase A.

---

## Requirements

### Requirement 1: Node Wiring Manifest Parsing

**User Story:** As a developer, I want BEAR to parse Node wiring manifests correctly so that the full check pipeline can load block metadata for Node projects.

#### Acceptance Criteria

1. WHEN a valid Node wiring manifest is provided, THE NodeManifestParser SHALL parse it into a `WiringManifest` with `version`, `blockKey`, `targetId`, `generatedPackage`, `implPackage`, `wrappers`, and `ports` fields.
2. WHEN a Node wiring manifest has `"version": "1"` and `"targetId": "node"`, THE NodeManifestParser SHALL accept the manifest as valid.
3. WHEN a Node wiring manifest is missing the `version` field, THE NodeManifestParser SHALL throw `ManifestParseException` with code `MISSING_KEY_version`.
4. WHEN a Node wiring manifest is missing the `blockKey` field, THE NodeManifestParser SHALL throw `ManifestParseException` with code `MISSING_KEY_blockKey`.
5. WHEN a Node wiring manifest contains malformed JSON (does not start with `{` or end with `}`), THE NodeManifestParser SHALL throw `ManifestParseException` with code `MALFORMED_JSON`.
6. WHEN a Node wiring manifest has a `version` value other than `"1"`, THE NodeManifestParser SHALL throw `ManifestParseException` with code `UNSUPPORTED_WIRING_SCHEMA_VERSION`.
7. THE NodeManifestParser SHALL NOT use `TargetManifestParsers` (the JVM parser); Node manifests use a distinct schema.
8. THE NodeTarget SHALL delegate `parseWiringManifest()` to `NodeManifestParser`.
9. FOR ALL valid Node wiring manifests, parsing then serializing then parsing SHALL produce an equivalent `WiringManifest` (round-trip stability).

---

### Requirement 2: Check Workspace Preparation

**User Story:** As a developer, I want BEAR to prepare the check workspace correctly for Node projects so that `_shared` source is available during containment scanning.

#### Acceptance Criteria

1. WHEN `src/blocks/_shared/` exists in the project root, THE NodeTarget SHALL copy the directory into the temp check workspace at `src/blocks/_shared/` during `prepareCheckWorkspace()`.
2. WHEN `src/blocks/_shared/` does not exist in the project root, THE NodeTarget SHALL complete `prepareCheckWorkspace()` without error and without creating the directory in the temp workspace.
3. THE NodeTarget SHALL follow the same `prepareCheckWorkspace()` pattern as `PythonTarget` and `ReactTarget`.

---

### Requirement 3: JVM-Specific Stub Resolution

**User Story:** As a developer, I want BEAR to handle JVM-specific interface methods gracefully for Node projects so that the check pipeline does not throw unexpected errors.

#### Acceptance Criteria

1. THE NodeTarget SHALL return `null` from `containmentSkipInfoLine()` because JVM-style containment markers do not apply to Node projects.
2. THE NodeTarget SHALL return `null` from `preflightContainmentIfRequired()` because JVM-style containment markers do not apply to Node projects.
3. THE NodeTarget SHALL return `null` from `verifyContainmentMarkersIfRequired()` because JVM-style containment markers do not apply to Node projects.
4. THE NodeTarget SHALL return `List.of()` from `scanPortImplContainmentBypass()` because JVM-specific port binding checks do not apply to Node projects.
5. THE NodeTarget SHALL return `List.of()` from `scanBlockPortBindings()` because JVM-specific port binding checks do not apply to Node projects.
6. THE NodeTarget SHALL return `List.of()` from `scanMultiBlockPortImplAllowedSignals()` because JVM-specific port binding checks do not apply to Node projects.
7. THE NodeTarget SHALL return `List.of()` from `scanUndeclaredReach()` because no covered power surfaces are in scope for the first Node runtime slice.
8. THE NodeTarget SHALL return `List.of()` from `scanForbiddenReflectionDispatch()` because no dynamic execution scanning is in scope for the first Node runtime slice.
9. WHEN any of the above methods is called, THE NodeTarget SHALL NOT throw `UnsupportedOperationException`.

---

### Requirement 4: Project Verification

**User Story:** As a developer, I want BEAR to run TypeScript type-checking on my Node project so that structural type errors are caught as part of the governance gate.

#### Acceptance Criteria

1. WHEN `runProjectVerification()` is called and `pnpm` is available on PATH, THE NodeProjectVerificationRunner SHALL execute `pnpm exec tsc --noEmit` in the project root.
2. WHEN `pnpm exec tsc --noEmit` exits with code `0`, THE NodeProjectVerificationRunner SHALL return `ProjectTestResult` with status `PASSED`.
3. WHEN `pnpm exec tsc --noEmit` exits with a non-zero code, THE NodeProjectVerificationRunner SHALL return `ProjectTestResult` with status `FAILED` and include the compiler output.
4. WHEN `pnpm` is not found on PATH, THE NodeProjectVerificationRunner SHALL return `ProjectTestResult` with status `BOOTSTRAP_IO` and exit `74`.
5. WHEN `tsc` is not found (pnpm exec fails with module-not-found or command-not-found output), THE NodeProjectVerificationRunner SHALL return `ProjectTestResult` with status `BOOTSTRAP_IO` and exit `74`.
6. WHEN the verification process exceeds 300 seconds, THE NodeProjectVerificationRunner SHALL terminate the process and return `ProjectTestResult` with status `TIMEOUT`.
7. THE NodeProjectVerificationRunner SHALL capture both stdout and stderr from the `tsc` process.
8. THE NodeProjectVerificationRunner SHALL follow the same structural pattern as `ReactProjectVerificationRunner`.
9. THE NodeTarget SHALL delegate `runProjectVerification()` to `NodeProjectVerificationRunner`.

---

### Requirement 5: Dynamic Import Enforcement

**User Story:** As a developer, I want BEAR to enforce that governed Node files do not use dynamic `import()` expressions so that import boundaries remain statically analyzable.

#### Acceptance Criteria

1. WHEN a governed `.ts` file contains a `import(...)` expression, THE NodeImportContainmentScanner SHALL report a `BoundaryBypassFinding` with `rule()` equal to `DYNAMIC_IMPORT_FORBIDDEN` (exit `7`, `CODE=BOUNDARY_BYPASS` in error envelope).
2. WHEN a governed `.ts` file contains no `import(...)` expressions, THE NodeImportContainmentScanner SHALL report no dynamic import findings for that file.
3. THE NodeImportContainmentScanner SHALL detect dynamic imports using the existing `NodeDynamicImportDetector` and promote detections from advisory to enforced findings.
4. THE NodeImportContainmentScanner SHALL include the repo-relative file path and the dynamic import specifier in each `DYNAMIC_IMPORT_FORBIDDEN` finding.
5. WHEN a file contains both a static boundary bypass and a dynamic import, THE NodeImportContainmentScanner SHALL report both findings independently.
6. THE NodeImportContainmentScanner SHALL collect all dynamic import violations before reporting (no early exit on first violation).

Note: All containment findings use `CODE=BOUNDARY_BYPASS` in the error envelope. The specific rule (e.g., `DYNAMIC_IMPORT_FORBIDDEN`) is available via `BoundaryBypassFinding.rule()` and appears in the `RULE=` diagnostic line.

---

### Requirement 6: TypeScript Path Alias Resolution

**User Story:** As a developer, I want BEAR to resolve TypeScript path aliases from `tsconfig.json` so that standard `@/*` imports in governed files are correctly governed instead of rejected.

#### Acceptance Criteria

1. WHEN `tsconfig.json` at the project root contains `compilerOptions.paths` with an `@/*` entry mapping to `["./src/*"]`, THE NodePathAliasResolver SHALL resolve specifiers starting with `@/` by replacing `@/` with `./src/` relative to the project root.
2. WHEN a resolved `@/` import targets a file within the same block root, THE NodeImportBoundaryResolver SHALL pass the import.
3. WHEN a resolved `@/` import targets a file in a sibling block root, THE NodeImportBoundaryResolver SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
4. WHEN a resolved `@/` import targets a file in `src/blocks/_shared/`, THE NodeImportBoundaryResolver SHALL pass the import.
5. WHEN a resolved `@/` import targets a file outside governed roots, THE NodeImportBoundaryResolver SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
6. WHEN `tsconfig.json` does not contain `compilerOptions.paths`, THE NodeImportBoundaryResolver SHALL treat `@/`-prefixed specifiers as bare specifiers and fail with `CODE=BOUNDARY_BYPASS`.
7. WHEN `tsconfig.json` contains `compilerOptions.paths` entries other than `@/*`, THE NodeImportBoundaryResolver SHALL ignore those additional entries and treat imports using them as bare specifiers (fail with `CODE=BOUNDARY_BYPASS`).
8. WHEN `tsconfig.json` is missing or unreadable, THE NodeImportBoundaryResolver SHALL treat all non-relative, non-bare specifiers as bare specifiers (fail with `CODE=BOUNDARY_BYPASS`).
9. IF `tsconfig.json` contains a `@/*` entry mapping to a path array with more than one element, THEN THE NodePathAliasResolver SHALL use only the first element of the array.
10. THE NodePathAliasResolver SHALL read `tsconfig.json` once per scan invocation and cache the result for the duration of that scan.

---

### Requirement 7: End-to-End Check Pipeline

**User Story:** As a developer, I want the full `bear check` pipeline to work for Node projects so that all governance gates run in a single command.

#### Acceptance Criteria

1. WHEN `bear check` is run on a clean Node project, THE NodeTarget SHALL complete all pipeline stages (manifest parsing, workspace preparation, import containment, project verification) and exit `0`.
2. WHEN `bear check` is run on a Node project with a dynamic import in a governed file, THE NodeTarget SHALL exit `7` with `CODE=DYNAMIC_IMPORT_FORBIDDEN`.
3. WHEN `bear check` is run on a Node project with a TypeScript type error, THE NodeTarget SHALL exit `4`.
4. WHEN `bear check` is run on a Node project with `pnpm` missing, THE NodeTarget SHALL exit `74`.
5. WHEN `bear check` is run on a Node project with a boundary bypass import, THE NodeTarget SHALL exit `7` with `CODE=BOUNDARY_BYPASS`.
6. WHEN `bear check` is run on a Node project with a `@/*` alias resolving within the same block, THE NodeTarget SHALL exit `0`.
7. WHEN `bear check` is run on a Node project with a `@/*` alias resolving to a sibling block, THE NodeTarget SHALL exit `7` with `CODE=BOUNDARY_BYPASS`.
8. THE NodeTarget SHALL produce deterministic output across repeated invocations on the same project state.

---

## Scope

In scope:
- `NodeTarget.parseWiringManifest()` — Node-specific manifest parser (`NodeManifestParser`)
- `NodeTarget.prepareCheckWorkspace()` — copies `src/blocks/_shared/` to temp workspace if present
- `NodeTarget.runProjectVerification()` — delegates to `NodeProjectVerificationRunner` (`pnpm exec tsc --noEmit`)
- `NodeTarget` JVM-specific stubs — `containmentSkipInfoLine`, `preflightContainmentIfRequired`, `verifyContainmentMarkersIfRequired`, `scanPortImplContainmentBypass`, `scanBlockPortBindings`, `scanMultiBlockPortImplAllowedSignals` return `null`/`List.of()`
- `NodeTarget.scanUndeclaredReach()` and `scanForbiddenReflectionDispatch()` — return `List.of()` (no covered power surfaces in first Node runtime slice)
- Dynamic `import()` enforcement — `NodeDynamicImportDetector` findings promoted from advisory to enforced (exit `7`, `CODE=DYNAMIC_IMPORT_FORBIDDEN`)
- TypeScript path alias resolution — `@/*` → `./src/*` from `tsconfig.json` `compilerOptions.paths`

Out of scope (future phases):
- `tsconfig.json` `compilerOptions.paths` entries beyond `@/*`
- Undeclared reach scanning (covered Node built-ins: `fs`, `net`, `http`, `child_process`, etc.)
- Dynamic execution scanning (`eval`, `Function()`)
- Dependency governance (`pr-check` lock-file delta for Node)
- Workspace/monorepo layouts
- npm/yarn package managers
- CommonJS projects
- Vite `resolve.alias` support

## Exit Codes

- `0` — success
- `4` — project verification failure (`pnpm exec tsc --noEmit` non-zero exit)
- `5` — drift (generated artifact modified)
- `7` — boundary bypass (import escapes block root, dynamic import in governed file)
- `64` — unsupported target (`impl.allowedDeps`)
- `74` — tool missing (`pnpm` or `tsc` not found on PATH)

## Test Fixtures

Required new fixtures:
- `node/check-clean/` — clean Node project, all pipeline stages pass (exit 0)
- `node/check-dynamic-import/` — governed file with `import()` expression (exit 7, `DYNAMIC_IMPORT_FORBIDDEN`)
- `node/check-project-verification-failure/` — TypeScript type error in governed file (exit 4)
- `node/check-alias-same-block/` — `@/*` alias resolving within same block (exit 0)
- `node/check-alias-sibling-block/` — `@/*` alias resolving to sibling block (exit 7, `BOUNDARY_BYPASS`)
- `node/check-alias-no-tsconfig-paths/` — `@/*` import with no `compilerOptions.paths` in tsconfig (exit 7, `BOUNDARY_BYPASS`)

Existing fixtures (unchanged):
- `node/valid-single-block/`, `node/valid-multi-block/`, `node/valid-with-shared/`
- `node/invalid-workspace/`, `node/invalid-missing-lockfile/`
- `node/boundary-bypass-escape/`, `node/boundary-bypass-sibling/`, `node/boundary-bypass-bare-import/`

## Dependencies

Phase B prerequisites (complete):
- `NodeTarget` skeleton with stubs
- `NodeTargetDetector`
- `NodeImportContainmentScanner`, `NodeImportSpecifierExtractor`, `NodeDynamicImportDetector`, `NodeImportBoundaryResolver`
- `TypeScriptArtifactGenerator`, `TypeScriptManifestGenerator`
- `BoundaryDecision` model

Phase R prerequisites (complete):
- `ReactProjectVerificationRunner` — exact structural pattern for `NodeProjectVerificationRunner`
- `TargetManifestParsers` in shared `com.bear.kernel.target` package

## Related Documents

- `.kiro/specs/phase-b-node-target-scan-only/` — Phase B spec (Node scan-only)
- `.kiro/specs/phase-p2-python-checking/` — Phase P2 spec (Python full pipeline, reference for stub patterns)
- `.kiro/specs/react-target-scan-only/` — Phase R spec (React full pipeline, reference for verification runner)
- `roadmap/features/multi-target-foundation-phases.md` — phase tracking
- `docs/context/architecture.md` — core architecture principles
- `docs/context/ir-spec.md` — IR v1 contract (unchanged)
