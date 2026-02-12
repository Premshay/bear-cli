# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-12

---

## Current Focus

Phase 2 (JVM target): `bear compile` first slice implemented with deterministic JVM artifact generation and two-tree ownership model.

---

## Current Phase

Phase: 2 -- JVM Target

Checklist:
- [x] Phase 1 `bear validate <file>` implemented end-to-end
- [x] Strict schema + semantic validation implemented and tested
- [x] Deterministic normalization + canonical YAML emission implemented and golden-tested
- [x] Phase 2 codegen scaffolding started

Exit condition:
`bear compile` emits deterministic JVM artifacts (ports + skeleton + test templates) for the demo IR.

---

## Next Concrete Task

Phase 2 (JVM target): harden compile surface and align generated templates with golden corpus:

1. Add golden corpus for `bear compile` generated outputs
2. Tighten generated idempotency/invariant tests beyond compile-only templates
3. Add compile spec conformance checks for naming + package sanitation edge cases
4. Start Phase 3 drift detection (`bear check` regen + compare)

Notes:
- Gradle wrapper is available: use `.\gradlew.bat` (Windows) to build/run without a global Gradle install.
- Canonical IR specification is now `doc/IR_SPEC.md`.
- Canonical demo IR fixture path is `spec/fixtures/withdraw.bear.yaml`.

---

## Phase Pipeline (Do Not Skip)

Phase 1 -- IR foundation  
Phase 2 -- JVM target  
Phase 3 -- Two-file enforcement  
Phase 4 -- `bear check`  
Phase 5 -- Demo proof  

If work does not advance the pipeline toward:

> "Naive withdraw fails. Correct withdraw passes."

It is scope drift.

---

## Upcoming Design Decisions (Not Blocking Current Phase)

- Strictness model for effects enforcement (compile-time vs runtime test only)

---

## Non-Blocking (Parked)

- Early self-hosting (NormalizeIr)
- CLI packaging strategy
- Multi-target ideas
- Enterprise features

---

## v0 Success Condition

All of the following must be true:

- Deterministic IR validation
- Deterministic JVM code generation
- Two-file enforcement
- `bear check` gate
- Demo proves regression prevention

---

## Session Notes

Append short bullet points only.  
No essays. No philosophy.

- Added Gradle wrapper scripts + wrapper jar.
- Aligned docs to v0 clarified scope: deterministic constraint compiler, structured ports, explicit guarantees/non-guarantees.
- Locked canonical demo IR details (`version`, invariant `kind`, idempotency `store.port/getOp/putOp`).
- Implemented `bear validate <file>` end-to-end (strict schema + semantic validation, deterministic normalization, canonical YAML emission) with spec fixtures + golden output.
- Stabilized Gradle behavior for Windows locks: wrapper defaults `GRADLE_USER_HOME` to temp and Gradle build outputs are redirected to temp (`bear-cli-build/<runId>`).
- Implemented `bear compile <ir-file> --project <path>` with validate+normalize pre-pass and deterministic generation to `<project>/build/generated/bear`.
- Implemented two-tree ownership model: generated artifacts are fully regenerated; user-owned `<BlockName>Impl.java` is created once under `src/main/java` and then preserved.
- Added compile command spec at `spec/commands/compile.md`.
- Added compile coverage in app/kernel tests for argument handling, deterministic regeneration, and impl preservation.
- Parked feature request for later: configurable compile base package (`--base-package`) so adopter apps can own package namespace.
- Integrated minimal demo wiring with `../bear-account-demo`: manual compile works, generated sourceSets are wired, demo tests run green, and user impl preservation was verified.
- Fixed generator bug in runtime invariant emission for idempotency replay path (generated code now references correct result variable).
