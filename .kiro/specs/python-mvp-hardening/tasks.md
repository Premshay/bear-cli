# Tasks: Python Target MVP Hardening

## Task 1: Add runpy detection to PythonDynamicExecutionScanner
- [x] 1.1 Update the embedded `PYTHON_SCRIPT` in `PythonDynamicExecutionScanner.java` to detect `runpy.run_module(...)` and `runpy.run_path(...)` calls via `ast.Attribute` check (Requirements 1.1, 1.2)
- [x] 1.2 Add unit tests to `PythonDynamicExecutionScannerTest.java` for `runpy.run_module` detection, `runpy.run_path` detection, TYPE_CHECKING exclusion, test file exclusion, and multiple-findings sorting (Requirements 1.1–1.7)
- [x] 1.3 Add property-based tests to `DynamicExecutionProperties.java` for runpy detection with 100+ iterations: run_module detected, run_path detected, TYPE_CHECKING excluded, test files excluded, sort order preserved (Requirements 1.1–1.7)

## Task 2: Clean up deprecated stdlib modules
- [x] 2.1 Remove 21 deprecated/removed modules from `STDLIB_MODULES` in `PythonImportBoundaryResolver.java`: aifc, audioop, cgi, cgitb, chunk, crypt, distutils, imghdr, imp, mailcap, msilib, nis, nntplib, ossaudiodev, pipes, sndhdr, spwd, sunau, telnetlib, uu, xdrlib (Requirements 2.1)
- [x] 2.2 Verify `tomllib` and `zoneinfo` are present in `STDLIB_MODULES` (Requirements 2.2, 2.3)
- [x] 2.3 Add unit tests to `PythonImportBoundaryResolverTest.java` verifying removed modules are classified as third-party and tomllib/zoneinfo remain stdlib (Requirements 2.1–2.5)

## Task 3: Verify exit code mapping
- [x] 3.1 Add integration test fixture `kernel/src/test/resources/fixtures/python/check-runpy/` with a governed file containing `runpy.run_module(...)` (Requirements 3.1, 3.2)
- [x] 3.2 Add integration test in `PythonCheckIntegrationTest.java` verifying runpy fixture produces exit code 6 with CODE=REFLECTION_DISPATCH_FORBIDDEN (Requirements 3.3, 3.4, 1.8)

## Task 4: Document known detection gaps
- [x] 4.1 Create `docs/context/python-known-gaps.md` documenting all known bypass patterns with examples, reasons, and threat model justification (Requirements 4.1–4.5)
