# Terms

This page defines the minimal vocabulary used in BEAR docs and outputs.

## Core concepts

- `block`: one governed unit of backend logic.
- `IR`: a small YAML contract (for example `spec/<block>.bear.yaml`) that declares a block's API (inputs/outputs) and boundary authority.
- `operation`: one entrypoint inside a block (declared under `block.operations`).

## Boundary vocabulary

- `effect` / `effects.allow`: the block's declared external capability boundary.
  - Example: "this block may call the `ledger` port ops `getBalance` and `setBalance`".
- `port`: a named external dependency surface.
  - Think: a client facade BEAR wants you to route through (database, HTTP API, queue, idempotency store).
- `op`: an allowed action on a port.
  - Example: `ledger.getBalance`, `ledger.setBalance`.
- `uses.allow`: per-operation subset of the block boundary.
  - Rule: every `uses.allow` entry must be subset-or-equal to `effects.allow`.

## Generated surface

- `wrapper`: generated code that forms the governed integration surface and owns any wrapper-enforceable semantics (for example idempotency/invariant checks).
- `ports (generated)`: generated interfaces that represent the allowed boundary; implementations belong under governed source roots.

## Governance signals

- `boundary expansion`: a change that widens declared boundary authority (new ports/ops, new operations, idempotency/invariant changes, etc.). Detected by `bear pr-check`.
- `boundary bypass`: code shape that reaches around the governed surface (for example implementing generated ports outside governed roots). Detected by `bear check` / `bear pr-check`.
- `drift`: generated artifacts are stale/missing/edited compared to what BEAR would deterministically produce from IR.

## Example (conceptual)

```yaml
block:
  operations:
    - name: ExecuteWithdraw
      uses:
        allow:
          - port: ledger
            ops: [getBalance, setBalance]
  effects:
    allow:
      - port: ledger
        ops: [getBalance, setBalance]
      - port: idempotency
        ops: [get, put]
```

In this example:
- the block is allowed to talk to `ledger` and `idempotency` overall (`effects.allow`)
- the `ExecuteWithdraw` operation is only using the `ledger` portion (`uses.allow`)

## Where the full details live

- Agent IR reference: [IR_REFERENCE.md](../bear-package/.bear/agent/ref/IR_REFERENCE.md)
- Canonical IR spec (bear-cli maintainer): [docs/context/ir-spec.md](../context/ir-spec.md)

