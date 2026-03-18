# Requirements: Python Target MVP Hardening

## Overview

Requirements derived from the design document for closing the remaining gaps identified by Phase P2 deep research. Four work areas: runpy detection, stdlib cleanup, exit code verification, and known-gaps documentation.

## Requirements

### Requirement 1: Add runpy.run_module and runpy.run_path Detection

**User Story**: As a governance gate operator, I want `bear check` to detect `runpy.run_module(...)` and `runpy.run_path(...)` calls in governed Python files so that code execution via the `runpy` module is flagged as a dynamic execution escape hatch.

**Acceptance Criteria**:
- 1.1 `PythonDynamicExecutionScanner` detects `runpy.run_module(...)` calls and produces a finding with `surface == "runpy.run_module"`
- 1.2 `PythonDynamicExecutionScanner` detects `runpy.run_path(...)` calls and produces a finding with `surface == "runpy.run_path"`
- 1.3 `runpy` calls inside `if TYPE_CHECKING:` blocks produce no findings
- 1.4 `runpy` calls in test files (`test_*.py`, `*_test.py`) produce no findings
- 1.5 `runpy` calls in non-governed files produce no findings
- 1.6 Existing `eval`/`exec`/`compile` detection is unaffected (regression guard)
- 1.7 Findings from runpy detection are sorted by path then surface, consistent with all other scanners
- 1.8 runpy findings map to exit code 6 (`EXIT_UNDECLARED_REACH`) with `CODE=REFLECTION_DISPATCH_FORBIDDEN` via `scanForbiddenReflectionDispatch`

### Requirement 2: Clean Up Deprecated stdlib Modules from STDLIB_MODULES

**User Story**: As a governance gate operator, I want the stdlib module list to reflect Python 3.12+ so that imports of removed modules (like `distutils`) are correctly classified as third-party and blocked in governed roots.

**Acceptance Criteria**:
- 2.1 The following 21 modules are removed from `STDLIB_MODULES` in `PythonImportBoundaryResolver.java`: `aifc`, `audioop`, `cgi`, `cgitb`, `chunk`, `crypt`, `distutils`, `imghdr`, `imp`, `mailcap`, `msilib`, `nis`, `nntplib`, `ossaudiodev`, `pipes`, `sndhdr`, `spwd`, `sunau`, `telnetlib`, `uu`, `xdrlib`
- 2.2 `tomllib` (added Python 3.11) remains in `STDLIB_MODULES` (already present — verify)
- 2.3 `zoneinfo` (added Python 3.9) remains in `STDLIB_MODULES` (already present — verify)
- 2.4 Importing a removed module (e.g., `import distutils`) in a governed file produces `BoundaryDecision.fail("THIRD_PARTY_IMPORT")`
- 2.5 All other stdlib modules remain correctly classified (no regressions)

### Requirement 3: Verify Exit Code Mapping

**User Story**: As a governance gate operator, I want to confirm that dynamic execution and dynamic import findings use exit code 6 (`UNDECLARED_REACH` / `REFLECTION_DISPATCH_FORBIDDEN`), not exit code 7 (`BOUNDARY_BYPASS`), so that the failure envelope is semantically correct.

**Acceptance Criteria**:
- 3.1 `PythonTarget.scanForbiddenReflectionDispatch()` returns `UndeclaredReachFinding` for all dynamic execution findings (eval, exec, compile, runpy.run_module, runpy.run_path)
- 3.2 `PythonTarget.scanForbiddenReflectionDispatch()` returns `UndeclaredReachFinding` for all dynamic import findings (importlib.import_module, __import__, sys.path, importlib.util.spec_from_file_location)
- 3.3 `CheckCommandService` maps `scanForbiddenReflectionDispatch` findings to `EXIT_UNDECLARED_REACH` (6) with `CODE=REFLECTION_DISPATCH_FORBIDDEN`
- 3.4 This is verification only — no code change expected. Confirmed by integration test.

### Requirement 4: Document Known Detection Gaps

**User Story**: As a developer or security reviewer, I want a formal document listing the Python code patterns that BEAR intentionally does not detect, so that the tool's limitations are transparent and the threat model is clear.

**Acceptance Criteria**:
- 4.1 A `docs/context/python-known-gaps.md` file is created documenting all known bypass patterns
- 4.2 Document covers: `builtins.exec/eval` indirection, `getattr` + string concatenation, `sys.modules` manipulation, `globals()`/`locals()` injection, `compile` + `FunctionType`, aliased dangerous function calls
- 4.3 Each gap includes: pattern name, code example, reason not detected, and threat model justification
- 4.4 Document states BEAR's threat model: "prevent accidental boundary expansion by well-intentioned agent code"
- 4.5 Document references industry comparisons (Semgrep, Bandit, tach) to validate detection level
