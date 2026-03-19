# Design: P3 Boundary Escape Coverage

## Overview

Two parallel workstreams:

1. **Scanner extension** — add database, filesystem, and messaging surfaces to
   `PythonUndeclaredReachScanner.COVERED_MODULES` and extend the Python AST script.
2. **App-layer seam parity** — add `pinPython` to `TestTargetPins`, then add
   `PythonSeamParityTest` and `PythonAllowedDepsGuardTest` mirroring the JVM/Node equivalents.

No new classes, no new subprocess scripts, no new kernel abstractions. Both workstreams
are additive changes to existing files.

---

## Workstream 1: Scanner Extension

### Affected file

`kernel/src/main/java/com/bear/kernel/target/python/PythonUndeclaredReachScanner.java`

### Change: extend `COVERED_MODULES` in the embedded Python script

The Python AST script already has a `COVERED_MODULES` set. Extend it with three new groups:

```python
COVERED_MODULES = {
    # Network / HTTP (existing)
    'socket', 'http', 'http.client', 'http.server',
    'urllib', 'urllib.request', 'subprocess', 'multiprocessing',
    # Database (new)
    'sqlite3', 'dbm', 'dbm.gnu', 'dbm.ndbm', 'dbm.dumb',
    # Filesystem (new)
    'pathlib', 'shutil', 'tempfile', 'glob', 'fnmatch',
    # Messaging (new)
    'smtplib', 'smtpd', 'ftplib', 'imaplib',
    'poplib', 'nntplib', 'telnetlib',
}
```

### Change: add `open` and `io.open` call-site detection

The existing script already detects `os.system(...)` / `os.popen(...)` via `ast.Call` nodes.
Extend the same `ast.Call` branch to also detect:

- `open(...)` — `isinstance(node.func, ast.Name) and node.func.id == 'open'`
- `io.open(...)` — `isinstance(node.func, ast.Attribute) and node.func.attr == 'open' and isinstance(node.func.value, ast.Name) and node.func.value.id == 'io'`

Surface names: `open` and `io.open` respectively.

### Change: update Java-side Javadoc

Update the class-level Javadoc in `PythonUndeclaredReachScanner` to list the new covered
surfaces alongside the existing ones.

### No changes to other scanner classes

`PythonImportContainmentScanner`, `PythonDynamicExecutionScanner`,
`PythonDynamicImportEnforcer` — unchanged.

### Test coverage

New tests go in the existing
`kernel/src/test/java/com/bear/kernel/target/python/PythonUndeclaredReachScannerTest.java`.

One positive test and one negative test per new surface group:

| Group | Positive (detects) | Negative (clean) |
|---|---|---|
| Database | `import sqlite3` → surface `sqlite3` | `import json` → no finding |
| Database sub-module | `import dbm.gnu` → surface `dbm.gnu` | — |
| Database from-import | `from sqlite3 import connect` → surface `sqlite3` | — |
| Filesystem module | `import pathlib` → surface `pathlib` | — |
| Filesystem from-import | `from shutil import copy` → surface `shutil` | — |
| `open` call-site | `open("f.txt")` → surface `open` | `os.path.join(...)` → no finding |
| `io.open` call-site | `io.open("f.txt")` → surface `io.open` | — |
| Messaging | `import smtplib` → surface `smtplib` | — |
| Messaging from-import | `from ftplib import FTP` → surface `ftplib` | — |
| TYPE_CHECKING exclusion | `if TYPE_CHECKING: import sqlite3` → no finding | — |

---

## Workstream 2: App-Layer Seam Parity

### 2a. `TestTargetPins.pinPython`

**File:** `app/src/test/java/com/bear/app/TestTargetPins.java`

Add a `pinPython(Path projectRoot)` static method identical in structure to `pinJvm` but
writing `TargetId.PYTHON.value() + "\n"`:

```java
static void pinPython(Path projectRoot) throws IOException {
    Path bearDir = projectRoot.resolve(".bear");
    Files.createDirectories(bearDir);
    Files.writeString(bearDir.resolve("target.id"),
            TargetId.PYTHON.value() + "\n", StandardCharsets.UTF_8);
}
```

### 2b. `PythonSeamParityTest`

**File:** `app/src/test/java/com/bear/app/PythonSeamParityTest.java`

Mirrors `TargetSeamParityTest` for the Python target.

#### Test 1: `explicitPythonCheckMatchesRegistryRoutedCheck`

Setup:
1. `TestTargetPins.pinPython(tempDir)` — pins target to Python
2. Write a minimal Python IR fixture (no `impl.allowedDeps`, no effects)
3. Call `BearCli.executeCompile(irFile, tempDir, null, null)` — must exit 0
4. Write a minimal `src/blocks/<blockKey>/impl/<blockKey>_impl.py` stub

Execute:
- `explicit = CheckCommandService.executeCheck(..., new PythonTarget())`
- `routed  = CheckCommandService.executeCheck(...)` (no explicit target)

Assert: `assertEquals(explicit, routed)`

#### Test 2: `explicitPythonPrCheckMatchesRegistryRoutedPrCheck`

Setup:
1. `initGitRepo(tempDir.resolve("repo"))` + `TestTargetPins.pinPython(repo)`
2. Write IR file, `git commit -A`

Execute:
- `explicit = PrCheckCommandService.executePrCheck(..., new PythonTarget())`
- `routed  = PrCheckCommandService.executePrCheck(...)` (no explicit target)

Assert: all result fields equal (exitCode, stdoutLines, stderrLines, failureCode,
failurePath, failureRemediation, detail, deltaLines, hasBoundary, hasDeltas,
governanceLines).

#### IR fixture helper

Write a minimal Python IR YAML (no `allowedDeps`, no effects, one operation with
string input/output). Reuse the same `initGitRepo` / `gitCommitAll` / `git` helpers
from `TargetSeamParityTest` (copy-paste into this class — they are private static
utilities, not shared infrastructure).

### 2c. `PythonAllowedDepsGuardTest`

**File:** `app/src/test/java/com/bear/app/PythonAllowedDepsGuardTest.java`

Mirrors `NodeAllowedDepsGuardTest` for the Python target.

#### Test 1: `checkWithAllowedDepsAndPythonTarget_failsWithExit64`

Write IR with `impl.allowedDeps`, compile with `PythonTarget`, call
`CheckCommandService.executeCheck(..., pythonTarget)`.
Assert: `exitCode == 64`, `failureCode == "UNSUPPORTED_TARGET"`.

#### Test 2: `checkWithAllowedDepsAndPythonTarget_errorIncludesIrPath`

Same setup. Assert: `failurePath` contains the IR filename.

#### Test 3: `checkWithAllowedDepsAndPythonTarget_errorIncludesRemediation`

Same setup. Assert: `failureRemediation` contains `"Remove impl.allowedDeps"`.

#### Test 4: `checkWithoutAllowedDepsAndPythonTarget_doesNotTriggerGuard`

Write IR without `impl.allowedDeps`, compile, check.
Assert: `exitCode != 64`.

#### Test 5: `prCheckUnaffectedByAllowedDeps`

Call `pythonTarget.generateWiringOnly(ir, tempDir, outputRoot, blockKey)` with an IR
that has `impl.allowedDeps`. Assert: no exception thrown, wiring manifest exists at
`outputRoot/wiring/<blockKey>.wiring.json`.

#### IR helpers

`writeIrWithAllowedDeps(Path tempDir, String blockName)` and
`writeIrWithoutAllowedDeps(Path tempDir, String blockName)` — write YAML files
matching the Node equivalents but using Python-compatible IR (no JVM-specific fields).

`compilePythonProject(PythonTarget target, Path irFile, Path projectRoot, String blockKey)`
— parse IR and call `target.compile(ir, projectRoot, blockKey)`.

---

## Known-Gaps Doc Update

**File:** `docs/context/python-known-gaps.md`

Add the new surfaces to the "What BEAR Does Detect" table:

| Pattern | Scanner | Surface |
|---------|---------|---------|
| `import sqlite3` / `from sqlite3 import ...` | `PythonUndeclaredReachScanner` | `sqlite3` |
| `import dbm` / sub-modules | `PythonUndeclaredReachScanner` | `dbm`, `dbm.gnu`, etc. |
| `import pathlib` / `from pathlib import ...` | `PythonUndeclaredReachScanner` | `pathlib` |
| `import shutil` / `import tempfile` / `import glob` / `import fnmatch` | `PythonUndeclaredReachScanner` | module name |
| `open(...)` built-in call | `PythonUndeclaredReachScanner` | `open` |
| `io.open(...)` call | `PythonUndeclaredReachScanner` | `io.open` |
| `import smtplib` / `import ftplib` / etc. | `PythonUndeclaredReachScanner` | module name |

---

## Correctness Properties

**P1 — Coverage completeness**: For every module name in the new `COVERED_MODULES` additions,
a governed Python file containing `import <module>` produces exactly one finding with
`surface == "<module>"`.

**P2 — From-import coverage**: For every new covered module M, `from M import X` produces
a finding with `surface == M` (not `X`).

**P3 — Call-site coverage**: `open("x")` produces `surface == "open"`;
`io.open("x")` produces `surface == "io.open"`.

**P4 — TYPE_CHECKING exclusion preserved**: New surfaces inside `if TYPE_CHECKING:` blocks
produce no findings.

**P5 — Existing surfaces unaffected**: The existing covered modules (`socket`, `http`,
`urllib`, `subprocess`, `multiprocessing`) continue to produce findings identical to
pre-change behavior.

**P6 — Seam routing parity**: For Python target, explicit-target check result equals
registry-routed check result (same exit code, stdout, stderr, all result fields).

**P7 — allowedDeps guard fires for Python**: A Python-target block with `impl.allowedDeps`
exits 64 with `UNSUPPORTED_TARGET`; without `impl.allowedDeps` it does not.
