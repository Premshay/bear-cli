# Start Here (bear-cli)

This is the quick navigation map for active work.

## Read Next (in order)

1. `docs/context/CONTEXT_BOOTSTRAP.md`
2. `docs/context/state.md`
3. `roadmap/board.md`
4. `roadmap/scope.md`
5. `docs/context/roadmap.md`
6. `docs/context/ir-spec.md`
7. `docs/context/user-guide.md`

On demand:
1. `docs/context/governance.md`
2. `docs/context/architecture.md`
3. `docs/context/prompt-bootstrap.md`
4. `docs/context/archive/archive-readme.md`

## Source Ownership

1. `docs/context/state.md`: short current-window handoff.
2. `roadmap/board.md` + `roadmap/scope.md`: canonical live roadmap planning state.
3. `roadmap/features/*.md` + `roadmap/ideas/*.md`: canonical item ownership, including completed features.
4. `docs/context/roadmap.md`: milestone definitions and done criteria.
5. `docs/context/ir-spec.md`: canonical v1 IR contract.
6. `docs/context/archive/*`: historical references only.

Interpretation guardrail:
- "What are milestone features?" -> `docs/context/roadmap.md`
- "Where are we standing now?" -> `roadmap/board.md` + `roadmap/scope.md`
- "What already shipped?" -> `roadmap/features/*.md` + `roadmap/board.md`
- "What should I do this session?" -> `docs/context/state.md`

## Session Close Protocol

1. Update `docs/context/state.md`:
- `Last Updated`
- `Current Focus`
- `Next Concrete Task`
- short `Session Notes`
 - keep `Session Notes` within `ContextDocsConsistencyTest` cap; move oldest notes to `docs/context/archive/archive-state-history.md` when needed.
2. Update `roadmap/board.md`, `roadmap/scope.md`, and the owning minimap item files if roadmap status or ordering changed.
3. Update canonical docs only when semantics changed:
- `docs/context/ir-spec.md`
- `docs/context/architecture.md`
- `docs/context/governance.md`
- `docs/context/roadmap.md`
4. Update `docs/context/project-log.md` only for major architectural decisions.
5. Before push, run docs guard: `./gradlew --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest` (or Windows equivalent).
