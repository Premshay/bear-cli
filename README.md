# BEAR (bear-cli)

Multi-module Java 17 Gradle project for the `bear` CLI.

- Start here: `doc/START_HERE.md`

- `kernel`: deterministic core. Contains BEAR IR parsing (YAML), validation, normalization, and target abstractions. This module is trusted seed code and is never BEAR-generated.
- `app`: CLI wrapper. Exposes `bear validate`, `bear compile`, and `bear check`. BEAR may later self-host parts of `app`, but never `kernel`.

Docs:
- `doc/STATE.md` (current focus + next steps)
- `doc/ARCHITECTURE.md` (what BEAR is + v0 scope)
- `doc/ROADMAP.md` (v0 phases + definition of done)
- `doc/PROJECT_LOG.md` (background + major decisions)
- `doc/FUTURE.md` (explicitly out-of-scope ideas)
- `doc/PROMPT_BOOTSTRAP.md` (paste into a fresh AI session to restore context)
