# BEAR v0 Roadmap

This roadmap is strictly for v0.
If something is not listed here, it does not get built.

---

## Phase 0 -- Project Setup (bear-cli)

- [ ] Create Gradle multi-module project
- [ ] Create `kernel` module (trusted seed)
- [ ] Create `app` module (CLI wrapper)
- [ ] Ensure CLI entrypoint runs: `bear --help`
- [ ] Add JUnit 5 test setup
- [ ] Add README
- [ ] Add ARCHITECTURE.md (locked)

Milestone: CLI builds and runs, no BEAR logic yet.

---

## Phase 1 -- BEAR IR Foundation (kernel)

Goal: Deterministic parsing + validation.

- [ ] Define `BlockModel`
  - name
  - kind
  - contract (inputs/outputs)
  - effects.allow
  - idempotency
  - invariants (non_negative only)

- [ ] Add YAML parsing (SnakeYAML)
- [ ] Implement schema validation
- [ ] Implement semantic validation
- [ ] Implement normalization (sorted effects, canonical form)
- [ ] Implement `bear validate <file>`

Milestone: `bear validate withdraw.bear.yaml` succeeds/fails deterministically.

---

## Phase 2 -- Early Self-Hosting (Controlled)

Goal: BEAR generates part of BEAR (pure logic only).

Rules:
- Kernel remains trusted seed.
- No CLI, filesystem, or generation logic is self-hosted.
- Only pure deterministic logic blocks may be self-hosted.

- [ ] Create `bear/` folder inside bear-cli
- [ ] Define first BEAR block: `NormalizeIr`
  - Input: raw IR
  - Output: canonical IR
  - effects.allow: none
  - invariants: deterministic output

- [ ] Use BEAR to generate its skeleton + tests
- [ ] Implement NormalizeIr logic via BEAR-generated impl file
- [ ] Replace kernel normalization call with generated block call (adapter layer)

Milestone: Part of normalization logic is BEAR-generated and enforced.

This is the first proof of self-hosting.

---

## Phase 3 -- JVM Target (Deterministic Codegen)

Goal: Generate enforcement artifacts for demo projects.

- [ ] Define `Target` interface
- [ ] Implement `JvmTarget`

Generation must produce:

- [ ] Skeleton class (non-editable)
- [ ] Implementation stub (if missing)
- [ ] Port interface derived from effects.allow
- [ ] JUnit test templates:
  - idempotency
  - non_negative invariant

- [ ] Output to `build/generated/bear`
- [ ] Ensure deterministic generation

Milestone: `bear compile` creates compilable artifacts.

---

## Phase 4 -- Two-File Enforcement

Goal: Prevent drift.

- [ ] Skeleton and impl separated
- [ ] Skeleton always regenerated
- [ ] Impl preserved
- [ ] Drift detection (fail if generated artifacts differ unexpectedly)

Milestone: Manual edits to skeleton are rejected or overwritten.

---

## Phase 5 -- bear check

Goal: Single deterministic enforcement gate.

- [ ] Implement `bear check --project <path>`
  - validate IR
  - compile artifacts
  - invoke Gradle tests
- [ ] Fail on:
  - invalid IR
  - generation drift
  - invariant violation
  - idempotency violation

Milestone: One command enforces BEAR guarantees.

---

## Phase 6 -- Demo (bear-demo)

Goal: Prove value.

- [ ] Create simple bank account domain
- [ ] Write Withdraw BEAR IR
- [ ] Implement naive Withdraw
- [ ] Confirm `bear check` fails
- [ ] Fix implementation
- [ ] Confirm `bear check` passes

Milestone: Clear before/after demonstration.

---

## Explicitly Not in v0

- Spec -> IR lowering
- LLM inside BEAR core
- Cross-service modeling
- Multi-language targets
- Plugin architecture
- UI support
- Rich invariant catalog
- Full self-hosting of kernel
- Rewriting CLI wiring using BEAR

If it does not contribute to:
"Naive withdraw fails. Correct withdraw passes."
It is out of scope.
