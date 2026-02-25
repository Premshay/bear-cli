# TROUBLESHOOTING.md

Purpose:
- Deterministic troubleshooting router for BEAR gate failures.
- Consult only when command output is non-zero or blocked.

## Triage Router

1. Usage/args issue (`64`) -> fix invocation/arguments and rerun same command.
2. IR validation/schema/semantic issue (`2`) -> fix IR or policy syntax, then rerun validate/check.
3. Drift failure (`3`) -> regenerate deterministically (`compile` or `fix`), then rerun check.
4. Boundary bypass (`7`) -> move wiring/logic back into governed seams/roots, then rerun.
5. Undeclared reach or strict hygiene (`6`) -> declare boundary or remediate unexpected path, then rerun.
6. Project tests failed (`4`) -> fix implementation/tests; rerun check.
7. PR boundary expansion / `BOUNDARY_EXPANSION_DETECTED` (`5`) -> confirm intentional expansion and review process.
8. IO/git/runtime environment (`74`) -> resolve repo/path/lock/bootstrap and rerun.
9. Schema/path mismatch or missing routed docs -> suspect stale package sync, re-sync package, verify `.bear/agent/**` tree parity, rerun.
10. `SPEC_POLICY_CONFLICT` -> apply conflict checklist and escalate if criteria hold.
11. `CONTAINMENT_METADATA_MISMATCH` -> apply bounded compile-once repair flow, then escalate if still inconsistent.
12. Internal failure (`70`) -> capture output and report tool defect.

## Deterministic Remediation by Failure Class

1. `CODE=POLICY_INVALID`:
- fix `.bear/policy/*.txt` format/order/path exactness.

2. Drift lane (`exit 3`):
- use `bear fix` / `fix --all` for generated artifacts.
- or rerun compile for changed IR.

3. Boundary bypass lane (`exit 7`):
- remove direct impl usage from production seams.
- remove classloading reflection unless allowlisted.
- remove governed logic->governed impl service/module bindings.
- ensure generated entrypoint wiring uses non-null required ports.
- ensure generated-port adapters live only under governed roots.
- for cross-block adapters, either split by block package or use valid `_shared` marker contract.

4. Test failure lane (`exit 4`):
- fix business/test logic.
- for generated `*Impl.java` placeholder stubs, replace generated stub body fully.

5. Boundary expansion lane (`exit 5`, `pr-check`):
- treat as governance review signal, not random failure.
- verify `--base` selection first; `--base HEAD` can misclassify or hide intended delta unless explicitly instructed.

6. Schema/path mismatch or missing routed docs:
- rerun package sync from canonical source package.
- verify destination `.bear/agent/**` tree exactly matches source package tree.
- rerun the failing gate after parity is restored.

7. `SPEC_POLICY_CONFLICT`:
- apply conflict checklist in this file.
- if checklist confirms conflict, escalate and stop implementation edits.
- do not patch harness/policy/runtime files unless explicitly instructed.

8. `CONTAINMENT_METADATA_MISMATCH`:
- inspect containment metadata against intended mode evidence.
- run exactly one deterministic repair: `bear compile --all --project <repoRoot>`.
- re-check; if still mismatched, escalate with evidence.

## SPEC_POLICY_CONFLICT

Decision checklist:
1. Does the spec require behavior that would violate an explicit repo enforcement or BEAR contract rule?
2. Is changing that rule absent from the spec and not explicitly instructed by the user?
3. Can implementation proceed without changing forbidden infrastructure/policy/runtime surfaces?

Interpretation:
1. If `1=true` and `2=true` and `3=false`, classify as `SPEC_POLICY_CONFLICT`, escalate, and stop.
2. If not all criteria hold, treat as normal implementation/gate remediation, not conflict.

## CONTAINMENT_METADATA_MISMATCH

Decision checklist:
1. Collect intended mode evidence (single-block vs index-managed multi-block).
2. Inspect `build/generated/bear/config/containment-required.json` for consistency with intended mode.
3. If unexpected, run one repair only: `bear compile --all --project <repoRoot>`, then re-check.

Escalation threshold:
1. Escalate only if mismatch remains after the single deterministic repair.
2. Include intended mode evidence + pre/post containment snapshots in escalation.

## Forbidden Actions

1. Do not edit `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, or `bin/bear*` unless explicitly instructed.
2. Do not move impl seams to alternate roots or create duplicate shim copies in `_shared`.
3. Do not override containment excludes to force checks green.

## BOUNDARY_EXPANSION_DETECTED

Meaning:
1. `pr-check` detected boundary-affecting change relative to provided base.
2. This can be expected when adding/changing blocks, contracts, effects, idempotency, invariants, or governed adapter boundaries.

Success criteria:
1. Use correct base (merge-base against target branch, or provided SHA) and rerun if base was wrong.
2. Confirm classification matches intentional IR/implementation delta.
3. Report classification as expected or unintended in completion report with rationale.
4. Do not "eliminate" an intentional boundary expansion by hiding or reverting valid contract changes.

## Lock/IO Environment Branch

When lock signatures appear (for example `.zip.lck`, `Access is denied`, generated-file replacement lock):
1. Treat as tooling/environment IO issue first.
2. Do not mutate unrelated IR to match stale generated outputs.
3. Do not introduce workaround classes under `com.bear.generated.*`.
4. Fixed retry action 1: stop concurrent build daemons/processes that hold locks.
5. Fixed retry action 2: rerun the same failing command unchanged.
6. Fixed retry action 3: if containment is suspected, run one `bear compile --all --project <repoRoot>`, then rerun check.
7. Retry budget is max 2 failed retries.
8. If still failing after budget, stop and escalate with command outputs and lock evidence.

## Marker Handling Branch

1. Check marker `build/bear/check.blocked.marker` is advisory.
2. Continue fixing root cause; do not treat marker as completion evidence.
3. Use `bear unblock --project <repoRoot>` only to clear stale marker state.
4. For containment markers, rerun `compile` then `check` after fixing stale/missing marker causes.
