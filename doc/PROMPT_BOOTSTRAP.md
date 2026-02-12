# BEAR Project Bootstrap

This file is used to restart a new AI session without losing context.

Always paste the SHORT version first.
Use the LONG version only if more context is needed.

---

# SHORT BOOTSTRAP (Default)

We are building BEAR (Block Enforcement & Representation).

BEAR is a deterministic constraint compiler for backend systems.

Source of truth for current work:
- `doc/STATE.md` (current focus + next steps)
- `doc/IR_SPEC.md` (canonical v0 IR model and strict rules)
- `doc/PROJECT_LOG.md` (background + major decisions)

It introduces a BEAR IR (block intermediate representation) and compiles it into:
- non-editable skeletons
- structured capability ports (effects boundary)
- deterministic tests (idempotency + invariants)
- a `bear check` enforcement gate

Core rules:
- BEAR is fully deterministic
- No LLM logic inside BEAR core
- Agent-agnostic
- Two-file approach (generated skeleton + separate impl)

v0 scope:
- JVM (Java) only
- Single logic block per IR file
- Enforce:
  - root IR version `v0`
  - allowed effects via structured ports
  - idempotency key plus explicit store ops (`port/getOp/putOp`)
  - `kind: non_negative` invariant on output field
- Demo: bank account Withdraw block
- Naive implementation must fail `bear check`
- Correct implementation must pass

v0 non-guarantees:
- Business correctness beyond declared invariants
- DB/concurrency/transaction semantics
- Runtime enforcement beyond test harness
- Concurrency-safe duplicate handling (v0 covers deterministic replay only)

Current Phase:
[UPDATE EACH SESSION]

Session Goal:
[STATE SINGLE TASK]

Continue from here.

---

# LONG BOOTSTRAP (If More Context Is Needed)

## Core Idea

BEAR introduces a small intermediate representation (BEAR IR) for "blocks".

BEAR compiles IR into:
- Non-editable skeleton classes
- Structured port interfaces derived from effects.allow
- Deterministic test templates
- A single enforcement command: `bear check`

BEAR acts as a constraint layer between:
- Spec / human intent
- AI-generated or human-written implementation

It is not a full development platform.
It does not embed LLM logic.

---

## Architecture (bear-cli repo)

- kernel/
  - Trusted deterministic seed
  - IR parsing
  - Validation
  - Normalization
  - Target abstraction

- app/
  - CLI wrapper
  - Commands:
    - bear validate
    - bear compile
    - bear check

Two-file enforcement:
- Generated skeleton (non-editable)
- Separate implementation file

---

## v0 Scope (Locked)

Target:
- JVM (Java only)

Enforced Guarantees:
- Structured effect boundary (effects.allow ports + ops)
- Idempotency by key
- non_negative(field) invariant

Demo:
- Bank account domain
- Withdraw block
- Show:
  - naive implementation fails
  - correct implementation passes

---

## Explicitly Out of Scope (v0)

- Spec -> IR lowering
- Capability blocks in IR
- Block-to-block composition/graph modeling
- Behavior DSL
- requires/ensures
- State delta modeling
- Infrastructure simulation
- Cross-service modeling
- Multi-language targets
- LLM inside BEAR core
- Plugin system
- UI
- Rich invariant catalog
- Full self-hosting of kernel

---

## Current Phase
[UPDATE EACH SESSION]

## Session Goal
[STATE SINGLE TASK]

Continue from this state.
