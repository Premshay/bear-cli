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

## Agent workflow and developer visibility

- BEAR is primarily an agent execution framework, not a manual developer checklist.
- Agent runs the deterministic loop (IR update, generation, gates) under BEAR restrictions.
- Developer uses BEAR outputs for visibility and governance, especially in PR review and CI.

Typical agent loop:

1. update IR and implementation from project specs
2. run `bear validate`
3. run `bear compile` or `bear fix`
4. run `bear check`
5. run `bear pr-check` against base for governance classification

Developer-facing visibility:

- PR signal: `pr-check` classifies boundary-expanding vs ordinary changes so reviewers can focus on contract/power expansion.
- CI signal: deterministic exit codes and failure footer let teams enforce stable merge gates.
- Local triage signal: consistent output ordering and path normalization make failures actionable quickly.

## Preview scope mindset

Preview focuses on structural contract enforcement, deterministic diagnostics, and boundary governance.
It is intentionally not a business-rules engine and does not claim full runtime semantics.

See [CONTRACTS.md](CONTRACTS.md) and [VERSIONING.md](VERSIONING.md).

## CLI architecture at a glance

BEAR CLI is split into two modules:

- `kernel/`: deterministic trusted seed for IR parsing, validation, normalization, and target abstractions.
- `app/`: CLI orchestration (`validate`, `compile`, `fix`, `check`, `pr-check`) and contract rendering.

Design intent:

- core deterministic logic in `kernel`
- command workflow and reporting in `app`
- stable command/output contracts for external automation

## Related

- [INDEX.md](INDEX.md)
- [INSTALL.md](INSTALL.md)
- [MODEL.md](MODEL.md)
- [CONTRACTS.md](CONTRACTS.md)
- [commands-check.md](commands-check.md)
- [commands-pr-check.md](commands-pr-check.md)
