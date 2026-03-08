---
id: p3-target-adaptable-cli-preparation
title: Target-adaptable CLI preparation
status: done
priority: high
commitment: committed
milestone: P3
---

## Summary

Prepare BEAR CLI for multi-target support by extracting one deterministic target seam while preserving current JVM behavior byte-for-byte.

## Why

This slice created the architectural seam needed for future multi-target work without scattering target conditionals through the core or changing current JVM contracts.

## In Scope

- contract freeze and parity coverage for current JVM behavior
- one deterministic kernel-owned target seam with `TargetId`, `Target`, and `TargetRegistry`
- moving JVM-specific generation, scanning, and verification behind `JvmTarget`

## Out of Scope

- any `NodeTarget` implementation in this slice
- TypeScript, pnpm, or Node import-containment work
- target selection or pinning UX such as `.bear/target.id`
- public command-surface expansion

## Done When

1. Existing JVM command behavior remains byte-identical on pinned parity tests.
2. Core command services do not reference JVM-only scanners or path conventions directly outside the target dispatch seam.
3. No generated JVM layout or CLI contract changes are introduced by the refactor.
4. The seam is sufficient to let a later `NodeTarget` land without reopening core orchestration design.

## Notes

Source spec: `docs/context/backlog/p3-target-adaptable-cli-preparation.md`

## Follow-On Work

- initial `NodeTarget` scan-only support
- Node covered undeclared reach
- Node dependency governance
- Node project verification runner
- target-profile docs and stabilization follow-ups
