# BEAR (bear-cli) -- Agent Notes

If you are an AI coding assistant (or a human picking up the repo), start here:

1. Read `doc/STATE.md` (source of truth for current work + next steps).
2. Read `doc/START_HERE.md`.
3. Read `doc/IR_SPEC.md` for canonical v0 IR structure and constraints.
4. If this is a fresh session, use `doc/PROMPT_BOOTSTRAP.md` (paste the SHORT section first).
5. If you need historical rationale, read `doc/PROJECT_LOG.md`.

Guardrails (v0):
- Keep `kernel/` deterministic and small (trusted seed). No LLM/agent logic in core.
- Stay within v0 scope defined in `doc/ARCHITECTURE.md` + `doc/ROADMAP.md`.
- Treat `doc/FUTURE.md` as explicitly out-of-scope unless the user asks.
- Prefer the two-file approach (generated skeleton + separate impl) and deterministic enforcement via `bear check`.

Session hygiene:
- Update `doc/STATE.md` whenever work progresses (Last Updated, Current Focus, Next Concrete Task).
- Update `doc/PROJECT_LOG.md` only for major architectural shifts/decisions.
