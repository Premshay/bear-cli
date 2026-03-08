---
id: future-node-containment-profile
title: Honest Node/TypeScript containment profile
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Summary

Define the smallest honest Node and TypeScript profile BEAR could support later without overclaiming containment or runtime guarantees.

## Why

The target seam is ready, but actual Node support is only worth pursuing if the supported profile stays narrow enough to keep BEAR deterministic and truthful.

## In Scope

- narrow supported repo and toolchain profile for a future Node target
- governed roots and import-containment rules
- repo-level dependency-governance expectations
- project-verification contract
- explicit `ENFORCED / PARTIAL / NOT_SUPPORTED` capability matrix

## Out of Scope

- implementing `NodeTarget`
- adding target-selection UX or `.bear/target.id`
- IR schema changes for Node semantics
- broad Node ecosystem support or workspace support

## Done When

1. BEAR has a parked, spec-backed Node profile that is narrow, deterministic, and product-honest.
2. The profile clearly states governed roots, verification contract, and covered power surfaces.
3. The profile explicitly marks unsupported and only-partial areas so Node support is not overclaimed.

## Notes

Source spec: `docs/context/backlog/future-node-containment-profile.md`
Recommended profile: `node-ts-pnpm-single-package-v1`
