# M1 Evaluation Scenarios

Evaluator-facing docs only.
Do not copy these runbooks into `bear-account-demo`.

## Canonical M1 Branches

| Branch | Start Condition | Agent Task | Expected High-Level Outcome |
| --- | --- | --- | --- |
| `scenario/greenfield-build` | minimal app + domain specs; no completed BEAR block implementation | create initial block IR(s), compile/generate, implement, pass gate | canonical gate exits `0` |
| `scenario/feature-extension` | existing app passes gate | decide update-existing-block vs create-new-block for new feature; perform IR-first boundary work when needed | stale-baseline check emits boundary expansion + drift (`3`) when applicable, then final gate exits `0` |

## Legacy Branches (Non-Canonical)

- `scenario/naive-fail-withdraw`
- `scenario/corrected-pass-withdraw`

These remain historical proof branches and are not the active M1 realism model.
