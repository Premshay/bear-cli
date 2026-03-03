# PR / CI Review Guide

BEAR is designed so humans can review agent changes via deterministic signals, without needing to understand every implementation detail.

If you are new to BEAR vocabulary (effects/ports/ops), start with [TERMS.md](TERMS.md).

## The two gates

In a PR or CI job, the canonical pair is:

```text
bear check --all --project <repoRoot>
bear pr-check --all --project <repoRoot> --base <ref>
```

`check` answers: "is the repo consistent with the declared boundary and generated artifacts, and do tests pass?"

`pr-check` answers: "did the declared boundary expand compared to base?"

## How to interpret `pr-check`

### `exit 0`: no boundary expansion detected
You should still review implementation as usual, but BEAR is not warning about increased boundary authority.

### `exit 5`: boundary expansion detected
This means the IR delta widened boundary authority (for example, new effect ports/ops, new operation entrypoints, new/relaxed invariants, idempotency usage changes).

You will see `pr-delta:` lines, then a boundary verdict:

```text
pr-delta: BOUNDARY_EXPANDING: ...
pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
CODE=BOUNDARY_EXPANSION
PATH=<ir-file>
REMEDIATION=Review boundary-expanding deltas and route through explicit boundary review.
```

Action:
- treat it as an explicit governance event
- accept (with intent) or revert

### `exit 7`: boundary bypass detected
This is not "a policy disagreement"; it is a structural bypass signal.

Example bypass line:

```text
pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <relative/path>: KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <interfaceFqcn> -> <implClassFqcn>
```

Action:
- fix the code shape (move/split adapters, remove forbidden seams)

## Where the details live

- Exact output shapes and ordering guarantees: [output-format.md](output-format.md)
- Exit code registry: [exit-codes.md](exit-codes.md)
- Full `pr-check` contract: [commands-pr-check.md](commands-pr-check.md)
- Governance policy (normative, maintainer doc): [docs/context/governance.md](../context/governance.md)
