# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-06

## Current Focus

Local `main` includes the completed downstream CI integration kit plus the repo-layout and containment-classification hardening. A small post-push CI fix is now verified locally and waiting to be committed or pushed. The next BEAR execution slice after that is `P3` capability templates.

## Next Concrete Task

1. Land the local post-push CI fix for Linux wrapper tests on `main`.
2. Start `docs/context/backlog/p3-capability-templates.md` as the next execution slice.
3. Keep the shipped CI contracts stable in adopter or demo usage: `extensions.prGovernance`, `bear.ci.governance.v1`, `.bear/ci/baseline-allow.json`, and `build/bear/ci/bear-ci-summary.md`.

## Session Notes

- Fixed the remaining post-push Linux CI breakage locally: `BearCiIntegrationScriptsTest` now resolves the correct PowerShell executable per OS and skips cleanly when a PowerShell runtime is unavailable.
- Earlier in this window, finished the repo-owned fixture migration from `spec/` to `bear-ir/` in app tests and stabilized the packaged bash launcher line endings for cross-platform execution.
- Verification: `./gradlew.bat :app:test --tests com.bear.app.BearCiIntegrationScriptsTest`, `./gradlew.bat :app:test :kernel:test`, and `./gradlew.bat :app:test --tests com.bear.app.ContextDocsConsistencyTest`.
- Local `main` already contains the shipped CI boundary-governance integration, repo-owned layout split, and containment hardening; those contracts should stay stable while capability-template work starts.
