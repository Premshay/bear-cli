---
id: future-target-adaptable-cli-node
title: Target-adaptable CLI and initial Node/TypeScript target
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Summary

Continue the parked multi-target initiative by adding an initial Node and TypeScript target behind the new deterministic target seam.

## Why

This is one of BEAR's highest-value future expansions because it broadens the product beyond JVM while keeping the same deterministic governance model.

## In Scope

- target selection and pinning that remain deterministic
- `NodeTarget` compile, drift, containment, and `pr-check` participation
- covered undeclared reach for selected Node surfaces
- repo-level Node dependency governance and later Node verification-runner work
- minimal target-profile and troubleshooting docs updates

## Out of Scope

- IR schema changes to add a target field
- exit-code registry expansion
- a full target-neutral package rewrite in one sweep

## Done When

1. JVM behavior remains unchanged and fully green.
2. Target selection is deterministic and pin-able when needed.
3. Node can compile deterministically, pass drift and containment checks, and participate in `pr-check` boundary governance.
4. Core CLI orchestration stays free of scattered JVM or Node conditionals.

## Notes

Source spec: `docs/context/backlog/future-target-adaptable-cli-node.md`
Containment/profile discovery: `docs/context/backlog/future-node-containment-profile.md`
Recommendation lock: keep Node parked unless the team explicitly accepts the narrow `node-ts-pnpm-single-package-v1` profile and its `ENFORCED / PARTIAL / NOT_SUPPORTED` boundary.
