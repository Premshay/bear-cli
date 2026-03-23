# Copilot Instructions for bear-cli PR Review

Purpose: make GitHub Copilot PR reviews align with BEAR's deterministic governance model instead of style-only feedback.

## Start Here (required read order)

1. `AGENTS.md`
2. `docs/context/CONTEXT_BOOTSTRAP.md`
3. Task-routed canonical docs from the bootstrap map, especially:
   - `docs/context/architecture.md`
   - `docs/context/governance.md`
   - `docs/context/ir-spec.md`
   - `docs/public/commands-check.md`
   - `docs/public/commands-pr-check.md`
   - `docs/public/output-format.md`
   - `docs/public/exit-codes.md`

## Architecture and Scope Guardrails

- Keep `kernel/` deterministic and small (trusted seed). No agent/LLM logic in core.
- `app/` is CLI orchestration and contract rendering; `kernel/` holds deterministic semantics.
- Stay within v1-preview scope from architecture + roadmap docs; do not treat parked roadmap ideas as required changes.
- Prefer explicit, deterministic contracts over inferred behavior.

## What PR Review Should Prioritize

1. **Boundary governance first**
   - Flag silent boundary-expanding behavior.
   - Ensure IR deltas follow governance classification (`ordinary` vs `boundary-expanding`).
2. **Contract correctness over style**
   - Check command/output contracts and deterministic ordering guarantees.
   - Verify non-zero failure footer contract stays exact and last on stderr:
     - `CODE=<enum>`
     - `PATH=<locator>`
     - `REMEDIATION=<deterministic-step>`
3. **Exit-code integrity**
   - Validate behavior against canonical registry in `docs/public/exit-codes.md`.
   - For `--all` flows, ensure severity-rank aggregation rules are preserved.
4. **IR and generated-artifact discipline**
   - IR schema/normalization/semantics must follow `docs/context/ir-spec.md`.
   - Enforce BEAR workflow intent: IR updates first when boundary authority changes, then deterministic generation/checks.
5. **No hidden path/format regressions**
   - `PATH` locators must not become absolute filesystem paths.
   - Preserve deterministic, machine-parseable output shapes used by CI/reporting.

## Important Files and Ownership

- Canonical repo workflow guardrails: `AGENTS.md`
- Context routing and session contracts: `docs/context/CONTEXT_BOOTSTRAP.md`
- Architecture/governance/IR canon:
  - `docs/context/architecture.md`
  - `docs/context/governance.md`
  - `docs/context/ir-spec.md`
- Public command and automation contracts:
  - `docs/public/commands-check.md`
  - `docs/public/commands-pr-check.md`
  - `docs/public/output-format.md`
  - `docs/public/exit-codes.md`
  - `docs/public/PR_REVIEW.md`

## Expected Verification Signals for Code Changes

When reviewing PRs that alter behavior/contracts, expect evidence of deterministic gates:

- `./gradlew :app:test :kernel:test`
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`

If a change claims governance/contract updates without corresponding gate evidence, request follow-up validation.
