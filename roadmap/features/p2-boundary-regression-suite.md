---
id: p2-boundary-regression-suite
title: Boundary regression suite
status: done
priority: medium
commitment: committed
milestone: P2
---

## Summary

Harden BEAR's boundary and governance contracts with regression tests that lock intended deterministic behavior without broadening the product surface.

## Why

This completed the regression-hardening slice that protects classifier, bypass, ordering, and `--all` routing behavior from silent drift.

## In Scope

- governance classification decision-table coverage
- deterministic boundary-bypass and failure-envelope regressions
- `check --all` and `pr-check --all` ordering and edge-case coverage
- targeted tests rather than new product behavior

## Out of Scope

- CI wrapper or reporting UX work
- Maven parity
- new user-facing command semantics

## Done When

1. Meaningful regression coverage protects classifier, bypass, ordering, and all-mode edge cases.
2. Production changes occur only when tests expose a genuine contract bug.
3. The added tests are explicit about the protected contract rather than accidental output snapshots.

## Notes

Source spec: `docs/context/backlog/p2-boundary-regression-suite.md`
