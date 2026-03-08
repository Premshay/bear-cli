---
id: p3-capability-templates
title: Capability templates
status: queued
priority: high
commitment: committed
milestone: P3
---

## Summary

Provide deterministic starter packs that generate common capability scaffolding so agents and developers can start from governed templates instead of ad hoc boundary code.

## Why

Capability templates are one of the clearest product-facing ways to turn BEAR's governance model into a faster, more usable starting point for real work.

## In Scope

- template packs that generate ports, effects scaffolding, invariants, structural tests, and standard stubs
- deterministic generation only; templates must produce reproducible BEAR-owned output
- template selection and emitted artifacts aligned with existing IR and compile ownership rules

## Out of Scope

- template-specific runtime framework coupling in the kernel
- agent-specific generation behavior
- IR expansion solely to support template convenience

## Done When

1. Template packs generate deterministic scaffolding for at least one clearly useful capability shape.
2. Generated artifacts preserve the existing two-tree ownership model.
3. Template output integrates with existing compile, check, and structural-test contracts.

## Notes

Source spec: `docs/context/backlog/p3-capability-templates.md`