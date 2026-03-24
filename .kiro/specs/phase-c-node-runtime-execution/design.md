# Phase C: Node Target — Runtime Execution Design

Phase C completes the Node/TypeScript target by implementing the runtime execution capabilities
deferred from Phase B. After this phase, a Node/TypeScript project runs the full `bear check`
pipeline: manifest parsing, workspace preparation, import containment (with dynamic import
enforcement and `@/*` alias resolution), and TypeScript project verification.

Builds on Phase B (`NodeTarget`, `NodeImportContainmentScanner`, `NodeDynamicImportDetector`,
`NodeImportBoundaryResolver`). Follows the patterns established by Phase R (React full pipeline).

## Scope

In scope:
- `NodeManifestParser` — Node-specific wiring manifest parser (distinct from JVM `TargetManifestParsers`)
- `NodeTarget.prepareCheckWorkspace()` — copies `src/blocks/_shared/` to temp workspace if present
- `NodeTarget` JVM-specific stubs — all 8 methods return `null`/`List.of()` (no `UnsupportedOperationException`)
- `NodeProjectVerificationRunner` — runs `pnpm exec tsc --noEmit`
- Dynamic `import()` enforcement — promoted from advisory to enforced (`DYNAMIC_IMPORT_FORBIDDEN`)
- `NodePathAliasResolver` — reads `tsconfig.json` `compilerOptions.paths["@/*"]`
- `NodeImportBoundaryResolver` — extended with `@/*` alias resolution

Out of scope (future phases):
- `tsconfig.json` paths entries beyond `@/*`
- Undeclared reach scanning (Node built-ins: `fs`, `net`, `http`, `child_process`)
- Dynamic execution scanning (`eval`, `Function()`)
- Dependency governance (`pr-check` lock-file delta for Node)
- Workspace/monorepo layouts, npm/yarn, CommonJS


---

## Architecture

Phase C slots into the existing Node target seam. No changes to the deterministic core outside
`com.bear.kernel.target.node` and the two touch-points already established in Phase B.

```
TargetRegistry
  └── NodeTarget (Phase C — stubs replaced, new delegates wired)
        ├── NodeManifestParser          (NEW — parses Node wiring manifest JSON)
        ├── NodeProjectVerificationRunner (NEW — pnpm exec tsc --noEmit)
        ├── NodePathAliasResolver        (NEW — reads tsconfig.json @/* paths)
        ├── NodeImportContainmentScanner (UPDATED — dynamic imports enforced)
        │     ├── NodeImportSpecifierExtractor  (unchanged)
        │     ├── NodeDynamicImportDetector     (unchanged — detection logic)
        │     └── NodeImportBoundaryResolver    (UPDATED — @/* alias resolution)
        └── [Phase B components unchanged]
              TypeScriptArtifactGenerator
              TypeScriptManifestGenerator
              NodeTargetDetector
```

All Phase B tests pass without modification. Phase C changes are strictly additive or
replace `UnsupportedOperationException` stubs with real implementations.

---

## Components and Interfaces

### 1. `NodeManifestParser`

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodeManifestParser.java`

Parses the Node wiring manifest JSON schema. This schema is **distinct** from the JVM schema
in `TargetManifestParsers` — it uses `version`/`targetId`/`generatedPackage`/`implPackage`/
`wrappers`/`ports` rather than `schemaVersion`/`entrypointFqcn`/`logicInterfaceFqcn`/etc.

Uses the same regex-based JSON parsing approach as `TargetManifestParsers` (no Jackson in kernel).
Reuses `TargetManifestParsers.extractRequiredString()` and `extractRequiredArrayPayload()` as
static utility methods.

**Node wiring manifest schema:**
```json
{
  "version": "1",
  "blockKey": "<blockKey>",
  "targetId": "node",
  "generatedPackage": "build/generated/bear/types/<blockKey>",
  "implPackage": "src/blocks/<blockKey>/impl",
  "wrappers": [{ "operation": "<op>", "wrapperClass": "<Block>_<Op>", "wrapperPath": "..." }],
  "ports": [{ "name": "<port>", "kind": "EXTERNAL|BLOCK", "interface": "<Port>Port" }]
}
```

**`WiringManifest` field mapping** (Node JSON → shared record):

| Node JSON field    | `WiringManifest` field    | Notes                                      |
|--------------------|---------------------------|--------------------------------------------|
| `version`          | `schemaVersion`           | Must equal `"1"`                           |
| `blockKey`         | `blockKey`                | Required                                   |
| `targetId`         | `entrypointFqcn`          | Stored as-is (`"node"`)                    |
| `generatedPackage` | `logicInterfaceFqcn`      | Stored as-is                               |
| `implPackage`      | `implFqcn`                | Stored as-is                               |
| `wrappers` array   | `governedSourceRoots`     | Serialized as JSON strings                 |
| `ports` array      | `requiredEffectPorts`     | Serialized as JSON strings                 |
| (absent)           | `implSourcePath`          | `""` (empty)                               |
| (absent)           | `blockRootSourceDir`      | `""` (empty)                               |
| (absent)           | `constructorPortParams`   | `List.of()`                                |
| (absent)           | `logicRequiredPorts`      | `List.of()`                                |
| (absent)           | `wrapperOwnedSemanticPorts` | `List.of()`                              |
| (absent)           | `wrapperOwnedSemanticChecks` | `List.of()`                             |
| (absent)           | `blockPortBindings`       | `List.of()`                                |

**Interface:**
```java
public final class NodeManifestParser {
    private NodeManifestParser() {}

    public static WiringManifest parse(Path path)
            throws IOException, ManifestParseException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String version = TargetManifestParsers.extractRequiredString(json, "version");
        if (!"1".equals(version)) {
            throw new ManifestParseException("UNSUPPORTED_WIRING_SCHEMA_VERSION");
        }
        String blockKey    = TargetManifestParsers.extractRequiredString(json, "blockKey");
        String targetId    = TargetManifestParsers.extractRequiredString(json, "targetId");
        String genPackage  = extractOptionalString(json, "generatedPackage");
        String implPackage = extractOptionalString(json, "implPackage");
        List<String> wrappers = extractOptionalStringArray(json, "wrappers");
        List<String> ports    = extractOptionalStringArray(json, "ports");

        return new WiringManifest(
            version, blockKey, targetId, genPackage, implPackage,
            "", "", wrappers, ports, List.of(), List.of(), List.of(), List.of()
        );
    }
}
```

**Error codes:**

| Condition                          | `ManifestParseException` code          |
|------------------------------------|----------------------------------------|
| Does not start with `{`/end `}`    | `MALFORMED_JSON`                       |
| `version` field absent             | `MISSING_KEY_version`                  |
| `blockKey` field absent            | `MISSING_KEY_blockKey`                 |
| `version` ≠ `"1"`                  | `UNSUPPORTED_WIRING_SCHEMA_VERSION`    |

---

### 2. `NodeTarget` — stub replacement and new delegates

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (modified)

Replaces all Phase B `UnsupportedOperationException` stubs with real implementations.
Follows `ReactTarget` as the exact structural model.

**`prepareCheckWorkspace()`** — copies `src/blocks/_shared/` to temp workspace if present:
```java
@Override
public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException {
    Path sharedDir = projectRoot.resolve("src/blocks/_shared");
    if (Files.isDirectory(sharedDir)) {
        Files.createDirectories(tempRoot.resolve("src/blocks/_shared"));
    }
}
```
Same pattern as `PythonTarget.prepareCheckWorkspace()` and `ReactTarget.prepareCheckWorkspace()`.

**`parseWiringManifest()`** — delegates to `NodeManifestParser`:
```java
@Override
public WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
    return NodeManifestParser.parse(path);
}
```

**`runProjectVerification()`** — delegates to `NodeProjectVerificationRunner`:
```java
@Override
public ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath)
        throws IOException, InterruptedException {
    // initScriptRelativePath is JVM-specific (Gradle init script); Node ignores it
    return NodeProjectVerificationRunner.run(projectRoot);
}
```

**JVM-specific stubs** — all return `null` or `List.of()`, no exception:

| Method                                  | Return value  |
|-----------------------------------------|---------------|
| `containmentSkipInfoLine()`             | `null`        |
| `preflightContainmentIfRequired()`      | `null`        |
| `verifyContainmentMarkersIfRequired()`  | `null`        |
| `scanPortImplContainmentBypass()`       | `List.of()`   |
| `scanBlockPortBindings()`               | `List.of()`   |
| `scanMultiBlockPortImplAllowedSignals()`| `List.of()`   |
| `scanUndeclaredReach()`                 | `List.of()`   |
| `scanForbiddenReflectionDispatch()`     | `List.of()`   |

---

### 3. `NodeProjectVerificationRunner`

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodeProjectVerificationRunner.java`

Runs `pnpm exec tsc --noEmit` in the project root. Follows `ReactProjectVerificationRunner`
**exactly** — same structure, same timeout (300s), same BOOTSTRAP_IO detection logic, same
thread-based output capture.

**`run(projectRoot)` algorithm:**
```
1. isPnpmAvailable() → which pnpm (Linux/Mac) or where pnpm (Windows)
   if not found → return ProjectTestResult(BOOTSTRAP_IO, "pnpm not found on PATH", phase="tsc")

2. command = ["pnpm", "exec", "tsc", "--noEmit"]
3. ProcessBuilder(command).directory(projectRoot).redirectErrorStream(true)
4. start process, capture stdout+stderr via daemon background thread
5. waitFor(300 seconds)
   if timeout → destroyForcibly, waitFor(5s), joinOutputReader
               → return ProjectTestResult(TIMEOUT, output, phase="tsc")
6. joinOutputReader
   exitCode = process.exitValue()
   if exitCode == 0 → return ProjectTestResult(PASSED, output, phase="tsc")
   if isTscMissing(output) → return ProjectTestResult(BOOTSTRAP_IO, output, phase="tsc")
   → return ProjectTestResult(FAILED, output, phase="tsc")
```

**`isTscMissing(output)` detection** — same patterns as `ReactProjectVerificationRunner`:
- `output.contains("Cannot find module")` AND output references `typescript/lib/tsc.js`,
  `node_modules/.bin/tsc`, or `typescript'`
- `output` contains `command "tsc" not found`, `tsc: command not found`, `tsc is not recognized`,
  `unknown command "tsc"`, etc.

**Exit code mapping** (handled by `app/` layer):

| Status         | Exit |
|----------------|------|
| `PASSED`       | `0`  |
| `FAILED`       | `4`  |
| `BOOTSTRAP_IO` | `74` |
| `TIMEOUT`      | `4`  |

**`findPnpm()` / `isPnpmAvailable()`** — checks `which pnpm` (Linux/Mac) or `where pnpm` (Windows),
10-second timeout, returns `true` if exit code 0.

---

### 4. Dynamic Import Enforcement in `NodeImportContainmentScanner`

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodeImportContainmentScanner.java` (modified)

Phase B: `dynamicDetector.detectDynamicImports(content)` result was ignored (advisory only).
Phase C: promote to enforced — each `DynamicImport` found produces a `BoundaryBypassFinding`.

**Change in `scan()` loop:**
```java
// Phase C: dynamic imports are enforced (exit 7, CODE=DYNAMIC_IMPORT_FORBIDDEN)
List<NodeDynamicImportDetector.DynamicImport> dynamicImports =
    dynamicDetector.detectDynamicImports(content);
for (NodeDynamicImportDetector.DynamicImport di : dynamicImports) {
    findings.add(new BoundaryBypassFinding(
        "DYNAMIC_IMPORT_FORBIDDEN",
        projectRoot.relativize(file).toString(),
        "Dynamic import specifier: " + di.specifier()
    ));
}
```

The `NodeDynamicImportDetector` class itself is **unchanged**. Only the scanner's handling
of its output changes. All findings are collected before returning (no early exit).

---

### 5. `NodePathAliasResolver`

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodePathAliasResolver.java`

Reads `tsconfig.json` at project root, extracts `compilerOptions.paths["@/*"][0]`.
Result is cached in the resolver instance (read once per scan invocation).

**Resolution algorithm:**
```
resolve(specifier, projectRoot):
  if not specifier.startsWith("@/") → return Optional.empty()
  alias = getAlias(projectRoot)   // cached
  if alias.isEmpty() → return Optional.empty()
  // alias is e.g. "./src/*" → strip trailing "/*" → "./src/"
  prefix = alias.removeSuffix("*")   // "./src/"
  suffix = specifier.removePrefix("@/")  // "foo/bar"
  resolved = projectRoot.resolve(prefix + suffix).normalize()
  return Optional.of(resolved)

getAlias(projectRoot):
  if cached → return cached
  tsconfig = projectRoot.resolve("tsconfig.json")
  if not exists → cache(Optional.empty()), return Optional.empty()
  json = readString(tsconfig)
  // extract compilerOptions.paths["@/*"][0] via regex
  match = regex: "\"@/\\*\"\\s*:\\s*\\[\\s*\"([^\"]+)\"" in json
  if no match → cache(Optional.empty()), return Optional.empty()
  cache(Optional.of(match.group(1)))
  return cached
```

**Key behaviors:**
- `tsconfig.json` missing or unreadable → `Optional.empty()` (caller treats `@/` as bare → FAIL)
- No `@/*` entry in `compilerOptions.paths` → `Optional.empty()`
- Multi-element array → only first element used (regex matches first `"..."` after `[`)
- Other `paths` entries (e.g., `#utils`) → ignored; only `@/*` is extracted
- Uses simple string/regex parsing (no Jackson in kernel)

---

### 6. `NodeImportBoundaryResolver` — `@/*` alias resolution

**File:** `kernel/src/main/java/com/bear/kernel/target/node/NodeImportBoundaryResolver.java` (modified)

Adds `@/*` alias resolution before the existing bare-specifier check. The resolver needs
access to `NodePathAliasResolver` — injected via constructor (or passed `projectRoot` to
`resolve()`). The simplest approach: add `projectRoot` as a parameter to `resolve()` and
construct `NodePathAliasResolver` lazily (or accept it as a constructor parameter).

**Design decision:** Accept `NodePathAliasResolver` as a constructor parameter so the
resolver instance (with its cache) is shared across all files in a single scan invocation.
`NodeImportContainmentScanner` constructs one `NodePathAliasResolver` per scan and passes
it to `NodeImportBoundaryResolver`.

**Updated `resolve()` logic** (inserted before the existing bare-specifier check):
```java
// @/* alias resolution (Phase C)
if (specifier.startsWith("@/")) {
    Optional<Path> resolved = aliasResolver.resolve(specifier, projectRoot);
    if (resolved.isEmpty()) {
        return BoundaryDecision.fail("BOUNDARY_BYPASS");
    }
    Path target = resolved.get();
    // Apply same boundary rules as relative imports
    Path generatedDir = projectRoot.resolve("build/generated/bear");
    if (target.startsWith(generatedDir)) return BoundaryDecision.allowed();
    Path importingRoot = findGovernedRoot(importingFile, governedRoots);
    if (importingRoot != null && target.startsWith(importingRoot)) return BoundaryDecision.allowed();
    Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
    if (target.startsWith(sharedRoot)) return BoundaryDecision.allowed();
    return BoundaryDecision.fail("BOUNDARY_BYPASS");
}
```

The existing `isAliasSpecifier()` check (which catches `#`-prefixed specifiers) is unchanged.
`@/` specifiers are now handled before reaching the bare-specifier check.

---

## Data Models

No new data models. All Phase C components use existing shared models:

```java
// Unchanged from Phase B
record WiringManifest(String schemaVersion, String blockKey, String entrypointFqcn,
    String logicInterfaceFqcn, String implFqcn, String implSourcePath,
    String blockRootSourceDir, List<String> governedSourceRoots,
    List<String> requiredEffectPorts, List<String> constructorPortParams,
    List<String> logicRequiredPorts, List<String> wrapperOwnedSemanticPorts,
    List<String> wrapperOwnedSemanticChecks, List<BlockPortBinding> blockPortBindings)

record BoundaryBypassFinding(String rule, String path, String detail)
record BoundaryDecision(boolean pass, String failureReason)
record ProjectTestResult(ProjectTestStatus status, String output, ...)
```

`NodeDynamicImportDetector.DynamicImport` record (unchanged from Phase B):
```java
record DynamicImport(String specifier, int lineNumber, int columnNumber)
```

`NodePathAliasResolver` is a stateful class (not a record) because it caches the tsconfig
parse result across multiple `resolve()` calls within a single scan.

---

## Error Handling

Error envelope (frozen from Phase A):
```
<error details>
CODE=<error_code>
PATH=<file_path>
REMEDIATION=<remediation_message>
```

Phase C adds one new error code:

| Category      | Code                       | Exit | Remediation                                      |
|---------------|----------------------------|------|--------------------------------------------------|
| Containment   | `DYNAMIC_IMPORT_FORBIDDEN` | `7`  | Replace `import()` with a static `import` statement |

All existing Phase B error codes are unchanged:

| Category      | Code                | Exit | Remediation                              |
|---------------|---------------------|------|------------------------------------------|
| Containment   | `BOUNDARY_BYPASS`   | `7`  | Remove or relocate the import            |
| Containment   | `BARE_PACKAGE_IMPORT` | `7` | Remove bare import from governed code   |
| Containment   | `ALIAS_IMPORT`      | `7`  | Use relative imports instead             |
| Verification  | (tsc failure)       | `4`  | Fix TypeScript type errors               |
| Tool missing  | (pnpm/tsc absent)   | `74` | Install pnpm and TypeScript              |
| Drift         | `DRIFT_DETECTED`    | `5`  | Run `bear compile`                       |

**Error handling rules:**
- `NodeManifestParser` throws `ManifestParseException` (checked); caller maps to structured error.
- `NodeProjectVerificationRunner` returns `ProjectTestResult` (never throws for tool-missing cases).
- `NodePathAliasResolver` returns `Optional.empty()` for all unresolvable cases (never throws).
- `IOException` from file I/O → wrapped in `TargetException`, exit `74`.
- All containment findings collected before reporting (no early exit on first violation).


---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions
of a system — essentially, a formal statement about what the system should do. Properties serve
as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

PBT library: **jqwik** (Java). Minimum 100 iterations per property.
Tag format: `// Feature: phase-c-node-runtime-execution, Property N: <text>`

---

### Property 1: Manifest parsing populates all fields

*For any* valid Node wiring manifest JSON (with `version="1"`, `blockKey`, `targetId`, `generatedPackage`,
`implPackage`, non-empty `wrappers` and `ports` arrays), `NodeManifestParser.parse()` returns a
`WiringManifest` where `schemaVersion`, `blockKey`, `entrypointFqcn`, `logicInterfaceFqcn`, and
`implFqcn` are all non-null and non-empty.

**Validates: Requirements 1.1, 1.2**

---

### Property 2: Manifest round-trip stability

*For any* valid Node wiring manifest JSON, parsing it to a `WiringManifest`, serializing the
key fields back to a JSON string, then parsing again SHALL produce a `WiringManifest` with
identical `schemaVersion`, `blockKey`, `entrypointFqcn`, `logicInterfaceFqcn`, and `implFqcn`
values.

**Validates: Requirements 1.9**

---

### Property 3: Malformed JSON rejected

*For any* string that does not start with `{` or does not end with `}`, `NodeManifestParser.parse()`
SHALL throw `ManifestParseException` with code `MALFORMED_JSON`.

**Validates: Requirements 1.5**

---

### Property 4: Unsupported version rejected

*For any* otherwise-valid Node manifest JSON where the `version` field is any string other than
`"1"`, `NodeManifestParser.parse()` SHALL throw `ManifestParseException` with code
`UNSUPPORTED_WIRING_SCHEMA_VERSION`.

**Validates: Requirements 1.6**

---

### Property 5: Check workspace preparation with `_shared`

*For any* project root that contains a `src/blocks/_shared/` directory, calling
`NodeTarget.prepareCheckWorkspace(projectRoot, tempRoot)` SHALL result in
`tempRoot/src/blocks/_shared/` existing as a directory, and no exception being thrown.

**Validates: Requirements 2.1**

---

### Property 6: JVM stub methods never throw

*For all* 8 JVM-specific stub methods (`containmentSkipInfoLine`, `preflightContainmentIfRequired`,
`verifyContainmentMarkersIfRequired`, `scanPortImplContainmentBypass`, `scanBlockPortBindings`,
`scanMultiBlockPortImplAllowedSignals`, `scanUndeclaredReach`, `scanForbiddenReflectionDispatch`),
calling each on any `NodeTarget` instance SHALL return the expected value (`null` or `List.of()`)
without throwing any exception.

**Validates: Requirements 3.1–3.9**

---

### Property 7: BOOTSTRAP_IO on tsc-missing output patterns

*For any* output string that contains a recognized tsc-missing pattern (`"Cannot find module"`
combined with a TypeScript reference, or `"command \"tsc\" not found"`, `"tsc: command not found"`,
`"tsc is not recognized"`, `"unknown command \"tsc\""`, etc.), `NodeProjectVerificationRunner`'s
`isTscMissing()` check SHALL return `true`, causing the runner to return `BOOTSTRAP_IO` status.

**Validates: Requirements 4.5**

---

### Property 8: Dynamic import findings include path and specifier

*For any* governed `.ts` file containing one or more `import(...)` expressions,
`NodeImportContainmentScanner.scan()` SHALL produce at least one `BoundaryBypassFinding` per
dynamic import where `rule` equals `"DYNAMIC_IMPORT_FORBIDDEN"`, `path` is the repo-relative
file path, and `detail` contains the import specifier.

**Validates: Requirements 5.1, 5.4**

---

### Property 9: No dynamic import findings for clean files

*For any* governed `.ts` file containing no `import(...)` expressions,
`NodeImportContainmentScanner.scan()` SHALL produce zero `BoundaryBypassFinding` entries with
`rule` equal to `"DYNAMIC_IMPORT_FORBIDDEN"` for that file.

**Validates: Requirements 5.2**

---

### Property 10: All dynamic imports collected before reporting

*For any* governed `.ts` file containing N dynamic `import(...)` expressions (N ≥ 2),
`NodeImportContainmentScanner.scan()` SHALL produce exactly N `DYNAMIC_IMPORT_FORBIDDEN`
findings for that file (no early exit after the first violation).

**Validates: Requirements 5.6**

---

### Property 11: `@/*` alias resolves to `src/` subtree

*For any* project root with a `tsconfig.json` containing `compilerOptions.paths["@/*"]`
mapping to `["./src/*"]`, and *for any* specifier of the form `@/<path>`,
`NodePathAliasResolver.resolve()` SHALL return `Optional.of(projectRoot.resolve("src/" + path).normalize())`.

**Validates: Requirements 6.1**

---

### Property 12: `@/*` alias to same block passes

*For any* project with a configured `@/*` alias, and *for any* `@/`-prefixed specifier that
resolves to a path within the importing file's own block root,
`NodeImportBoundaryResolver.resolve()` SHALL return `BoundaryDecision.allowed()`.

**Validates: Requirements 6.2**

---

### Property 13: `@/*` alias to sibling block fails

*For any* project with a configured `@/*` alias, and *for any* `@/`-prefixed specifier that
resolves to a path within a different block root (sibling block),
`NodeImportBoundaryResolver.resolve()` SHALL return `BoundaryDecision.fail("BOUNDARY_BYPASS")`.

**Validates: Requirements 6.3**

---

### Property 14: `@/*` alias to `_shared` passes

*For any* project with a configured `@/*` alias, and *for any* `@/`-prefixed specifier that
resolves to a path within `src/blocks/_shared/`,
`NodeImportBoundaryResolver.resolve()` SHALL return `BoundaryDecision.allowed()`.

**Validates: Requirements 6.4**

---

### Property 15: `@/*` alias to ungoverned path fails

*For any* project with a configured `@/*` alias, and *for any* `@/`-prefixed specifier that
resolves to a path outside all governed roots and outside `build/generated/bear/`,
`NodeImportBoundaryResolver.resolve()` SHALL return `BoundaryDecision.fail("BOUNDARY_BYPASS")`.

**Validates: Requirements 6.5**

---

### Property 16: Non-`@/*` alias entries ignored

*For any* `tsconfig.json` containing `compilerOptions.paths` entries with keys other than `@/*`
(e.g., `#utils`, `~/*`, `$lib/*`), `NodePathAliasResolver` SHALL return `Optional.empty()` for
specifiers using those aliases, causing `NodeImportBoundaryResolver` to fail with `BOUNDARY_BYPASS`.

**Validates: Requirements 6.7**

---

### Property 17: Deterministic scan output

*For any* Node project state (fixed file contents, fixed wiring manifests), calling
`NodeImportContainmentScanner.scan()` twice in succession SHALL produce identical
`BoundaryBypassFinding` lists (same order, same content).

**Validates: Requirements 7.8**

---

### Property 18: `NodeTarget.parseWiringManifest()` delegates to `NodeManifestParser`

*For any* valid Node wiring manifest file, `NodeTarget.parseWiringManifest(path)` SHALL return
a `WiringManifest` with `schemaVersion` equal to `"1"` and `entrypointFqcn` equal to `"node"`,
confirming it uses the Node parser (not the JVM `TargetManifestParsers` which requires `schemaVersion: "v3"`).

**Validates: Requirements 1.7, 1.8**

---

### Property 19: `NodeProjectVerificationRunner` phase tag

*For any* invocation of `NodeProjectVerificationRunner.run()` that completes (PASSED, FAILED,
BOOTSTRAP_IO, or TIMEOUT), the returned `ProjectTestResult` SHALL have `phase` equal to `"tsc"`.

**Validates: Requirements 4.9**

---

### Property 20: `NodePathAliasResolver` caches tsconfig read

*For any* project root, calling `NodePathAliasResolver.resolve()` N times (N ≥ 2) on the same
resolver instance SHALL read `tsconfig.json` exactly once (verified by wrapping file I/O in a
counting mock), confirming the cache is used for subsequent calls.

**Validates: Requirements 6.10**

---

### Property 21: `prepareCheckWorkspace()` no-op when `_shared` absent

*For any* project root that does NOT contain `src/blocks/_shared/`, calling
`NodeTarget.prepareCheckWorkspace(projectRoot, tempRoot)` SHALL complete without error and
SHALL NOT create `tempRoot/src/blocks/_shared/`.

**Validates: Requirements 2.2**

---

### Property 22: Both static bypass and dynamic import reported independently

*For any* governed `.ts` file containing both a static boundary-bypass import and a dynamic
`import(...)` expression, `NodeImportContainmentScanner.scan()` SHALL produce at least two
findings: one with `rule="BOUNDARY_BYPASS"` and one with `rule="DYNAMIC_IMPORT_FORBIDDEN"`.

**Validates: Requirements 5.5**

---

## Testing Strategy

### Dual Testing Approach

Unit tests verify specific examples, edge cases, and error conditions. Property tests verify
universal properties across all inputs. Both are required for comprehensive coverage.

Unit tests focus on:
- Specific manifest parsing examples (valid, each missing field, wrong version)
- Specific stub return values
- Specific verification runner outcomes (PASSED, FAILED, BOOTSTRAP_IO, TIMEOUT)
- Specific alias resolution examples (no tsconfig, missing paths, multi-element array)
- Integration scenarios (both findings reported, clean project)

Property tests focus on:
- Universal manifest parsing correctness (all valid inputs, all malformed inputs)
- Round-trip stability
- Dynamic import enforcement across all file shapes
- Alias resolution boundary decisions across all path combinations
- Determinism across repeated invocations

### Unit Tests

```
kernel/src/test/java/com/bear/kernel/target/node/
  NodeManifestParserTest.java
  NodeProjectVerificationRunnerTest.java
  NodePathAliasResolverTest.java
  NodeImportBoundaryResolverTest.java      (updated — alias cases)
  NodeImportContainmentScannerTest.java    (updated — dynamic import enforcement)
  NodeTargetTest.java                      (updated — stub resolution + new delegates)
```

Key cases per class:

**`NodeManifestParserTest`:**
- Valid manifest → all fields populated correctly
- `version` missing → `MISSING_KEY_version`
- `blockKey` missing → `MISSING_KEY_blockKey`
- `version` = `"2"` → `UNSUPPORTED_WIRING_SCHEMA_VERSION`
- Malformed JSON (no `{`) → `MALFORMED_JSON`
- Empty `wrappers`/`ports` arrays → `List.of()` in result
- `NodeTarget.parseWiringManifest()` on Node manifest → succeeds (not JVM parser)

**`NodeProjectVerificationRunnerTest`:**
- `isTscMissing()` with `"Cannot find module"` + `"typescript'"` → `true`
- `isTscMissing()` with `"command \"tsc\" not found"` → `true`
- `isTscMissing()` with normal tsc error output → `false`
- `isPnpmAvailable()` mocked → BOOTSTRAP_IO returned when false
- Result phase always `"tsc"`

**`NodePathAliasResolverTest`:**
- `tsconfig.json` with `@/*` → `["./src/*"]` → resolves `@/foo/bar` to `projectRoot/src/foo/bar`
- `tsconfig.json` missing → `Optional.empty()`
- `tsconfig.json` with no `compilerOptions.paths` → `Optional.empty()`
- `tsconfig.json` with `@/*` → `["./src/*", "./alt/*"]` → uses first element only
- `tsconfig.json` with only `#utils` entry → `Optional.empty()` for `@/` specifiers
- Second call on same instance → reads file only once (count-based mock)

**`NodeImportBoundaryResolverTest` (updated):**
- `@/blocks/my-block/foo` with alias configured, same block → `allowed()`
- `@/blocks/other-block/foo` with alias configured → `fail("BOUNDARY_BYPASS")`
- `@/blocks/_shared/utils` with alias configured → `allowed()`
- `@/outside/path` with alias configured → `fail("BOUNDARY_BYPASS")`
- `@/foo` with no alias configured → `fail("BOUNDARY_BYPASS")`
- `@/foo` with missing tsconfig → `fail("BOUNDARY_BYPASS")`

**`NodeImportContainmentScannerTest` (updated):**
- File with `import('./other')` → finding `DYNAMIC_IMPORT_FORBIDDEN`
- File with no dynamic imports → no `DYNAMIC_IMPORT_FORBIDDEN` findings
- File with 3 dynamic imports → 3 `DYNAMIC_IMPORT_FORBIDDEN` findings
- File with static bypass + dynamic import → both findings present
- Finding `path` is repo-relative, `detail` contains specifier

**`NodeTargetTest` (updated):**
- All 8 stub methods return expected value, no exception
- `prepareCheckWorkspace()` with `_shared` → directory created in tempRoot
- `prepareCheckWorkspace()` without `_shared` → no error, no directory
- `parseWiringManifest()` on Node manifest → succeeds with `schemaVersion="1"`
- `runProjectVerification()` returns result with `phase="tsc"`

### Property Tests

```
kernel/src/test/java/com/bear/kernel/target/node/properties/
  ManifestParsingProperties.java
  ProjectVerificationProperties.java
  DynamicImportEnforcementProperties.java
  PathAliasResolutionProperties.java
```

**`ManifestParsingProperties`** — Properties 1, 2, 3, 4, 18:
```java
@Provide Arbitrary<String> validNodeManifestJson() {
    // generates JSON with version="1", random blockKey, targetId="node",
    // random generatedPackage, implPackage, wrappers array, ports array
}
@Provide Arbitrary<String> malformedJson() {
    // generates strings not starting with { or not ending with }
}
@Provide Arbitrary<String> nonOneVersionManifest() {
    // generates valid manifests with version != "1"
}
```

**`ProjectVerificationProperties`** — Property 7, 19:
```java
@Provide Arbitrary<String> tscMissingOutputStrings() {
    // generates strings containing recognized tsc-missing patterns
}
```

**`DynamicImportEnforcementProperties`** — Properties 8, 9, 10, 17, 22:
```java
@Provide Arbitrary<String> tsSourceWithDynamicImports(int count) {
    // generates TypeScript source with exactly `count` dynamic import() expressions
}
@Provide Arbitrary<String> tsSourceWithoutDynamicImports() {
    // generates TypeScript source with only static imports
}
```

**`PathAliasResolutionProperties`** — Properties 11, 12, 13, 14, 15, 16:
```java
@Provide Arbitrary<String> atSlashSpecifiers() {
    // generates strings starting with "@/" followed by random path segments
}
@Provide Arbitrary<String> nonAtStarAliasKeys() {
    // generates alias keys like "#utils", "~/*", "$lib/*" (not "@/*")
}
```

**Property test configuration:**
- Minimum 100 iterations per property (`@Property(tries = 100)`)
- Each test tagged with: `// Feature: phase-c-node-runtime-execution, Property N: <text>`
- Each correctness property implemented by exactly one property-based test

### Integration Test Fixtures

```
kernel/src/test/resources/fixtures/node/
  check-clean/                        (NEW) — all pipeline stages pass, exit 0
  check-dynamic-import/               (NEW) — dynamic import in governed file, exit 7
  check-project-verification-failure/ (NEW) — TypeScript type error, exit 4
  check-alias-same-block/             (NEW) — @/* alias to same block, exit 0
  check-alias-sibling-block/          (NEW) — @/* alias to sibling block, exit 7
  check-alias-no-tsconfig-paths/      (NEW) — @/* import, no compilerOptions.paths, exit 7
```

**`check-clean/` structure:**
```
package.json          (type:module, pnpm, no react deps)
pnpm-lock.yaml
tsconfig.json         (compilerOptions.paths: {"@/*": ["./src/*"]})
build/generated/bear/wiring/my-block.wiring.json  (Node schema, version="1")
src/blocks/my-block/
  impl/MyBlockImpl.ts
  index.ts            (only static imports within block)
```

**`check-dynamic-import/` structure:**
```
[same base as check-clean]
src/blocks/my-block/
  index.ts            (contains: const mod = await import('./other'))
```

**`check-alias-same-block/` structure:**
```
[same base with tsconfig @/* alias]
src/blocks/my-block/
  index.ts            (contains: import { x } from '@/blocks/my-block/utils')
  utils.ts
```

**`check-alias-sibling-block/` structure:**
```
[same base with tsconfig @/* alias, two blocks]
src/blocks/my-block/
  index.ts            (contains: import { x } from '@/blocks/other-block/utils')
src/blocks/other-block/
  utils.ts
```

**End-to-end scenarios:**
1. `check-clean` → all stages pass, exit 0
2. `check-dynamic-import` → exit 7, `DYNAMIC_IMPORT_FORBIDDEN`
3. `check-project-verification-failure` → exit 4 (tsc type error)
4. `check-alias-same-block` → exit 0
5. `check-alias-sibling-block` → exit 7, `BOUNDARY_BYPASS`
6. `check-alias-no-tsconfig-paths` → exit 7, `BOUNDARY_BYPASS`

### Regression

- All existing Phase B tests pass without modification
- All existing JVM, Python, React tests pass without modification
- `NodeTarget` Phase B behavior (scan-only) unchanged for projects not using Phase C features
- `NodeImportBoundaryResolver` existing tests (relative imports, bare specifiers, `_shared`) unchanged

---

## File Layout

New files:
```
kernel/src/main/java/com/bear/kernel/target/node/
  NodeManifestParser.java
  NodeProjectVerificationRunner.java
  NodePathAliasResolver.java
```

Modified files:
```
kernel/src/main/java/com/bear/kernel/target/node/
  NodeTarget.java                    — replace stubs, delegate to new classes
  NodeImportBoundaryResolver.java    — add @/* alias resolution
  NodeImportContainmentScanner.java  — enforce dynamic imports
```

New test files:
```
kernel/src/test/java/com/bear/kernel/target/node/
  NodeManifestParserTest.java
  NodeProjectVerificationRunnerTest.java
  NodePathAliasResolverTest.java
  properties/
    ManifestParsingProperties.java
    ProjectVerificationProperties.java
    DynamicImportEnforcementProperties.java
    PathAliasResolutionProperties.java
```

Updated test files:
```
kernel/src/test/java/com/bear/kernel/target/node/
  NodeImportBoundaryResolverTest.java
  NodeImportContainmentScannerTest.java
  NodeTargetTest.java
```

New fixtures:
```
kernel/src/test/resources/fixtures/node/
  check-clean/
  check-dynamic-import/
  check-project-verification-failure/
  check-alias-same-block/
  check-alias-sibling-block/
  check-alias-no-tsconfig-paths/
```

No changes to:
```
kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java   (NodeTarget already registered)
kernel/src/main/java/com/bear/kernel/target/node/NodeDynamicImportDetector.java  (unchanged)
kernel/src/main/java/com/bear/kernel/target/node/NodeImportSpecifierExtractor.java (unchanged)
kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java          (unchanged)
```

---

## Validation Criteria

Phase C is complete when:
- All 22 correctness properties pass (100+ iterations each)
- All unit tests pass
- All integration tests pass
- All existing Phase B tests pass without modification
- All existing JVM, Python, React tests pass without modification
- `check-clean` fixture passes full `bear check` (exit 0)
- `check-dynamic-import` fixture fails `bear check` (exit 7, `DYNAMIC_IMPORT_FORBIDDEN`)
- `check-project-verification-failure` fixture fails `bear check` (exit 4)
- `check-alias-same-block` fixture passes `bear check` (exit 0)
- `check-alias-sibling-block` fixture fails `bear check` (exit 7, `BOUNDARY_BYPASS`)
- `check-alias-no-tsconfig-paths` fixture fails `bear check` (exit 7, `BOUNDARY_BYPASS`)
