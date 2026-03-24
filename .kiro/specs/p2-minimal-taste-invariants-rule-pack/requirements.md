# Requirements: P2 Minimal Taste-Invariants Rule Pack

## Introduction

BEAR governs structural boundaries and deterministic contracts, not coding style. However,
certain obviously bad structural patterns in BEAR-owned files and generated zones can
silently accumulate without triggering existing governance gates. These patterns are
machine-checkable, deterministic, and unambiguously wrong — not subjective style preferences.

This feature adds a small, explicit rule pack that enforces layout and naming invariants
for BEAR-owned files, constraints on generated zone structure, and forbidden dependency
edges where BEAR already owns the contract boundary. The rule pack is intentionally narrow:
it targets BEAR-owned or generated surfaces first, avoids subjective heuristics, and fits
within the existing deterministic failure-envelope behavior.

The initial scope includes three rule categories:
1. Generated file layout and naming invariants under `build/generated/bear/**`
2. Constraints on generated zone sprawl or unexpected file structure
3. Forbidden dependency edges between guarded packages where BEAR owns the contract

All findings map to existing exit codes and produce stable, deterministic output.

## Glossary

- **Taste_Invariant_Scanner**: The kernel component that scans BEAR-owned files and
  generated zones for violations of deterministic layout, naming, and structure rules.
- **Generated_Zone**: The directory tree under `build/generated/bear/` containing all
  BEAR-generated artifacts (surfaces, wiring, source, gradle config).
- **Layout_Invariant**: A deterministic rule specifying required directory structure,
  file naming patterns, or path conventions for BEAR-owned artifacts.
- **Naming_Invariant**: A deterministic rule specifying required naming conventions for
  generated files (e.g., `{blockKey}.surface.json`, `{blockKey}.wiring.json`).
- **Zone_Sprawl**: Unexpected files or directories appearing in the Generated_Zone that
  do not match the expected BEAR-generated artifact patterns.
- **Forbidden_Dependency_Edge**: A dependency relationship between packages or modules
  that violates a BEAR-owned contract boundary (e.g., generated code importing impl code).
- **Taste_Finding**: A governance finding produced by the Taste_Invariant_Scanner,
  containing rule ID, path, and remediation text in the standard failure-envelope format.
- **Rule_Pack**: The collection of deterministic rules enforced by the Taste_Invariant_Scanner.
- **BEAR_Owned_File**: A file whose content is fully controlled by BEAR generation and
  should not be manually edited (e.g., surface manifests, wiring manifests, generated wrappers).
- **Allowlist**: A configuration mechanism (similar to `bear-policy/hygiene-allowlist.txt`)
  that permits specific paths to bypass a rule when justified.

---

## Requirements

### Requirement 1: Generated zone layout invariants are enforced

**User Story:** As a developer, I want BEAR to detect when generated zone structure deviates
from expected patterns, so that I can catch accidental file placement or generation bugs early.

#### Acceptance Criteria

1. THE Taste_Invariant_Scanner SHALL verify that all files under `build/generated/bear/surfaces/`
   match the pattern `{blockKey}.surface.json`.

2. THE Taste_Invariant_Scanner SHALL verify that all files under `build/generated/bear/wiring/`
   match the pattern `{blockKey}.wiring.json`.

3. WHEN a file under `build/generated/bear/surfaces/` does not match the expected naming pattern,
   THE Taste_Invariant_Scanner SHALL emit a Taste_Finding with rule ID `GENERATED_SURFACE_NAMING_VIOLATION`.

4. WHEN a file under `build/generated/bear/wiring/` does not match the expected naming pattern,
   THE Taste_Invariant_Scanner SHALL emit a Taste_Finding with rule ID `GENERATED_WIRING_NAMING_VIOLATION`.

5. FOR ALL valid block configurations, scanning then re-scanning the same Generated_Zone SHALL
   produce identical findings in identical order (determinism property).

### Requirement 2: Generated zone sprawl is detected

**User Story:** As a developer, I want BEAR to detect unexpected files or directories in the
generated zone, so that I can identify stale artifacts or accidental manual additions.

#### Acceptance Criteria

1. THE Taste_Invariant_Scanner SHALL enumerate all paths under `build/generated/bear/` and
   verify each path belongs to a known artifact category (surfaces, wiring, src, gradle).

2. WHEN a file or directory under `build/generated/bear/` does not match any expected artifact
   pattern, THE Taste_Invariant_Scanner SHALL emit a Taste_Finding with rule ID
   `GENERATED_ZONE_UNEXPECTED_PATH`.

3. THE Taste_Invariant_Scanner SHALL support an allowlist mechanism (e.g.,
   `bear-policy/generated-zone-allowlist.txt`) to permit specific paths when justified.

4. WHEN a path is present in the allowlist, THE Taste_Invariant_Scanner SHALL skip that path
   without emitting a finding.

5. THE Taste_Invariant_Scanner SHALL emit findings in deterministic, sorted order by path.

### Requirement 3: Generated source structure invariants are enforced

**User Story:** As a developer, I want BEAR to verify that generated Java sources follow the
expected package and directory structure, so that compilation and governance remain consistent.

#### Acceptance Criteria

1. THE Taste_Invariant_Scanner SHALL verify that generated Java sources under
   `build/generated/bear/src/main/java/` follow the expected package structure
   `com.bear.generated.{block}/`.

2. WHEN a generated Java source file exists outside the expected package structure,
   THE Taste_Invariant_Scanner SHALL emit a Taste_Finding with rule ID
   `GENERATED_SOURCE_STRUCTURE_VIOLATION`.

3. THE Taste_Invariant_Scanner SHALL verify that each block's generated source directory
   contains only expected artifact types (wrapper, ports, models, runtime classes).

4. WHEN an unexpected file type appears in a block's generated source directory,
   THE Taste_Invariant_Scanner SHALL emit a Taste_Finding with rule ID
   `GENERATED_SOURCE_UNEXPECTED_FILE`.

5. FOR ALL generated source trees, the scanner SHALL produce stable findings regardless of
   filesystem enumeration order (ordering invariant).

### Requirement 4: Forbidden dependency edges are detected

**User Story:** As a developer, I want BEAR to detect when generated code incorrectly depends
on impl code or other forbidden packages, so that boundary contracts remain enforceable.

#### Acceptance Criteria

1. THE Taste_Invariant_Scanner SHALL detect when generated code under
   `build/generated/bear/src/` imports from `blocks.*.impl.*` packages.

2. WHEN generated code imports from a forbidden package, THE Taste_Invariant_Scanner SHALL
   emit a Taste_Finding with rule ID `GENERATED_FORBIDDEN_DEPENDENCY`.

3. THE Taste_Invariant_Scanner SHALL detect when generated code imports from user-owned
   `src/main/java/blocks/` paths that are not declared ports or shared types.

4. THE remediation text for `GENERATED_FORBIDDEN_DEPENDENCY` SHALL identify the specific
   import statement and the forbidden package.

5. THE Taste_Invariant_Scanner SHALL NOT flag imports from `blocks._shared.pure.*` or
   declared port interfaces (these are permitted dependencies).

### Requirement 5: Findings fit existing failure-envelope behavior

**User Story:** As a developer, I want taste-invariant findings to use the same output format
and exit codes as existing governance findings, so that CI pipelines and tooling work unchanged.

#### Acceptance Criteria

1. THE Taste_Invariant_Scanner SHALL emit findings in the standard failure-envelope format:
   `CODE/PATH/REMEDIATION`.

2. WHEN taste-invariant findings are present, THE `bear check` command SHALL exit with code
   `6` (EXIT_UNDECLARED_REACH) — reusing the existing lane for structural governance findings.

3. THE Taste_Finding output SHALL be deterministic: identical inputs produce identical
   CODE, PATH, and REMEDIATION text across runs.

4. THE Taste_Invariant_Scanner SHALL NOT introduce new exit codes beyond the frozen set.

5. WHEN no taste-invariant findings are present, THE scanner SHALL produce no output and
   not affect the exit code.

### Requirement 6: Rule pack is small, explicit, and documented

**User Story:** As a BEAR maintainer, I want the rule pack scope to be explicitly documented
and tested, so that the feature does not drift into subjective style policing.

#### Acceptance Criteria

1. THE Rule_Pack SHALL include exactly the following rule IDs in the initial release:
   - `GENERATED_SURFACE_NAMING_VIOLATION`
   - `GENERATED_WIRING_NAMING_VIOLATION`
   - `GENERATED_ZONE_UNEXPECTED_PATH`
   - `GENERATED_SOURCE_STRUCTURE_VIOLATION`
   - `GENERATED_SOURCE_UNEXPECTED_FILE`
   - `GENERATED_FORBIDDEN_DEPENDENCY`

2. THE Rule_Pack SHALL be registered in `GovernanceRuleRegistry` with all rule IDs listed
   in the `PUBLIC_RULE_IDS` set.

3. THE documentation SHALL explicitly state that these rules target BEAR-owned surfaces only
   and do not enforce style on user-owned implementation code.

4. THE test suite SHALL include tests that verify each rule ID produces findings only for
   its intended violation pattern (no false positives on valid structures).

5. THE Rule_Pack SHALL NOT include any rules that require subjective judgment or are
   difficult to explain deterministically.

### Requirement 7: Allowlist mechanism supports justified exceptions

**User Story:** As a developer, I want to allowlist specific paths that legitimately deviate
from taste invariants, so that I can handle edge cases without disabling the entire rule.

#### Acceptance Criteria

1. THE Taste_Invariant_Scanner SHALL read allowlist entries from
   `bear-policy/taste-invariants-allowlist.txt` if the file exists.

2. WHEN a path matches an allowlist entry, THE scanner SHALL skip that path for all
   taste-invariant rules.

3. THE allowlist format SHALL match the existing `hygiene-allowlist.txt` format: one path
   per line, relative to project root, with `#` comments supported.

4. WHEN the allowlist file does not exist, THE scanner SHALL proceed with no allowlisted paths.

5. THE allowlist mechanism SHALL be documented with examples of when allowlisting is appropriate.

### Requirement 8: Scanner integrates with existing check pipeline

**User Story:** As a developer, I want taste-invariant scanning to run as part of `bear check`,
so that I get a single unified governance report.

#### Acceptance Criteria

1. WHEN `bear check` is invoked, THE command SHALL invoke the Taste_Invariant_Scanner after
   existing governance scans complete.

2. THE Taste_Invariant_Scanner SHALL run only when a Generated_Zone exists; if
   `build/generated/bear/` does not exist, the scanner SHALL produce no findings.

3. WHEN taste-invariant findings are combined with other governance findings, THE output
   SHALL maintain deterministic ordering (existing findings first, then taste findings sorted by path).

4. THE Taste_Invariant_Scanner SHALL NOT modify any files; it is a read-only scan.

5. WHEN `bear check --verbose` is invoked, THE scanner SHALL emit progress information
   indicating which rules are being evaluated.

### Requirement 9: Test coverage pins rule scope and prevents drift

**User Story:** As a BEAR maintainer, I want tests that verify each rule's exact scope, so
that future changes cannot accidentally expand rules into subjective territory.

#### Acceptance Criteria

1. THE test suite SHALL include at least one test per rule ID that verifies the rule fires
   on its intended violation pattern.

2. THE test suite SHALL include at least one test per rule ID that verifies the rule does
   NOT fire on valid, compliant structures.

3. THE test suite SHALL include a test that verifies findings are deterministic: scanning
   the same Generated_Zone twice produces identical output.

4. THE test suite SHALL include a test that verifies allowlist entries suppress findings
   for the specified paths.

5. THE test suite SHALL include a test that verifies the Rule_Pack contains exactly the
   documented rule IDs and no others (scope lock test).

6. FOR ALL rule implementations, the test SHALL verify that remediation text is stable and
   does not include non-deterministic content (timestamps, random IDs, etc.).
