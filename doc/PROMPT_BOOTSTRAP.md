# BEAR Project Bootstrap (For Non-Codex Sessions)

Use this when starting a chat session that does not have repo file access (for example ChatGPT).
Paste the SHORT block first. Paste LONG only if needed.

---

# SHORT BOOTSTRAP (Paste First)

We are building BEAR (Block Enforcement & Representation), a deterministic constraint compiler for backend blocks.

Core purpose:
- Agent-generated code is non-deterministic.
- Natural language specs are loose.
- Production systems need deterministic enforcement.

What BEAR does:
- Takes BEAR IR (strict intermediate representation for one logic block).
- Deterministically validates and normalizes IR.
- Deterministically compiles IR into:
  - non-editable skeletons
  - structured capability port interfaces
  - deterministic tests
- Enforces with one gate: `bear check` (validate + compile + test + drift detection).

v0 scope (locked):
- JVM/Java only.
- One logic block per IR file.
- Effects are structured ports (`effects.allow` with `port` + `ops[]`).
- Idempotency uses `key` plus store ops (`store.port/getOp/putOp`).
- Invariants: only `kind: non_negative` on output fields.

v0 guarantees:
- Structural contract enforcement.
- Structural effect-boundary enforcement via generated ports.
- Deterministic invariant/idempotency test gating.
- Drift detection for generated artifacts.

v0 non-guarantees:
- Business correctness beyond declared invariants.
- Real DB/concurrency/transaction semantics.
- Runtime enforcement beyond test harness.
- Concurrency-safe duplicate handling (v0 idempotency is deterministic replay safety only).

Demo proof target:
- Naive Withdraw fails `bear check`.
- Corrected Withdraw passes `bear check`.

Current Phase:
[UPDATE EACH SESSION]

Session Goal:
[STATE SINGLE TASK]

Done Criteria (optional):
[STATE 1-2 CHECKS]

Constraints for this session:
- Do not add features beyond v0 scope.
- Do not expand IR expressiveness.
- Keep behavior deterministic.

Continue from here.

---

# LONG BOOTSTRAP (If More Context Is Needed)

BEAR is not:
- a full verifier
- a behavior DSL
- infrastructure simulation
- a replacement for developers

BEAR IR v0 canonical model:
- root: `version: v0`, `block`
- `block.kind` must be `logic`
- `contract.inputs` and `contract.outputs` are typed fields
- `effects.allow` is a list of ports; each port has ops
- `idempotency.key` references an input
- `idempotency.store.port/getOp/putOp` reference declared effects
- `invariants` supports only:
  - `kind: non_negative`
  - `field` referencing an output
- unknown keys or invalid references must fail validation

Deterministic normalization requirements:
- canonical key ordering
- sorted inputs/outputs by name
- sorted ports by name
- sorted ops within each port
- deterministic invariant ordering

Two-file enforcement model:
- generated skeleton is non-editable
- implementation file is editable
- regeneration must not allow silent drift

Demo IR shape (canonical intent):
- one Withdraw logic block
- inputs: `accountId`, `amount`, `currency`, `txId`
- output: `balance`
- ports:
  - `ledger`: `getBalance`, `setBalance`
  - `idempotency`: `get`, `put`
- idempotency key: `txId`
- invariant: non-negative `balance`
