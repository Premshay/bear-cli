# Foundations

## Why BEAR exists

BEAR exists to make AI-assisted and agentic backend development safer without killing delivery speed.
In agent-heavy workflows, code can evolve quickly, but boundary expansion and generated-artifact drift can become hard to see.
BEAR addresses this with deterministic contracts and deterministic gates.

## Core philosophy

- Keep implementation velocity high inside declared boundaries.
- Make boundary power changes explicit and reviewable.
- Prefer deterministic contract enforcement over process or prompt discipline.
- Produce CI-friendly, machine-parseable outputs and stable exit semantics.

## Architecture at a glance

BEAR CLI is split into two modules:

- `kernel/`: deterministic trusted seed for IR parsing, validation, normalization, and target abstractions.
- `app/`: CLI orchestration (`validate`, `compile`, `fix`, `check`, `pr-check`) and contract rendering.

Design intent:

- core deterministic logic in `kernel`
- command workflow and reporting in `app`
- stable command/output contracts for external automation

## BEAR IR fundamentals

BEAR IR is a constrained YAML contract for one governed block.
It declares block interface and boundary surface, then drives deterministic generation and checks.

Typical shape:

```yaml
version: v1
block:
  name: Withdraw
  kind: logic
  contract:
    inputs:
      - name: txId
        type: string
    outputs:
      - name: balance
        type: decimal
  effects:
    allow:
      - port: ledger
        ops: [getBalance, setBalance]
      - port: idempotency
        ops: [get, put]
  idempotency:
    key: txId
    store:
      port: idempotency
      getOp: get
      putOp: put
```

Key idea:

- IR is the declared boundary contract.
- `compile` materializes deterministic generated artifacts from that contract.
- `check` enforces consistency and policy against that contract.
- `pr-check` classifies contract deltas against base branch for governance.

## What determinism means in practice

- same IR and project state produce the same generated artifacts
- same failure class produces stable output envelope and locator semantics
- stable exit code meanings enable deterministic CI gates

See [output-format.md](output-format.md) and [exit-codes.md](exit-codes.md).

## Developer and agent workflow model

- Developer provides domain intent and reviews boundary-expanding changes.
- Agent handles mechanics: IR updates, generation, and gate execution.
- BEAR provides deterministic guardrails and deterministic diagnostics.

Typical flow:

1. update or review IR
2. run `bear validate`
3. run `bear compile` or `bear fix`
4. run `bear check`
5. run `bear pr-check` for PR governance

## Preview scope mindset

Preview focuses on structural contract enforcement, deterministic diagnostics, and boundary governance.
It is intentionally not a business-rules engine and does not claim full runtime semantics.

See [CONTRACTS.md](CONTRACTS.md) and [VERSIONING.md](VERSIONING.md).

## Related

- [INDEX.md](INDEX.md)
- [MODEL.md](MODEL.md)
- [CONTRACTS.md](CONTRACTS.md)
- [commands-check.md](commands-check.md)
- [commands-pr-check.md](commands-pr-check.md)