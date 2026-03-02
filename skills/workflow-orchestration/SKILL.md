---
name: "workflow-orchestration"
description: "Repo-adapted execution workflow for non-trivial bear-cli tasks. Trigger for tasks with 3+ steps, architectural decisions, bug investigations, or when user asks for plan-first or verification-heavy execution. Skip for trivial one-step edits or pure Q&A."
---

# Workflow Orchestration (bear-cli)

## Intent

Use a consistent execution loop that balances speed with correctness:
- plan first for non-trivial work
- re-plan when assumptions break
- prove behavior before marking done
- keep scope minimal and deterministic

## Fit To This Repo

This skill maps to the existing BEAR context flow:
- use `docs/context/state.md` for session handoff updates
- use `docs/context/program-board.md` when queue/status/risk changes
- do not introduce parallel trackers like `tasks/todo.md` or `tasks/lessons.md` unless the user explicitly asks
- stay within v1-preview guardrails in `docs/context/architecture.md` and `docs/context/roadmap.md`

## Trigger Rules

Use this skill when any condition is true:
1. The task likely needs 3+ implementation or verification steps.
2. The task includes architecture/refactor seams or behavior-parity risk.
3. The user reports a bug, failing test, or CI failure.
4. The user explicitly asks for plan-first or orchestration workflow.

Skip this skill when:
1. The task is a trivial one-liner with no meaningful verification risk.
2. The task is pure discussion/Q&A with no implementation.
3. The user explicitly asks to avoid planning overhead.

## Workflow

1. Load minimal context:
- read `docs/context/CONTEXT_BOOTSTRAP.md`
- load only task-relevant docs from the routing map
2. Plan:
- write a short checkable execution plan in chat (or planning tool when available)
- include verification steps, not only implementation steps
3. Execute:
- implement the minimal-impact root-cause fix
- keep `kernel/` deterministic (no agent/LLM logic in core)
4. Re-plan on breakage:
- if an assumption fails, stop and re-plan before continuing
5. Verify before done:
- run smallest meaningful test slice first, then broader gates as needed
- for substantial changes, prefer completion evidence:
  - `bear check --all --project <repoRoot>`
  - `bear pr-check --all --project <repoRoot> --base <ref>`
6. Close loop:
- update `docs/context/state.md` (`Last Updated`, `Current Focus`, `Next Concrete Task`, short `Session Notes`)
- update `docs/context/program-board.md` only when queue/status/risk changes
- summarize verification evidence and any residual risk

## Quality Bar

Before marking complete, ask:
1. Is this the simplest defensible solution?
2. Did I verify behavior instead of assuming it?
3. Is there a cleaner design than the current approach?
4. Did I minimize blast radius and preserve existing contracts?

## Constraints

- no assumption of autonomous subagents
- avoid over-engineering simple fixes
- prefer focused diffs and explicit evidence
- if user corrections occur, capture prevention guidance in `docs/context/state.md` notes unless a different location is requested
