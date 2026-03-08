---
id: p2-bear-fix-generated-only
title: bear fix for generated artifacts only
status: done
priority: high
commitment: committed
milestone: P2
---

## Summary

Provide a deterministic repair command that normalizes and regenerates BEAR-owned generated artifacts without touching user-owned implementation files.

## Why

This completed a key post-preview workflow gap by giving agents and developers a canonical recovery command for generated drift without weakening the two-tree ownership model.

## In Scope

- `bear fix` single-block mode
- `bear fix --all` repo mode
- deterministic regeneration of BEAR-owned generated outputs only
- public docs and agent-package workflow updates for `fix`

## Out of Scope

- edits to user-owned impl files
- domain logic synthesis
- runtime or policy behavior changes

## Done When

1. Deterministic output and file diffs hold for unchanged IR input.
2. Only BEAR-owned generated paths are touched.
3. Non-zero exits use the standard failure envelope.
4. `fix` integrates cleanly with existing `check` and `pr-check` contracts.

## Notes

Source spec: `docs/context/backlog/p2-bear-fix-generated-only.md`
