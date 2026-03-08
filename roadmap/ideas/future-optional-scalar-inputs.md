---
id: future-optional-scalar-inputs
title: Optional scalar inputs in BEAR IR
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Summary

Support truly optional scalar input fields in BEAR IR and generated contracts so presence versus absence does not require sentinel values or custom transport workarounds.

## Why

The current workaround leaks encoding tricks into domain logic, which weakens the BEAR contract and makes agent-generated code less honest about optional inputs.

## In Scope

- IR support for optional scalar fields such as string, int, and enum
- validator and code-generation support for explicit presence or absence
- generated request or accessor contracts that preserve absence distinctly
- focused docs and tests around optional string input first

## Out of Scope

- collection or union type systems
- broad redesign of BEAR value semantics
- changes to current required-field behavior

## Done When

1. Optional scalar inputs are representable in IR with a narrow explicit syntax.
2. Generated code can distinguish absence from present-empty or placeholder values.
3. The transaction-note scenario no longer needs sentinel or Base64-style workarounds.
4. Existing required-field behavior remains unchanged.

## Notes

Source spec: `docs/context/backlog/future-optional-scalar-inputs.md`
