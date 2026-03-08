# BEAR Future Ideas (Parking Lot)

Anything listed here is explicitly not part of the active roadmap queue.
Concrete parked roadmap items now live in minimap under `roadmap/ideas/*.md`.
Use this file only for broader idea families that are not yet promoted into individual roadmap items.

Parked spec-backed future items already promoted into minimap include:
- `future-target-adaptable-cli-node`
- `future-node-containment-profile`
- `future-spec-to-bear-ir-lowering`
- `future-compile-package-customization`
- `future-optional-scalar-inputs`
- `p1-preview-closure-gaps`

## Broad Future Idea Families

### Spec -> BEAR IR Lowering

- LLM-assisted transformation from feature specs to BEAR IR
- Validation that spec-derived IR matches domain constraints
- Diffing intent vs IR
- Partial regeneration when spec changes

### LLM Integration (Optional Layer)

- "Implement block" helper command
- Iterative agent loop inside CLI
- Agent plugin adapters (Codex, Copilot, Claude, etc.)
- Auto-fix suggestions when `bear check` fails

BEAR core must remain deterministic and agent-agnostic.

### Cross-Block / System Modeling

- Multi-block graph
- Dependency modeling
- Cross-block invariants
- Event-driven flows
- Microservice boundary modeling
- System-level IR

Not part of v0.

### Extended Invariants

- Referential integrity checks
- Concurrency invariants
- Balance consistency across aggregates
- Domain-specific invariant catalog
- Custom invariant plugins

v0 supports only:
- non_negative(field=...)

### Capability / Effects Model Expansion

- Effect types (read/write/network/event)
- Side-effect classification
- Security policies
- Policy-based effect enforcement
- Forbidden dependency scanning
- Integration with static analysis tools
- Capability contract metadata:
  - allowed event types
  - allowed destinations or topics
  - operation mode constraints (read/write/etc.)
  - schema or version constraints

v0 supports only:
- simple allowlist of effects

### Side-Effect Taxonomy (v1 Candidate)

Principle:
- side-effect gating, not library gating
- pure or internal libraries stay allowed
- external reach and escape hatches are governed

Candidate cross-language categories:
- network
- database
- messaging
- filesystem
- process
- time or random (policy-dependent)
- reflection or escape-hatch

Success target:
- agents can use pure libraries freely
- new external side effects require declared boundary or IR changes
- policy remains small and stable

### Boundary Usage Constraints (Post-v0)

This track addresses the gap between:
- capability allowance ("can call this")
- capability usage semantics ("how this is used")

Candidate constraint types (optional, narrow, boundary-focused):
- max or expected call counts per capability op
- outcome-coupled constraints (must or must-not call on success or failure)
- exactly-once style interaction expectations where meaningful
- interaction ordering constraints for specific boundary events

Non-goal:
- full business-behavior specification DSL

### Operation-Set Governance Precision (Future Expansion Candidate)

Current baseline:
- IR v1 includes first-class `block.operations` with per-operation contract or usages and block-level boundary authority.
- operation usage is constrained by block effects, idempotency capability, and allowed invariant set.

Candidate direction:
- improve precision of operation-level governance signals
- strengthen cross-operation boundary diagnostics without introducing router-style contracts

### Multi-Target Support

- Kotlin target
- TypeScript target
- Python target
- Go target
- Framework integrations (Spring, Micronaut, etc.)

v0 supports:
- JVM (Java) only

Parked discovery note:
- the current spec-backed Node discovery recommends keeping Node support parked unless the product explicitly accepts the narrow `node-ts-pnpm-single-package-v1` profile documented in `docs/context/backlog/future-node-containment-profile.md`

### Plugin Architecture

- External invariant providers
- External target providers
- Extension SPI
- Third-party effect packs

### UI / Visualization

- Block graph visualization
- IR inspector
- Generated artifact viewer
- IDE plugin

### Self-Hosting BEAR

- Kernel isolation proof
- Formal bootstrapping model

Not before v1+.

### Packaging & Distribution

- Standalone binary distribution
- Homebrew install
- Docker distribution
- Maven plugin
- Gradle plugin

v0 can run as plain CLI only.

### Enterprise Features

- Policy enforcement modes
- Signed IR files
- Drift audit logs
- CI integration templates

### Research Ideas

- Formal verification integration
- Constraint solving backends
- Deterministic replay models
- Stronger semantic guarantees

If something feels exciting but does not directly contribute to:

"Naive withdraw fails. Correct withdraw passes."

It belongs here.
