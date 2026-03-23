# Copilot Instructions for bear-cli

Purpose: guide GitHub Copilot PR review to prioritize BEAR's deterministic governance contracts over style-only feedback.

For coding agent sessions, start with `AGENTS.md` instead — it routes you through the full context bootstrap.

## What This Repo Is

BEAR is a deterministic governance CLI for structural boundary enforcement in agent-assisted backend development. `kernel/` is the trusted deterministic seed (IR, target abstractions, semantics). `app/` is CLI orchestration and contract rendering. Keep `kernel/` deterministic and small — no agent/LLM logic in core.

## PR Review Priorities

1. **Boundary governance first**
   - Flag silent boundary-expanding behavior.
   - Ensure IR deltas follow governance classification (`ordinary` vs `boundary-expanding`).

2. **Contract correctness over style**
   - Preserve deterministic command/output contracts and ordering guarantees.
   - Non-zero failure footer must stay exact and remain the last stderr lines:
     - `CODE=<enum>`
     - `PATH=<locator>`
     - `REMEDIATION=<deterministic-step>`
   - See `docs/public/output-format.md` for full line format contracts.

3. **Exit-code integrity**
   - Validate behavior against `docs/public/exit-codes.md`.
   - For `--all` flows, preserve severity-rank aggregation (frozen Preview contract).

4. **IR and generated-artifact discipline**
   - IR schema/normalization/semantics come from `docs/context/ir-spec.md`.
   - Generated artifacts are BEAR-owned; user implementation files are separate.
   - When boundary authority changes: update IR first, then regenerate, then implement.

5. **No hidden path/format regressions**
   - `PATH` locators must not become absolute filesystem paths.
   - Preserve deterministic, machine-parseable output used by CI/reporting.

## Target and Multi-Target Guidance

- New targets extend through the deterministic `Target` seam in `kernel/src/main/java/com/bear/kernel/target/` — do not add ad-hoc command logic.
- Detection shapes, sub-profiles, and containment rules are defined per-target in their respective packages.
- For current target status and phase details, check `roadmap/features/multi-target-foundation-phases.md`.
- Ambiguous multi-ecosystem repos use `.bear/target.id` to disambiguate.

## Shipped vs Roadmap Distinction

Before flagging something as wrong, verify whether the source of truth is:
- **Shipped/public behavior**: `docs/context/*`, `docs/public/*`, `README.md`
- **Active execution planning**: `roadmap/features/*.md`
- **Parked future design**: `roadmap/ideas/*.md`

Do not flag active feature work as violations of shipped contracts. Do not silently promote parked ideas into shipped behavior.

## Canonical Reference Files

- Architecture/governance/IR: `docs/context/architecture.md`, `docs/context/governance.md`, `docs/context/ir-spec.md`
- Public command contracts: `docs/public/commands-check.md`, `docs/public/commands-pr-check.md`
- Output/exit contracts: `docs/public/output-format.md`, `docs/public/exit-codes.md`
- Repo workflow guardrails: `AGENTS.md`

## Verification Evidence

When reviewing PRs that alter behavior or contracts, expect evidence of:
- `./gradlew :kernel:test :app:test` (run separately if timeout occurs)
- `bear check --all --project <repoRoot>` for governance/contract changes
- `bear pr-check --all --project <repoRoot> --base <ref>` for boundary changes

If a change claims contract updates without corresponding gate evidence, request follow-up.
