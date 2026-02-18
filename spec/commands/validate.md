# `bear validate` (v0)

## Command
`bear validate <path>`

## Behavior
- On success:
  - prints canonical normalized YAML to stdout
  - exits 0
- On failure:
  - prints a single error line to stderr
  - exits non-zero

## Exit codes
Exit codes are defined centrally in `spec/commands/exit-codes.md`.
`bear validate` uses:
- `0`: valid
- `2`: schema/validation error
- `64`: usage error
- `74`: IO error
- `70`: internal/unexpected error

## Error line format
Validation errors (exit `2`) use:

`<category> at <path>: <CODE>: <message>`

Where:
- `category` is `schema` or `semantic`
- `path` is dot-separated with list indexes, for example `block.contract.inputs[2].type`
- `CODE` is a stable token (tests assert through this)

Other error prefixes:
- Usage errors (exit `64`): `usage: <CODE>: <message>`
- IO errors (exit `74`): `io: <CODE>: <message>`
- Internal errors (exit `70`): `internal: <CODE>: <message>`

## Failure Envelope (non-zero exits)
For every non-zero exit, `validate` appends the standard failure footer defined in `spec/commands/exit-codes.md`:
- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<deterministic-step>`

Common codes:
- `MISSING_FIELD`
- `UNKNOWN_KEY`
- `INVALID_ENUM`
- `INVALID_TYPE`
- `INVALID_VALUE`
- `DUPLICATE`
- `UNKNOWN_REFERENCE`
- `MULTI_DOCUMENT`
- `INVALID_YAML`
