# Design: P2 Minimal Taste-Invariants Rule Pack

## Overview

This design specifies a deterministic scanner (`TasteInvariantScanner`) that enforces structural
invariants on BEAR-owned generated artifacts. The scanner detects:

1. **Naming violations** in surface/wiring manifest files
2. **Zone sprawl** (unexpected paths in the generated zone)
3. **Source structure violations** (incorrect package structure in generated Java sources)
4. **Forbidden dependencies** (generated code importing impl code)

The scanner integrates into the existing `bear check` pipeline, reuses exit code 6
(`EXIT_UNDECLARED_REACH`), and follows the established `BoundaryBypassFinding` pattern for findings.

### Design Principles

1. **Deterministic**: All rules produce identical findings for identical inputs
2. **BEAR-owned surfaces only**: Rules target generated artifacts, not user implementation code
3. **Narrow scope**: Exactly 6 rule IDs, no subjective heuristics
4. **Existing patterns**: Reuse `PolicyAllowlistParser`, `BoundaryBypassFinding`, and exit code 6

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CheckCommandService                                │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   Drift     │  │ Undeclared  │  │  Boundary   │  │ TasteInvariant      │ │
│  │  Analyzer   │→ │   Reach     │→ │   Bypass    │→ │    Scanner          │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────────┘ │
│                                                              ↓              │
│                                                     ┌─────────────────────┐ │
│                                                     │ BoundaryBypassFinding│ │
│                                                     │ (rule, path, detail) │ │
│                                                     └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Module Location

The `TasteInvariantScanner` resides in `app/` module (not `kernel/`) because:
- It's specific to the `bear check` pipeline
- It doesn't need to be target-agnostic
- It follows the `HygieneScanner` pattern already in `app/`

## Components and Interfaces

### TasteInvariantScanner

```java
package com.bear.app;

import com.bear.kernel.target.BoundaryBypassFinding;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

final class TasteInvariantScanner {
    // Rule ID constants
    static final String GENERATED_SURFACE_NAMING_VIOLATION = "GENERATED_SURFACE_NAMING_VIOLATION";
    static final String GENERATED_WIRING_NAMING_VIOLATION = "GENERATED_WIRING_NAMING_VIOLATION";
    static final String GENERATED_ZONE_UNEXPECTED_PATH = "GENERATED_ZONE_UNEXPECTED_PATH";
    static final String GENERATED_SOURCE_STRUCTURE_VIOLATION = "GENERATED_SOURCE_STRUCTURE_VIOLATION";
    static final String GENERATED_SOURCE_UNEXPECTED_FILE = "GENERATED_SOURCE_UNEXPECTED_FILE";
    static final String GENERATED_FORBIDDEN_DEPENDENCY = "GENERATED_FORBIDDEN_DEPENDENCY";

    // Allowlist path constant
    static final String TASTE_INVARIANTS_ALLOWLIST_PATH = "bear-policy/taste-invariants-allowlist.txt";

    private TasteInvariantScanner() {}

    /**
     * Scans the generated zone for taste-invariant violations.
     *
     * @param projectRoot the project root directory
     * @param allowlist paths to skip (from taste-invariants-allowlist.txt)
     * @return list of findings sorted by path, then rule, then detail
     */
    static List<BoundaryBypassFinding> scanTasteInvariants(
        Path projectRoot,
        Set<String> allowlist
    );
}
```

### Integration with GovernanceRuleRegistry

Add the 6 new rule IDs to `GovernanceRuleRegistry.PUBLIC_RULE_IDS`:

```java
static final Set<String> PUBLIC_RULE_IDS = Set.of(
    // ... existing rules ...
    "GENERATED_SURFACE_NAMING_VIOLATION",
    "GENERATED_WIRING_NAMING_VIOLATION",
    "GENERATED_ZONE_UNEXPECTED_PATH",
    "GENERATED_SOURCE_STRUCTURE_VIOLATION",
    "GENERATED_SOURCE_UNEXPECTED_FILE",
    "GENERATED_FORBIDDEN_DEPENDENCY"
);
```

### Integration with CheckCommandService

Add taste-invariant scanning after boundary bypass checks:

```java
// After bypassFindings check, before project tests
if (runReachAndTests) {
    Set<String> tasteAllowlist = PolicyAllowlistParser.parseExactPathAllowlist(
        projectRoot,
        TasteInvariantScanner.TASTE_INVARIANTS_ALLOWLIST_PATH
    );
    List<BoundaryBypassFinding> tasteFindings = TasteInvariantScanner.scanTasteInvariants(
        projectRoot,
        tasteAllowlist
    );
    if (!tasteFindings.isEmpty()) {
        // Report findings with EXIT_UNDECLARED_REACH (exit code 6)
        // Format: check: BOUNDARY_BYPASS: RULE=<rule>: <path>: <detail>
    }
}
```

## Data Models

### Finding Structure

Reuse existing `BoundaryBypassFinding` record:

```java
public record BoundaryBypassFinding(String rule, String path, String detail) {}
```

### Expected Generated Zone Structure

```
build/generated/bear/
├── surfaces/
│   └── {blockKey}.surface.json
├── wiring/
│   └── {blockKey}.wiring.json
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── bear/
│                   └── generated/
│                       ├── {block}/
│                       │   ├── {Block}Logic.java
│                       │   ├── {Block}_{Op}.java (wrappers)
│                       │   └── {Block}_{Port}BlockClient.java (clients)
│                       └── runtime/
│                           └── Bear*.java (runtime classes)
└── gradle/
    └── bear-containment.gradle
```

## Algorithms

### Rule 1: Surface Naming Validation

```
ALGORITHM: validateSurfaceNaming(surfacesDir)
  INPUT: Path to build/generated/bear/surfaces/
  OUTPUT: List<BoundaryBypassFinding>

  findings = []
  FOR each file in surfacesDir:
    IF file is directory:
      CONTINUE  // directories are unexpected, handled by zone sprawl
    filename = file.getFileName()
    IF NOT matches pattern "^[a-z][a-z0-9-]*\\.surface\\.json$":
      findings.add(BoundaryBypassFinding(
        GENERATED_SURFACE_NAMING_VIOLATION,
        relativePath(file),
        "surface file must match pattern {blockKey}.surface.json"
      ))
  RETURN findings sorted by path
```

### Rule 2: Wiring Naming Validation

```
ALGORITHM: validateWiringNaming(wiringDir)
  INPUT: Path to build/generated/bear/wiring/
  OUTPUT: List<BoundaryBypassFinding>

  findings = []
  FOR each file in wiringDir:
    IF file is directory:
      CONTINUE
    filename = file.getFileName()
    IF NOT matches pattern "^[a-z][a-z0-9-]*\\.wiring\\.json$":
      findings.add(BoundaryBypassFinding(
        GENERATED_WIRING_NAMING_VIOLATION,
        relativePath(file),
        "wiring file must match pattern {blockKey}.wiring.json"
      ))
  RETURN findings sorted by path
```

### Rule 3: Zone Sprawl Detection

```
ALGORITHM: detectZoneSprawl(generatedBearRoot, allowlist)
  INPUT: Path to build/generated/bear/, Set<String> allowlist
  OUTPUT: List<BoundaryBypassFinding>

  KNOWN_CATEGORIES = ["surfaces", "wiring", "src", "gradle"]
  findings = []

  FOR each entry in generatedBearRoot (recursive):
    relPath = relativePath(entry, projectRoot)
    IF relPath in allowlist:
      CONTINUE

    // Check top-level directories
    IF entry is direct child of generatedBearRoot:
      IF entry.name NOT in KNOWN_CATEGORIES:
        findings.add(BoundaryBypassFinding(
          GENERATED_ZONE_UNEXPECTED_PATH,
          relPath,
          "unexpected path in generated zone; expected: surfaces/, wiring/, src/, gradle/"
        ))
        CONTINUE

    // Check surfaces/ contents
    IF relPath starts with "build/generated/bear/surfaces/":
      IF entry is directory:
        findings.add(BoundaryBypassFinding(
          GENERATED_ZONE_UNEXPECTED_PATH,
          relPath,
          "unexpected directory in surfaces/; only .surface.json files expected"
        ))

    // Check wiring/ contents
    IF relPath starts with "build/generated/bear/wiring/":
      IF entry is directory:
        findings.add(BoundaryBypassFinding(
          GENERATED_ZONE_UNEXPECTED_PATH,
          relPath,
          "unexpected directory in wiring/; only .wiring.json files expected"
        ))

    // Check gradle/ contents
    IF relPath starts with "build/generated/bear/gradle/":
      IF entry is file AND entry.name != "bear-containment.gradle":
        findings.add(BoundaryBypassFinding(
          GENERATED_ZONE_UNEXPECTED_PATH,
          relPath,
          "unexpected file in gradle/; only bear-containment.gradle expected"
        ))

  RETURN findings sorted by path
```

### Rule 4: Source Structure Validation

```
ALGORITHM: validateSourceStructure(srcRoot)
  INPUT: Path to build/generated/bear/src/main/java/
  OUTPUT: List<BoundaryBypassFinding>

  EXPECTED_PREFIX = "com/bear/generated/"
  findings = []

  FOR each .java file in srcRoot (recursive):
    relPath = relativePath(file, srcRoot)

    IF NOT relPath starts with EXPECTED_PREFIX:
      findings.add(BoundaryBypassFinding(
        GENERATED_SOURCE_STRUCTURE_VIOLATION,
        "build/generated/bear/src/main/java/" + relPath,
        "generated source must be under com.bear.generated.{block}/ package"
      ))
      CONTINUE

    // Extract package segment after com/bear/generated/
    packageSegment = relPath.substring(EXPECTED_PREFIX.length())
    parts = packageSegment.split("/")

    IF parts.length < 2:
      findings.add(BoundaryBypassFinding(
        GENERATED_SOURCE_STRUCTURE_VIOLATION,
        "build/generated/bear/src/main/java/" + relPath,
        "generated source must be in a block-specific package"
      ))
      CONTINUE

    blockDir = parts[0]
    IF blockDir != "runtime" AND NOT isValidBlockKey(blockDir):
      findings.add(BoundaryBypassFinding(
        GENERATED_SOURCE_STRUCTURE_VIOLATION,
        "build/generated/bear/src/main/java/" + relPath,
        "invalid block package name: " + blockDir
      ))

  RETURN findings sorted by path
```

### Rule 5: Source File Type Validation

```
ALGORITHM: validateSourceFileTypes(srcRoot)
  INPUT: Path to build/generated/bear/src/main/java/com/bear/generated/
  OUTPUT: List<BoundaryBypassFinding>

  findings = []

  FOR each .java file in srcRoot (recursive):
    relPath = relativePath(file, srcRoot)
    parts = relPath.split("/")

    IF parts.length < 2:
      CONTINUE  // handled by structure validation

    blockDir = parts[0]
    filename = parts[parts.length - 1]

    IF blockDir == "runtime":
      // Runtime classes must start with "Bear"
      IF NOT filename starts with "Bear":
        findings.add(BoundaryBypassFinding(
          GENERATED_SOURCE_UNEXPECTED_FILE,
          fullRelPath(file),
          "runtime class must start with 'Bear' prefix"
        ))
    ELSE:
      // Block classes must follow naming conventions
      blockName = capitalize(blockDir.replace("-", ""))
      IF NOT isExpectedBlockFile(filename, blockName):
        findings.add(BoundaryBypassFinding(
          GENERATED_SOURCE_UNEXPECTED_FILE,
          fullRelPath(file),
          "unexpected file in block package; expected: {Block}Logic.java, {Block}_{Op}.java, or {Block}_{Port}BlockClient.java"
        ))

  RETURN findings sorted by path

FUNCTION isExpectedBlockFile(filename, blockName):
  // {Block}Logic.java
  IF filename == blockName + "Logic.java": RETURN true
  // {Block}_{Op}.java (wrapper)
  IF filename matches "^" + blockName + "_[A-Z][A-Za-z0-9]*\\.java$": RETURN true
  // {Block}_{Port}BlockClient.java
  IF filename matches "^" + blockName + "_[A-Z][A-Za-z0-9]*BlockClient\\.java$": RETURN true
  // BearValue.java (shared value type)
  IF filename == "BearValue.java": RETURN true
  RETURN false
```

### Rule 6: Forbidden Dependency Detection

```
ALGORITHM: detectForbiddenDependencies(srcRoot)
  INPUT: Path to build/generated/bear/src/main/java/
  OUTPUT: List<BoundaryBypassFinding>

  FORBIDDEN_IMPORT_PATTERNS = [
    "blocks\\.[a-z][a-z0-9_]*\\.impl\\.",  // blocks.*.impl.*
  ]
  ALLOWED_IMPORT_PATTERNS = [
    "blocks\\._shared\\.pure\\.",           // blocks._shared.pure.*
    "blocks\\._shared\\.state\\.",          // blocks._shared.state.*
  ]

  findings = []

  FOR each .java file in srcRoot (recursive):
    source = readFile(file)
    imports = extractImports(source)

    FOR each import in imports:
      IF matchesAny(import, ALLOWED_IMPORT_PATTERNS):
        CONTINUE

      FOR each pattern in FORBIDDEN_IMPORT_PATTERNS:
        IF import matches pattern:
          findings.add(BoundaryBypassFinding(
            GENERATED_FORBIDDEN_DEPENDENCY,
            relativePath(file),
            "forbidden import from impl package: " + import
          ))
          BREAK

  RETURN findings sorted by path, then detail

FUNCTION extractImports(source):
  imports = []
  FOR each line in source:
    IF line matches "^import\\s+([a-zA-Z0-9_.]+);":
      imports.add(match.group(1))
  RETURN imports
```

## Error Handling

### Missing Generated Zone

If `build/generated/bear/` does not exist, the scanner returns an empty list without error.
This is expected for projects that haven't run `bear compile` yet.

```java
static List<BoundaryBypassFinding> scanTasteInvariants(Path projectRoot, Set<String> allowlist) {
    Path generatedBearRoot = projectRoot.resolve("build/generated/bear");
    if (!Files.isDirectory(generatedBearRoot)) {
        return List.of();
    }
    // ... proceed with scanning
}
```

### Allowlist File Missing

If `bear-policy/taste-invariants-allowlist.txt` does not exist, proceed with an empty allowlist.
This follows the existing `PolicyAllowlistParser` pattern.

### IO Errors

IO errors during scanning should propagate as `IOException` to be handled by `CheckCommandService`,
which will emit an appropriate `IO_ERROR` failure envelope.

### Malformed Files

The scanner does not parse JSON content of surface/wiring files—it only validates naming patterns.
Malformed JSON is handled by existing manifest parsing in `CheckCommandService`.

## Testing Strategy

### Dual Testing Approach

- **Unit tests**: Verify specific examples, edge cases, and error conditions
- **Property tests**: Verify universal properties across all inputs

### Property-Based Testing Configuration

- Library: jqwik (already used in the project)
- Minimum 100 iterations per property test
- Tag format: `Feature: p2-minimal-taste-invariants-rule-pack, Property {number}: {property_text}`

### Test Categories

1. **Rule-specific tests**: One test per rule ID verifying it fires on intended violations
2. **Negative tests**: One test per rule ID verifying it does NOT fire on valid structures
3. **Determinism tests**: Verify identical inputs produce identical outputs
4. **Allowlist tests**: Verify allowlist entries suppress findings
5. **Scope lock test**: Verify exactly 6 rule IDs exist

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Surface/Wiring Naming Pattern Validation

*For any* file in `build/generated/bear/surfaces/` or `build/generated/bear/wiring/`, if the filename does not match the pattern `{blockKey}.surface.json` or `{blockKey}.wiring.json` respectively, then the scanner shall emit a finding with the appropriate rule ID (`GENERATED_SURFACE_NAMING_VIOLATION` or `GENERATED_WIRING_NAMING_VIOLATION`).

**Validates: Requirements 1.1, 1.2, 1.3, 1.4**

### Property 2: Zone Sprawl Detection

*For any* path under `build/generated/bear/` that does not belong to a known artifact category (surfaces, wiring, src, gradle) and is not in the allowlist, the scanner shall emit a finding with rule ID `GENERATED_ZONE_UNEXPECTED_PATH`.

**Validates: Requirements 2.1, 2.2**

### Property 3: Source Package Structure Validation

*For any* Java source file under `build/generated/bear/src/main/java/` that does not follow the expected package structure `com.bear.generated.{block}/`, the scanner shall emit a finding with rule ID `GENERATED_SOURCE_STRUCTURE_VIOLATION`.

**Validates: Requirements 3.1, 3.2**

### Property 4: Source File Type Validation

*For any* Java source file in a block's generated source directory that does not match expected artifact types (wrapper, ports, models, runtime classes), the scanner shall emit a finding with rule ID `GENERATED_SOURCE_UNEXPECTED_FILE`.

**Validates: Requirements 3.3, 3.4**

### Property 5: Forbidden Dependency Detection

*For any* generated Java source file that imports from `blocks.*.impl.*` packages (and not from allowed packages like `blocks._shared.pure.*`), the scanner shall emit a finding with rule ID `GENERATED_FORBIDDEN_DEPENDENCY` that identifies the specific import statement.

**Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5**

### Property 6: Allowlist Suppression

*For any* path that matches an entry in the allowlist, the scanner shall not emit any findings for that path, regardless of which rules would otherwise apply.

**Validates: Requirements 2.3, 2.4, 7.1, 7.2**

### Property 7: Scan Determinism

*For any* generated zone, scanning twice with identical inputs shall produce identical findings in identical order (sorted by path, then rule, then detail).

**Validates: Requirements 1.5, 2.5, 3.5, 5.3, 8.3**

### Property 8: Failure Envelope Format

*For any* finding emitted by the scanner, the output shall follow the standard failure-envelope format with CODE, PATH, and REMEDIATION fields that are deterministic and stable across runs.

**Validates: Requirements 5.1**

### Property 9: Read-Only Scan

*For any* scan execution, the scanner shall not modify any files in the project directory; it is a read-only operation.

**Validates: Requirements 8.4**
