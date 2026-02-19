# BEAR Block Index (v0)

## Purpose

`bear.blocks.yaml` is the source of truth for BEAR-managed blocks in repo-level `--all` enforcement.
v0 does not support discovery-based inclusion.

## File

Default location:
- `<repoRoot>/bear.blocks.yaml`

Override:
- `--blocks <path>` (repo-relative)

## Schema (v0)

Root fields:
- `version` (required, must be `v0`)
- `blocks` (required, non-empty list)

Each block entry:
- `name` (required, unique, regex `[a-z][a-z0-9-]*`)
- `ir` (required, repo-relative path to IR YAML)
- `projectRoot` (required, repo-relative directory path)
- `enabled` (optional, default `true`)

## Validation Rules

- fail on missing/invalid required fields
- fail on duplicate block names
- fail on non repo-relative paths
- fail when path escapes repo root
- v0 hard constraint: enabled blocks must have unique `projectRoot`

## Canonicalization

- execution and output order is canonical: sort selected blocks by `name`
- output paths use `/` separators

## Marker Path Invariant (v0)

Canonical marker path per enabled block:
- `<projectRoot>/build/generated/bear/bear.surface.json`

This path is a v0 BEAR-owned contract path.
Changing it is a breaking change and requires a spec/version update.

## Strict Orphan Mode

`--strict-orphans` performs repo-wide marker scan for:
- `**/build/generated/bear/bear.surface.json`

Any found marker not mapped to an enabled block index entry is an orphan and fails the command.

Without `--strict-orphans`, v0 enforces index validity only (no repo-wide marker scan).
