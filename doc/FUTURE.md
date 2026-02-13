# BEAR Future Ideas (Parking Lot)

Anything listed here is explicitly NOT part of v0.

These ideas are intentionally deferred to protect scope.

---

## Spec -> BEAR IR Lowering

- LLM-assisted transformation from feature specs to BEAR IR
- Validation that spec-derived IR matches domain constraints
- Diffing spec vs IR
- Partial regeneration when spec changes

---

## LLM Integration (Optional Layer)

- "Implement block" helper command
- Iterative agent loop inside CLI
- Agent plugin adapters (Codex, Copilot, Claude, etc.)
- Auto-fix suggestions when `bear check` fails

BEAR core must remain deterministic and agent-agnostic.

---

## Cross-Block / System Modeling

- Multi-block graph
- Dependency modeling
- Cross-block invariants
- Event-driven flows
- Microservice boundary modeling
- System-level IR

Not part of v0.

---

## Extended Invariants

- Referential integrity checks
- Concurrency invariants
- Balance consistency across aggregates
- Domain-specific invariant catalog
- Custom invariant plugins

v0 supports only:
- non_negative(field=...)

---

## Capability / Effects Model Expansion

- Effect types (read/write/network/event)
- Side-effect classification
- Security policies
- Policy-based effect enforcement
- Forbidden dependency scanning
- Integration with static analysis tools
- Capability contract metadata:
  - allowed event types
  - allowed destinations/topics
  - operation mode constraints (read/write/etc.)
  - schema/version constraints

v0 supports only:
- simple allowlist of effects

---

## Boundary Usage Constraints (Post-v0)

This track addresses the gap between:
- capability allowance ("can call this")
- capability usage semantics ("how this is used")

Candidate constraint types (optional, narrow, boundary-focused):
- max/expected call counts per capability op
- outcome-coupled constraints (must/must-not call on success/failure)
- exactly-once style interaction expectations where meaningful
- interaction ordering constraints for specific boundary events

Enforcement direction:
- deterministic validation/normalization in IR layer
- generated policy assertion tests in target scaffolding
- CI-visible signals for interaction-pattern changes

Non-goal:
- full business-behavior specification DSL

---

## Multi-Target Support

- Kotlin target
- TypeScript target
- Python target
- Go target
- Framework integrations (Spring, Micronaut, etc.)

v0 supports:
- JVM (Java) only

---

## Plugin Architecture

- External invariant providers
- External target providers
- Extension SPI
- Third-party effect packs

---

## UI / Visualization

- Block graph visualization
- IR inspector
- Generated artifact viewer
- IDE plugin

---

## Self-Hosting BEAR

- Kernel isolation proof
- Formal bootstrapping model

Not before v1+.

---

## Packaging & Distribution

- Standalone binary distribution
- Homebrew install
- Docker distribution
- Maven plugin
- Gradle plugin

v0 can run as plain CLI only.

---

## Compile Package Customization

- Add `--base-package <pkg>` to `bear compile`
- Generate under `<base-package>.generated.<blockname-sanitized>` instead of fixed `com.bear.generated...`
- Keep deterministic package/name sanitization rules
- Preserve two-tree ownership model (generated tree + user-owned impl)

Deferred from v0 first slice to keep Phase 2 scope focused.

---

## Enterprise Features

- Policy enforcement modes
- Signed IR files
- Drift audit logs
- CI integration templates

---

## Research Ideas

- Formal verification integration
- Constraint solving backends
- Deterministic replay models
- Stronger semantic guarantees

---

If something feels exciting but does not directly contribute to:

"Naive withdraw fails. Correct withdraw passes."

It belongs here.
