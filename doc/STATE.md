# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-11

---

## Current Focus

Scaffolding `bear-cli` multi-module project.

---

## Current Phase

Phase: 0 -- Project Setup

Checklist:
- [ ] Gradle multi-module project created
- [ ] `kernel` module builds
- [ ] `app` module builds
- [ ] CLI entry point wired
- [ ] `bear --help` works

Exit condition:
CLI builds and runs with placeholder commands.

---

## Next Concrete Task

Scaffold `bear-cli`:

1. Create Gradle multi-module structure
2. Add `kernel` (trusted seed, no generation logic yet)
3. Add `app` (CLI wrapper)
4. Implement minimal command dispatcher
5. Verify `bear --help` works

Nothing else.

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

- Final YAML IR schema shape
- Idempotency storage strategy in demo
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
