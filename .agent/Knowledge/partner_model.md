# Partner Model: BEAR (bear-cli)

This file tracks collaboration patterns, project preferences, and user calibration notes.
**Update this at the end of every session** — it's your working memory for how to work
effectively with this user on this project.

## User Profile

- **Role**: Technical Lead / Architect
- **Technical Depth**: High. Understands system internals, agentic architectures,
  governance systems, multi-target CLI design.
- **Values**: Deterministic behavior, explicit contracts, root cause analysis,
  comprehensive documentation, boundary governance.

---

## Project Context

- **Project**: BEAR — Block Enforceable Architectural Representation
- **Type**: Deterministic governance CLI for agentic backend development
- **Language**: Java (Gradle multi-module: `kernel/` + `app/`)
- **Key Constraints**:
  - Determinism is a product contract, not just a preference
  - Exit codes, failure envelopes, and IR schemas are frozen
  - `kernel/` is the trusted seed — no LLM/agent logic allowed
  - New targets extend through the Target seam without changing core

---

## Collaboration Protocols

### Communication Style
- Proactive: fix the underlying issue, don't just answer
- Explain "why" for architectural decisions
- No fluff — get straight to technical details
- Direct feedback with specific corrections
- Lowercase informal in chat, formal documentation expected
- Genuinely wants AI input on design decisions

### Documentation Expectations
- Comprehensive context docs targeted by audience
- Documentation is part of "done"
- Living documents updated proactively as patterns emerge
- Session-end protocol is mandatory

### Quality Standards
- Determinism is non-negotiable
- "Canonical" as target — "good enough" is insufficient for contracts
- Test integrity: update tests when behavior changes
- Documentation is part of task completion

### Task Execution
- Trusts well-structured plans (LGTM approval)
- Expects efficient execution with brief status updates
- Test failures are normal — expects methodical resolution
- Batch related edits before verification (fast-by-default)

## Key Patterns

### What Works Well
- Iterative refinement with specific feedback
- Catching contradictions early through careful spec review
- Explicit documentation of limitations and assumptions
- Focused sessions that complete discrete units of work
- "Tighten the screws" approach: fix critical issues first
- Strategic thinking about multi-target expansion
- Spec-driven development with clear acceptance criteria
- Proactive state updates without being asked

### What Doesn't Work
- Verbose explanations when code speaks for itself
- Implicit assumptions or undocumented transition rules
- Quick fixes without architectural justification
- Settling for "good enough" when contracts are involved
- Re-explaining problems already diagnosed in context docs
- Ignoring frozen contract boundaries
- Duplicating content across files instead of referencing

### When User Says...
- "LGTM" / "approved" → Plan accepted, proceed with implementation
- "wrap up" / "end session" → Execute session close protocol
- "full verify" → Run `--no-daemon` CI-parity tests
- "update state" → Update `docs/context/state.md`
- "clean the demo" → Use `scripts/clean-demo-branch.ps1`
- "update memory" → Update this file + `docs/context/state.md`

---

## BEAR-Specific Patterns

### Deterministic Contracts
- Exit codes are frozen: never add new ones without updating the contract
- Failure envelope (CODE/PATH/REMEDIATION) is frozen
- IR v1 schema is frozen: no per-target IR additions
- Generated artifacts must be reproducible across runs
- `--all` aggregation uses severity rank, not numeric max

### Kernel Purity
- `kernel/` is the trusted deterministic seed
- No LLM/agent logic in kernel
- Generic types in `com.bear.kernel.target`
- Target-specific code in `com.bear.kernel.target.<targetId>`

### Scope Discipline
- Stay within v1-preview scope
- Parked ideas in `roadmap/ideas/` are out-of-scope unless asked
- BEAR is not a behavior DSL, style engine, or runtime policy system
- New targets extend through the Target seam without changing core

### Documentation Hierarchy
- `docs/context/state.md` = project handoff (what was done, what's next)
- `.agent/Knowledge/partner_model.md` = behavioral memory (how to work together)
- These are complementary, not competing — both must be maintained
- `.kiro/steering/` files are thin bridges pointing here, not duplicates

---

## Calibration Notes

**This is a living document.** Update it at the end of every session.
Add a dated entry for any session with notable patterns, corrections, or new phrase mappings.
Don't just suggest updates — make them. Specific observations beat generic ones.

### 2026-03-12 — Initial Kiro Setup + Antigravity Integration
- Established steering documents for Kiro from repo analysis
- Adapted antigravity-kit concepts for BEAR's existing conventions
- Key insight: `.agent/` should be IDE-agnostic canonical home; `.kiro/steering/` should be thin bridges
- Key insight: devlog creation from session-end skill is NOT used (state.md serves this); partner_model updates ARE fully adopted
- Key insight: `state.md` = project handoff, `partner_model.md` = behavioral memory — complementary, not competing
- Correction received: "don't duplicate content between .agent/ and .kiro/steering/ — reference instead"
- Correction received: intelligent-routing skill should be kept (relevant to Kiro's subagent routing)

### 2026-03-19 — P3 Boundary Escape Coverage Implemented
- Extended `PythonUndeclaredReachScanner` with 15 new modules across database/filesystem/messaging groups + `open`/`io.open` call-site detection
- Fixed pre-existing test regression: `cleanProjectNoFindings` used `from pathlib import Path` which now triggers a finding — updated fixture to use `import re` instead
- Closed app-layer Python seam parity gap: `pinPython` in `TestTargetPins`, `PythonSeamParityTest` (2 tests), `PythonAllowedDepsGuardTest` (5 tests)
- Echo-safe IR constraint discovered in practice: `effects.allow: []` requires output names to mirror input names — updated Python IR fixture accordingly
- Full suite green: zero regressions across all modules
- Key pattern confirmed: when extending `COVERED_MODULES`, always audit existing test fixtures for newly-covered module names (pathlib was in a "clean" fixture)
- Key pattern confirmed: Python IR fixtures must be echo-safe (output name:type mirrors input) when `effects.allow: []` — same constraint as JVM/Node

### 2026-03-19 — P3 Boundary Escape Coverage Spec Created
- Created full spec under `.kiro/specs/p3-boundary-escape-coverage/` (requirements, design, tasks)
- Sprint scope: extend `PythonUndeclaredReachScanner` with database/filesystem/messaging surfaces + `open`/`io.open` call-site detection; close app-layer Python seam parity gap (`pinPython`, `PythonSeamParityTest`, `PythonAllowedDepsGuardTest`)
- `p3-maven-allowed-deps-containment` explicitly kept in Later Queue (medium priority, optional expansion)
- Roadmap updated: `p3-broader-boundary-escape-coverage` moved to Active in `roadmap/board.md`
- Key correction received: tasks must include explicit `Read:` context file references per sub-task, not just at the top level
- Key correction received: session-end skill (partner_model + state.md update) must be a named task in the task list, not just implied by the final state-update task
- Pattern confirmed: user expects tasks.md to be self-contained enough that an agent can execute each task without re-reading the full spec — context files per task are mandatory
- Pattern confirmed: "documentation to update" must be explicit tasks, not afterthoughts

### 2026-03-18 — Python MVP Hardening Complete
- Completed all 9 tasks for Python MVP Hardening spec on `feature/python-mvp-hardening` branch
- Added runpy.run_module/run_path detection to PythonDynamicExecutionScanner (ast.Attribute check)
- Removed 21 deprecated stdlib modules from PythonImportBoundaryResolver (Python 3.12/3.13 removals)
- Created integration test fixture `check-runpy` and integration test in PythonCheckIntegrationTest
- Created `docs/context/python-known-gaps.md` documenting 6 known bypass patterns with threat model
- Full kernel suite green: 5442 tests, zero regressions across JVM, Node, Python targets
- Key pattern: context transfer across sessions works well — picked up mid-task and completed cleanly
- Verified: all 5 property tests (H1-H4, H7) pass with 110 iterations each

### 2026-03-19 — P3 Multi-Block Multi-Module Composition Hardening Complete
- Shipped 10 new tests across 2 test files covering multi-root `check --all` and `pr-check --all` composition behavior
- `BlockPortGraphResolverTest`: 3 multi-root unit tests with shared `multiRootGraph()` helper for cross-root block-port edge resolution
- `MultiRootCompositionTest`: 5 `check --all` + 2 `pr-check --all` integration tests exercising two-root layouts, drift isolation, idempotence, and deterministic ordering
- Key discovery: block names in IR must canonicalize to match the index entry `name` field — mismatch causes silent resolution failures
- Key discovery: when `check --all` has failures, block results render to stderr (not stdout) — tests must capture both streams
- Key discovery: multi-root `pr-check` tests need `TestTargetPins.pinJvm(repoRoot)` to set up target resolution correctly
- Pattern confirmed: multi-root fixture setup follows the same `writeProjectWrapper` + `writeWorkingWithdrawImpl` pattern as single-root, just repeated per root directory
- Pattern confirmed: git helpers (`initGitRepo`, `gitCommitAll`, `git`) copied from `TargetSeamParityTest` work cleanly for pr-check integration tests
- No corrections received this session

---

## Usage Notes for AI Agents

- Read this file at session start to calibrate collaboration style
- Update this file at session end — take ownership, don't just suggest
- Cross-reference with `docs/context/state.md` for project handoff context
- The two files serve different purposes: this = HOW, state.md = WHAT
- When in doubt about BEAR conventions, check `docs/context/` first
- Frozen contracts (exit codes, IR schema, failure envelope) are non-negotiable
