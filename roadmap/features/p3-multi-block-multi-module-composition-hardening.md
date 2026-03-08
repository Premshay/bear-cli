---
id: p3-multi-block-multi-module-composition-hardening
title: Multi-block and multi-module composition hardening
status: queued
priority: high
commitment: committed
milestone: P3
---

## Summary

Strengthen BEAR's deterministic behavior for repos that span multiple blocks and multiple modules without weakening boundary visibility or drift reporting.

## Why

BEAR needs to stay stable on more realistic composed repo layouts before its broader capability story will feel credible outside the current baseline shape.

## In Scope

- cross-block dependency constraints where composition should remain governed
- repo-wide drift reporting improvements for multi-root or multi-module layouts
- repo-wide boundary-expansion reporting that remains stable across composed projects

## Out of Scope

- system-level IR redesign in this slice
- orchestration platform or deployment modeling
- relaxation of existing deterministic compile, check, or pr-check contracts

## Done When

1. Composition rules are explicit and deterministic across supported repo layouts.
2. Repo-wide reporting remains stable, ordered, and reproducible.
3. Tests cover representative multi-block and multi-module cases, including cross-root governance visibility.

## Notes

Source spec: `docs/context/backlog/p3-multi-block-multi-module-composition-hardening.md`