# `bear fix` (v1)

## Command
`bear fix <ir-file> --project <path>`

`bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]`

`bear fix` is a deterministic generated-artifact repair command.
It regenerates BEAR-owned artifacts from IR without mutating user-owned implementation files.

## Scope
- Includes:
  - parse + validate + normalize IR
  - deterministic regeneration of BEAR-owned generated artifacts
- Excludes:
  - drift comparison/reporting (`check` owns this)
  - undeclared-reach scanning (`check` owns this)
  - project test execution (`check` owns this)
  - PR/base boundary classification (`pr-check` owns this)

## Ownership Contract
- BEAR-owned generated paths are repaired/regenerated.
- User-owned implementation files (for example `*Impl.java` under `src/main/java`) are preserved.

## Exit codes
Exit codes are defined centrally in `spec/commands/exit-codes.md`.
- `0`: fix succeeded
- `2`: schema/semantic IR validation error
- `64`: usage error
- `74`: IO error
- `70`: internal/unexpected error

For `--all`, final exit code uses explicit severity-rank aggregation:
- `70` > `74` > `64` > `2` > `0`

## Success output
- single-block success prints:
  - `fix: OK`

## Failure Envelope (non-zero exits)
For every non-zero exit, `fix` appends the standard failure footer defined in `spec/commands/exit-codes.md`:
- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<deterministic-step>`

Envelope invariants:
- emitted exactly once
- last three stderr lines
- no stderr output after `REMEDIATION=...`

For `--all` aggregated failures:
- `CODE=REPO_MULTI_BLOCK_FAILED`
- `PATH=bear.blocks.yaml`
- `REMEDIATION=Review per-block results above and fix failing blocks, then rerun the command.`

## `--all` Mode

Index source:
- default: `<repoRoot>/bear.blocks.yaml`
- override: `--blocks <path>` (repo-relative)
- schema and constraints: `spec/repo/block-index.md`

Selection:
- default: all index blocks
- `--only <csv>`: selected names only; unknown names fail with usage error (`64`)
- disabled selected blocks render as:
  - `STATUS: SKIP`
  - `REASON: DISABLED`

Execution:
- selected enabled blocks run in canonical `name` order
- default is continue-all (all selected enabled blocks are evaluated)
- `--fail-fast`:
  - after first block failure, remaining selected enabled blocks are rendered as:
    - `STATUS: SKIP`
    - `REASON: FAIL_FAST_ABORT`

Strict orphan mode:
- default (no strict flag): managed-root marker guard only
  - scan `<managedRoot>/build/generated/bear/surfaces/*.surface.json`
  - orphan marker under managed root fails
  - legacy marker `<managedRoot>/build/generated/bear/bear.surface.json` fails
- `--strict-orphans`: repo-wide marker scan (`**/build/generated/bear/surfaces/*.surface.json`)
  plus repo-wide legacy marker scan (`**/build/generated/bear/bear.surface.json`)
- with `--only`, strict mode remains repo-wide (strict means strict)

Per-block output section (deterministic):
- `BLOCK: <name>`
- `IR: <path>`
- `PROJECT: <path>`
- `STATUS: PASS|FAIL|SKIP`
- `EXIT_CODE: <n>`
- on FAIL:
  - `CATEGORY: <...>`
  - `BLOCK_CODE: <...>`
  - `BLOCK_PATH: <...>`
  - `DETAIL: <single-line>`
  - `BLOCK_REMEDIATION: <single-line>`
- on SKIP:
  - `REASON: DISABLED|FAIL_FAST_ABORT`

Summary section:
- `SUMMARY:`
- `<N> blocks total`
- `<C> checked`
- `<P> passed`
- `<F> failed`
- `<S> skipped`
- `FAIL_FAST_TRIGGERED: true|false`
- `EXIT_CODE: <n>`
