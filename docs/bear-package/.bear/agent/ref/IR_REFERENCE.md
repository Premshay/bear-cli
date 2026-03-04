# IR_REFERENCE.md

Purpose:
- Single authoritative BEAR IR reference.
- Canonical schema and rule quick reference.

Authority rule:
- If a shape/rule is not declared here, do not assume it.
- IR file = one block; block may contain multiple operations.

## Required Root Shape

```yaml
version: v1
block:
  ...
```

Required root keys:
- `version` (`v1`)
- `block`

## `block` Object

Required:
- `name`
- `kind` (`logic`)
- `operations` (non-empty)
- `effects`

Optional:
- `idempotency` (store capability only)
- `invariants` (allowed set)
- `impl`

## Operation Object

Required:
- `name`
- `contract.inputs`
- `contract.outputs`
- `uses.allow`

Optional:
- `idempotency` (`mode: use|none`)
- `invariants`

Forbidden:
- per-operation `impl`

Rules:
- operation names unique and trimmed
- field uniqueness is per operation only
- `uses.allow` must be subset of `block.effects.allow` (field-aware by port kind)

## Contract Fields

```yaml
contract:
  inputs:
    - name: requestId
      type: string
  outputs:
    - name: accepted
      type: bool
```

Types:
- `string`
- `decimal`
- `int`
- `bool`
- `enum`

## Effects / Uses Boundary

External port shape:

```yaml
- port: stateStore
  kind: external
  ops: [get, put]
```

Block-port shape:

```yaml
- port: transactionLog
  kind: block
  targetBlock: transaction-log
  targetOps: [AppendTransaction, GetTransactions]
```

Rules:
- block effects are authoritative boundary superset.
- `port` names must be unique across all entries (regardless of `kind`).
- `kind=external`:
  - `ops` required (non-empty, distinct)
  - `targetBlock`/`targetOps` forbidden
- `kind=block` in `effects.allow`:
  - `targetBlock` required
  - `targetOps` required (non-empty, distinct)
  - `ops` forbidden
- `kind=block` in `uses.allow`:
  - `targetBlock` forbidden
  - optional `targetOps` must be non-empty when present and subset of block-level `targetOps`

Index-aware graph rules (`compile|fix|check|pr-check`):
- `kind=block` target block and target ops are resolved against indexed IRs.
- block-port cycle graph is invalid.
- single-file mode with `kind=block` requires `--index` and tuple membership `(ir, projectRoot)` in index.

## Idempotency

Block capability:
```yaml
idempotency:
  store:
    port: idempotency
    getOp: get
    putOp: put
```

Operation usage:
```yaml
idempotency:
  mode: use
  key: requestId
```
or
```yaml
idempotency:
  mode: use
  keyFromInputs: [walletId, requestId]
```
or
```yaml
idempotency:
  mode: none
```

Rules:
- `mode=use` requires block idempotency store
- `mode=use` requires exactly one of `key` / `keyFromInputs`
- key fields must exist in that operation inputs
- operation `uses` must include idempotency `getOp` and `putOp`
- `mode=none` forbids key fields

## Invariants

Block-level `invariants` define allowed rules; operation invariants choose subset.

```yaml
invariants:
  - kind: non_negative
    scope: result
    field: balanceCents
    params: {}
```

Kinds:
- `non_negative`
- `non_empty`
- `equals`
- `one_of`

Rules:
- `scope` must be `result`
- `field` must reference operation outputs
- operation invariants must be subset of block allowed set by deterministic fingerprint

## Impl Allowed Deps (Optional)

```yaml
impl:
  allowedDeps:
    - maven: com.fasterxml.jackson.core:jackson-databind
      version: 2.17.2
```

Rules:
- exact `groupId:artifactId`
- pinned exact version
- duplicate GA invalid

## Generator Contract

Generated artifacts:
- shared: `BearValue`, `<Block>Logic`, ports, `<Block>Impl` stub
- per operation:
  - `<Block>_<Operation>Request`
  - `<Block>_<Operation>Result`
  - `<Block>_<Operation>` wrapper

Block-port generation:
- for `kind=block` effects, source block gets generated block-port interfaces with single `call(BearValue)` dispatch.
- dispatch requires input key `op` matching one of configured `targetOps`.
- generated block clients route `call(...)` to target typed wrappers.

Wrapper behavior:
- idempotency and invariants are wrapper-owned
- idempotency key includes operation identity segment

## Minimal Example

```yaml
version: v1
block:
  name: Account
  kind: logic
  operations:
    - name: Withdraw
      contract:
        inputs:
          - name: accountId
            type: string
          - name: amountCents
            type: int
          - name: requestId
            type: string
        outputs:
          - name: balanceCents
            type: int
          - name: txSeq
            type: int
      uses:
        allow:
          - port: accountStore
            kind: external
            ops: [get, update]
          - port: transactionLog
            kind: block
            targetOps: [AppendTransaction]
          - port: idempotency
            kind: external
            ops: [get, put]
      idempotency:
        mode: use
        keyFromInputs: [accountId, requestId]
      invariants:
        - kind: non_negative
          field: balanceCents
  effects:
    allow:
      - port: accountStore
        kind: external
        ops: [get, update]
      - port: transactionLog
        kind: block
        targetBlock: transaction-log
        targetOps: [AppendTransaction]
      - port: idempotency
        kind: external
        ops: [get, put]
  idempotency:
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      field: balanceCents
```

## Commands

For each changed IR:
1. `bear validate <ir-file>`
2. `bear compile <ir-file> --project <repoRoot> [--index <path>]` or `bear compile --all --project <repoRoot>`
3. `bear fix <ir-file> --project <repoRoot> [--index <path>]` (or `fix --all`)
4. `bear check <ir-file> --project <repoRoot> [--strict-hygiene] [--index <path>]` (or `check --all`)
5. `bear pr-check <ir-file> --project <repoRoot> --base <ref> [--index <path>]` (or `pr-check --all`)

