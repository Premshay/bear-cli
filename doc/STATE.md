# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-12

---

## Current Focus

Phase 1 (IR foundation): implement strict `doc/IR_SPEC.md` parsing + validation + deterministic normalization.

---

## Current Phase

Phase: 1 -- BEAR IR Foundation

Checklist:
- [x] Gradle multi-module project created
- [x] `kernel` module scaffolded (IR parser/validator/normalizer stubs)
- [x] `app` module scaffolded (CLI placeholder commands)
- [x] CLI entry point wired (including `--help`)
- [x] JUnit 5 configured in Gradle

Exit condition:
`bear validate <file>` succeeds/fails deterministically and emits canonical form.

---

## Next Concrete Task

Implement Phase 1 core model + strict validation:

1. Replace map-based IR with `BlockModel` + structured `EffectPortModel`
2. Define strict YAML schema (including root `version: v0`, fail on unknown keys)
3. Implement semantic validation rules from `doc/ROADMAP.md`
4. Implement deterministic canonicalization (sorted structure + canonical key order)
5. Wire `bear validate <file>` to parse -> validate -> emit canonical form

Notes:
- Gradle wrapper is available: use `.\gradlew.bat` (Windows) to build/run without a global Gradle install.
- Canonical IR specification is now `doc/IR_SPEC.md`.
- Canonical demo IR path is `bear/withdraw.bear.yaml`.

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

## Upcoming Design Decisions (Not Blocking Phase 0)

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
