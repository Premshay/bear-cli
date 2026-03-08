---
id: p3-maven-allowed-deps-containment
title: Maven allowed-deps containment parity
status: queued
priority: medium
commitment: committed
milestone: P3
---

## Summary

Add parity support for allowed-deps containment on Java and Maven projects, matching the existing Java and Gradle governance and enforcement model.

## Why

This is useful compatibility work, but it remains lower priority than the capability-first P3 slices already at the front of the queue.

## In Scope

- Maven target detection contract for containment enforcement
- generated Maven entrypoint and wiring artifacts
- marker location, format, and hash handshake equivalent to the Gradle flow
- deterministic `bear check` failure paths for missing or stale wiring

## Out of Scope

- non-Java build tools
- runtime purity proof
- cross-language dependency modeling

## Done When

1. Non-allowlisted library usage in impl fails under the Maven containment compile path.
2. Allowlisted allowed deps compile successfully.
3. Marker missing or stale yields deterministic `bear check` failure.
4. Fresh marker yields `bear check: OK`.
5. `pr-check` allowed-deps delta behavior remains unchanged.

## Notes

Source spec: `docs/context/backlog/p3-maven-allowed-deps-containment.md`
- This remains an optional future expansion relative to the already shipped Java and Gradle path.