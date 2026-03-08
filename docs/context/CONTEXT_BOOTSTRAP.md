# Context Bootstrap (bear-cli)

Purpose:
- Small always-load context contract for repo sessions.
- Route readers to the right canonical doc with minimal context cost.

## If You Remember Nothing Else

1. Use this file as the first read in repo sessions.
2. `docs/context/state.md` is current-window handoff only.
3. `roadmap/board.md` is the canonical live roadmap board.
4. `roadmap/scope.md` is the canonical near-term roadmap narrative.
5. `docs/context/roadmap.md` is milestone definitions/done criteria.
6. `docs/context/program-board.md` is a thin compatibility bridge pointing back to minimap.
7. `docs/context/ir-spec.md` is canonical IR contract.
8. `docs/context/governance.md` is canonical diff-class policy.
9. Completion evidence expects both local gates:
- `bear check --all --project <repoRoot>`
- `bear pr-check --all --project <repoRoot> --base <ref>`
10. Verification default is fast-by-default:
- batch related edits before verification (avoid repeated micro-runs).
- use targeted/method-level tests during edits when possible.
- use Gradle daemon for iterative local verification; reserve `--no-daemon` for explicit CI-parity or `full verify`.
- run full `:app:test :kernel:test` only on explicit `full verify`.

## Routing Map

Always load:
1. `docs/context/CONTEXT_BOOTSTRAP.md`

Load by intent:
1. Session execution now:
- `docs/context/state.md`
2. Live roadmap groups and order:
- `roadmap/board.md`
3. Current roadmap focus narrative:
- `roadmap/scope.md`
4. Milestone status bridge and compatibility routing:
- `docs/context/program-board.md`
5. Milestone definitions and done criteria:
- `docs/context/roadmap.md`
6. IR schema, validation, normalization, semantic constraints:
- `docs/context/ir-spec.md`
7. Governance classification policy:
- `docs/context/governance.md`
8. Architecture guarantees and scope lock:
- `docs/context/architecture.md`
9. Operator command and failure handling:
- `docs/context/user-guide.md`
10. Safety rules before cleanup/delete operations:
- `docs/context/safety-rules.md`
11. Demo simulation protocol and grading rubric:
- `docs/context/demo-agent-simulation.md`
12. Reusable BEAR run grading rubric (cross-run consistency):
- `docs/context/bear-run-grading-rubric.md`
13. Non-repo chat bootstrap prompt:
- `docs/context/prompt-bootstrap.md`
14. Historical rationale:
- `docs/context/project-log.md`
- `docs/context/archive/archive-readme.md`

## Source Ownership

1. `docs/context/state.md`:
- short current-window handoff only
2. `roadmap/board.md` + `roadmap/scope.md`:
- canonical live roadmap ordering and focus
3. `docs/context/program-board.md`:
- thin compatibility bridge for older routing and tests
4. `docs/context/roadmap.md`:
- milestone feature contracts and done criteria
5. `docs/context/ir-spec.md`:
- canonical v1 IR model and normalization
6. `docs/context/archive/*`:
- historical records only, never primary planning input

## Session Update Contract

When work progresses:
1. Update `docs/context/state.md`:
- `Last Updated`
- `Current Focus`
- `Next Concrete Task`
- short `Session Notes`
2. Update `roadmap/board.md`, `roadmap/scope.md`, and the owning minimap item files if roadmap queue/status/focus changed.
3. Update canonical docs only when semantics changed.
4. Put long narrative/history in archive docs, not `state.md`.

## Session Close Contract

Before ending a session:
1. Follow `docs/context/start-here.md` -> `Session Close Protocol`.
2. Ensure `state.md` remains concise.
3. If context was compacted, add a dated archive snapshot entry.

## No-Loss Context Rule

1. Context compaction must preserve topics via mapping in:
- `docs/context/context-coverage-map.md`
2. No section is removed unless mapped to:
- a canonical active doc, or
- an archive destination.
