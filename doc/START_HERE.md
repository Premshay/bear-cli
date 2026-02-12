# Start Here (bear-cli)

This repository is the CLI + deterministic kernel for **BEAR** (Block Enforcement & Representation).

BEAR is a deterministic constraint compiler for AI-assisted (or human) backend code changes:
you describe a single **block** in **BEAR IR v0**, then BEAR validates/normalizes it and compiles it into
non-editable skeletons + structured ports + deterministic tests, all enforced by a single `bear check` gate.

## Read next (in order)

1. `doc/STATE.md` -- current focus and next steps (keep updated).
2. `doc/ARCHITECTURE.md` -- what BEAR is, v0 scope, non-goals.
3. `doc/IR_SPEC.md` -- canonical v0 IR model and validation rules.
4. `doc/ROADMAP.md` -- what to build in v0 and in what phases.
5. `doc/PROJECT_LOG.md` -- background and major decisions.
6. `doc/FUTURE.md` -- parking lot; explicitly not part of v0.
7. `doc/PROMPT_BOOTSTRAP.md` -- what to paste into a fresh AI session to restore context.

## v0 scope (quick)

- Target: JVM (Java) only
- Single logic block per IR file
- Enforces:
  - root IR version `v0`
  - allowed effects via structured ports (`effects.allow` with `port` + `ops[]`)
  - idempotency by key with explicit store ops (`store.port`, `store.getOp`, `store.putOp`)
  - one invariant template: `kind: non_negative` + `field`
- Non-guarantees:
  - business correctness beyond declared invariants
  - DB/concurrency/transaction semantics
  - runtime enforcement beyond test harness
  - duplicate-request concurrency correctness (v0 covers deterministic replay only)
- Out of scope: capability blocks in IR, block graphs/composition, behavior DSL, requires/ensures, state delta modeling, infrastructure simulation

## Repo layout

- `kernel/` -- trusted deterministic seed: BEAR IR parsing/validation/normalization + target abstractions
- `app/` -- CLI wrapper (commands like `bear validate`, `bear compile`, `bear check`)

## Working agreement (for contributors + agents)

- Determinism first: validation, normalization, and codegen must be reproducible.
- Agent-agnostic: BEAR must work with any agent tool (or none).
- "Cage, not code": generate boundaries + tests; implement business logic in a separate impl file.
- If it doesn't help demonstrate "naive Withdraw fails; corrected Withdraw passes", it's not v0.
