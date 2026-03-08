---
id: p3-broader-boundary-escape-coverage
title: Broader boundary-escape coverage
status: queued
priority: high
commitment: committed
milestone: P3
---

## Summary

Extend deterministic boundary-bypass coverage beyond the current JVM HTTP-focused surfaces to additional external-power paths.

## Why

This is the highest-value next capability slice in the active queue because it expands BEAR's governance coverage directly while preserving the current deterministic workflow model.

## In Scope

- deterministic checks for direct database usage bypass paths
- deterministic checks for direct filesystem usage bypass paths
- deterministic checks for direct messaging usage bypass paths
- stable finding ordering, repo-relative path rendering, and existing failure-envelope behavior

## Out of Scope

- proving whole-program safety
- policy-engine integration
- broad static-analysis platform build-out

## Done When

1. Newly covered bypass paths are explicit, documented, and deterministic.
2. Findings map into stable existing governance or bypass lanes rather than creating ad hoc semantics.
3. Tests pin representative positive and negative cases for each newly covered surface.

## Notes

Source spec: `docs/context/backlog/p3-broader-boundary-escape-coverage.md`