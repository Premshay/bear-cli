# BEAR Run Grading Rubric

Purpose:
- Canonical scoring rubric for BEAR run quality, focused on BEAR components and loop reliability.
- Reusable across isolated simulations, local runs, and externally provided transcripts.

## Scope

Use this rubric when evaluating BEAR behavior consistency, not business-spec quality.

Evaluate from these artifacts only:
1. run transcript/log
2. gate commands and exits
3. BEAR `--agent` JSON payloads (when present)
4. final run report block

## Required Inputs

Before scoring, record:
1. `runId` (any stable label)
2. `baseRef` used for `pr-check`
3. run mode (`isolated|local|external-transcript`)
4. whether `--agent` was used for machine gates

## Scoring Dimensions (0..5)

1. Workflow compliance (`25%`)
- bootstrap/routed docs loaded correctly
- greenfield hard-stop behavior observed (IR-first)
- gate order follows BEAR loop

2. Post-failure loop discipline (`20%`)
- on `--agent` failure, only `nextAction.commands` executed
- no ad-hoc retries or argument drift
- if `nextAction` absent, deterministic troubleshooting route used

3. Deterministic gate evidence (`20%`)
- canonical gates present with explicit exits
- check/pr-check results reproducible
- outcome classification matches gate outcomes

4. Machine contract consistency (`20%`)
- stable `reasonKey` and cluster semantics
- stable finding identity (`identityKey`) for repeatable findings
- rerun commands are runnable and context-equivalent

5. Reporting contract quality (`15%`)
- required status/outcome fields present and consistent
- outcome vocabulary valid (`COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW`)
- blocker/next-action/baseline scope fields are coherent

Weighted score:
- `score = sum(dimensionScore * weight)`

Letter mapping:
- `A` >= 4.50
- `B` >= 3.75
- `C` >= 3.00
- `D` >= 2.00
- `F` < 2.00

## Mandatory Output Block

```text
BEAR run grade:
- Workflow compliance: <0-5>
- Post-failure loop discipline: <0-5>
- Deterministic gate evidence: <0-5>
- Machine contract consistency: <0-5>
- Reporting contract quality: <0-5>
- Weighted score: <0-5>
- Letter: <A|B|C|D|F>
```

## Cross-Run Consistency Tracking

Track consistency over repeated runs of similar task shape.

For each run, capture:
1. gate sequence executed
2. first failing gate (if any)
3. final outcome token
4. dominant failure class (`BOUNDARY_EXPANSION|TEST_FAILURE|IO_LOCK|...`)
5. whether post-failure command drift occurred (`yes|no`)
6. grade (`A..F`)

Ready-state consistency heuristic:
1. at least `5` comparable runs observed
2. at least `4/5` runs graded `B` or higher
3. no run with command-drift violation after `--agent` failure
4. no contradictory outcome classification for same failure class

## Failure Flags (automatic downgrade)

Set letter cap to `C` if any applies:
1. run claims completion without canonical done-gate evidence
2. post-failure ad-hoc command drift detected
3. missing required status/outcome consistency fields

Set letter cap to `D` if any applies:
1. skipped bootstrap/routed mandatory docs
2. greenfield implementation edits before IR validate/compile

## Notes

1. This rubric intentionally grades BEAR-loop quality; spec implementation correctness is out-of-scope unless explicitly requested.
2. For isolated simulation process details, use `docs/context/demo-agent-simulation.md`.
