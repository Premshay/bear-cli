# CI Integration

BEAR ships a downstream CI wrapper under `.bear/ci/` so consuming repos can run deterministic governance gates without copying policy logic into workflow YAML.

Canonical packaged assets:
- `.bear/ci/bear-gates.ps1`
- `.bear/ci/bear-gates.sh`
- `.bear/ci/baseline-allow.json`
- `.bear/ci/README.md`

Canonical report artifact:
- `build/bear/ci/bear-ci-report.json`
- `schemaVersion=bear.ci.governance.v1`

## What The Wrapper Runs

The wrapper owns CI policy and base selection. BEAR still owns the governance facts.

Execution order:
1. `bear check --all --project . --blocks <path> --collect=all --agent`
2. `bear pr-check --all --project . --base <sha> --blocks <path> --collect=all --agent` when base resolution and `check` exit rules allow it

Skip rules:
- stop after `check` on exits `2`, `64`, `70`, `74`
- stop after `check` on unexpected exit `5`
- if base cannot be resolved, record `prCheck.status=not-run` with reason `BASE_UNRESOLVED`
- continue to `pr-check` after `check` exits `0`, `3`, `4`, `6`, `7`

In v1, `check` exit `6` is reported under `CI_GOVERNANCE_DRIFT` because the CI class vocabulary does not add a dedicated undeclared-reach token.

## Wrapper Modes

`enforce`:
- wrapper fails on any non-zero `check` or `pr-check`
- exception: `pr-check exit 5` may pass as `allowed-expansion` when `.bear/ci/baseline-allow.json` exactly matches resolved base SHA and observed boundary-expanding `deltaId` set

`observe`:
- wrapper still records both gate results
- wrapper swallows result lanes `3`, `4`, `5`, `6`, `7`
- wrapper still fails on `2`, `64`, `70`, `74`

Wrapper process exit contract:
- `0` for `pass`
- `0` for `allowed-expansion`
- `1` for `fail`

## Base Resolution

Priority:
1. explicit `--base-sha <sha>`
2. GitHub pull request event payload `pull_request.base.sha`
3. GitHub push event payload `before`
4. GitHub push fallback `HEAD~1` only when `before` is missing or all zeroes

If no base SHA can be resolved, `pr-check` is not run and the wrapper fails closed.

## Allow File

Path:
- `.bear/ci/baseline-allow.json`

Minimal shape:

```json
{
  "schemaVersion": "bear.ci.allow.v1",
  "entries": [
    {
      "baseSha": "abc123",
      "deltaIds": [
        "BOUNDARY_EXPANDING|ALLOWED_DEPS|ADDED|.:_shared:com.example:demo@1.0.0"
      ]
    }
  ]
}
```

Rules:
- only boundary expansion (`pr-check exit 5`) consults the allow file
- match is exact on both `baseSha` and the full boundary-expanding `deltaId` set
- missing, stale, extra, or mismatched entries fail in `enforce`
- if `extensions.prGovernance` is missing or unparsable on a boundary-expansion path, allow evaluation is unavailable and the wrapper fails closed

## Report Contract

Top-level fields:
- `schemaVersion`, `mode`, `resolvedBaseSha`, `commands[]`, `bearRaw`, `check`, `prCheck`, `allowEvaluation`, `decision`

`check` shape:
- `status="ran"`
- `exitCode`, `code`, `path`, `remediation`, `classes[]`

`prCheck` ran shape:
- `status="ran"`
- `exitCode`, `code`, `path`, `remediation`, `classes[]`, `deltas[]`, `governanceSignals[]`

`prCheck` not-run shape:
- `status="not-run"`
- `reason`
- `exitCode=null`, `code=null`, `path=null`, `remediation=null`
- `classes=[]`, `deltas=[]`, `governanceSignals=[]`

Allowed `reason` values:
- `CHECK_PRECONDITION_FAILURE`
- `BASE_UNRESOLVED`
- `UNEXPECTED_CHECK_EXIT`

`decision` values:
- `pass`
- `fail`
- `allowed-expansion`

The report also stores `bearRaw.checkAgentJson`, `bearRaw.prCheckAgentJson`, and deterministic stdout/stderr SHA-256 hashes so derived fields are auditable.

## Minimal Console Summary

Wrapper stdout stays compact:

```text
MODE=enforce DECISION=pass BASE=<sha>
CHECK exit=0 code=- classes=CI_NO_STRUCTURAL_CHANGE
PR-CHECK exit=0 code=- classes=CI_NO_STRUCTURAL_CHANGE
```

When `pr-check` is skipped:

```text
PR-CHECK NOT_RUN: BASE_UNRESOLVED
```

## GitHub Actions Examples

Ubuntu runner (`enforce`):

```yaml
- name: BEAR CI governance
  run: ./.bear/ci/bear-gates.sh --mode enforce
```

The bash wrapper is intended for bash environments with `pwsh` available. In local Windows shells without reliable PowerShell interop from bash, run `.bear/ci/bear-gates.ps1` directly.

Ubuntu runner (`observe`):

```yaml
- name: BEAR CI governance (observe)
  run: ./.bear/ci/bear-gates.sh --mode observe
```

Windows runner (`enforce`):

```yaml
- name: BEAR CI governance
  shell: powershell
  run: .\.bear\ci\bear-gates.ps1 --mode enforce
```

The wrapper assumes GitHub Actions event context by default. Other CI systems should pass `--base-sha <sha>` explicitly.

## Related

- [INSTALL.md](INSTALL.md)
- [QUICKSTART.md](QUICKSTART.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [commands-pr-check.md](commands-pr-check.md)
- [output-format.md](output-format.md)

