---
id: p2-minimal-taste-invariants-rule-pack
title: Minimal taste-invariants rule pack
status: queued
priority: medium
commitment: committed
milestone: P2
---

## Summary

Add a small deterministic rule pack for obviously bad structural patterns without expanding BEAR into subjective style policing.

## Why

There is still value in a narrow hardening slice, but it should remain clearly bounded so BEAR does not drift into a general lint framework.

## In Scope

- deterministic layout and naming invariants for BEAR-owned files
- size and structure constraints on generated zones
- forbidden dependency edges between selected packages or modules where the contract is BEAR-owned and machine-checkable
- stable failure mapping and remediation for any new findings

## Out of Scope

- broad style or formatting linting
- repo-wide taste enforcement across user-owned implementation code beyond clearly governed boundaries
- subjective heuristics that are hard to explain or reproduce

## Done When

1. The initial rule pack is small, explicit, and deterministic.
2. New findings are stable in ordering, path rendering, and remediation text.
3. Existing runtime contracts and exit taxonomy remain unchanged unless an already existing lane is reused.
4. Docs and tests pin the intended rule scope so the feature does not drift into subjective policy.

## Notes

Source spec: `docs/context/backlog/p2-minimal-taste-invariants-rule-pack.md`

## Decision Locks

1. Rules must be deterministic, machine-checkable, and repo-local.
2. Rules should prefer BEAR-owned or generated surfaces before touching user-owned code.
3. Findings must fit existing deterministic failure-envelope behavior.
4. This feature is a narrow rule pack, not a general lint framework.