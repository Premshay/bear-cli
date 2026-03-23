# React Target — Full Pipeline

React target adds `ReactTarget` as the fourth BEAR target with a full check pipeline.
After this phase, a React/TypeScript fixture project can compile governed blocks, pass
`bear check` for drift, import containment, project verification, and dependency governance,
and fail `check` deterministically when any boundary or structural constraint is violated.

Supports two sub-profiles under one `react` target: Vite+React and Next.js App Router.

Builds on Phase A: `TargetDetector`, `DetectedTarget`, refactored `TargetRegistry`,
`CanonicalLocator`, `GovernanceProfile`. Reuses Node infrastructure (pnpm, TypeScript,
import specifier extraction) via the shared `javascript` ecosystem family.

## Anchoring Constraints

- IR v1 is the boundary source of truth. No target-specific IR extensions.
- Exit code registry is frozen: `0`, `2`, `3`, `4`, `5`, `6`, `7`, `64`, `70`, `74`.
- `CODE/PATH/REMEDIATION` envelope is frozen.
- JVM, Node, and Python behavior must remain byte-identical. React work arrives behind the Target seam.
- No runtime policy engine additions.
- Generated artifacts live under `build/generated/bear/`. User-owned impl is never overwritten.
- `TargetId.REACT` already exists in the enum with `ecosystemFamily()` returning `"javascript"`.

## Glossary

- **ReactTarget** — Target implementation for React/TypeScript frontend projects.
- **ReactTargetDetector** — Identifies supported React project shapes (Vite+React and Next.js App Router).
- **ReactImportContainmentScanner** — Enforces import boundaries in governed `.ts`/`.tsx` files under feature roots.
- **ReactApiBoundaryScanner** — Detects direct `fetch()`/`XMLHttpRequest` calls in governed `.tsx` component files.
- **ReactArtifactGenerator** — Generates TypeScript artifacts with React-flavored naming conventions.
- **ReactProjectVerificationRunner** — Runs `pnpm exec tsc --noEmit` as the project verification step.
- **ReactPrCheckContributor** — Computes `package.json` + `pnpm-lock.yaml` delta for `pr-check` boundary governance.
- **governed root** — Source directories under BEAR governance: `src/features/<blockKey>/` and `src/shared/`.
- **BEAR-generated artifact** — TypeScript files under `build/generated/bear/`.
- **user-owned impl** — Implementation files created once by BEAR; owned and modified by the user.
- **import containment** — Enforcement preventing imports from escaping feature boundaries.
- **drift gate** — Compares workspace artifacts against freshly generated output.
- **block key** — Kebab-case block identifier (e.g., `user-dashboard`).
- **feature root** — `src/features/<blockKey>/`, the governed directory for a single feature module.
- **`@/*` alias** — Next.js deterministic path alias mapping `@/*` to `./src/*`.
- **`"use client"` directive** — React Server Components boundary marker indicating client-side code.
- **`"use server"` directive** — React Server Components boundary marker indicating server action code.
- **sub-profile** — One of two supported project shapes: `vite-react` or `nextjs-app-router`.
- **CanonicalLocator** — Structured finding locator from Phase A.
- **GovernanceProfile** — Target-specific governance shape identifier from Phase A.

---

## Requirements

### Requirement 1: React Project Detection

**User Story:** As a developer, I want BEAR to automatically detect my React project so that governance applies without manual target configuration.

#### Acceptance Criteria

1. WHEN `package.json` exists at project root with `"type": "module"`, `"packageManager": "pnpm@..."`, and `react` plus `react-dom` in `dependencies`, AND `pnpm-lock.yaml` exists, AND `tsconfig.json` exists, AND `vite.config.ts` exists, AND no `next.config.*` file exists, THE ReactTargetDetector SHALL return `SUPPORTED` with `targetId=REACT` and sub-profile `vite-react`.
2. WHEN `package.json` exists at project root with `"type": "module"`, `"packageManager": "pnpm@..."`, and `react` plus `react-dom` in `dependencies`, AND `pnpm-lock.yaml` exists, AND `tsconfig.json` exists, AND `next.config.js` or `next.config.mjs` or `next.config.ts` exists, THE ReactTargetDetector SHALL return `SUPPORTED` with `targetId=REACT` and sub-profile `nextjs-app-router`.
3. WHEN `pnpm-workspace.yaml` is present at project root, THE ReactTargetDetector SHALL return `UNSUPPORTED` with reason "pnpm workspace detected".
4. WHEN `package.json` is missing at project root, THE ReactTargetDetector SHALL return `NONE`.
5. WHEN `pnpm-lock.yaml` is missing at project root, THE ReactTargetDetector SHALL return `NONE`.
6. WHEN `tsconfig.json` is missing at project root, THE ReactTargetDetector SHALL return `NONE`.
7. WHEN `package.json` lacks `react` or `react-dom` in `dependencies`, THE ReactTargetDetector SHALL return `NONE`.
8. WHEN `package.json` lacks `"type": "module"`, THE ReactTargetDetector SHALL return `NONE`.
9. WHEN `package.json` lacks `"packageManager"` starting with `pnpm`, THE ReactTargetDetector SHALL return `NONE`.
10. WHEN neither `vite.config.ts` nor any `next.config.*` file exists, THE ReactTargetDetector SHALL return `NONE`.
11. WHEN both `vite.config.ts` AND a `next.config.*` file exist at project root, THE ReactTargetDetector SHALL return `UNSUPPORTED` with reason "ambiguous React project shape: both Vite and Next.js config detected".

---

### Requirement 2: Target Registry Integration

**User Story:** As a developer, I want BEAR to resolve the React target alongside existing JVM, Node, and Python targets so that multi-target projects work correctly.

#### Acceptance Criteria

1. THE ReactTarget SHALL use `TargetId.REACT` with `ecosystemFamily()` returning `"javascript"`.
2. THE TargetRegistry SHALL register ReactTarget alongside JvmTarget, NodeTarget, and PythonTarget in `defaultRegistry()`.
3. WHEN a React fixture project root is passed to `TargetRegistry.resolve()`, THE TargetRegistry SHALL return ReactTarget.
4. WHEN a JVM project root is passed to `TargetRegistry.resolve()`, THE TargetRegistry SHALL return JvmTarget unchanged.
5. WHEN a Node project root is passed to `TargetRegistry.resolve()`, THE TargetRegistry SHALL return NodeTarget unchanged.
6. WHEN a Python project root is passed to `TargetRegistry.resolve()`, THE TargetRegistry SHALL return PythonTarget unchanged.
7. WHEN both NodeTargetDetector returns `UNSUPPORTED` and ReactTargetDetector returns `SUPPORTED` for the same project root, THE TargetRegistry SHALL block ReactTarget resolution because both share the `javascript` ecosystem family.
8. WHEN `.bear/target.id` contains `react`, THE TargetRegistry SHALL return ReactTarget regardless of detector results.

---

### Requirement 3: Ecosystem Family Interaction

**User Story:** As a developer, I want React detection to coexist correctly with Node detection so that projects in the same JavaScript ecosystem family resolve deterministically.

#### Acceptance Criteria

1. WHEN a project has `react`/`react-dom` in dependencies AND `vite.config.ts`, THE NodeTargetDetector SHALL return `NONE` because the project is a React project, not a plain Node backend.
2. WHEN a project has `react`/`react-dom` in dependencies AND `next.config.*`, THE NodeTargetDetector SHALL return `NONE` because the project is a Next.js React project.
3. WHEN a project has `package.json` with `type:module` and `pnpm` but no `react`/`react-dom` in dependencies, THE NodeTargetDetector SHALL continue to return `SUPPORTED` as before.
4. IF both NodeTargetDetector and ReactTargetDetector return `SUPPORTED` for the same project root, THEN THE TargetRegistry SHALL throw `TARGET_AMBIGUOUS` with remediation instructing the user to add `.bear/target.id`.

---

### Requirement 4: TypeScript Artifact Generation

**User Story:** As a developer, I want BEAR to generate React-flavored TypeScript artifacts so that my feature modules have proper port, logic, and wrapper skeletons.

#### Acceptance Criteria

1. WHEN `compile()` is called for a React block, THE ReactArtifactGenerator SHALL generate `<BlockName>FeaturePorts.ts` under `build/generated/bear/types/<blockKey>/`.
2. WHEN `compile()` is called for a React block, THE ReactArtifactGenerator SHALL generate `<BlockName>FeatureLogic.ts` under `build/generated/bear/types/<blockKey>/`.
3. WHEN `compile()` is called for a React block, THE ReactArtifactGenerator SHALL generate `<BlockName>FeatureWrapper.ts` under `build/generated/bear/types/<blockKey>/`.
4. WHEN `compile()` is called for a React block, THE ReactArtifactGenerator SHALL generate a wiring manifest at `build/generated/bear/wiring/<blockKey>.wiring.json`.
5. WHEN `compile()` is called for a React block AND no user-owned impl exists, THE ReactArtifactGenerator SHALL create `src/features/<blockKey>/impl/<BlockName>FeatureImpl.tsx`.
6. WHEN `compile()` is called for a React block AND a user-owned impl already exists, THE ReactArtifactGenerator SHALL preserve the existing user-owned impl file without modification.
7. WHEN `generateWiringOnly()` is called, THE ReactArtifactGenerator SHALL generate only the wiring manifest without creating or touching user-owned impl files.
8. THE ReactArtifactGenerator SHALL produce TypeScript output that is syntactically valid and parseable by `tsc`.
9. THE ReactArtifactGenerator SHALL use React-flavored naming: `FeaturePorts`, `FeatureLogic`, `FeatureWrapper` suffixes instead of plain `Ports`, `Logic`, `Wrapper`.
10. THE ReactArtifactGenerator SHALL convert block keys from kebab-case to PascalCase for type names (e.g., `user-dashboard` → `UserDashboard`).

---

### Requirement 5: TypeScript Pretty Printer Round-Trip

**User Story:** As a developer, I want generated TypeScript artifacts to be formatting-stable so that repeated generation does not produce spurious drift.

#### Acceptance Criteria

1. THE ReactArtifactGenerator SHALL use consistent indentation and line breaks across all generated files.
2. THE ReactArtifactGenerator SHALL produce output that is parseable by `tsc` without syntax errors.
3. FOR ALL valid block IR inputs, generating then parsing then generating SHALL produce byte-identical output (round-trip stability).

---

### Requirement 6: Governed Source Roots

**User Story:** As a developer, I want BEAR to govern my feature module directories so that import containment applies to the correct source files.

#### Acceptance Criteria

1. THE ReactTarget SHALL compute governed roots including `src/features/<blockKey>/` for each block.
2. THE ReactTarget SHALL compute governed roots including `src/shared/` when the directory exists.
3. WHEN `src/shared/` does not exist, THE ReactTarget SHALL exclude the shared root without error.
4. THE ReactTarget SHALL exclude files outside `src/features/` and `src/shared/` from governance.
5. THE ReactTarget SHALL exclude `*.test.ts`, `*.test.tsx`, `*.spec.ts`, and `*.spec.tsx` files from governed source.
6. THE ReactTarget SHALL govern both `.ts` and `.tsx` files within governed roots.
7. THE ReactTarget SHALL exclude `src/app/`, `src/pages/`, `src/main.tsx`, `src/index.tsx`, `public/`, and config files from governance.

---

### Requirement 7: Import Containment Enforcement

**User Story:** As a developer, I want BEAR to enforce import boundaries in my React feature modules so that features remain self-contained and do not silently reach into sibling features.

#### Acceptance Criteria

1. WHEN an import within the same feature root is found, THE ReactImportContainmentScanner SHALL pass the import.
2. WHEN an import from `src/shared/` is found in a governed file, THE ReactImportContainmentScanner SHALL pass the import.
3. WHEN an import of a BEAR-generated companion under `build/generated/bear/` is found, THE ReactImportContainmentScanner SHALL pass the import.
4. WHEN an import of `react` is found in a governed file, THE ReactImportContainmentScanner SHALL pass the import.
5. WHEN an import of `react-dom` is found in a governed file, THE ReactImportContainmentScanner SHALL pass the import.
6. WHEN a relative import escapes the feature root, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
7. WHEN an import reaches a sibling feature root, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
8. WHEN a bare package import other than `react` or `react-dom` is found in a governed file, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
9. WHEN a `#` alias import is found in a governed file, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
10. WHEN a URL-like specifier is found in a governed file, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
11. WHEN a `src/shared/` file imports from a feature root, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
12. THE ReactImportContainmentScanner SHALL include repo-relative path and import specifier in each finding locator.

---

### Requirement 8: `@/*` Path Alias Resolution (Next.js)

**User Story:** As a Next.js developer, I want BEAR to resolve the `@/*` path alias so that my standard Next.js imports are correctly governed instead of rejected.

#### Acceptance Criteria

1. WHILE the detected sub-profile is `nextjs-app-router`, WHEN an import specifier starts with `@/`, THE ReactImportContainmentScanner SHALL resolve the specifier by replacing `@/` with `./src/` relative to the project root.
2. WHILE the detected sub-profile is `nextjs-app-router`, WHEN a resolved `@/` import targets a file within the same feature root, THE ReactImportContainmentScanner SHALL pass the import.
3. WHILE the detected sub-profile is `nextjs-app-router`, WHEN a resolved `@/` import targets a file in a sibling feature root, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
4. WHILE the detected sub-profile is `nextjs-app-router`, WHEN a resolved `@/` import targets a file outside governed roots, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS`.
5. WHILE the detected sub-profile is `vite-react`, WHEN an import specifier starts with `@/`, THE ReactImportContainmentScanner SHALL fail with exit `7` and `CODE=BOUNDARY_BYPASS` because path aliases are not supported in the Vite sub-profile.
6. WHILE the detected sub-profile is `nextjs-app-router`, WHEN `tsconfig.json` contains `compilerOptions.paths` with a `@/*` entry mapping to `["./src/*"]`, THE ReactImportContainmentScanner SHALL use that mapping for alias resolution.
7. IF `tsconfig.json` contains `compilerOptions.paths` entries other than `@/*`, THEN THE ReactImportContainmentScanner SHALL ignore those additional path aliases and treat imports using them as bare specifiers (fail with `BOUNDARY_BYPASS`).

---

### Requirement 9: `"use client"` / `"use server"` Directive Detection

**User Story:** As a Next.js developer, I want BEAR to detect `"use client"` and `"use server"` directives so that governance signals reflect React Server Component boundaries.

#### Acceptance Criteria

1. WHEN a governed `.ts` or `.tsx` file contains `"use client"` as the first statement, THE ReactImportContainmentScanner SHALL record the directive as a governance metadata signal on the file.
2. WHEN a governed `.ts` or `.tsx` file contains `"use server"` as the first statement, THE ReactImportContainmentScanner SHALL record the directive as a governance metadata signal on the file.
3. WHILE the detected sub-profile is `vite-react`, THE ReactImportContainmentScanner SHALL ignore `"use client"` and `"use server"` directives (no governance signal recorded).
4. THE ReactImportContainmentScanner SHALL detect directives by checking for string literal expressions (`"use client"` or `"use server"`) as the first statement in the file, consistent with the React compiler specification.

---

### Requirement 10: Drift Gate

**User Story:** As a developer, I want BEAR to detect when generated artifacts have been manually modified so that structural integrity is maintained.

#### Acceptance Criteria

1. WHEN all generated artifacts match freshly generated output, THE drift gate SHALL report no drift findings.
2. WHEN a generated `.ts` file under `build/generated/bear/types/<blockKey>/` has been modified, THE drift gate SHALL report `DRIFT_DETECTED`.
3. WHEN a generated wiring manifest under `build/generated/bear/wiring/` is missing, THE drift gate SHALL report `DRIFT_MISSING_BASELINE`.
4. WHEN a user-owned impl file under `src/features/<blockKey>/impl/` has been modified, THE drift gate SHALL report no drift findings for that file.
5. THE drift gate SHALL produce exit `5` when drift is detected.

---

### Requirement 11: `impl.allowedDeps` Unsupported Guard

**User Story:** As a developer, I want BEAR to clearly reject `impl.allowedDeps` for React targets so that I receive actionable guidance instead of silent misbehavior.

#### Acceptance Criteria

1. WHEN a block IR contains `impl.allowedDeps` and the resolved target is React, THE ReactTarget SHALL fail with exit `64` and `CODE=UNSUPPORTED_TARGET`.
2. THE ReactTarget SHALL include `REMEDIATION=Remove impl.allowedDeps for react target, or switch to JVM target.` in the failure envelope.
3. WHEN a block IR does not contain `impl.allowedDeps`, THE ReactTarget SHALL pass the guard.
4. WHEN the same block IR with `impl.allowedDeps` is checked under JVM target, THE JvmTarget SHALL continue to work unchanged.

---

### Requirement 12: API Boundary Signaling

**User Story:** As a developer, I want BEAR to detect direct `fetch()` and `XMLHttpRequest` calls in my React component files so that API access is routed through declared service layers.

#### Acceptance Criteria

1. WHEN a governed `.tsx` file contains a direct `fetch(` call expression, THE ReactApiBoundaryScanner SHALL report a finding with `CODE=BOUNDARY_BYPASS` and `PARTIAL` status.
2. WHEN a governed `.tsx` file contains a `new XMLHttpRequest(` expression, THE ReactApiBoundaryScanner SHALL report a finding with `CODE=BOUNDARY_BYPASS` and `PARTIAL` status.
3. WHEN a governed `.ts` file (non-component, e.g., `*Service.ts`, `*Api.ts`) within the same feature root contains `fetch(` or `XMLHttpRequest`, THE ReactApiBoundaryScanner SHALL not report a finding for that file.
4. WHEN a governed `.tsx` file does not contain direct `fetch(` or `XMLHttpRequest` calls, THE ReactApiBoundaryScanner SHALL report no findings for that file.
5. THE ReactApiBoundaryScanner SHALL produce exit `7` when API boundary violations are detected.
6. THE ReactApiBoundaryScanner SHALL include repo-relative file path and the detected call pattern in each finding locator.

---

### Requirement 13: Import Specifier Extraction

**User Story:** As a developer, I want BEAR to correctly extract all import specifiers from my `.ts` and `.tsx` files so that containment scanning covers all import forms.

#### Acceptance Criteria

1. THE ReactImportContainmentScanner SHALL extract specifiers from static `import ... from '...'` statements.
2. THE ReactImportContainmentScanner SHALL extract specifiers from `export ... from '...'` statements.
3. THE ReactImportContainmentScanner SHALL extract specifiers from side-effect `import '...'` statements.
4. THE ReactImportContainmentScanner SHALL extract specifiers from `.tsx` files containing JSX syntax without parse errors.
5. WHEN a `import(...)` dynamic import expression is found in a governed file, THE ReactImportContainmentScanner SHALL detect the dynamic import as advisory (no enforcement in this phase).

---

### Requirement 14: Project Verification

**User Story:** As a developer, I want BEAR to run TypeScript type-checking on my React project so that structural type errors are caught as part of the governance gate.

#### Acceptance Criteria

1. WHEN `runProjectVerification()` is called and `pnpm` is available on PATH, THE ReactProjectVerificationRunner SHALL execute `pnpm exec tsc --noEmit -p tsconfig.json` in the project root.
2. WHEN `pnpm exec tsc --noEmit` exits with code `0`, THE ReactProjectVerificationRunner SHALL return `ProjectTestResult` with status `PASSED`.
3. WHEN `pnpm exec tsc --noEmit` exits with a non-zero code, THE ReactProjectVerificationRunner SHALL return `ProjectTestResult` with status `FAILED` and include the compiler output.
4. WHEN `pnpm` is not found on PATH, THE ReactProjectVerificationRunner SHALL return `ProjectTestResult` with status `BOOTSTRAP_IO` and exit `74`.
5. WHEN `tsc` is not found (pnpm exec fails with module-not-found), THE ReactProjectVerificationRunner SHALL return `ProjectTestResult` with status `BOOTSTRAP_IO` and exit `74`.
6. WHEN the verification process exceeds the timeout (300 seconds), THE ReactProjectVerificationRunner SHALL terminate the process and return `ProjectTestResult` with status `TIMEOUT`.
7. THE ReactProjectVerificationRunner SHALL capture both stdout and stderr from the `tsc` process.

---

### Requirement 15: Dependency Governance (`pr-check`)

**User Story:** As a developer, I want BEAR to detect dependency changes in my React project during PR review so that new package additions are explicitly visible as boundary-expanding changes.

#### Acceptance Criteria

1. WHEN `package.json` `dependencies` or `devDependencies` differ between base and head, THE ReactPrCheckContributor SHALL classify the delta as `BOUNDARY_EXPANDING`.
2. WHEN `pnpm-lock.yaml` differs between base and head, THE ReactPrCheckContributor SHALL classify the delta as `BOUNDARY_EXPANDING`.
3. WHEN neither `package.json` nor `pnpm-lock.yaml` differ between base and head, THE ReactPrCheckContributor SHALL report no boundary-expanding changes for dependency governance.
4. THE ReactPrCheckContributor SHALL produce exit `5` when boundary-expanding dependency changes are detected.
5. THE ReactPrCheckContributor SHALL include the changed file path in each finding.

---

## Scope

In scope:
- `ReactTarget` implementing full `Target` interface
- `ReactTargetDetector` supporting Vite+React AND Next.js App Router sub-profiles
- TypeScript artifact generation with React-flavored naming (`FeaturePorts.ts`, `FeatureLogic.ts`, `FeatureWrapper.ts`, `.tsx` impl skeleton)
- Governed roots: `src/features/<blockKey>/` and `src/shared/`
- Import containment scanning for `.ts`/`.tsx` files in governed roots
- `@/*` path alias resolution for Next.js sub-profile
- `"use client"` / `"use server"` directive detection as governance signals
- `react`/`react-dom` allowed as bare imports from governed roots
- Drift gate for generated artifacts
- `impl.allowedDeps` unsupported guard (exit 64)
- API boundary signaling: direct `fetch()`/`XMLHttpRequest` detection in governed `.tsx` component files (PARTIAL)
- Ecosystem-family interaction: React detection alongside Node detection in the `javascript` family
- Project verification: `pnpm exec tsc --noEmit` (exit 4 on failure, exit 74 on tool missing)
- Dependency governance: `package.json` + `pnpm-lock.yaml` delta → `BOUNDARY_EXPANDING` (exit 5)
- `NodeTargetDetector` updated to return `NONE` when `react`/`react-dom` in dependencies

Out of scope (future phases):
- Undeclared reach scanning (covered Node built-ins)
- Data-fetching hook detection (TanStack Query, SWR, Apollo, tRPC, Axios)
- Workspace/monorepo layouts
- TypeScript `compilerOptions.paths` beyond `@/*` (Next.js only)
- Vite `resolve.alias` support
- CSS-in-JS module resolution
- Server-side rendering enforcement beyond directive detection
- `site-packages` or `node_modules` power-surface scanning
- React Native or mobile targets

## Sub-Profiles

Two project shapes under one `react` target:

### `vite-react`
- `vite.config.ts` present, no `next.config.*`
- No path alias support (all aliases rejected)
- No `"use client"` / `"use server"` governance signals
- Governed roots: `src/features/<blockKey>/`, `src/shared/`

### `nextjs-app-router`
- `next.config.*` present (`.js`, `.mjs`, or `.ts`)
- `@/*` alias resolved to `./src/*` (single deterministic mapping)
- `"use client"` / `"use server"` directives detected as governance signals
- File-based routing (`app/` directory) stays ungoverned
- Governed roots: `src/features/<blockKey>/`, `src/shared/`

## Exit Codes

- `0` — success
- `4` — project verification failure (`pnpm exec tsc --noEmit` non-zero exit)
- `5` — drift (generated artifact modified) or boundary-expanding dependency change
- `7` — boundary bypass (import escapes feature root, or direct fetch/XHR in component)
- `64` — unsupported target (workspace layout, `impl.allowedDeps`, ambiguous project shape)
- `74` — tool missing (`pnpm` or `tsc` not found on PATH)

## Test Fixtures

Required:
- `vite-react-single-block` — one block, Vite+React, clean imports
- `vite-react-multi-block` — two blocks, no cross-feature imports
- `vite-react-with-shared` — block imports from `src/shared/`
- `nextjs-single-block` — one block, Next.js App Router, clean imports with `@/` alias
- `nextjs-use-client` — block with `"use client"` directive in governed files
- `invalid-workspace` — `pnpm-workspace.yaml` present (UNSUPPORTED)
- `invalid-ambiguous-shape` — both `vite.config.ts` and `next.config.js` present (UNSUPPORTED)
- `boundary-bypass-escape` — relative import escapes feature root
- `boundary-bypass-sibling` — import from sibling feature
- `boundary-bypass-bare-import` — import from `lodash` package
- `boundary-bypass-fetch` — direct `fetch()` in governed `.tsx` component file
- `nextjs-alias-sibling` — `@/` alias resolving to sibling feature (BOUNDARY_BYPASS)
- `check-project-verification-failure` — `tsc` type error in governed file (exit 4)
- `check-pr-check-boundary-expanding` — `package.json` dependency added (exit 5)

## Dependencies

Phase A prerequisites (complete):
- `TargetDetector` interface
- `DetectedTarget` model
- `TargetRegistry.resolve()` refactoring
- `CanonicalLocator`
- `GovernanceProfile`
- `TargetId.REACT` enum value (already exists, `ecosystemFamily()` returns `"javascript"`)

Node infrastructure reuse:
- `NodeImportSpecifierExtractor` — reusable for `.ts` files; extended for `.tsx`
- `BoundaryDecision` model — reusable from Node implementation
- pnpm/TypeScript toolchain assumptions — shared with Node target

## Related Documents

- `roadmap/ideas/future-react-containment-profile.md` — original (conservative) React profile
- `roadmap/ideas/future-multi-target-spec-design.md` — cross-target architecture
- `roadmap/ideas/future-multi-target-expansion-plan.md` — expansion strategy
- `roadmap/features/multi-target-foundation-phases.md` — phase tracking
- `.kiro/specs/phase-b-node-target-scan-only/` — Node scan-only spec (reference)
- `.kiro/specs/phase-p-python-scan-only/` — Python scan-only spec (reference)
- `.kiro/specs/phase-p2-python-checking/` — Python full pipeline spec (reference)
