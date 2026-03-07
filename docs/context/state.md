# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-07

## Current Focus

Local `main` includes the completed downstream CI integration kit plus the repo-layout and containment-classification hardening. A second post-push CI follow-up is now verified locally and waiting to be committed or pushed. The next BEAR execution slice after that is `P3` capability templates.

## Next Concrete Task

1. Land the local cross-platform CI follow-up for wrapper argument forwarding and hermetic GitHub env handling on `main`.
2. Start `docs/context/backlog/p3-capability-templates.md` as the next execution slice.
3. Keep the shipped CI contracts stable in adopter or demo usage: `extensions.prGovernance`, `bear.ci.governance.v1`, `.bear/ci/baseline-allow.json`, and `build/bear/ci/bear-ci-summary.md`.

## Session Notes

- Fixed the first post-push Linux CI breakage locally: `BearCiIntegrationScriptsTest` now resolves the correct PowerShell executable per OS and skips cleanly when a PowerShell runtime is unavailable.
- Added a follow-up local CI fix for GitHub Linux runners: the packaged bash launcher now inserts an explicit `--` sentinel before script args, `bear-gates.ps1` strips that sentinel deterministically, and `BearCiIntegrationScriptsTest` now removes inherited `GITHUB_*` env so wrapper tests do not accidentally read the real workflow event context.
- Earlier in this window, finished the repo-owned fixture migration from `spec/` to `bear-ir/` in app tests and stabilized the packaged bash launcher line endings for cross-platform execution.
- Verification: `./gradlew.bat :app:test --tests com.bear.app.BearCiIntegrationScriptsTest`, `./gradlew.bat :app:test :kernel:test`, and `./gradlew.bat :app:test --tests com.bear.app.ContextDocsConsistencyTest`.
- Local `main` already contains the shipped CI boundary-governance integration, repo-owned layout split, and containment hardening; those contracts should stay stable while capability-template work starts.
- Public docs now point more directly to `CI_INTEGRATION.md` from `INDEX.md`, `OVERVIEW.md`, and `QUICKSTART.md` so the downstream wrapper pattern is easier to discover.
