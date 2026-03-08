---
id: p2-generated-structural-tests
title: Generated structural tests
status: done
priority: high
commitment: committed
milestone: P2
---

## Summary

Generate deterministic structural evidence tests that lock dependency direction and covered structural reach semantics as part of the normal BEAR workflow.

## Why

This strengthened BEAR's post-preview story by turning structural governance into explicit test evidence instead of relying only on prose or CLI narratives.

## In Scope

- generated structural direction and reach tests
- stable structural signal line format for grep-friendly evidence
- evidence-first default behavior with strict mode opt-in
- docs and package updates for the new structural-test contract

## Out of Scope

- impl-path style policing
- subjective structural taste rules
- changing the core exit taxonomy just for structural evidence

## Done When

1. Structural tests are generated deterministically from canonical ordering.
2. Structural signal output is stable, single-line, and grep-friendly.
3. Default behavior emits evidence without forcing failures, while strict mode remains opt-in.
4. Docs and package references explain the contract without changing existing JVM behavior.

## Notes

Historical source: `docs/context/archive/archive-state-history.md`
Strict mode remains opt-in via `-Dbear.structural.tests.strict=true`.
