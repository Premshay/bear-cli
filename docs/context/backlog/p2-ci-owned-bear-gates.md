# P2 Backlog: CI Boundary Governance + PR Telemetry (Grounded + Extended)

## Summary
Ship one CI governance feature that is explicit about:
1. what BEAR already provides today
2. what this feature adds

The feature includes both:
1. CI-owned gating/routing orchestration
2. BEAR telemetry extension in `pr-check --agent`

## Current Baseline (Already Implemented)
1. `pr-check` deterministically emits `pr-delta` lines and exits `5` on boundary expansion.
2. `pr-check --all` emits deterministic per-block sections (`CLASSIFICATION`, `DELTA`, `SUMMARY`) plus repo-level shared-policy deltas.
3. Existing deterministic delta vocabulary:
   - `PrClass`: `BOUNDARY_EXPANDING|ORDINARY`
   - `PrCategory`: `SURFACE|PORTS|ALLOWED_DEPS|OPS|IDEMPOTENCY|CONTRACT|INVARIANTS`
   - `PrChange`: `CHANGED|ADDED|REMOVED`
4. `--agent` exists (`schemaVersion=bear.nextAction.v1`) and is JSON-only on stdout.
5. Current `--agent` payload is diagnostics-oriented (`problems`, `clusters`, `nextAction`) and does not expose full pass-path governance telemetry.

## Source Of Truth
1. BEAR exits, failure footer (`CODE/PATH/REMEDIATION`), and `pr-delta`/governance signal lines are authoritative facts.
2. `extensions.prGovernance` is a deterministic projection of those facts.
3. Wrapper `classes[]` and final `decision` are derived artifacts and must be reproducible from:
   - BEAR outputs
   - wrapper policy mode
   - allow-file state

## Additions In This Feature (New Work)
1. Canonical CI entrypoint scripts:
   - `ci/bear-gates.sh`
   - `ci/bear-gates.ps1`
2. Canonical CI workflow template using the entrypoint.
3. Deterministic boundary-allow mechanism (committed file, exact-match semantics):
   - `.bear/ci/baseline-allow.json`
4. `pr-check --agent` telemetry extension (single and `--all`) in v1:
   - keep top-level schema `bear.nextAction.v1`
   - add deterministic payload under `extensions.prGovernance`
   - include pass and fail paths
5. Deterministic wrapper CI report artifact:
   - `build/bear/ci/bear-ci-report.json`
   - schema `bear.ci.governance.v1`

## Decision Locks
1. Existing BEAR command exits/footers stay authoritative (`0,2,3,4,5,6,7,64,70,74`).
2. No regression for existing non-`--agent` output contracts.
3. Telemetry extension is additive and deterministic.
4. Explicit blocking contract:
   - BEAR may return non-zero.
   - wrapper mode determines whether CI job fails.

## Base Selection Ownership (Locked)
1. Wrapper owns base resolution and must pass a deterministic commit SHA as `--base <sha>`.
2. CLI does not infer provider branch context; it only computes merge-base using provided `--base` and `HEAD`.
3. Wrapper base rules:
   - PR: pinned target base SHA from event payload.
   - main push: provider `before` SHA, fallback `HEAD~1` only when unavailable.

## Delta Vocabulary vs CI Classes (No Dual Truth)
1. Canonical delta vocabulary remains `PrClass/PrCategory/PrChange`.
2. Wrapper-facing CI classes are derived only from exits + deltas + signals.
3. CI classes are not an additional source of truth.

## CI Classes (Derived, Namespaced)
1. `CI_NO_STRUCTURAL_CHANGE`
2. `CI_BOUNDARY_EXPANSION`
3. `CI_DEPENDENCY_POWER_EXPANSION`
4. `CI_POLICY_BYPASS_ATTEMPT`
5. `CI_GOVERNANCE_DRIFT`
6. `CI_TEST_FAILURE`
7. `CI_IO_GIT_ERROR`
8. `CI_VALIDATION_OR_USAGE_ERROR`
9. `CI_INTERNAL_ERROR`

Deterministic mapping notes:
1. `CI_BOUNDARY_EXPANSION`: `exit 5` and/or boundary-expanding deltas.
2. `CI_DEPENDENCY_POWER_EXPANSION`: boundary-expanding deltas where category is `ALLOWED_DEPS`.
3. `CI_POLICY_BYPASS_ATTEMPT`: `exit 7` and/or `BOUNDARY_BYPASS` findings.
4. `CI_GOVERNANCE_DRIFT`: `check` drift lane (`exit 3`).
5. `CI_TEST_FAILURE`: `check` test lane (`exit 4`).

## `pr-check --agent` Extension Contract (v1)
Add under `extensions.prGovernance`:
1. `schemaVersion` (`bear.pr-governance.v1`)
2. `hasDeltas` (boolean)
3. `hasBoundaryExpansion` (boolean)
4. `classifications[]` (derived `CI_*` tokens, sorted)
5. `deltas[]` entries:
   - `class`, `category`, `change`, `key`, `deltaId`
6. `governanceSignals[]` (sorted stable signal entries)

`deltaId` lock:
- `deltaId = <class>|<category>|<change>|<key>`

Availability lock:
1. for successful telemetry computation, `extensions.prGovernance` must be present.
2. when telemetry is unavailable, `extensions.prGovernance` must be absent (not empty).

Determinism rules:
1. no absolute paths in extension fields.
2. arrays sorted with canonical ordering semantics.
3. identical git/input state yields byte-stable extension output.

## CI Policy Modes (Wrapper Behavior)
`enforce`:
1. wrapper returns failure on any non-zero `check --all` or `pr-check --all`, except allow-file-approved boundary expansion.
2. `2/64/70/74` always fail.

`observe`:
1. wrapper always returns success for governance/result lanes `3/4/5/6/7`.
2. wrapper still fails on infra/config/usage/internal lanes `2/64/70/74`.
3. wrapper always records underlying BEAR exit codes and derived classes in report.

## Allow-File Contract (v1)
1. boundary expansion (`exit 5`) fails by default in `enforce`.
2. pass only when allow entries exactly match:
   - resolved base SHA
   - observed `deltaId` set
3. missing/extra/stale mismatches are hard failures.
4. label-only approval is not baseline v1.

## Wrapper CI Report Contract (`bear.ci.governance.v1`)
Minimal fields:
1. `schemaVersion`
2. `mode`
3. `resolvedBaseSha`
4. `commands[]`
5. `bearRaw`:
   - `checkAgentJson` (optional)
   - `prCheckAgentJson` (optional)
   - `checkStdoutHash`, `checkStderrHash`, `prCheckStdoutHash`, `prCheckStderrHash`
6. `check` (`exitCode`, `code`, `path`, `remediation`, `classes[]`)
7. `prCheck` (`exitCode`, `code`, `path`, `remediation`, `classes[]`, `deltas[]`, `governanceSignals[]`)
8. `decision` (`pass|fail|allowed-expansion`)

Audit lock:
- derived decision fields must be reproducible from `bearRaw` + mode + allow-file.

## Acceptance Criteria
1. Existing non-agent CLI contracts remain unchanged.
2. `pr-check --agent` emits deterministic `extensions.prGovernance` on pass and fail paths.
3. Entry-point scripts enforce deterministic base selection and command logging.
4. `enforce` blocks per contract; boundary expansion passes only by exact allow-file match.
5. `observe` follows explicit swallow/fail rules (`3/4/5/6/7` swallow, `2/64/70/74` fail).
6. CI output/report always includes executed commands, resolved base SHA, underlying exit lane, and `CODE`.

## Deliverables
1. `ci/bear-gates.(ps1|sh)`
2. `.github/workflows/bear-gates.yml` template (+ optional observe-mode snippet)
3. `docs/public/CI_INTEGRATION.md`
4. tests for:
   - base-selection normalization
   - allow-file deterministic matching by `deltaId`
   - `extensions.prGovernance` schema + deterministic ordering/presence rules
   - deterministic class mapping from exits/deltas/signals
   - wrapper report reproducibility from raw outputs

## Optional v1.1
1. dedicated native telemetry flag (`--format ci-json`) if still useful after extension rollout
2. SARIF/check-run adapters from same stable class tokens
3. report-contract lint gate wiring (`RunReportLint`)
