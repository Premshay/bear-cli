# React Target — Full Pipeline Design

## Overview

This document describes the design for `ReactTarget`, the fourth BEAR target. It follows the
same seam-based architecture as `NodeTarget` and `PythonTarget`: all React-specific logic lives
in `com.bear.kernel.target.react`, behind the `Target` interface. No changes to the deterministic
core (`kernel/`) outside this package and the two registry/detector touch-points.

Package: `com.bear.kernel.target.react`

## Architecture Diagram

```
TargetRegistry (updated)
  └── ReactTarget
        ├── ReactTargetDetector          (detection)
        ├── ReactArtifactGenerator       (compile / generateWiringOnly)
        ├── ReactManifestGenerator       (wiring.json)
        ├── ReactImportContainmentScanner (scanBoundaryBypass)
        ├── ReactApiBoundaryScanner      (scanBoundaryBypass — PARTIAL)
        ├── ReactProjectVerificationRunner (runProjectVerification)
        └── ReactPrCheckContributor      (pr-check delta)

NodeTargetDetector (updated)
  └── returns NONE when react/react-dom in package.json dependencies
```


## Component Designs

---

### 1. `ReactTargetDetector`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactTargetDetector.java`

Implements `TargetDetector`. Detection is purely file-system based — no process execution.

**Detection algorithm:**

```
detect(projectRoot):
  if package.json missing → NONE
  parse package.json (Jackson strict JSON)
  if "type" != "module" → NONE
  if "packageManager" not starting with "pnpm" → NONE
  if pnpm-lock.yaml missing → NONE
  if tsconfig.json missing → NONE
  if "react" or "react-dom" not in dependencies → NONE
  if pnpm-workspace.yaml present → UNSUPPORTED("pnpm workspace detected")
  hasVite = vite.config.ts exists
  hasNext = next.config.js OR next.config.mjs OR next.config.ts exists
  if hasVite AND hasNext → UNSUPPORTED("ambiguous React project shape: both Vite and Next.js config detected")
  if hasNext → SUPPORTED(REACT, "nextjs-app-router")
  if hasVite → SUPPORTED(REACT, "vite-react")
  → NONE
```

The sub-profile string is stored in `DetectedTarget.reason()` and passed to `ReactTarget` at
construction time via a `ReactProjectShape` enum (`VITE_REACT`, `NEXTJS_APP_ROUTER`).

**Key design decision:** `ReactTargetDetector` stores the detected sub-profile in the
`DetectedTarget.reason()` field. `ReactTarget` reads this at resolve time. Since `TargetRegistry`
returns a pre-constructed `Target` instance, the sub-profile must be stored in the `ReactTarget`
instance itself. The registry will construct `ReactTarget` with a mutable sub-profile field that
gets set after detection, OR `ReactTarget` will accept a `ReactProjectShape` constructor parameter
with a default of `VITE_REACT`. The simpler approach: `ReactTarget` holds a `ReactProjectShape`
field set to `VITE_REACT` by default; `ReactTargetDetector` returns the sub-profile in
`DetectedTarget.reason()`, and the `TargetRegistry` does not need to propagate it — the scanner
reads the project root at scan time to re-detect the sub-profile. This avoids coupling the
registry to sub-profile state.

**Sub-profile re-detection at scan time:** `ReactImportContainmentScanner.scan()` accepts
`projectRoot` and re-runs the lightweight sub-profile check (presence of `next.config.*`) to
determine alias resolution behavior. This is a pure file-system check, deterministic and cheap.


---

### 2. `ReactTarget`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactTarget.java`

Implements `Target`. Follows `PythonTarget` as the closest structural model: JVM-specific
methods return `null` or `List.of()`, no containment markers, no reflection dispatch scanning.

**Interface method mapping:**

| Method | Behavior |
|---|---|
| `targetId()` | `TargetId.REACT` |
| `defaultProfile()` | `GovernanceProfile.of(TargetId.REACT, "feature-ui")` |
| `compile()` | delegates to `ReactArtifactGenerator` + `ReactManifestGenerator` |
| `generateWiringOnly()` | delegates to `ReactManifestGenerator` only |
| `parseWiringManifest()` | delegates to `TargetManifestParsers.parseWiringManifest()` |
| `prepareCheckWorkspace()` | creates `src/shared/` in tempRoot if present in projectRoot |
| `ownedGeneratedPrefixes()` | `build/generated/bear/types/<blockKey>/` + `build/generated/bear/wiring/<blockKey>.wiring.json` |
| `considerContainmentSurfaces()` | `false` — `impl.allowedDeps` unsupported |
| `sharedContainmentInScope()` | `false` |
| `blockDeclaresAllowedDeps()` | parse IR, return true if `impl.allowedDeps` non-empty → triggers exit 64 |
| `containmentSkipInfoLine()` | `null` — JVM markers not applicable |
| `preflightContainmentIfRequired()` | `null` — JVM markers not applicable |
| `verifyContainmentMarkersIfRequired()` | `null` — JVM markers not applicable |
| `scanUndeclaredReach()` | `List.of()` — no covered power surfaces in first slice |
| `scanForbiddenReflectionDispatch()` | `List.of()` — no dynamic execution scanning |
| `scanBoundaryBypass()` | delegates to `ReactImportContainmentScanner` + `ReactApiBoundaryScanner` |
| `scanPortImplContainmentBypass()` | `List.of()` — JVM-specific |
| `scanBlockPortBindings()` | `List.of()` — JVM-specific |
| `scanMultiBlockPortImplAllowedSignals()` | `List.of()` — JVM-specific |
| `runProjectVerification()` | delegates to `ReactProjectVerificationRunner` |

**`checkDrift()` method** (public, same pattern as `NodeTarget.checkDrift()`):
- Compiles to temp directory, byte-compares generated artifacts against workspace
- Excludes `src/features/<blockKey>/impl/` from drift checking (user-owned)
- Returns `List<TargetCheckIssue>` with `DRIFT_DETECTED` or `DRIFT_MISSING_BASELINE`

**`blockDeclaresAllowedDeps()` guard:**
```java
// If impl.allowedDeps present → caller maps to exit 64 + UNSUPPORTED_TARGET
// REMEDIATION: "Remove impl.allowedDeps for react target, or switch to JVM target."
```


---

### 3. `ReactArtifactGenerator`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactArtifactGenerator.java`

Generates React-flavored TypeScript artifacts. Mirrors `TypeScriptArtifactGenerator` from the
Node target but uses `FeaturePorts`, `FeatureLogic`, `FeatureWrapper` suffixes and `.tsx` for
the user impl skeleton.

**Generated file layout for block key `user-dashboard`:**

```
build/generated/bear/types/user-dashboard/
  UserDashboardFeaturePorts.ts
  UserDashboardFeatureLogic.ts
  UserDashboardFeatureWrapper.ts
build/generated/bear/wiring/
  user-dashboard.wiring.json
src/features/user-dashboard/impl/
  UserDashboardFeatureImpl.tsx   ← created once, never overwritten
```

**`generatePorts(ir, typesDir, blockKey)`** — emits:
```typescript
// BEAR-generated — do not edit
export interface UserDashboardFeaturePorts {
  // port declarations from IR
}
```

**`generateLogic(ir, typesDir, blockKey)`** — emits:
```typescript
// BEAR-generated — do not edit
import type { UserDashboardFeaturePorts } from './UserDashboardFeaturePorts';
export interface UserDashboardFeatureLogic {
  // logic method signatures from IR
}
```

**`generateWrapper(ir, typesDir, blockKey)`** — emits:
```typescript
// BEAR-generated — do not edit
import type { UserDashboardFeatureLogic } from './UserDashboardFeatureLogic';
export function createUserDashboardFeatureWrapper(
  impl: UserDashboardFeatureLogic
): UserDashboardFeatureLogic {
  return impl;
}
```

**`generateUserImplSkeleton(ir, implDir, blockKey)`** — emits `.tsx`:
```typescript
// User-owned — BEAR will not overwrite this file
import type { UserDashboardFeatureLogic } from '../../../build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic';

export class UserDashboardFeatureImpl implements UserDashboardFeatureLogic {
  // TODO: implement
}
```

**Naming helpers** (reuse `TypeScriptLexicalSupport` pattern):
- `deriveBlockName(blockKey)` — kebab-case → PascalCase: `user-dashboard` → `UserDashboard`
- All generated type names append the React-flavored suffix: `UserDashboardFeaturePorts`, etc.

**`writeIfDifferent(path, content)`** — same byte-stable write pattern as Node target
(uses `StandardOpenOption.SYNC` for WSL2 filesystem caching).


---

### 4. `ReactManifestGenerator`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactManifestGenerator.java`

Generates `<blockKey>.wiring.json`. Identical structure to `TypeScriptManifestGenerator` —
the wiring manifest schema is target-agnostic (IR v1 contract). Delegates to
`TargetManifestParsers` for the JSON schema.

**Output path:** `build/generated/bear/wiring/<blockKey>.wiring.json`

---

### 5. `ReactImportContainmentScanner`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactImportContainmentScanner.java`

Static scanner for import boundary enforcement in governed `.ts`/`.tsx` files.

**`scan(projectRoot, wiringManifests)` algorithm:**

```
1. computeGovernedRoots(projectRoot, wiringManifests)
   → src/features/<blockKey>/ for each manifest blockKey
   → src/shared/ if directory exists

2. collectGovernedFiles(governedRoots)
   → walk each root, collect .ts and .tsx files
   → exclude *.test.ts, *.test.tsx, *.spec.ts, *.spec.tsx

3. detectSubProfile(projectRoot)
   → NEXTJS_APP_ROUTER if next.config.js/mjs/ts exists
   → VITE_REACT otherwise

4. for each file:
   a. read content
   b. detectDirective(content) → "use client" | "use server" | null
      (only recorded as metadata signal; no enforcement action)
   c. extractImports(file, content) via ReactImportSpecifierExtractor
   d. for each specifier:
      resolve(file, specifier, governedRoots, projectRoot, subProfile)
      → BoundaryDecision.allowed() or BoundaryDecision.fail(reason)
      if fail → add BoundaryBypassFinding

5. sort findings by file path
6. return findings
```

**`ReactImportBoundaryResolver.resolve()` logic:**

```
specifier starts with "@/" AND subProfile == NEXTJS_APP_ROUTER:
  resolved = projectRoot / "src" / specifier.removePrefix("@/")
  if resolved is within same feature root → allowed
  if resolved is within src/shared/ → allowed
  if resolved is within build/generated/bear/ → allowed
  → fail(BOUNDARY_BYPASS)

specifier starts with "@/" AND subProfile == VITE_REACT:
  → fail(BOUNDARY_BYPASS)  // aliases not supported in Vite sub-profile

specifier starts with "#":
  → fail(BOUNDARY_BYPASS)

specifier is URL-like (starts with "http://" or "https://"):
  → fail(BOUNDARY_BYPASS)

specifier is relative ("." or ".."):
  resolved = file.parent / specifier (normalize)
  if resolved is within same feature root → allowed
  if resolved is within src/shared/ → allowed
  if resolved is within build/generated/bear/ → allowed
  → fail(BOUNDARY_BYPASS)

specifier is bare (no "/" prefix, not relative):
  if specifier == "react" or "react-dom" → allowed
  → fail(BOUNDARY_BYPASS)
```

**`ReactImportSpecifierExtractor`** — extends `NodeImportSpecifierExtractor` behavior to handle
`.tsx` files. The existing regex-based extractor already handles JSX files correctly since JSX
syntax does not appear in import statements. Reuse `NodeImportSpecifierExtractor` directly;
no subclass needed. The extractor handles:
- `import ... from '...'`
- `export ... from '...'`
- `import '...'` (side-effect)
- `import(...)` dynamic (detected as advisory, not enforced)

**Directive detection** (`detectDirective(content)`):
```
trim leading whitespace/comments
if first non-comment statement is "use client"; or 'use client'; → return "use client"
if first non-comment statement is "use server"; or 'use server'; → return "use server"
return null
```
Only recorded as metadata signal in `nextjs-app-router` sub-profile. No enforcement action.


---

### 6. `ReactApiBoundaryScanner`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactApiBoundaryScanner.java`

PARTIAL scanner — detects direct `fetch()` and `new XMLHttpRequest()` calls in governed `.tsx`
component files only (not `.ts` service/logic files).

**`scan(projectRoot, wiringManifests)` algorithm:**

```
1. computeGovernedRoots (same as ReactImportContainmentScanner)
2. collectComponentFiles(governedRoots)
   → walk each root, collect .tsx files only
   → exclude *.test.tsx, *.spec.tsx
3. for each .tsx file:
   content = readString(file)
   if content contains "fetch(" → add finding(BOUNDARY_BYPASS, file, "fetch(")
   if content contains "new XMLHttpRequest(" → add finding(BOUNDARY_BYPASS, file, "new XMLHttpRequest(")
4. sort findings by file path
5. return findings
```

**Finding structure:**
- `code`: `BOUNDARY_BYPASS`
- `path`: repo-relative file path
- `detail`: detected call pattern (`fetch(` or `new XMLHttpRequest(`)

**PARTIAL status:** This scanner uses string matching, not AST analysis. It may produce false
positives for commented-out code or string literals. This is acceptable for the first slice —
the same PARTIAL approach used by Python's dynamic execution scanner.

---

### 7. `ReactProjectVerificationRunner`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactProjectVerificationRunner.java`

Runs `pnpm exec tsc --noEmit -p tsconfig.json` as the project verification step.
Follows `PythonProjectVerificationRunner` as the structural model.

**`run(projectRoot)` algorithm:**

```
1. check pnpm available on PATH (which pnpm / where pnpm)
   if not found → return ProjectTestResult(BOOTSTRAP_IO, "pnpm not found on PATH", phase="tsc")

2. command = ["pnpm", "exec", "tsc", "--noEmit", "-p", "tsconfig.json"]
3. ProcessBuilder(command).directory(projectRoot).redirectErrorStream(true)
4. start process, capture stdout+stderr via background thread
5. waitFor(300 seconds)
   if timeout → destroyForcibly, return ProjectTestResult(TIMEOUT, output, phase="tsc")
6. exitCode = process.exitValue()
   if exitCode == 0 → return ProjectTestResult(PASSED, output, phase="tsc")
   if output contains "Cannot find module" or "error TS2307" → BOOTSTRAP_IO (tsc not installed)
   → return ProjectTestResult(FAILED, output, phase="tsc")
```

**Exit code mapping** (handled by `app/` layer, not this runner):
- `PASSED` → exit 0
- `FAILED` → exit 4
- `BOOTSTRAP_IO` → exit 74
- `TIMEOUT` → exit 4 (treated as verification failure)

**`initScriptRelativePath` parameter** — JVM-specific Gradle init script path. React ignores it
(same pattern as `PythonTarget.runProjectVerification()`).


---

### 8. `ReactPrCheckContributor`

**File:** `kernel/src/main/java/com/bear/kernel/target/react/ReactPrCheckContributor.java`

Computes dependency governance delta for `pr-check`. Checks whether `package.json` or
`pnpm-lock.yaml` differ between base and head snapshots.

**`computeDelta(baseRoot, headRoot)` algorithm:**

```
findings = []
for each file in ["package.json", "pnpm-lock.yaml"]:
  basePath = baseRoot / file
  headPath = headRoot / file
  if both exist AND bytes differ → findings.add(BOUNDARY_EXPANDING, file)
  if headPath exists AND basePath missing → findings.add(BOUNDARY_EXPANDING, file)
return findings
```

**Finding structure:**
- `code`: `BOUNDARY_EXPANDING`
- `path`: relative file path (`package.json` or `pnpm-lock.yaml`)
- `detail`: "dependency governance: file changed"

**Exit code:** `5` when any findings present (handled by `app/` layer).

**Note:** This contributor is called by the `pr-check` command in `app/`. The `app/` layer
already has the base/head snapshot infrastructure from JVM and Python pr-check support.
`ReactPrCheckContributor` plugs into the same pattern.

---

### 9. `NodeTargetDetector` Update

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java`

Add React exclusion check after the existing `pnpm-workspace.yaml` check:

```java
// Exclude React projects — they belong to ReactTargetDetector
JsonNode deps = pkg.get("dependencies");
if (deps != null && (deps.has("react") || deps.has("react-dom"))) {
    return DetectedTarget.none();
}
```

This ensures `NodeTargetDetector` returns `NONE` for React projects, preventing ecosystem-family
ambiguity. The check runs before the `SUPPORTED` return, after workspace detection.

---

### 10. `TargetRegistry` Update

**File:** `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java`

Add `ReactTarget` and `ReactTargetDetector` to `defaultRegistry()`:

```java
private static final TargetRegistry DEFAULT = new TargetRegistry(
    Map.of(
        TargetId.JVM, new JvmTarget(),
        TargetId.NODE, new NodeTarget(),
        TargetId.PYTHON, new PythonTarget(),
        TargetId.REACT, new ReactTarget()          // ← add
    ),
    List.of(
        new JvmTargetDetector(),
        new NodeTargetDetector(),
        new PythonTargetDetector(),
        new ReactTargetDetector()                  // ← add
    )
);
```

No other changes to `TargetRegistry`. The existing ecosystem-family blocking logic already
handles the Node/React same-family case correctly.


---

## Correctness Properties (PBT)

All properties use plain JUnit 5 (no jqwik/assertj). Each property runs 100+ iterations
over generated inputs.

### Detection Properties

**P1 — Detection completeness:** For any project root with valid Vite+React structure
(package.json with react+react-dom deps, pnpm lock, tsconfig, vite.config.ts, no next.config.*),
`ReactTargetDetector.detect()` returns `SUPPORTED` with `targetId=REACT`.

**P2 — Detection exclusivity (Next.js):** For any project root with valid Next.js App Router
structure (package.json with react+react-dom deps, pnpm lock, tsconfig, next.config.js/mjs/ts,
no vite.config.ts), `ReactTargetDetector.detect()` returns `SUPPORTED` with `targetId=REACT`.

**P3 — Detection NONE on missing react dep:** For any project root missing `react` or `react-dom`
in `package.json` dependencies, `ReactTargetDetector.detect()` returns `NONE`.

**P4 — Detection UNSUPPORTED on workspace:** For any project root with `pnpm-workspace.yaml`,
`ReactTargetDetector.detect()` returns `UNSUPPORTED`.

**P5 — Detection UNSUPPORTED on ambiguous shape:** For any project root with both `vite.config.ts`
and `next.config.*`, `ReactTargetDetector.detect()` returns `UNSUPPORTED`.

**P6 — Node exclusion:** For any project root where `ReactTargetDetector` returns `SUPPORTED`,
`NodeTargetDetector.detect()` returns `NONE`.

### Artifact Generation Properties

**P7 — Round-trip stability:** For any valid `BearIr` input, generating artifacts then
re-generating produces byte-identical output. `generate(ir) == generate(generate(ir))`.

**P8 — PascalCase naming:** For any kebab-case block key, the generated artifact filenames
use the correct PascalCase prefix with `FeaturePorts`, `FeatureLogic`, `FeatureWrapper` suffixes.

**P9 — Impl preservation:** For any block key where a user impl file already exists,
`compile()` does not modify the existing impl file.

**P10 — Wiring manifest present:** For any valid `BearIr` input, `compile()` produces a
`<blockKey>.wiring.json` file under `build/generated/bear/wiring/`.

### Import Containment Properties

**P11 — Intra-feature imports allowed:** For any import specifier that resolves within the
same feature root, `ReactImportBoundaryResolver.resolve()` returns `allowed()`.

**P12 — Shared imports allowed:** For any import specifier that resolves within `src/shared/`,
`ReactImportBoundaryResolver.resolve()` returns `allowed()`.

**P13 — react/react-dom allowed:** For bare specifiers `"react"` and `"react-dom"`,
`ReactImportBoundaryResolver.resolve()` returns `allowed()`.

**P14 — Sibling feature imports fail:** For any import specifier that resolves within a
different feature root, `ReactImportBoundaryResolver.resolve()` returns `fail(BOUNDARY_BYPASS)`.

**P15 — Bare package imports fail:** For any bare specifier other than `react`/`react-dom`,
`ReactImportBoundaryResolver.resolve()` returns `fail(BOUNDARY_BYPASS)`.

**P16 — Next.js @/* alias within same feature allowed:** For any `@/features/<blockKey>/...`
specifier in `nextjs-app-router` sub-profile, resolver returns `allowed()` when target is
within the same feature root.

**P17 — Next.js @/* alias to sibling fails:** For any `@/features/<otherBlock>/...` specifier
in `nextjs-app-router` sub-profile, resolver returns `fail(BOUNDARY_BYPASS)`.

**P18 — Vite @/* alias always fails:** For any `@/...` specifier in `vite-react` sub-profile,
resolver returns `fail(BOUNDARY_BYPASS)`.

### Drift Gate Properties

**P19 — No drift on fresh compile:** For any valid `BearIr` input, `checkDrift()` on a
freshly compiled workspace returns an empty findings list.

**P20 — Drift detected on modification:** For any generated artifact that has been modified
(byte-level change), `checkDrift()` returns a non-empty findings list with `DRIFT_DETECTED`.

### Project Verification Properties

**P21 — PASSED on zero tsc exit:** When `pnpm exec tsc --noEmit` exits 0,
`ReactProjectVerificationRunner.run()` returns `ProjectTestResult` with status `PASSED`.

**P22 — FAILED on non-zero tsc exit:** When `pnpm exec tsc --noEmit` exits non-zero,
`ReactProjectVerificationRunner.run()` returns `ProjectTestResult` with status `FAILED`.

**P23 — BOOTSTRAP_IO on missing pnpm:** When `pnpm` is not on PATH,
`ReactProjectVerificationRunner.run()` returns `ProjectTestResult` with status `BOOTSTRAP_IO`.


---

## Test Fixtures

All fixtures live under `kernel/src/test/resources/fixtures/react/`.

### `vite-react-single-block`
```
package.json          (type:module, pnpm, react+react-dom deps)
pnpm-lock.yaml
tsconfig.json
vite.config.ts
src/features/user-dashboard/
  impl/UserDashboardFeatureImpl.tsx
build/generated/bear/types/user-dashboard/
  UserDashboardFeaturePorts.ts
  UserDashboardFeatureLogic.ts
  UserDashboardFeatureWrapper.ts
build/generated/bear/wiring/
  user-dashboard.wiring.json
```
Expected: detection SUPPORTED (vite-react), check passes.

### `vite-react-multi-block`
Two blocks (`user-dashboard`, `product-catalog`), no cross-feature imports.
Expected: detection SUPPORTED, check passes.

### `vite-react-with-shared`
One block importing from `src/shared/utils.ts`.
Expected: check passes (shared import allowed).

### `nextjs-single-block`
```
package.json          (type:module, pnpm, react+react-dom deps)
pnpm-lock.yaml
tsconfig.json         (compilerOptions.paths: {"@/*": ["./src/*"]})
next.config.js
src/features/user-dashboard/
  impl/UserDashboardFeatureImpl.tsx  (uses @/features/user-dashboard/... import)
```
Expected: detection SUPPORTED (nextjs-app-router), check passes.

### `nextjs-use-client`
Next.js block with `"use client";` as first statement in impl file.
Expected: directive detected as metadata signal, check passes.

### `invalid-workspace`
`pnpm-workspace.yaml` present.
Expected: detection UNSUPPORTED.

### `invalid-ambiguous-shape`
Both `vite.config.ts` and `next.config.js` present.
Expected: detection UNSUPPORTED.

### `boundary-bypass-escape`
Governed file with `import { x } from '../../outside/module'` escaping feature root.
Expected: check fails exit 7, BOUNDARY_BYPASS.

### `boundary-bypass-sibling`
Governed file with `import { x } from '../product-catalog/SomeComponent'`.
Expected: check fails exit 7, BOUNDARY_BYPASS.

### `boundary-bypass-bare-import`
Governed file with `import _ from 'lodash'`.
Expected: check fails exit 7, BOUNDARY_BYPASS.

### `boundary-bypass-fetch`
Governed `.tsx` file with `const data = await fetch('/api/users')`.
Expected: check fails exit 7, BOUNDARY_BYPASS (API boundary scanner).

### `nextjs-alias-sibling`
Next.js block with `import { x } from '@/features/other-block/Component'`.
Expected: check fails exit 7, BOUNDARY_BYPASS.

### `check-project-verification-failure`
Governed file with a TypeScript type error (e.g., `const x: number = "string"`).
Expected: `runProjectVerification()` returns FAILED, exit 4.

### `check-pr-check-boundary-expanding`
Base snapshot: `package.json` without `axios`. Head snapshot: `package.json` with `axios` added.
Expected: `ReactPrCheckContributor` returns BOUNDARY_EXPANDING finding, exit 5.

---

## File Layout

```
kernel/src/main/java/com/bear/kernel/target/react/
  ReactTarget.java
  ReactTargetDetector.java
  ReactProjectShape.java                  (enum: VITE_REACT, NEXTJS_APP_ROUTER)
  ReactArtifactGenerator.java
  ReactManifestGenerator.java
  ReactImportSpecifierExtractor.java      (thin wrapper/reuse of NodeImportSpecifierExtractor)
  ReactImportBoundaryResolver.java
  ReactImportContainmentScanner.java
  ReactApiBoundaryScanner.java
  ReactProjectVerificationRunner.java
  ReactPrCheckContributor.java

kernel/src/test/java/com/bear/kernel/target/react/
  ReactTargetTest.java
  ReactTargetDetectorTest.java
  ReactArtifactGeneratorTest.java
  ReactImportSpecifierExtractorTest.java
  ReactImportBoundaryResolverTest.java
  ReactImportContainmentScannerTest.java
  ReactApiBoundaryScannerTest.java
  ReactProjectVerificationRunnerTest.java
  ReactPrCheckContributorTest.java
  properties/
    ReactDetectionProperties.java
    ReactArtifactGenerationProperties.java
    ReactGovernedRootsProperties.java
    ReactImportContainmentProperties.java
    ReactDriftGateProperties.java
    ReactProjectVerificationProperties.java

kernel/src/test/resources/fixtures/react/
  vite-react-single-block/
  vite-react-multi-block/
  vite-react-with-shared/
  nextjs-single-block/
  nextjs-use-client/
  invalid-workspace/
  invalid-ambiguous-shape/
  boundary-bypass-escape/
  boundary-bypass-sibling/
  boundary-bypass-bare-import/
  boundary-bypass-fetch/
  nextjs-alias-sibling/
  check-project-verification-failure/
  check-pr-check-boundary-expanding/
```

**Touch-points in existing files:**
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` — add ReactTarget + ReactTargetDetector
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java` — add react/react-dom exclusion
- `kernel/src/main/java/com/bear/kernel/target/GovernanceProfile.java` — no change needed (react/feature-ui already documented in Javadoc)

---

## Naming Conventions

Follows BEAR conventions:
- Package: `com.bear.kernel.target.react` (all lowercase, no hyphens)
- Classes: PascalCase with `React` prefix for all new classes
- Enum: `ReactProjectShape` with values `VITE_REACT`, `NEXTJS_APP_ROUTER`
- Test classes: `<ClassName>Test.java` for unit tests, `<Domain>Properties.java` for PBT
- Fixtures: kebab-case directory names under `fixtures/react/`
- Generated artifacts: `build/generated/bear/types/<blockKey>/` and `build/generated/bear/wiring/`
- User impl: `src/features/<blockKey>/impl/<BlockName>FeatureImpl.tsx`

## Versioning

No version bumps required. This is an additive change behind the `Target` seam.
IR v1 schema is unchanged. Exit code registry is unchanged.
`TargetId.REACT` already exists in the enum.
