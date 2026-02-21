# BEAR IR v1 Spec (Canonical)

## Purpose
BEAR IR is a strict, machine-checkable contract for one logic block.
It is intentionally constrained so validate/normalize/compile/check behavior stays deterministic.

## Scope Lock (v1.2)
v1 supports:
- one `logic` block per IR file
- structured effect ports
- contract inputs/outputs
- idempotency with either `key` or `keyFromInputs`
- invariant rules with explicit `kind/scope/field/params`
- optional implementation dependency allow-list (`impl.allowedDeps`)

v1 does not support:
- block graphs/composition DSL
- behavior DSL (`requires/ensures`)
- transaction semantics / cross-port atomicity
- undeclared dynamic semantics outside declared IR fields

## Model
Root object:
- `version` (required, must be `v1`)
- `block` (required)

`block` fields:
- `name` (required)
- `kind` (required, must be `logic`)
- `contract` (required)
- `effects` (required)
- `idempotency` (optional)
- `invariants` (optional)
- `impl` (optional)

`contract` fields:
- `inputs` (required non-empty list)
- `outputs` (required non-empty list)

Field shape:
- `name` (required)
- `type` (required enum: `string`, `decimal`, `int`, `bool`, `enum`)

`effects` fields:
- `allow` (required list, may be empty)

Effect-port shape:
- `port` (required)
- `ops` (required list, may be empty)

## Idempotency Schema
When `block.idempotency` is present:
- exactly one of:
  - `key` (single input field name)
  - `keyFromInputs` (non-empty ordered list of input field names)
- `store` (required):
  - `port`
  - `getOp`
  - `putOp`

Validation:
- referenced key fields must exist in `contract.inputs`
- `keyFromInputs` values must be unique
- `store.port` must exist in `effects.allow[*].port`
- `store.getOp` / `store.putOp` must exist under that port

## Invariant Schema
`block.invariants` is a list of rules.
Each rule shape:
- `kind` (required enum)
- `scope` (required in canonical form, must be `result`)
- `field` (required output field name)
- `params` (optional map; kind-specific)

Supported kinds:
- `non_negative`
- `non_empty`
- `equals`
- `one_of`

Kind rules:
- `non_negative`
  - output type must be `int` or `decimal`
  - no params allowed
- `non_empty`
  - output type must be `string`
  - no params allowed
- `equals`
  - requires `params.value` (string)
  - `params.values` forbidden
- `one_of`
  - requires non-empty `params.values` (unique string list)
  - `params.value` forbidden

## `impl.allowedDeps`
Optional:
- `impl.allowedDeps` list entries:
  - `maven` (exact `groupId:artifactId`)
  - `version` (pinned exact version)

Validation:
- no wildcard/range forms
- duplicate `maven` entries are invalid

## Validation Rules (Strict)
- fail on unknown keys at every level
- fail on invalid enums
- fail on invalid references
- input/output names unique
- port names unique
- ops unique within each port
- idempotency exactly-one-of (`key`, `keyFromInputs`)
- invariant field must reference declared output
- invariant scope must be `result`

## Deterministic Normalization
Canonical form:
- root key order: `version`, `block`
- `block` key order: `name`, `kind`, `contract`, `effects`, `idempotency`, `invariants`, `impl`
- sort inputs/outputs by `name`
- sort ports by `port`
- sort ops within each port
- preserve invariant list order (IR order)
- sort `impl.allowedDeps` by `maven`
- emit canonical invariant shape (`scope` + explicit `params` object)

## Wrapper-Owned Semantics (Contract)
Generated wrappers own semantic enforcement:
- idempotency get/replay/put is wrapper-owned
- invariants are checked on:
  - fresh logic result
  - replay-decoded result

Logic implementations are not the semantic authority for these checks.

## Canonical Example (excerpt)
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
      - port: idempotency
        ops: [get, put]
  idempotency:
    key: txId
    store:
      port: idempotency
      getOp: get
      putOp: put
  invariants:
    - kind: non_negative
      scope: result
      field: balance
      params: {}
```

## Notes
- IR defines semantics; targets must enforce them or `check` fails deterministically.
- v1 does not prove cross-port atomicity.
