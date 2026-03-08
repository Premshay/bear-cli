---
id: p2-ci-boundary-governance-and-telemetry
title: CI boundary governance and telemetry unification
status: done
priority: high
commitment: committed
milestone: P2
---

## Summary

Ship a deterministic downstream CI integration kit around BEAR's governance facts so consuming repos can run one canonical CI flow without inventing their own wrapper policy, allow-file matching, or reviewer-facing summary layer.

## Why

This completed the CI-owned follow-up from the P2 queue and made BEAR's governance facts easier to use in downstream CI without weakening the core CLI contracts.

## In Scope

- deterministic `pr-check --agent` governance telemetry under `extensions.prGovernance` for single and `--all`
- packaged downstream CI assets under `.bear/ci/`
- deterministic wrapper behavior for `check --all` plus `pr-check --all`, allow-file evaluation, and fail-closed telemetry handling
- reproducible report and summary artifacts for CI and review surfaces
- public and package docs for downstream CI usage

## Out of Scope

- new public `bear` commands
- changes to existing exit codes, failure footers, or non-agent text contracts
- GitHub API comments or richer check-run UI in v1
- a second parsing or decision engine in bash

## Done When

1. Existing non-agent CLI contracts remain unchanged.
2. `extensions.prGovernance` is deterministic and documented for pass and fail paths.
3. The packaged wrapper deterministically resolves base SHA, executes the pinned gate order, and emits reproducible report and summary artifacts.
4. `enforce` blocks per contract while `observe` records the same canonical BEAR facts.
5. Reviewer-facing wrapper outputs remain additive, deterministic, and derived from the same canonical boundary facts.
6. Tests cover wrapper decisioning, not-run serialization, allow-file matching, markdown summary output, and cross-shell launcher behavior.

## Notes

Source spec: `docs/context/backlog/p2-ci-owned-bear-gates.md`

## Decision Locks

1. BEAR exits, footers, deltas, and governance signals remain authoritative facts.
2. Wrapper decisions and summaries are derived only from BEAR facts plus wrapper mode and allow-file state.
3. Allow-file approval remains exact-match only.
4. Wrapper process exit is pinned to a simplified deterministic contract.

## Delivered Artifacts

- `docs/bear-package/.bear/ci/bear-gates.ps1`
- `docs/bear-package/.bear/ci/bear-gates.sh`
- `docs/bear-package/.bear/ci/baseline-allow.json`
- `docs/bear-package/.bear/ci/README.md`
- `docs/public/CI_INTEGRATION.md`
- `docs/public/examples/github-actions-bear-ci.yml`
- `app/src/test/java/com/bear/app/BearCiIntegrationScriptsTest.java`