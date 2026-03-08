# Context Coverage Map

Purpose:
- No-loss mapping for context refactors and compaction.
- Every critical topic has a canonical destination.

| Topic | Canonical active destination | Archive destination (if historical) |
| --- | --- | --- |
| Session handoff protocol | `docs/context/state.md`, `docs/context/start-here.md` | `docs/context/archive/archive-state-history.md` |
| Milestone status and ordered queue | `roadmap/board.md`, `roadmap/scope.md` | `docs/context/archive/archive-state-history.md` |
| Completed roadmap history | `roadmap/features/*.md`, `roadmap/board.md` | `docs/context/archive/archive-state-history.md` |
| Milestone definitions and done criteria | `docs/context/roadmap.md` | `docs/context/archive/archive-state-history.md` |
| IR schema/normalization/semantic rule | `docs/context/ir-spec.md` | n/a |
| Governance diff classification and enforcement intent | `docs/context/governance.md` | `docs/context/archive/archive-state-history.md` |
| Safety cleanup/deletion guardrails | `docs/context/safety-rules.md` | `docs/context/archive/archive-state-history.md` |
| Operator command/failure guidance | `docs/context/user-guide.md` | `docs/context/archive/archive-state-history.md` |
| Architecture scope lock and non-goals | `docs/context/architecture.md` | `docs/context/archive/archive-state-history.md` |
| Historical rationale trail | `docs/context/project-log.md` | `docs/context/archive/archive-state-history.md` |
| Archive policy and retained history | `docs/context/archive/archive-readme.md`, `docs/context/archive/archive-state-history.md` | git history |

Compaction contract:
1. No section may be removed unless mapped here.
2. Historical details move to archive or remain recoverable through git history.
3. Active docs keep only current canonical wording.
