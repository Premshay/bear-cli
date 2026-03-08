---
id: p3-optional-deterministic-policy-hooks
title: Optional deterministic policy hooks
status: queued
priority: medium
commitment: committed
milestone: P3
---

## Summary

Allow projects to run a small set of deterministic custom checks without turning BEAR into a policy engine.

## Why

This can widen BEAR's applicability, but only if the hook surface stays narrow enough to remain deterministic, auditable, and supportable.

## In Scope

- project-provided deterministic checks or scripts invoked through a narrow BEAR-owned hook contract
- stable mapping from hook failures into existing BEAR categories or lanes
- clear reproducibility and timeout rules for hook execution

## Out of Scope

- an open-ended plugin policy engine
- non-deterministic remote integrations
- authority inversion where hooks redefine BEAR core semantics

## Done When

1. The hook contract is narrow enough to stay deterministic and supportable.
2. A project can add at least one custom deterministic check without changing BEAR core semantics.
3. Failures remain explainable through stable BEAR lanes, codes, paths, and remediation.

## Notes

Source spec: `docs/context/backlog/p3-optional-deterministic-policy-hooks.md`

## Decision Locks

1. Hooks are optional and explicitly configured.
2. Hook execution must be deterministic, reproducible, and locally auditable.
3. Hook outcomes map into stable BEAR result categories instead of inventing a parallel taxonomy.