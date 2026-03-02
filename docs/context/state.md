# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-02

## Current Focus

Stability-first quality rollout (aggressive track):
- restore green `main` for policy and docs gates
- add in-repo CI gate contract for test + dual BEAR checks
- introduce low-risk refactor seams (`IrPipeline`, shared marker/constants, envelope emitter)
- standardize non-trivial task execution via repo-local `workflow-orchestration` skill + AGENTS registry entry

## Next Concrete Task

1. Continue oversized-class decomposition for `JvmTarget` (largest remaining class), next by extracting sync-lock/file IO helpers and additional emitters with byte-stable output.
2. Start command-domain test split for `BearCliTest` after next refactor lock is in place.
3. Add guard slices for long app classes still near threshold (`PrCheckCommandService`, `ProjectTestRunner`).

## Session Notes

- Continued stability-first quality rollout for BEAR docs and process guardrails.
- Implemented v2.2.5.1 docs/test hardening:
  - added labeled preconditions in packaged BOOTSTRAP (`AGENT_PACKAGE_PARITY_PRECONDITION`, `GREENFIELD_HARD_STOP`, `INDEX_REQUIRED_PREFLIGHT`).
  - added deterministic process-violation signature format in TROUBLESHOOTING (`PROCESS_VIOLATION|<label>|<evidence>`).
  - aligned REPORTING for no-command preflight failures (`First failing command: none (preflight)`).
  - extended docs consistency anchors for the new headings while keeping minimal checks.
- Kept blocker taxonomy unchanged (`OTHER` for process/tool anomalies).
- Full historical details remain in archive docs; this file stays operational and bounded.
