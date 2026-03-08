---
id: p2-declared-allowed-deps-containment
title: Declared allowed-deps containment
status: done
priority: high
commitment: committed
milestone: P2
---

## Summary

Allow block implementation logic to use explicitly allowlisted dependencies while keeping dependency-power expansion visible and deterministic.

## Why

This delivered the first strong containment story for BEAR-owned implementation boundaries on Java and Gradle, including generated containment wiring and deterministic marker verification.

## In Scope

- `block.impl.allowedDeps` declaration in IR
- deterministic compile outputs for containment config and Gradle entrypoint wiring
- marker handshake and `bear check` verification for stale or missing containment wiring
- `pr-check` boundary governance for allowed-deps additions and changes

## Out of Scope

- wildcard or range dependency rules
- non-Java build tools
- runtime purity proofs

## Done When

1. BEAR compile emits deterministic containment config and wiring outputs.
2. Missing or stale containment markers yield deterministic `bear check` failures.
3. Fresh containment markers produce green `bear check` behavior.
4. `pr-check` continues to treat allowed-deps additions and changes as boundary expansion.

## Notes

Source spec: `docs/context/backlog/p2-declared-allowed-deps-containment.md`
Completed for Java and Gradle in P2; Maven parity remains a separate queued item.
