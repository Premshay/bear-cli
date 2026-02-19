# BEAR Package Docs

`doc/bear-package/` is the source of truth for BEAR-distributed workflow docs copied into adopter repos.

In scope here:
- `BEAR_PRIMER.md`
- `AGENTS.md` (optional template)
- `AGENTS_SHIM.md` (one-line snippet for existing project `AGENTS.md`)
- `BEAR_AGENT.md`
- `WORKFLOW.md`

Out of scope here:
- demo-domain app specs
- evaluator scenarios/runbooks

Those artifacts are owned by adopter repos or evaluator docs (`doc/m1-eval/*`).

## Packaging Note: `AGENTS.md` Is Optional

Many projects already have a root `AGENTS.md`.

In that case:
- do not replace project-owned `AGENTS.md`
- append the one-line shim from `AGENTS_SHIM.md`
- include `BEAR_AGENT.md` and `WORKFLOW.md` as BEAR-distributed assets
