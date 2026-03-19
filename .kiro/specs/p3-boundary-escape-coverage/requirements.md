# Requirements: P3 Boundary Escape Coverage

## Overview

Extend BEAR's Python boundary-escape detection to cover database, filesystem, and messaging
power-surface paths. Close the app-layer Python seam parity gap so the Python target has the
same app-level integration test coverage that JVM and Node already have.

## Background

The Python MVP hardening sprint (phase-p2) shipped HTTP/network, subprocess, dynamic-execution,
and dynamic-import detection. The `p3-broader-boundary-escape-coverage` roadmap item calls for
extending coverage to the remaining external-power paths: direct database usage, direct filesystem
usage, and direct messaging usage.

In parallel, the app layer has no Python-specific seam tests. `TargetSeamParityTest` and
`NodeAllowedDepsGuardTest` exist for JVM and Node respectively, but there is no equivalent for
Python. `TestTargetPins` has `pinJvm` but no `pinPython`. This tech debt must be closed in the
same sprint.

## Functional Requirements

### FR-1: Database surface detection

The Python undeclared-reach scanner MUST detect direct use of database-access stdlib modules
in governed Python source files:

- `sqlite3` (import and from-import)
- `dbm` (import and from-import)
- `dbm.gnu`, `dbm.ndbm`, `dbm.dumb` (sub-module imports)

Detection must follow the same rules as existing covered modules:
- `import sqlite3` → finding with surface `sqlite3`
- `from sqlite3 import connect` → finding with surface `sqlite3`
- Imports inside `if TYPE_CHECKING:` blocks → excluded
- Test files (`test_*.py`, `*_test.py`) → excluded

### FR-2: Filesystem surface detection

The Python undeclared-reach scanner MUST detect direct use of filesystem-access stdlib modules
and builtins in governed Python source files:

- `pathlib` (import and from-import)
- `shutil` (import and from-import)
- `tempfile` (import and from-import)
- `glob` (import and from-import)
- `fnmatch` (import and from-import)
- `open(...)` built-in call-site (direct call to the `open` builtin)
- `io.open(...)` call-site (attribute call on `io` module)

Detection rules:
- `import pathlib` → finding with surface `pathlib`
- `from shutil import copy` → finding with surface `shutil`
- `open("file.txt")` → finding with surface `open`
- `io.open("file.txt")` → finding with surface `io.open`
- `if TYPE_CHECKING:` exclusion applies
- Test file exclusion applies

### FR-3: Messaging surface detection

The Python undeclared-reach scanner MUST detect direct use of messaging/email stdlib modules
in governed Python source files:

- `smtplib` (import and from-import)
- `smtpd` (import and from-import)
- `ftplib` (import and from-import)
- `imaplib` (import and from-import)
- `poplib` (import and from-import)
- `nntplib` (import and from-import)
- `telnetlib` (import and from-import)

Detection rules follow the same pattern as FR-1 and FR-2.

### FR-4: Stable finding ordering

All new surfaces MUST produce findings that sort deterministically by file path then surface
name, consistent with existing scanner output ordering.

### FR-5: Existing surfaces unaffected

Adding new covered modules MUST NOT change the detection behavior for any surface already
covered in the current `COVERED_MODULES` set.

### FR-6: App-layer Python seam parity — target pin utility

`TestTargetPins` MUST gain a `pinPython(Path projectRoot)` method that writes
`.bear/target.id` containing `"python\n"`, mirroring the existing `pinJvm` method.

### FR-7: App-layer Python seam parity — check/pr-check routing

A new `PythonSeamParityTest` class MUST verify that:

- Explicit `PythonTarget` passed to `CheckCommandService.executeCheck` produces the same
  result as registry-routed check (no explicit target) when the project root is pinned to
  Python via `TestTargetPins.pinPython`.
- Explicit `PythonTarget` passed to `PrCheckCommandService.executePrCheck` produces the
  same result as registry-routed pr-check when the project root is pinned to Python.

This mirrors `TargetSeamParityTest` which covers the JVM target.

### FR-8: App-layer Python seam parity — allowedDeps guard

A new `PythonAllowedDepsGuardTest` class MUST verify that:

- A Python-target block with `impl.allowedDeps` declared causes `bear check` to fail with
  exit code 64 and `failureCode = "UNSUPPORTED_TARGET"`.
- The error output includes the IR file path.
- The error output includes a remediation message mentioning `Remove impl.allowedDeps`.
- A Python-target block without `impl.allowedDeps` does NOT trigger the guard.
- `generateWiringOnly` (pr-check path) is unaffected by `impl.allowedDeps`.

This mirrors `NodeAllowedDepsGuardTest` which covers the Node target.

## Non-Functional Requirements

### NFR-1: Determinism

All new scanner findings MUST be deterministic across repeated runs on the same input.
No randomness, no timestamp-dependent output.

### NFR-2: Fail-closed on scanner error

If the Python AST subprocess exits non-zero, the scanner MUST throw `IOException` rather
than silently returning an empty finding list. This matches the existing fail-closed contract.

### NFR-3: No new external dependencies

New detection surfaces MUST be implemented by extending the existing `COVERED_MODULES` set
in `PythonUndeclaredReachScanner`. No new Python scripts, no new subprocess invocations
beyond what already exists.

### NFR-4: Known-gaps doc updated

`docs/context/python-known-gaps.md` MUST be updated to reflect the newly covered surfaces
in the "What BEAR Does Detect" table.

## Out of Scope

- Third-party database drivers (`psycopg2`, `sqlalchemy`, `pymongo`, etc.) — covered by
  `PythonImportContainmentScanner` (third-party import containment), not this feature.
- `ctypes` / `cffi` foreign function calls — explicitly parked in known-gaps as out of scope for v1.
- Maven allowed-deps containment parity (`p3-maven-allowed-deps-containment`) — Later Queue item.
- Any changes to JVM or Node target scanners.
