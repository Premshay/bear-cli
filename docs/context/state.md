# BEAR Session State

This file is the short operational handoff for the current work window.
For live roadmap status and backlog ordering, use `roadmap/board.md` and `roadmap/scope.md`.
Use `docs/context/program-board.md` only as a compatibility bridge.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-08

## Current Focus

The packaged downstream CI integration is complete and stable, and `main` now also includes the completed JVM-only target-adaptation prep slice plus the follow-on seam cleanup that makes `com.bear.kernel.target` genuinely generic while keeping JVM implementation code under `com.bear.kernel.target.jvm`. The planning workflow now uses minimap under `roadmap/` as the canonical live roadmap surface. With the target seam and package ownership cleaned up, the next active product-value feature is broader boundary-escape coverage.

## Next Concrete Task

1. Start `docs/context/backlog/p3-broader-boundary-escape-coverage.md` as the next execution slice.
2. Keep the shipped target-seam and CI contracts stable while future multi-target work stays parked behind the prep seam.
3. Keep `roadmap/board.md`, `roadmap/scope.md`, and minimap item files as the canonical live planning source; avoid rebuilding detailed queue state in `docs/context/program-board.md`.

## Session Notes

- Completed `P3` target-adaptable CLI preparation as a JVM-only slice: app command orchestration now routes through a kernel-owned `Target` seam via `TargetRegistry`, with no Node behavior, no `.bear/target.id`, and no CLI surface changes.
- Followed with target-seam package cleanup and boundary cleanup: generic ownership stays in `com.bear.kernel.target`, JVM-only renderers/scanners and `JvmTarget` live under `com.bear.kernel.target.jvm`, and `Target.java` no longer imports JVM package types.
- Kept runtime behavior unchanged during the split: target-owned manifest/findings/project-verification DTOs now sit in the generic package, `TargetRegistry` still resolves `JvmTarget`, and app orchestration consumes only generic seam types rather than JVM package types.
- Moved the package-owned `ProjectTestRunnerTest` into kernel test ownership alongside the JVM project-test types, and updated repo guard tests to point at the new `kernel.target.jvm` paths.
- Adopted minimap as the canonical live planning workflow under `roadmap/`; `docs/context/program-board.md` and the planning portions of `docs/context/roadmap.md` / `docs/context/future.md` are now bridge docs instead of the primary queue source.
- Imported the parked Node containment discovery work into the minimap-first structure: `docs/context/backlog/future-node-containment-profile.md` is now the canonical discovery spec, and `roadmap/ideas/future-target-adaptable-cli-node.md` plus `roadmap/ideas/future-node-containment-profile.md` carry the parked item summaries.
- The Node discovery recommendation is intentionally narrow: keep Node support parked unless the product explicitly accepts the `node-ts-pnpm-single-package-v1` profile, with repo-level dependency governance only and typecheck-only project verification.
- The packaged downstream CI wrapper is complete and stable, including deterministic wrapper/report behavior, exact allow-entry output, GitHub-readable markdown summary generation, the wrapper-only observe-mode `review-required` decision split, and the additive human-facing `BEAR Decision: PASS|REVIEW REQUIRED|FAIL|ALLOWED EXPANSION` header.
- Fixed the wrapper's agent-JSON failure-footer fallback so report `check.path` / `prCheck.path` preserve full cluster or problem file paths instead of collapsing a single-item `files[]` value to its first character.
- The CI wrapper header polish is additive only: the structured `MODE=... DECISION=... BASE=...` line remains unchanged for deterministic parsing.
- Parked a new future feature for optional scalar inputs in BEAR IR so the idea is preserved as a spec-backed item without entering the active queue; see `docs/context/backlog/future-optional-scalar-inputs.md`.
- Verification: `./gradlew.bat --no-daemon :kernel:test`, `./gradlew.bat --no-daemon :app:test`, `./gradlew.bat --no-daemon :app:test :kernel:test`, `./gradlew.bat --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest`, `./gradlew.bat :app:compileJava :app:compileTestJava :kernel:compileJava :kernel:compileTestJava`.
