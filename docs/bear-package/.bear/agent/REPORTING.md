# REPORTING.md

Purpose:
- Canonical run report schema.
- Mechanically checkable governance-signal disposition contract.

## Required Fields

Run report MUST include:
1. `Request summary: <one line>`
2. `Block decision: updated=<...> added=<...>`
3. `Decomposition evidence: <single-block rationale OR per-block spec citations>`
4. `Decomposition mode: single|multi`
5. `Decomposition reason: default|trigger:<canonical_name>|spec_explicit`
6. `Blocks added: [...]`
7. `IR delta: <files + boundary notes>`
8. `Implementation delta: <files>`
9. `Tests delta: <files>`
10. `Gate results:`
11. `- bear check --all --project <repoRoot> => <exit>`
12. `- bear pr-check --all --project <repoRoot> --base <ref> => <exit>`
13. `Gate run order: <ordered list of executed gates>`
14. `Run outcome: COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW`
15. `Required next action: <...>` (required when `Run outcome` is `BLOCKED` or `WAITING_FOR_BASELINE_REVIEW`)
16. `Gate blocker: IO_LOCK | TEST_FAILURE | BOUNDARY_EXPANSION | OTHER`
17. `Stopped after blocker: yes|no`
18. `First failing command: <exact command line>`
19. `First failure signature: <one copied verbatim line>`
20. `PR base used: <ref>`
21. `PR base rationale: <merge-base against target branch OR user-provided base SHA>`
22. `PR classification interpretation: <expected|unintended> - <brief rationale>`
23. `Baseline review scope: <required for WAITING_FOR_BASELINE_REVIEW; must include bear.blocks.yaml and spec/*.bear.yaml>`
24. `Constraint conflicts encountered: none|<list>`
25. `Escalation decision: none|<reason>`
26. `Containment sanity check: pass|fail|n/a - <evidence>`
27. `Infra edits: none|<list>`
28. `Unblock used: no|yes - <reason>`
29. `Gate policy acknowledged: yes|no`
30. `Final git status: <git status --short summary>`
31. `GOVERNANCE_SIGNAL_DISPOSITION`
32. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none|<count>`
33. `JUSTIFICATION: <required when count > 0>`
34. `TRADEOFF: <required when count > 0>`

## Outcome Rules

1. Both gate exits are `0` -> `Run outcome` MUST be `COMPLETE`.
2. `Run outcome` MUST be `WAITING_FOR_BASELINE_REVIEW` only when `GREENFIELD_BASELINE_WAITING_SEMANTICS` criteria are all true.
3. Any other non-zero `pr-check` result -> `Run outcome` MUST be `BLOCKED`.
4. If `Run outcome` is `BLOCKED` or `WAITING_FOR_BASELINE_REVIEW`, `Required next action` is mandatory.
5. Do not present `BLOCKED` or `WAITING_FOR_BASELINE_REVIEW` runs as completion.
6. Gate policy acknowledged must reflect: completion requires both repo-level gates green.
7. If `Gate blocker` is `IO_LOCK`, `Stopped after blocker` MUST be `yes`.
8. If `pr-check` prints `BOUNDARY_EXPANSION_DETECTED` but exit is not `5`, classify `Gate blocker` as `OTHER` and stop.
9. For this anomaly, `First failure signature` must include both marker text and observed exit code.

## GREENFIELD_BASELINE_WAITING_SEMANTICS

Use `Run outcome: WAITING_FOR_BASELINE_REVIEW` only when all are true:
1. run is greenfield baseline (`spec/*.bear.yaml` newly introduced in this PR),
2. `pr-check` fails with `BOUNDARY_EXPANSION_DETECTED`,
3. expansion corresponds to newly introduced blocks/contracts/ports in this PR.

Deterministic pairing:
1. `Gate blocker: BOUNDARY_EXPANSION`
2. `Run outcome: WAITING_FOR_BASELINE_REVIEW`

Non-applicability:
1. do not use this outcome for unexpected expansion in non-greenfield repos.
2. do not use this outcome for unrelated failures.

## Blocker And Anomaly Reporting

1. For policy/tool/process anomalies, set `Gate blocker: OTHER`.
2. For `PR_CHECK_EXIT_ENVELOPE_ANOMALY`, stop immediately and capture exact marker + observed exit in `First failure signature`.
3. `Gate blocker`, `Stopped after blocker`, `First failing command`, and `First failure signature` are always required.
4. For process/preflight anomalies, use signature format: `PROCESS_VIOLATION|<label>|<evidence>`.
5. If no command failed because failure occurred at preflight observation time, set:
- `First failing command: none (preflight)`
- `First failure signature: PROCESS_VIOLATION|<label>|<missing/evidence>`

## Recommended Verification Notes (Optional)

Optional field:
1. `Recommended verification notes: <summary>`

Guidance:
1. For ordered/filtered/structured outputs, prefer parsed property assertions over substring-only checks.
2. This section is advisory and must not be treated as a required gate/report field.

## Governance-Signal Disposition Rules

1. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none` is valid when no governance signal lines exist.
2. If governance signal count is non-zero, both `JUSTIFICATION` and `TRADEOFF` are required.
3. Missing disposition block, mismatched count, or missing required fields means report is incomplete.
4. `PR base used` and `PR base rationale` are mandatory; defaulting to `HEAD` without explicit instruction is invalid.
5. `PR classification interpretation` is mandatory and must state whether the classification is expected or unintended for this change.
6. `Gate blocker`, `Stopped after blocker`, `First failing command`, `First failure signature`, `Constraint conflicts encountered`, `Escalation decision`, `Containment sanity check`, `Infra edits`, `Unblock used`, `Gate policy acknowledged`, `Gate run order`, and `Final git status` are mandatory.

## Count Rule (Frozen)

`<count>` equals:
- number of `MULTI_BLOCK_PORT_IMPL_ALLOWED` governance signal lines emitted by:
- `bear pr-check --all --project <repoRoot> --base <ref>`
- for the exact completion run being reported.
- Copy this count from the `pr-check` output of that exact completion run; do not infer.

## Minimal COMPLETE Example

```text
Request summary: Add transfer fee invariants to existing withdrawal flow
Block decision: updated=withdraw added=none
Decomposition evidence: single block retained; no new lifecycle/effect/authority/state split reason in spec
IR delta: spec/withdraw.bear.yaml (invariants updated)
Implementation delta: src/main/java/blocks/withdraw/impl/WithdrawImpl.java
Tests delta: src/test/java/blocks/withdraw/WithdrawImplTest.java
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base origin/main => 0
Gate run order: bear check --all --project . -> bear pr-check --all --project . --base origin/main
Run outcome: COMPLETE
Gate blocker: OTHER
Stopped after blocker: no
First failing command: none
First failure signature: none
PR base used: origin/main
PR base rationale: target branch merge-base reference for this completion run
PR classification interpretation: expected - new boundary declarations were intentional and match IR delta
Constraint conflicts encountered: none
Escalation decision: none
Containment sanity check: pass - containment diagnostics were not needed for this run
Infra edits: none
Unblock used: no - not needed
Gate policy acknowledged: yes
Final git status: clean (no tracked or untracked changes)
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```

## Minimal WAITING_FOR_BASELINE_REVIEW Example

```text
Request summary: Initial greenfield wallet baseline
Block decision: updated=none added=create-wallet,deposit-to-wallet,withdraw-from-wallet,get-wallet-balance,get-wallet-statement
Decomposition evidence: multiple externally visible operations without explicit command-router contract
Decomposition mode: multi
Decomposition reason: trigger:operation_multiplexer_anti_pattern
Blocks added: [create-wallet, deposit-to-wallet, withdraw-from-wallet, get-wallet-balance, get-wallet-statement]
IR delta: spec/*.bear.yaml, bear.blocks.yaml
Implementation delta: src/main/java/blocks/**, src/main/java/com/bear/account/demo/WalletService.java
Tests delta: src/test/java/com/bear/account/demo/AppTest.java
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base origin/main => 5
Gate run order: bear check --all --project . -> bear pr-check --all --project . --base origin/main
Run outcome: WAITING_FOR_BASELINE_REVIEW
Required next action: boundary governance review and baseline merge
Gate blocker: BOUNDARY_EXPANSION
Stopped after blocker: yes
First failing command: bear pr-check --all --project . --base origin/main
First failure signature: DETAIL: pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
PR base used: origin/main
PR base rationale: target branch merge-base reference for this run
PR classification interpretation: expected - baseline introduces new blocks/contracts/ports
Baseline review scope: bear.blocks.yaml, spec/*.bear.yaml
Constraint conflicts encountered: none
Escalation decision: required - baseline boundary review pending
Containment sanity check: n/a - no containment/classpath failure signature in check output
Infra edits: none
Unblock used: no - not needed
Gate policy acknowledged: yes
Final git status: M src/main/java/blocks/... (summary)
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```

## Minimal BLOCKED Example

```text
Request summary: Non-greenfield boundary expansion without approved scope
Block decision: updated=none added=wallet
Decomposition evidence: requested change added new externally visible operation in existing repo
Decomposition mode: multi
Decomposition reason: spec_explicit
Blocks added: [wallet]
IR delta: spec/wallet.bear.yaml, bear.blocks.yaml
Implementation delta: src/main/java/blocks/wallet/impl/WalletImpl.java
Tests delta: src/test/java/blocks/wallet/WalletImplTest.java
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base origin/main => 5
Gate run order: bear check --all --project . -> bear pr-check --all --project . --base origin/main
Run outcome: BLOCKED
Required next action: governance review/approval for expected boundary expansion
Gate blocker: BOUNDARY_EXPANSION
Stopped after blocker: yes
First failing command: bear pr-check --all --project . --base origin/main
First failure signature: CATEGORY: BOUNDARY_EXPANSION_DETECTED
PR base used: origin/main
PR base rationale: target branch merge-base reference for this completion run
PR classification interpretation: expected - intentional boundary expansion for new block
Baseline review scope: n/a
Constraint conflicts encountered: none
Escalation decision: required - boundary expansion governance review pending
Containment sanity check: n/a - no containment/classpath failure signature in check output
Infra edits: none
Unblock used: no - unblock is stale-marker-only and not valid for intentional expansion
Gate policy acknowledged: yes
Final git status: M src/main/java/blocks/wallet/impl/WalletImpl.java
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```
