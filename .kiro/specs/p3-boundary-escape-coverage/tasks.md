# Tasks: P3 Boundary Escape Coverage

## Context Files

Spec:
- `.kiro/specs/p3-boundary-escape-coverage/requirements.md`
- `.kiro/specs/p3-boundary-escape-coverage/design.md`

Primary source files to edit:
- `kernel/src/main/java/com/bear/kernel/target/python/PythonUndeclaredReachScanner.java`
- `app/src/test/java/com/bear/app/TestTargetPins.java`

Primary test files to edit or create:
- `kernel/src/test/java/com/bear/kernel/target/python/PythonUndeclaredReachScannerTest.java`
- `app/src/test/java/com/bear/app/PythonSeamParityTest.java` *(new)*
- `app/src/test/java/com/bear/app/PythonAllowedDepsGuardTest.java` *(new)*

Reference (read, do not edit):
- `app/src/test/java/com/bear/app/TargetSeamParityTest.java` — JVM seam parity pattern to mirror
- `app/src/test/java/com/bear/app/NodeAllowedDepsGuardTest.java` — Node allowedDeps guard pattern to mirror
- `kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java` — confirms `considerContainmentSurfaces` returns false for Python
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` — `TargetId.PYTHON.value()` == `"python"`

Docs to update:
- `docs/context/python-known-gaps.md`
- `docs/context/state.md`
- `roadmap/features/p3-broader-boundary-escape-coverage.md`
- `roadmap/board.md`

---

## Task List

- [x] 1. Extend `PythonUndeclaredReachScanner` with database, filesystem, and messaging surfaces
  - Read: `kernel/src/main/java/com/bear/kernel/target/python/PythonUndeclaredReachScanner.java`
  - Read: `.kiro/specs/p3-boundary-escape-coverage/design.md` §Workstream 1
  - [x] 1.1 Add database modules to `COVERED_MODULES` in the embedded Python AST script: `sqlite3`, `dbm`, `dbm.gnu`, `dbm.ndbm`, `dbm.dumb`
  - [x] 1.2 Add filesystem modules to `COVERED_MODULES`: `pathlib`, `shutil`, `tempfile`, `glob`, `fnmatch`
  - [x] 1.3 Add messaging modules to `COVERED_MODULES`: `smtplib`, `smtpd`, `ftplib`, `imaplib`, `poplib`, `nntplib`, `telnetlib`
  - [x] 1.4 Extend the `ast.Call` branch in the Python script to detect `open(...)` (surface `open`) and `io.open(...)` (surface `io.open`), following the same pattern as the existing `os.system(...)` detection
  - [x] 1.5 Update the Java-side class Javadoc to list all newly covered surfaces alongside the existing ones

- [x] 2. Add kernel-layer unit tests for new surfaces in `PythonUndeclaredReachScannerTest`
  - Read: `kernel/src/test/java/com/bear/kernel/target/python/PythonUndeclaredReachScannerTest.java` — follow existing test structure (`setupGovernedBlock` / `makeManifest` helpers)
  - Read: `.kiro/specs/p3-boundary-escape-coverage/design.md` §Test coverage table
  - [x] 2.1 Database group: add positive tests for `import sqlite3` → `sqlite3`, `import dbm` → `dbm`, `import dbm.gnu` → `dbm.gnu`, `from sqlite3 import connect` → `sqlite3`; add negative test (`import json` → no finding)
  - [x] 2.2 Filesystem module group: add positive tests for `import pathlib` → `pathlib`, `from shutil import copy` → `shutil`, `import tempfile` → `tempfile`, `import glob` → `glob`, `import fnmatch` → `fnmatch`; add negative test (clean file → no finding)
  - [x] 2.3 `open` / `io.open` call-site group: add positive test for `open("f.txt")` → `open`; add positive test for `io.open("f.txt")` → `io.open`; add negative test (`os.path.join(...)` → no finding)
  - [x] 2.4 Messaging group: add positive tests for `import smtplib` → `smtplib`, `from ftplib import FTP` → `ftplib`, `import imaplib` → `imaplib`; add negative test (clean file → no finding)
  - [x] 2.5 TYPE_CHECKING exclusion for new surfaces: `if TYPE_CHECKING: import sqlite3` → no finding; `if TYPE_CHECKING: import pathlib` → no finding; `if TYPE_CHECKING: import smtplib` → no finding
  - [x] 2.6 Run `./gradlew :kernel:test --tests "*.PythonUndeclaredReachScannerTest"` and confirm all new tests pass with no regressions to existing tests

- [x] 3. Add `pinPython` to `TestTargetPins`
  - Read: `app/src/test/java/com/bear/app/TestTargetPins.java` — add method directly below `pinJvm`
  - Read: `kernel/src/main/java/com/bear/kernel/target/TargetId.java` — use `TargetId.PYTHON.value()`
  - [x] 3.1 Add `static void pinPython(Path projectRoot) throws IOException` to `TestTargetPins`, writing `TargetId.PYTHON.value() + "\n"` to `.bear/target.id` — identical structure to `pinJvm`

- [x] 4. Add `PythonSeamParityTest`
  - Read: `app/src/test/java/com/bear/app/TargetSeamParityTest.java` — mirror this class for Python; copy `initGitRepo` / `gitCommitAll` / `git` private helpers verbatim
  - Read: `app/src/test/java/com/bear/app/TestTargetPins.java` — use `TestTargetPins.pinPython(tempDir)` (added in Task 3)
  - Read: `kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java` — use `new PythonTarget()` as the explicit target
  - [x] 4.1 Create `app/src/test/java/com/bear/app/PythonSeamParityTest.java`
  - [x] 4.2 Implement `explicitPythonCheckMatchesRegistryRoutedCheck`: call `TestTargetPins.pinPython(tempDir)`, write a minimal Python IR YAML (one string-in/string-out operation, no `allowedDeps`, no effects), call `BearCli.executeCompile`, write a minimal `src/blocks/<blockKey>/impl/<blockKey>_impl.py` stub, then assert `CheckCommandService.executeCheck(..., new PythonTarget())` equals `CheckCommandService.executeCheck(...)` (no explicit target)
  - [x] 4.3 Implement `explicitPythonPrCheckMatchesRegistryRoutedPrCheck`: init git repo, call `TestTargetPins.pinPython(repo)`, write IR, commit, then assert all fields of `PrCheckCommandService.executePrCheck(..., new PythonTarget())` equal `PrCheckCommandService.executePrCheck(...)` (no explicit target) — assert exitCode, stdoutLines, stderrLines, failureCode, failurePath, failureRemediation, detail, deltaLines, hasBoundary, hasDeltas, governanceLines
  - [x] 4.4 Run `./gradlew :app:test --tests "*.PythonSeamParityTest"` and confirm both tests pass

- [x] 5. Add `PythonAllowedDepsGuardTest`
  - Read: `app/src/test/java/com/bear/app/NodeAllowedDepsGuardTest.java` — mirror this class for Python; adapt IR helpers to use Python-compatible YAML (same structure, no JVM-specific fields)
  - Read: `kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java` — use `new PythonTarget()` and `target.compile(ir, projectRoot, blockKey)`
  - [x] 5.1 Create `app/src/test/java/com/bear/app/PythonAllowedDepsGuardTest.java`
  - [x] 5.2 Implement `checkWithAllowedDepsAndPythonTarget_failsWithExit64`: write IR with `impl.allowedDeps`, compile with `PythonTarget`, call `CheckCommandService.executeCheck(..., pythonTarget)`, assert `exitCode == 64` and `failureCode == "UNSUPPORTED_TARGET"`
  - [x] 5.3 Implement `checkWithAllowedDepsAndPythonTarget_errorIncludesIrPath`: same setup, assert `failurePath` contains the IR filename
  - [x] 5.4 Implement `checkWithAllowedDepsAndPythonTarget_errorIncludesRemediation`: same setup, assert `failureRemediation` contains `"Remove impl.allowedDeps"`
  - [x] 5.5 Implement `checkWithoutAllowedDepsAndPythonTarget_doesNotTriggerGuard`: write IR without `impl.allowedDeps`, compile, check, assert `exitCode != 64`
  - [x] 5.6 Implement `prCheckUnaffectedByAllowedDeps`: construct `BearIr` with `impl.allowedDeps` directly (no YAML), call `pythonTarget.generateWiringOnly(ir, tempDir, outputRoot, blockKey)`, assert no exception and wiring manifest exists at `outputRoot/wiring/<blockKey>.wiring.json`
  - [x] 5.7 Run `./gradlew :app:test --tests "*.PythonAllowedDepsGuardTest"` and confirm all 5 tests pass

- [x] 6. Update `docs/context/python-known-gaps.md`
  - Read: `docs/context/python-known-gaps.md` — extend the "What BEAR Does Detect" table
  - [x] 6.1 Add rows for all new surfaces: `sqlite3`/`dbm`/sub-modules (database), `pathlib`/`shutil`/`tempfile`/`glob`/`fnmatch` (filesystem), `open(...)`/`io.open(...)` (call-site), `smtplib`/`smtpd`/`ftplib`/`imaplib`/`poplib`/`nntplib`/`telnetlib` (messaging) — all mapped to `PythonUndeclaredReachScanner`

- [x] 7. Full test suite verification
  - [x] 7.1 Run `./gradlew test` and confirm no regressions across all modules

- [x] 8. Session-end: update partner model and project state
  - Read: `.agent/skills/session-end/SKILL.md` — follow the BEAR-specific section (no devlogs; update `state.md` and `partner_model.md`)
  - Read: `.agent/Knowledge/partner_model.md` — add a dated calibration note for this session
  - Read: `docs/context/state.md` — update handoff fields
  - Read: `roadmap/board.md` and `roadmap/features/p3-broader-boundary-escape-coverage.md` — update status
  - [x] 8.1 Update `.agent/Knowledge/partner_model.md`: add a dated entry under Calibration Notes summarising what shipped this session, any new patterns observed, and any corrections received
  - [x] 8.2 Update `docs/context/state.md`: set `Last Updated`, `Current Focus` to `p3-boundary-escape-coverage complete`, `Next Concrete Task` to `p3-multi-block-multi-module-composition-hardening`, add a brief session note
  - [x] 8.3 Update `roadmap/features/p3-broader-boundary-escape-coverage.md`: set `status: completed`
  - [x] 8.4 Update `roadmap/board.md`: move `p3-broader-boundary-escape-coverage` from Active to Completed; promote `p3-multi-block-multi-module-composition-hardening` to Active
