# Phase P: Python Target — Design

Phase P introduces `PythonTarget` as the second non-JVM target. Implements scan-only capabilities:
detection, Python artifact generation, governed root computation, import containment (AST-based),
drift checking, and the `impl.allowedDeps` unsupported guard.

Follows the Target interface contract from Phase A. Uses `NodeTarget` and `JvmTarget` as reference patterns.

Inner profile only: `python/service` (strict third-party import blocking).

## Scope

In scope:
- Python project detection (`pyproject.toml` + `uv`/`poetry` + `mypy`)
- Python artifact generation from IR
- Import containment scanning (static imports only, AST-based)
- Drift gate for generated artifacts
- `impl.allowedDeps` unsupported guard (exit `64`)
- Inner profile only: `python/service`

Out of scope (future phases):
- Runtime execution (`uv run mypy` verification)
- Outer profile (`python/service-relaxed`)
- `site-packages` power-surface scan
- Dynamic import resolution (`importlib.import_module`, `__import__`)
- Undeclared reach scanning (covered power surfaces)
- Dependency governance (`pr-check` lock-file delta)
- Workspace/monorepo layouts
- Namespace packages
- Flat layout

---

## Architecture

`PythonTarget` integrates at the Target seam:

```
BEAR CLI Commands (compile, check, pr-check, fix)
        │
        ▼
TargetRegistry
  ├── JvmTarget    (existing)
  ├── NodeTarget   (Phase B)
  └── PythonTarget (Phase P)
        ├── Python Artifact Generators
        ├── Governed Roots Computer
        ├── PythonImportContainmentScanner
        │     ├── PythonImportExtractor (AST-based)
        │     ├── PythonDynamicImportDetector
        │     └── PythonImportBoundaryResolver
        └── Drift Gate
```

All Python-specific logic lives behind the Target interface. Kernel orchestration is target-agnostic.

---

## Target Interface Contract

Phase P implements:

| Method | Behavior |
|--------|----------|
| `targetId()` | `TargetId.PYTHON` |
| `defaultProfile()` | `GovernanceProfile.of(PYTHON, "service")` |
| `compile()` | Python artifact generation |
| `generateWiringOnly()` | wiring.json only |
| `parseWiringManifest()` | JSON parsing |
| `ownedGeneratedPrefixes()` | `build/generated/bear/<blockKey>/`, `build/generated/bear/wiring/` |
| `considerContainmentSurfaces()` | `false` (`impl.allowedDeps` unsupported) |
| `sharedContainmentInScope()` | `false` (no shared policy in Phase P) |
| `blockDeclaresAllowedDeps()` | IR parsing for `allowedDeps` presence |
| `scanBoundaryBypass()` | delegates to `PythonImportContainmentScanner` |

Phase P stubs (throw `UnsupportedOperationException`):
- `prepareCheckWorkspace()`, `containmentSkipInfoLine()`, `preflightContainmentIfRequired()`, `verifyContainmentMarkersIfRequired()`
- `scanUndeclaredReach()`, `scanForbiddenReflectionDispatch()`, `scanPortImplContainmentBypass()`, `scanBlockPortBindings()`, `scanMultiBlockPortImplAllowedSignals()`, `runProjectVerification()`

---

## Components

### PythonTargetDetector

Detection algorithm:
```
1. Check pyproject.toml at projectRoot
   - absent → NONE
   - no [build-system] → NONE
   - no [project] → NONE
2. Check uv.lock OR poetry.lock at projectRoot — both absent → NONE
3. Check mypy.ini OR [tool.mypy] in pyproject.toml — both absent → NONE
4. Check src/blocks/ directory — absent → NONE
5. Check for workspace indicators:
   - uv.workspace in pyproject.toml → UNSUPPORTED
   - pnpm-workspace.yaml → UNSUPPORTED (Node project)
6. Check for ambiguous signals:
   - package.json + pyproject.toml both present → UNSUPPORTED (ambiguous)
7. Check for flat layout:
   - no src/ directory → UNSUPPORTED
8. Check for namespace packages:
   - any directory in src/blocks/ without __init__.py → UNSUPPORTED
9. All checks passed → SUPPORTED, targetId=PYTHON
```

- Detection is file-presence based; `pyproject.toml` parsed minimally (`[build-system]`, `[project]`, `[tool.mypy]` only).
- No Python version checking in Phase P.
- `mypy.ini` content is not validated.
- Workspace/ambiguous/flat/namespace detection returns `UNSUPPORTED` (not `NONE`) for clear feedback.

---

### PythonTarget

```java
public final class PythonTarget implements Target {
    @Override public TargetId targetId() { return TargetId.PYTHON; }

    @Override public GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(TargetId.PYTHON, "service");
    }

    @Override public void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        // 1. Compute paths
        // 2. Generate *_ports.py, *_logic.py, *_wrapper.py
        // 3. Generate wiring manifest
        // 4. Create user impl skeleton if absent
    }

    @Override public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) {
        // Generate only wiring manifest (for pr-check)
    }

    @Override public List<BoundaryBypassFinding> scanBoundaryBypass(
            Path projectRoot, List<WiringManifest> wiringManifests, Set<String> reflectionAllowlist)
            throws IOException {
        return PythonImportContainmentScanner.scan(projectRoot, wiringManifests);
    }

    @Override public Set<String> ownedGeneratedPrefixes(String blockName) {
        String blockKey = toKebabCase(blockName);
        return Set.of(
            "build/generated/bear/" + blockKey + "/",
            "build/generated/bear/wiring/" + blockKey + ".wiring.json"
        );
    }

    @Override public boolean considerContainmentSurfaces(BearIr ir, Path projectRoot) {
        return false; // impl.allowedDeps unsupported in Phase P
    }

    @Override public boolean blockDeclaresAllowedDeps(Path irFile) {
        try {
            BearIr ir = parseIr(irFile);
            return ir.block().impl() != null && ir.block().impl().allowedDeps() != null;
        } catch (Exception e) { return false; }
    }
}
```

- Follows `NodeTarget` structure; generates Python instead of TypeScript.
- Uses staging directory pattern for atomic artifact updates.
- User-owned impl files are never overwritten.

---

### Python Artifact Generators

***_ports.py** — port type declarations from `effects.allow`:
```python
# Generated by bear compile. DO NOT EDIT.

from typing import Protocol, Any

BearValue = dict[str, Any]

class DatabasePort(Protocol):
    def query(self, input: BearValue) -> BearValue: ...
    def execute(self, input: BearValue) -> BearValue: ...
```

***_logic.py** — logic interface from contract inputs/outputs:
```python
# Generated by bear compile. DO NOT EDIT.

from typing import Protocol
from dataclasses import dataclass

@dataclass
class LoginRequest:
    username: str
    password: str

@dataclass
class LoginResult:
    token: str
    expires_at: int

class AuthServiceLogic(Protocol):
    def login(self, request: LoginRequest, database: DatabasePort) -> LoginResult: ...
```

***_wrapper.py** — wiring factory connecting ports, logic, and impl:
```python
# Generated by bear compile. DO NOT EDIT.

class AuthService_Login:
    def __init__(self, database: DatabasePort, logic: AuthServiceLogic):
        self._database = database
        self._logic = logic

    def execute(self, request: LoginRequest) -> LoginResult:
        return self._logic.login(request, self._database)

    @staticmethod
    def of(database: DatabasePort) -> 'AuthService_Login':
        from ..impl.auth_service_impl import AuthServiceImpl
        return AuthService_Login(database, AuthServiceImpl())
```

**wiring.json**:
```json
{
  "version": "1",
  "blockKey": "<blockKey>",
  "targetId": "python",
  "generatedPackage": "build/generated/bear/<blockKey>",
  "implPackage": "src/blocks/<blockKey>/impl",
  "wrappers": [{ "operation": "<op>", "wrapperClass": "<Block>_<Op>", "wrapperPath": "..." }],
  "ports": [{ "name": "<port>", "kind": "EXTERNAL|BLOCK", "interface": "<Port>Port" }]
}
```

- Python 3.12+ syntax (type hints, `Protocol`, `dataclass`).
- `BearValue` is `dict[str, Any]`.
- No generics beyond standard library in Phase P.
- `DECIMAL` IR type maps to `Decimal` from `decimal` module (precision preservation).

---

### Governed Roots Computer

```
governedRoots(projectRoot, blockKeys):
  roots = []
  for blockKey in blockKeys:
    blockRoot = projectRoot / "src/blocks" / blockKey
    if exists(blockRoot) and exists(blockRoot / "__init__.py"): roots.add(blockRoot)
  sharedRoot = projectRoot / "src/blocks/_shared"
  if exists(sharedRoot) and exists(sharedRoot / "__init__.py"): roots.add(sharedRoot)
  return roots

governedFiles(governedRoots):
  for root in governedRoots:
    for file in walkTree(root):
      if file.extension == ".py" and not file.name.startsWith("test_") and not file.name.endsWith("_test.py"):
        yield file
```

Exclusions: files outside `src/blocks/`, `test_*.py`, `*_test.py`, `.pyi/.pyc/.pyo`, `__pycache__/` (implicitly excluded by walking only `src/blocks/`).

---

### Import Containment Scanner

Three helper classes + orchestrator. Each concern is independently testable.

**PythonImportExtractor** — parses Python source using `ast` module; extracts imports with locations:

Patterns detected (AST-based):
- `import x`
- `import x as y`
- `import x.y.z`
- `from x import y`
- `from x import y as z`
- `from x import *`
- `from . import x` (relative)
- `from .. import x` (relative)
- `from .submodule import x` (relative)

Returns imports with line/column from AST node locations.

**PythonDynamicImportDetector** — identifies dynamic import patterns via AST:

- Detects `importlib.import_module(...)` calls
- Detects `__import__(...)` calls
- Detects `importlib.util.spec_from_file_location(...)` calls
- Phase P: detects but does not enforce (advisory only)

**PythonImportBoundaryResolver** — classifies resolved module paths:

```java
public BoundaryDecision resolve(Path importingFile, String moduleName, boolean isRelative,
        Set<Path> governedRoots, Path projectRoot) {
    // 1. Relative import → resolve lexically
    if (isRelative) {
        Path resolved = resolveRelativeImport(importingFile, moduleName);
        
        // BEAR-generated → PASS
        if (isBearGenerated(resolved, projectRoot)) return BoundaryDecision.allowed();
        
        // Same governed root → PASS
        Path importingRoot = findGovernedRoot(importingFile, governedRoots);
        if (resolved.startsWith(importingRoot)) return BoundaryDecision.allowed();
        
        // _shared → PASS (unless _shared imports a block)
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        if (resolved.startsWith(sharedRoot)) {
            if (importingFile.startsWith(sharedRoot)) return BoundaryDecision.fail("SHARED_IMPORTS_BLOCK");
            return BoundaryDecision.allowed();
        }
        
        // All other cases → FAIL
        return BoundaryDecision.fail("BOUNDARY_BYPASS");
    }
    
    // 2. Absolute import → check if third-party or stdlib
    if (isStdlib(moduleName)) return BoundaryDecision.allowed(); // Phase P: allow stdlib
    if (isBearGenerated(moduleName, projectRoot)) return BoundaryDecision.allowed();
    
    // 3. Third-party package → FAIL (inner profile)
    return BoundaryDecision.fail("THIRD_PARTY_IMPORT");
}
```

- AST-based resolution (no filesystem traversal for imports).
- Uses Python's import resolution rules for relative imports.
- Uses `CanonicalLocator` for structured finding locators.

**PythonImportContainmentScanner** — orchestrator:

```java
public static List<BoundaryBypassFinding> scan(Path projectRoot,
        List<WiringManifest> wiringManifests) throws IOException {
    Set<Path> governedRoots = computeGovernedRoots(projectRoot, wiringManifests);
    List<Path> governedFiles = collectGovernedFiles(governedRoots);

    PythonImportExtractor extractor = new PythonImportExtractor();
    PythonDynamicImportDetector dynamicDetector = new PythonDynamicImportDetector();
    PythonImportBoundaryResolver resolver = new PythonImportBoundaryResolver();

    List<BoundaryBypassFinding> findings = new ArrayList<>();
    for (Path file : governedFiles) {
        String content = Files.readString(file);
        for (ImportStatement imp : extractor.extractImports(file, content)) {
            BoundaryDecision decision = resolver.resolve(file, imp.moduleName(), imp.isRelative(), governedRoots, projectRoot);
            if (decision.isFail()) findings.add(createFinding(file, imp, decision));
        }
        // Dynamic imports: detect but don't fail in Phase P
        dynamicDetector.detectDynamicImports(file, content);
    }
    return findings;
}
```

Findings are sorted by file path, then line number (deterministic output for CI).

---

### Drift Gate

```
checkDrift(projectRoot, blockKeys):
  for blockKey in blockKeys:
    ir = parseIr(projectRoot / "spec" / blockKey / "ir.bear.yaml")
    tempDir = createTempDirectory()
    PythonTarget.compile(ir, tempDir, blockKey)

    for artifact in ["build/generated/bear/<blockKey>/*.py",
                     "build/generated/bear/wiring/<blockKey>.wiring.json"]:
      if not exists(workspace / artifact): yield DRIFT_MISSING_BASELINE(artifact)
      else if not contentEquals(workspace / artifact, tempDir / artifact): yield DRIFT_DETECTED(artifact)
```

- Generates to temp directory (no workspace pollution).
- Byte-for-byte comparison.
- User-owned impl files excluded.

---

## Data Models

```java
// Detection (reuses existing from Phase A)
public record DetectedTarget(DetectionStatus status, TargetId targetId, String reason) { ... }
public enum DetectionStatus { NONE, SUPPORTED, UNSUPPORTED }

// Wiring (reuses existing)
public record WiringManifest(...) { ... }

// Findings (reuses existing)
public record BoundaryBypassFinding(String rule, String path, String detail) {}

// Python-specific
public record ImportStatement(String moduleName, boolean isRelative, int lineNumber, int columnNumber) {}
public record BoundaryDecision(boolean pass, String failureReason) {
    public static BoundaryDecision allowed() { return new BoundaryDecision(true, null); }
    public static BoundaryDecision fail(String r)  { return new BoundaryDecision(false, r); }
    public boolean isFail()                        { return !pass; }
}

// Internal generation models
public record PortModel(String originalName, String className, String variableName,
    BearIr.EffectPortKind kind, List<String> methods) {}
public record OperationModel(String operationName, String requestClassName,
    String resultClassName, String wrapperClassName, String logicMethodName,
    List<FieldModel> inputs, List<FieldModel> outputs, List<PortModel> logicPorts) {}
public record FieldModel(String originalName, String memberName, String pythonType) {}
```

IR type → Python type mapping:

| IR Type | Python Type | Notes |
|---------|-------------|-------|
| `string` | `str` | |
| `int` | `int` | |
| `decimal` | `Decimal` | from `decimal` module for precision |
| `bool` | `bool` | |

---

## Error Handling

Error envelope (frozen):
```
<error details>
CODE=<error_code>
PATH=<file_path>
REMEDIATION=<remediation_message>
```

| Category | Code | Exit | Remediation |
|----------|------|------|-------------|
| Detection | `TARGET_NOT_DETECTED` | `2` | Add `.bear/target.id` pin or fix project structure |
| Detection | `TARGET_UNSUPPORTED` | `64` | Remove workspace layout or add `__init__.py` files |
| Containment | `BOUNDARY_BYPASS` | `7` | Remove or relocate the import |
| Containment | `THIRD_PARTY_IMPORT` | `7` | Remove third-party import from governed code |
| Containment | `SHARED_IMPORTS_BLOCK` | `7` | Reverse dependency direction |
| Drift | `DRIFT_DETECTED` | `5` | Run `bear fix` or `bear compile` |
| Drift | `DRIFT_MISSING_BASELINE` | `5` | Run `bear compile` |
| Unsupported | `UNSUPPORTED_TARGET` | `64` | Remove `impl.allowedDeps` or switch to JVM target |

- Detection errors fail fast (prevent all subsequent operations).
- Containment scanner collects all violations before reporting (no whack-a-mole).
- Missing optional directories (`_shared`) are silently skipped.
- `IOException` → wrap in `TargetException`, exit `74`.
- Stubbed Phase P+ methods → `UnsupportedOperationException`, exit `64`.

---

## Correctness Properties

Properties are tagged to requirements. PBT library: **plain JUnit 5** (no jqwik). Minimum 100 iterations per property.

Tag format: `// Feature: phase-p-python-scan-only, Property N: <text>`

### Detection

1. Valid Python project structure → `SUPPORTED`, `targetId=PYTHON`. *(req: Python Project Detection)*
2. `TargetRegistry.resolve()` on valid Python project → `PythonTarget` instance, deterministically. *(req: Target Registry Integration)*

### Artifact Generation

3. `compile()` on any valid `BearIr` → all four artifacts generated at expected paths. *(req: Python Artifact Generation)*
4. `compile()` twice without modifying user impl → user impl content unchanged. *(req: Python Artifact Generation)*
5. `generateWiringOnly()` → only wiring manifest generated; no *_ports.py, *_logic.py, *_wrapper.py, or user impl. *(req: Python Artifact Generation)*
6. All Python files from `compile()` → parseable by Python AST without syntax errors. *(req: Python Artifact Generation)*

### Governed Roots

7. Any block key with `__init__.py` → `src/blocks/<blockKey>/` in governed roots. *(req: Governed Source Roots)*
8. Any path outside `src/blocks/` → excluded from governed roots. *(req: Governed Source Roots)*
9. Any `test_*.py` or `*_test.py` within governed roots → excluded from governed source files. *(req: Governed Source Roots)*
10. Any file in `src/blocks/` with extension other than `.py` → excluded from governed source. *(req: Governed Source Roots)*

### Import Containment

11. Relative import resolving within same block root → no findings. *(req: Import Containment Enforcement)*
12. Relative import resolving to `src/blocks/_shared/` → no findings. *(req: Import Containment Enforcement)*
13. Relative import resolving to `build/generated/bear/` → no findings. *(req: Import Containment Enforcement)*
14. Relative import escaping block root → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*
15. Import resolving to sibling block → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*
16. Third-party package import from governed root → finding, exit `7`, `CODE=THIRD_PARTY_IMPORT`. *(req: Import Containment Enforcement)*
17. Any `BOUNDARY_BYPASS` finding → includes repo-relative path and import module name. *(req: Import Containment Enforcement)*
18. `_shared` file importing a block root → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*

### Scanner Components

19. Any Python source → `PythonImportExtractor` extracts all static import statements with locations via AST. *(req: AST-First Analysis)*
20. Any Python source with `importlib.import_module()` → `PythonDynamicImportDetector` identifies all dynamic import calls. *(req: AST-First Analysis)*
21. Resolved path within same block root → `PythonImportBoundaryResolver` returns `ALLOWED`. *(req: AST-First Analysis)*
22. Resolved path within `_shared` → `PythonImportBoundaryResolver` returns `ALLOWED`. *(req: AST-First Analysis)*
23. Resolved path within `build/generated/bear/` → `PythonImportBoundaryResolver` returns `ALLOWED`. *(req: AST-First Analysis)*
24. Resolved path in sibling block → `PythonImportBoundaryResolver` returns `FAIL`. *(req: AST-First Analysis)*
25. Resolved path in nongoverned source → `PythonImportBoundaryResolver` returns `FAIL`. *(req: AST-First Analysis)*
26. Resolved path escaping block root → `PythonImportBoundaryResolver` returns `FAIL`. *(req: AST-First Analysis)*
27. Any `FAIL` from `PythonImportBoundaryResolver` → uses `CanonicalLocator` for structured locator. *(req: AST-First Analysis)*

### Drift Gate

28. `compile()` then immediate drift check → no findings. *(req: Drift Gate)*
29. Generated file modified after `compile()` → `DRIFT_DETECTED`. *(req: Drift Gate)*
30. User-owned impl modified → no drift findings. *(req: Drift Gate)*

### AllowedDeps Guard

31. Block IR with `impl.allowedDeps`, target=Python → `check` fails, exit `64`, `CODE=UNSUPPORTED_TARGET`. *(req: impl.allowedDeps Unsupported Guard)*
32. Block IR with `impl.allowedDeps`, target=Python → error output includes IR file path. *(req: impl.allowedDeps Unsupported Guard)*
33. Block IR with `impl.allowedDeps`, target=Python → `pr-check` operates normally. *(req: impl.allowedDeps Unsupported Guard)*

---

## Testing Strategy

### Unit Tests

```
kernel/src/test/java/com/bear/kernel/target/python/
  PythonTargetDetectorTest.java
  PythonTargetTest.java
  PythonImportExtractorTest.java
  PythonDynamicImportDetectorTest.java
  PythonImportBoundaryResolverTest.java
  PythonImportContainmentScannerTest.java
  PythonArtifactGeneratorTest.java
```

Key cases per class:
- `PythonTargetDetectorTest`: valid project → `SUPPORTED`; missing each required file → `NONE`; workspace → `UNSUPPORTED`; namespace package → `UNSUPPORTED`; ambiguous (Node+Python) → `UNSUPPORTED`
- `PythonTargetTest`: `targetId()` returns `PYTHON`; `compile()` creates expected artifacts; user impl created once, not overwritten; stubs throw `UnsupportedOperationException`
- `PythonImportExtractorTest`: all import patterns (absolute, relative, aliased); returns line numbers from AST
- `PythonDynamicImportDetectorTest`: detects `importlib.import_module()`, `__import__()`; distinguishes from static imports
- `PythonImportBoundaryResolverTest`: all pass/fail cases including `_shared` → block direction
- `PythonImportContainmentScannerTest`: clean project → no findings; bypass → finding with exit `7`; multiple violations collected
- `PythonArtifactGeneratorTest`: each artifact type; generated Python parses without errors

### Property Tests

```
kernel/src/test/java/com/bear/kernel/target/python/properties/
  PythonDetectionProperties.java
  ArtifactGenerationProperties.java
  GovernedRootsProperties.java
  ImportContainmentProperties.java
  DriftGateProperties.java
```

Generators (plain JUnit 5 parameterized tests):
```java
static Stream<Path> validPythonProjects() { /* temp dir with required files */ }
static Stream<BearIr> validBearIrBlocks() { /* random block name, inputs, outputs */ }
static Stream<String> relativeImportModules() { /* starts with . or .. */ }
static Stream<String> thirdPartyModules() { /* requests, flask, etc. */ }
```

### Integration Tests

Fixture projects:
```
kernel/src/test/resources/fixtures/python/
  valid-single-block/
  valid-multi-block/
  valid-with-shared/
  invalid-workspace/
  invalid-flat-layout/
  invalid-namespace-package/
  boundary-bypass-escape/
  boundary-bypass-sibling/
  boundary-bypass-third-party/
```

End-to-end scenarios:
1. Detect → compile → check (clean)
2. Detect → compile → modify generated file → check (drift)
3. Detect → compile → add boundary bypass → check (fail, exit `7`)
4. Detect with `allowedDeps` → check (exit `64`)
5. JVM/Node projects → resolve to correct target (no interference)

### Regression

- All existing JVM tests pass without modification.
- All existing Node tests pass without modification.
- Python project detection does not trigger `JvmTargetDetector` or `NodeTargetDetector`.

---

## Implementation Sequence

**Execution Strategy:** See tasks.md for parallel execution opportunities and task dependencies.

**Key Principles:**
- Implement directly (no subagent delegation for spec tasks)
- Use `context-gatherer` only if exploring unfamiliar patterns (one-time)
- Run tests after each component completion
- Verify JVM/Node regression tests pass after each major milestone

**Sequence:**

1. `PythonTargetDetector` + detection unit tests + detection property tests
2. `PythonTarget` skeleton + `TargetRegistry` registration + verify JVM/Node tests pass
3. Python lexical support (kebab-case, snake_case, type mapping)
4. `*_ports.py`, `*_logic.py`, `*_wrapper.py`, `wiring.json` generators + artifact tests
5. Governed roots computation + file filtering + governed roots tests
6. `PythonImportExtractor` (AST-based) + extraction tests
7. `PythonDynamicImportDetector` (AST-based) + detection tests
8. `PythonImportBoundaryResolver` + resolution tests
9. `PythonImportContainmentScanner` (orchestrator) + scanner integration tests
10. Drift gate + drift tests
11. `impl.allowedDeps` guard + guard tests
12. Fixture projects + end-to-end integration tests

---

## File Structure

New files:
```
kernel/src/main/java/com/bear/kernel/target/python/
  PythonTarget.java
  PythonTargetDetector.java
  PythonImportExtractor.java
  PythonDynamicImportDetector.java
  PythonImportBoundaryResolver.java
  PythonImportContainmentScanner.java
  PythonLexicalSupport.java
  PythonTypeMapper.java
  PythonArtifactGenerator.java
  PythonManifestGenerator.java
```

Modified files:
```
kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java
  — register PythonTarget + PythonTargetDetector in defaultRegistry()
kernel/src/main/java/com/bear/kernel/target/TargetId.java
  — add PYTHON enum value
```

---

## Validation Criteria

Phase P is complete when:
- All 33 correctness properties pass (100+ iterations each)
- All unit tests pass
- All integration tests pass
- All existing JVM tests pass without modification
- All existing Node tests pass without modification
- Fixture projects compile and check successfully
- Python fixture fails `check` on boundary bypass (exit `7`)
- Python fixture fails `check` on drift (exit `5`)
- Python fixture with `allowedDeps` fails `check` (exit `64`)
- JVM/Node fixture behavior unchanged
