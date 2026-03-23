# Copilot Instructions for bear-cli

Purpose: onboard GitHub Copilot coding agents to this repository so first-time sessions stay fast, deterministic, and aligned with BEAR's governance contracts instead of drifting into style-only edits or roadmap overreach.

## Start Here (required read order)

1. `AGENTS.md`
2. `docs/context/CONTEXT_BOOTSTRAP.md`
3. `docs/context/state.md`
4. `roadmap/board.md`
5. `roadmap/scope.md`
6. Then load task-routed canonical docs from the bootstrap map, especially:
   - `docs/context/architecture.md`
   - `docs/context/governance.md`
   - `docs/context/ir-spec.md`
   - `docs/context/user-guide.md`
   - `docs/public/commands-check.md`
   - `docs/public/commands-pr-check.md`
   - `docs/public/output-format.md`
   - `docs/public/exit-codes.md`
   - `docs/public/troubleshooting.md`

Use `docs/public/INDEX.md` only as a public-doc navigation page. For bear-cli implementation work, the `docs/context/*` files and public command contracts are canonical.

## What This Repo Is

- BEAR is a deterministic governance CLI for structural boundary enforcement in agent-assisted backend development.
- `kernel/` is the trusted deterministic seed: IR parsing/validation/normalization, target abstractions, and semantics. Do not add agent/LLM logic here.
- `app/` is CLI orchestration and deterministic contract rendering (`validate`, `compile`, `fix`, `check`, `unblock`, `pr-check`).
- `docs/context/` holds maintainer-facing canonical architecture/governance/IR context.
- `docs/public/` holds stable user/operator command and output contracts.

## How To Work Efficiently In This Repo

1. Read the bootstrap docs first; do not start by free-searching the whole repo.
2. Load only the task-relevant canonical docs from `docs/context/CONTEXT_BOOTSTRAP.md`.
3. Keep changes surgical. BEAR values deterministic contracts over broad cleanups.
4. When boundary authority changes, update IR first, then regenerate/fix, then implement inside those constraints.
5. Treat `check` and `pr-check` outputs as the authoritative machine contract, not summaries or style opinions.

## Architecture and Scope Guardrails

- Keep `kernel/` deterministic and small.
- Prefer explicit contracts over inference or heuristics.
- Do not treat parked roadmap ideas as committed requirements.
- Preserve deterministic ordering, output shapes, exit behavior, and repo-relative `PATH` locators.
- BEAR is a cage for boundary governance, not a style engine, orchestration platform, or runtime policy system.

## Current Product vs Roadmap Reality

- Public preview guarantees remain anchored to the deterministic BEAR command contracts and JVM-first governance model in `docs/context/architecture.md`.
- The repo is actively evolving multi-target support through the deterministic `Target` seam:
  - current public docs list JVM/Java in Preview, Node/TypeScript in Preview (scan-only), and Python as experimental scan-only
  - see `README.md` and `roadmap/features/multi-target-foundation-phases.md`
- Broader Node, Python, .NET, and React target documents under `roadmap/ideas/*.md` are planning material, not automatic product contract.
- If you touch target behavior, verify whether the source of truth is:
  - current shipped/public behavior (`docs/context/*`, `docs/public/*`, `README.md`), or
  - active execution planning (`roadmap/features/*.md`), or
  - parked future design (`roadmap/ideas/*.md`)

Do not silently promote roadmap ideas into shipped behavior or docs.

## What PR Review and Code Changes Should Prioritize

1. **Boundary governance first**
   - Flag silent boundary-expanding behavior.
   - Ensure IR deltas follow governance classification (`ordinary` vs `boundary-expanding`).
2. **Contract correctness over style**
   - Preserve deterministic command/output contracts and ordering guarantees.
   - Non-zero failure footer must stay exact and remain the last stderr lines:
     - `CODE=<enum>`
     - `PATH=<locator>`
     - `REMEDIATION=<deterministic-step>`
3. **Exit-code integrity**
   - Validate behavior against `docs/public/exit-codes.md`.
   - For `--all` flows, preserve severity-rank aggregation rules.
4. **IR and generated-artifact discipline**
   - IR schema/normalization/semantics come from `docs/context/ir-spec.md`.
   - Generated artifacts are BEAR-owned; user implementation files are separate.
5. **No hidden path/format regressions**
   - `PATH` locators must not become absolute filesystem paths.
   - Preserve deterministic, machine-parseable output used by CI/reporting.

## Verification Workflow

Start with existing repo checks before changing behavior:

- Baseline build/tests:
  - `./gradlew :app:test :kernel:test`

Use the smallest meaningful check while iterating:

- docs/context-only changes:
  - `./gradlew :app:test --tests com.bear.app.ContextDocsConsistencyTest`
- targeted app/kernel tests relevant to the changed area

Use BEAR command evidence when behavior/contracts/governance are touched:

- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`

Completion evidence should prefer targeted checks during iteration and the BEAR gates for behavior/contract changes.

## High-Value Repo-Specific Gotchas

- If you add or rename a `CliCodes` failure code, also update the failure-code-to-exit mapping in `app/src/main/java/com/bear/app/AgentDiagnostics.java` so agent JSON severity/ranking stays correct.
- `docs/context/state.md` is a short current-window handoff only. Update it when work progresses, but keep it concise enough to satisfy `ContextDocsConsistencyTest`.
- `roadmap/board.md` and `roadmap/scope.md` are the canonical live roadmap; do not duplicate roadmap queue state elsewhere.
- `roadmap/features/*.md` track active/committed work; `roadmap/ideas/*.md` are parked or uncommitted.

## Target and Multi-Target Guidance

- New target behavior should extend through the existing deterministic `Target` seam instead of adding ad hoc command logic.
- For ambiguous multi-ecosystem repos, BEAR uses `.bear/target.id` to disambiguate target resolution.
- Current Node detection is intentionally narrow: `package.json` with `"type": "module"` and `packageManager` starting with `pnpm`, plus `pnpm-lock.yaml` and `tsconfig.json`; `pnpm-workspace.yaml` makes the shape unsupported.
- JVM is still the only target with the current containment-surface enforcement handshake for `impl.allowedDeps`.
- Node and Python targets currently report `considerContainmentSurfaces=false`, so `impl.allowedDeps` on those targets is not a supported containment path.

If you change multi-target behavior, read the owning feature/idea docs first and make sure public contracts are updated only when the shipped behavior actually changes.

## Common Errors and Deterministic Workarounds

Documented repo-specific failure paths that agents commonly hit:

- `INDEX_REQUIRED_MISSING`
  - Cause: running `--all` without `bear.blocks.yaml`
  - Workaround: create `bear.blocks.yaml` or use the single-file command form
- `IO_ERROR` during Gradle lock/bootstrap in `check`
  - Workaround: let BEAR retry/fallback first; do not patch `build.gradle` as the first response; if needed, run `bear unblock --project <path>` after the lock cause is resolved
- `BOUNDARY_EXPANSION`
  - Workaround: review `pr-delta:` lines and route the change through explicit review rather than hiding the signal
- `TARGET_AMBIGUOUS`
  - Workaround: add `.bear/target.id` to pin the intended target
- Unsupported Node shape
  - Workaround: reduce to the supported single-package `pnpm` + TypeScript ESM profile or pin/use another target if appropriate
- Docs budget failures
  - Workaround: keep `docs/context/state.md` concise and validate with `ContextDocsConsistencyTest`

## Errors Encountered While Authoring This Onboarding File

- Hardcoded absolute paths made the first draft less portable across local and CI environments.
  - Workaround applied: all guidance in this file now uses repository-relative paths.
- Narrow PR-review-only guidance was insufficient for first-time Copilot sessions.
  - Workaround applied: this file now covers startup sequence, repo layout, verification flow, active multi-target context, and deterministic failure/workaround guidance.

## Important Files

- Workflow and session guardrails:
  - `AGENTS.md`
  - `docs/context/CONTEXT_BOOTSTRAP.md`
  - `docs/context/state.md`
  - `docs/context/start-here.md`
- Canonical architecture/governance/IR:
  - `docs/context/architecture.md`
  - `docs/context/governance.md`
  - `docs/context/ir-spec.md`
  - `docs/context/roadmap.md`
- Public command/output contracts:
  - `docs/public/commands-check.md`
  - `docs/public/commands-pr-check.md`
  - `docs/public/output-format.md`
  - `docs/public/exit-codes.md`
  - `docs/public/troubleshooting.md`
- Active planning:
  - `roadmap/board.md`
  - `roadmap/scope.md`
  - `roadmap/features/multi-target-foundation-phases.md`

## Done Criteria for Agents Working Here

Before calling work complete:

- verify the changed behavior with the smallest relevant tests first
- run broader evidence when contracts or governance changed
- preserve deterministic output/footer/exit behavior
- keep roadmap and state updates consistent with repo conventions
- summarize any remaining risk explicitly instead of implying behavior that BEAR does not currently guarantee
