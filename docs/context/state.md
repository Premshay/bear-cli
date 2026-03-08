# BEAR Session State

This file is the short operational handoff for the current work window.
For live roadmap status and backlog ordering, use `roadmap/board.md` and `roadmap/scope.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-08

## Current Focus

The packaged downstream CI integration is complete and stable, and `main` now also includes the completed JVM-only target-adaptation prep slice plus the follow-on seam cleanup that makes `com.bear.kernel.target` genuinely generic while keeping JVM implementation code under `com.bear.kernel.target.jvm`. The planning workflow now uses minimap under `roadmap/` as the canonical live roadmap surface. With the target seam and package ownership cleaned up, the next active product-value feature is broader boundary-escape coverage.

## Next Concrete Task

1. Start `docs/context/backlog/p3-broader-boundary-escape-coverage.md` as the next execution slice.
2. Keep the shipped target-seam and CI contracts stable while future multi-target work stays parked behind the prep seam.
3. Keep `roadmap/board.md`, `roadmap/scope.md`, and minimap item files as the canonical live planning source.

## Session Notes

- Completed `P3` target-adaptable CLI preparation as a JVM-only slice: app command orchestration now routes through a kernel-owned `Target` seam via `TargetRegistry`, with no Node behavior, no `.bear/target.id`, and no CLI surface changes.
- Followed with target-seam package cleanup and boundary cleanup: generic ownership stays in `com.bear.kernel.target`, JVM-only renderers/scanners and `JvmTarget` live under `com.bear.kernel.target.jvm`, and `Target.java` no longer imports JVM package types.
- Kept runtime behavior unchanged during the split: target-owned manifest/findings/project-verification DTOs now sit in the generic package, `TargetRegistry` still resolves `JvmTarget`, and app orchestration consumes only generic seam types rather than JVM package types.
- Moved the package-owned `ProjectTestRunnerTest` into kernel test ownership alongside the JVM project-test types, and updated repo guard tests to point at the new `kernel.target.jvm` paths.
- Adopted minimap as the canonical live planning workflow under `roadmap/`; `docs/context` now keeps milestone definitions, specs, and maintainer handoff rather than a second live planning board.
- Restored completed-item history into minimap so already-shipped P2/P3 slices are preserved in `roadmap/features/*.md` and `roadmap/board.md` instead of lingering only in old context prose.
- Imported the parked Node containment discovery work into the minimap-first structure: `docs/context/backlog/future-node-containment-profile.md` is now the canonical discovery spec, and `roadmap/ideas/future-target-adaptable-cli-node.md` plus `roadmap/ideas/future-node-containment-profile.md` carry the parked item summaries.
- The Node discovery recommendation is intentionally narrow: keep Node support parked unless the product explicitly accepts the `node-ts-pnpm-single-package-v1` profile, with repo-level dependency governance only and typecheck-only project verification.
- Archived or removed stale process docs that no longer earn their keep in a public repo, including the old simulation/grading/checkpoint docs and the redundant `program-board.md` bridge.
- Completed the minimap planning cleanup: completed roadmap history now lives under oadmap/features/*.md, oadmap/board.md is the only live queue, and the remaining context bridge docs point at minimap instead of maintaining duplicate planning state.
- Verification: `./gradlew.bat --no-daemon :kernel:test`, `./gradlew.bat --no-daemon :app:test`, `./gradlew.bat --no-daemon :app:test :kernel:test`, `./gradlew.bat --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest`, `./gradlew.bat :app:compileJava :app:compileTestJava :kernel:compileJava :kernel:compileTestJava`.
