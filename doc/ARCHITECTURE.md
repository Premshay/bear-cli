# BEAR v0 Architecture

## What BEAR is
BEAR is a deterministic enforcement layer for AI-assisted (or human) code changes in backend systems.

BEAR introduces a small, machine-checkable intermediate representation (BEAR IR) for "blocks" and compiles it into:
- non-editable skeletons and ports (capability boundaries)
- deterministic tests (invariants, idempotency)
- a single `bear check` gate suitable for CI

BEAR core contains no LLM logic and does not depend on any specific agent tool.

## Core principles
1. Deterministic core
   - Validation, normalization, and code generation must be deterministic and reproducible.

2. Agent-agnostic
   - BEAR must work with Copilot, Codex, or no agent at all.
   - Any agent instructions are separate from BEAR core.

3. "Cage, not code"
   - BEAR generates boundaries (ports), skeletons, and gates.
   - Business logic is implemented in a separate implementation file (two-file approach).

4. Small, enforceable semantics
   - v0 focuses on a minimal set of enforceable guarantees:
     - allowed effects (capability allowlist)
     - idempotency (by key)
     - one invariant template: non-negative numeric field

## Repository structure (bear-cli)
This repo is a Gradle multi-module project.

- kernel/
  - Trusted seed.
  - Contains BEAR IR parsing/validation/normalization and the target abstraction.
  - Must not depend on generated code.
  - Must remain small and stable.

- app/
  - CLI wrapper that exposes commands.
  - Depends on kernel.
  - No LLM logic.

## BEAR IR v0 scope
BEAR IR describes a single block:

- name, kind
- contract: inputs/outputs (simple types: string, decimal, enum)
- effects.allow: allowlisted capability operations (e.g., storage.readAccount)
- idempotency: key field + optional target effect
- invariants: only `non_negative(field=...)` in v0

No system graphs. No cross-service modeling.

## Target scope (v0)
Only one target is supported:

- JVM target
  - Generates Java sources and JUnit 5 tests.
  - Integrates with Gradle projects.

No other languages in v0.

## Demo scope (bear-account-demo repo)
The demo proves BEAR on a small "bank account" domain.

Only these operations exist:
- Deposit(accountId, amount, currency, txId)
- Withdraw(accountId, amount, currency, txId)
- GetBalance(accountId)

Only these guarantees are enforced in v0:
- idempotency by txId
- non-negative balance (or non-negative output balance field)
- effects restricted to declared storage port methods

The demo must show:
- a naive implementation fails `bear check`
- a corrected implementation passes `bear check`

## Non-goals for v0
Explicitly out of scope:
- Spec -> IR lowering automation
- LLM-driven implementation inside BEAR
- Cross-service/system flow modeling
- UI/frontend support
- Plugin architecture
- Multi-language targets
- Rich invariant catalog or domain ontologies
- Formal verification

## Definition of done (v0)
v0 is done when the following are true:

1. `bear validate` validates and normalizes BEAR IR files deterministically.
2. `bear compile` generates deterministic artifacts for JVM:
   - non-editable skeleton(s)
   - capability port interface(s) derived from effects.allow
   - JUnit tests for:
     - idempotency (if declared)
     - non_negative invariant
3. Two-file approach is enforced:
   - generated skeleton is never edited
   - implementation file is where logic lives
4. `bear check --project <path>` runs:
   - validate + compile
   - project tests
   and fails deterministically on violations.
5. In bear-demo:
   - naive Withdraw fails `bear check`
   - fixed Withdraw passes `bear check`

## Future ideas (parking lot)
Keep future ideas in FUTURE.md. Do not implement them in v0.
****
