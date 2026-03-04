# Model

For rationale see [FOUNDATIONS.md](FOUNDATIONS.md). For guarantees/signals see [ENFORCEMENT.md](ENFORCEMENT.md).

## Vocabulary

See also: [TERMS.md](TERMS.md) (effects/ports/ops).

- `block`: one governed unit of logic.
- `IR`: YAML declaration of block contract, effects, and selected semantics.
- `port kind`: dependency-kind on a port entry (`external` uses `ops`; `block` uses `targetBlock` + `targetOps`).
- `block.kind`: unchanged in v1 (`logic`).
- `compile`: deterministic generation of BEAR-owned artifacts.
- `check`: deterministic local gate for drift, boundary rules, and tests.
- `pr-check`: deterministic base-vs-head governance classification.
- `unblock`: clears advisory `check` blocked marker after lock/bootstrap IO failures.
- `determinism`: same inputs produce the same output lines, files, and exit behavior.
- `agent loop`: BEAR command sequence executed by the agent while implementing specs.
- `developer visibility`: PR/CI signals BEAR emits for review and governance.

## Agent execution model

1. Agent updates block boundaries and contract in IR from project specs.
2. Agent runs `compile` (or `fix`) to materialize deterministic generated artifacts.
3. Agent runs `check --collect=all --agent` to enforce sync/policy and receive one deterministic `nextAction`.
4. Agent executes only BEAR commands listed in `nextAction.commands`, then reruns until `status=ok`.
5. Agent runs `pr-check --base <ref> --collect=all --agent` to classify boundary changes against base.

Core flow: `IR -> compile -> check --agent`

Governance flow: `pr-check --base <ref> --agent`

## Who edits IR?

In the intended workflow, developers are not expected to hand-author IR as routine work.
IR is a control surface that the agent updates when it needs new boundary authority.
Developers primarily review the resulting governance signals.

## Developer visibility model

- PR signal: `pr-check` marks boundary-expanding vs ordinary changes.
- CI signal: deterministic exit codes and output shape support stable gates.
- Local signal: deterministic ordering and paths make failures fast to triage.

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)

