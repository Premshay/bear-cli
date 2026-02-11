# Start Here (bear-cli)

This repository is the CLI + deterministic kernel for **BEAR** (Block Enforcement & Representation).

BEAR is a deterministic enforcement layer for AI-assisted (or human) backend code changes:
you describe a single **block** in **BEAR IR v0**, then BEAR validates/normalizes it and compiles it into
non-editable skeletons + capability ports + deterministic tests, all enforced by a single `bear check` gate.

## Read next (in order)

1. `doc/STATE.md` -- current focus and next steps (keep updated).
2. `doc/ARCHITECTURE.md` -- what BEAR is, v0 scope, non-goals.
3. `doc/ROADMAP.md` -- what to build in v0 and in what phases.
4. `doc/PROJECT_LOG.md` -- background and major decisions.
5. `doc/FUTURE.md` -- parking lot; explicitly not part of v0.
6. `doc/PROMPT_BOOTSTRAP.md` -- what to paste into a fresh AI session to restore context.

## v0 scope (quick)

- Target: JVM (Java) only
- Enforces:
  - allowed effects via an allowlist (`effects.allow`)
  - idempotency by key (if declared)
  - one invariant template: `non_negative(field=...)`
- Non-goals: spec->IR lowering, cross-block/system modeling, plugins, multi-language targets, UI, LLM logic inside BEAR core

## Repo layout

- `kernel/` -- trusted deterministic seed: BEAR IR parsing/validation/normalization + target abstractions
- `app/` -- CLI wrapper (commands like `bear validate`, `bear compile`, `bear check`)

## Working agreement (for contributors + agents)

- Determinism first: validation, normalization, and codegen must be reproducible.
- Agent-agnostic: BEAR must work with any agent tool (or none).
- "Cage, not code": generate boundaries + tests; implement business logic in a separate impl file.
- If it doesn't help demonstrate "naive Withdraw fails; corrected Withdraw passes", it's not v0.
