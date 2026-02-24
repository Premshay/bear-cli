# bear pr-check

## Purpose

Run deterministic PR governance checks:
- classify normalized IR deltas versus merge-base (boundary expansion signaling)
- enforce generated-port implementation containment boundaries
- provide completion counterpart to `check --all` for local agent evidence before CI enforcement

## Invocation forms

```text
bear pr-check <ir-file> --project <path> --base <ref>
bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]
```

## Inputs and flags

- Single mode requires `<ir-file>`, `--project`, and `--base`.
- `<ir-file>` must be repo-relative.
- `--all` mode uses index selection and optional `--blocks`, `--only`, `--strict-orphans`.
- completion workflows should pair:
  - `bear check --all --project <repoRoot>`
  - `bear pr-check --all --project <repoRoot> --base <ref>`
- CI `pr-check` remains authoritative remote enforcement; local `pr-check` is expected for fast feedback.

## Output schema and ordering guarantees

- Delta lines to `stderr`:
  - `pr-delta: <CLASS>: <CATEGORY>: <CHANGE>: <KEY>`
- Governance signal lines to `stdout` (informational, non-failing):
  - `pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: <relative/path>: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
- Port-impl containment lines (when violated):
  - `pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <relative/path>: KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <interfaceFqcn> -> <implClassFqcn>`
  - `pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <relative/path>: KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
  - `pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <relative/path>: KIND=MARKER_MISUSED_OUTSIDE_SHARED: <implClassFqcn>`
- Boundary verdict:
  - `stderr`: `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`
  - `stdout`: `pr-check: OK: NO_BOUNDARY_EXPANSION`
- Deterministic sort precedence for class, category, change, key.
- Port-impl containment findings are deterministically sorted by path, rule, then detail.
- Governance signal lines are deterministically sorted by path, impl class, then package CSV.
- `pr-check --all` success output may include an aggregated `GOVERNANCE SIGNALS:` section after block sections and before `SUMMARY`.
- Multi-block marker contract:
  - marker text is exact: `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
  - valid only for files under `src/main/java/blocks/_shared/**`
  - marker must be within 5 non-empty lines above class declaration
- Non-zero exits append failure footer as last three `stderr` lines.

Implementation note:
- `pr-check` acquires wiring manifests using deterministic temp staging + wiring-only generation.
- It does not require full compile output to be present in project working tree.

## Exit codes emitted

- `0` no boundary-expanding deltas
- `5` boundary-expanding deltas found
- `7` structural bypass (`CODE=BOUNDARY_BYPASS`)
- `2` validation failure
- `64` usage failure
- `70` internal failure
- `74` IO or git failure

## Deterministic failure footer

Non-zero exits append:

- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<step>`

For aggregated `--all` non-zero failures, footer code is `REPO_MULTI_BLOCK_FAILED`.

## Remediation pointers

- [troubleshooting.md#boundary_expansion](troubleshooting.md#boundary_expansion)
- [troubleshooting.md#port_impl_outside_governed_root](troubleshooting.md#port_impl_outside_governed_root)
- [troubleshooting.md#multi_block_port_impl_forbidden](troubleshooting.md#multi_block_port_impl_forbidden)
- [troubleshooting.md#io_git](troubleshooting.md#io_git)
- [troubleshooting.md#ir_validation](troubleshooting.md#ir_validation)

## Related

- [MODEL.md](MODEL.md)
- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

