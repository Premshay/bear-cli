# BEAR IR v0 Spec (Canonical)

## Purpose
BEAR IR is a rigid, machine-checkable representation for one logic block.
It is intentionally limited and must remain deterministic to validate, normalize, and compile.

## Scope Lock
v0 supports:
- one `logic` block per IR file
- structured effect ports
- contract inputs/outputs
- idempotency by key
- invariant template `non_negative(field=<outputField>)`

v0 does not support:
- capability blocks in IR
- block graphs or block-to-block composition
- behavior DSL
- requires/ensures language
- state delta modeling
- infrastructure simulation

## Model
Root object:
- `block`

`block` fields:
- `name` (string, required)
- `kind` (enum, required, only `logic`)
- `contract` (object, required)
- `effects` (object, required)
- `idempotency` (object, optional)
- `invariants` (array, optional)

`contract` fields:
- `inputs` (array of fields, required)
- `outputs` (array of fields, required)

Field shape:
- `name` (string, required)
- `type` (enum, required; v0 primitives only)

`effects` fields:
- `allow` (array of effect ports, required)

Effect port shape:
- `port` (string, required)
- `ops` (array of operation names, required)

`idempotency` fields:
- `key` (string, required, must reference an input field name)
- `store` (object, optional, if present must reference declared `effects.allow` port/op)

Invariant shape:
- `type` (enum, required, only `non_negative`)
- `field` (string, required, must reference an output field name)

## Validation Rules (Strict)
- fail on unknown keys at any level
- fail on invalid enum values
- fail on invalid references
- input names must be unique
- output names must be unique
- port names must be unique
- ops must be unique within each port
- `idempotency.key` must reference an input field
- invariant `field` must reference an output field

## Deterministic Normalization
Canonical form must:
- sort inputs by `name`
- sort outputs by `name`
- sort ports by `port`
- sort ops within each port
- sort invariants deterministically
- emit canonical key order

## Example (Shape Only)
```yaml
block:
  name: Withdraw
  kind: logic
  contract:
    inputs:
      - name: accountId
        type: string
      - name: amount
        type: decimal
      - name: txId
        type: string
    outputs:
      - name: balance
        type: decimal
  effects:
    allow:
      - port: ledger
        ops: [getAccount, putAccount]
      - port: idempotencyStore
        ops: [getByKey, putByKey]
  idempotency:
    key: txId
    store:
      port: idempotencyStore
      op: putByKey
  invariants:
    - type: non_negative
      field: balance
```

## Notes
- This spec is structural, not behavioral.
- Do not expand IR expressiveness in v0.
