---
id: future-compile-package-customization
title: Compile package customization
status: queued
priority: low
commitment: uncommitted
milestone: Future
---

## Summary

Allow `bear compile` to generate under a caller-chosen base package while preserving deterministic naming and BEAR ownership boundaries.

## Why

This is useful customization work, but it remains future-scoped because it does not currently beat the active capability-first queue on product value.

## In Scope

- add `--base-package <pkg>` to `bear compile`
- generate under a caller-chosen base package instead of the fixed default root
- preserve deterministic package and name sanitization rules
- preserve the two-tree ownership model between generated BEAR code and user-owned implementation code

## Out of Scope

- relaxation of deterministic naming rules
- per-file package overrides
- changes to compile or check ownership and drift contracts

## Done When

1. Package customization is explicit and deterministic.
2. Generated package paths remain reproducible from the same inputs.
3. Existing compile and check ownership and drift contracts continue to hold.

## Notes

Source spec: `docs/context/backlog/future-compile-package-customization.md`