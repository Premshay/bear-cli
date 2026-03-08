---
id: future-spec-to-bear-ir-lowering
title: Spec to BEAR IR lowering
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Summary

Shorten the path from feature intent to BEAR IR by introducing a constrained spec-to-IR lowering workflow.

## Why

This is one of the highest-value future adoption features because it could let teams stay in domain language while still arriving at deterministic BEAR contracts.

## In Scope

- transforming narrow feature specs into candidate BEAR IR
- validating that lowered IR matches current BEAR constraints
- diffing intent versus IR when they diverge
- partial regeneration or update flows when the source spec changes

## Out of Scope

- hidden non-deterministic generation inside the BEAR core
- replacing explicit IR review with opaque automation
- broad behavior DSL expansion

## Done When

1. A narrow spec input can produce reviewable candidate BEAR IR deterministically enough to be useful.
2. The lowered IR still goes through the normal validate, compile, and check workflow.
3. Divergence between source spec and BEAR IR is explainable and review-visible.

## Notes

This remains an idea-level roadmap item only; there is no detailed backlog spec yet.
