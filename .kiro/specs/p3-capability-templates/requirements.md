# Requirements: P3 Capability Templates

## Introduction

BEAR currently requires developers and agents to hand-author IR files, then run `bear compile`
to generate governed scaffolding. This works well for experienced users but creates a cold-start
problem: new blocks require knowledge of IR schema, port naming conventions, effect declarations,
and the two-tree ownership model before any useful code can be generated.

Capability templates solve this by providing deterministic starter packs that emit a complete,
governed block scaffold — IR file, generated artifacts, impl stub, structural tests, and
standard port/effect declarations — for common capability shapes. Templates are a kernel-level
concept: they produce reproducible, BEAR-owned output that integrates with the existing
`compile`, `check`, and structural-test contracts without expanding IR semantics or introducing
agent-specific behavior.

The initial scope targets at least one clearly useful capability shape (e.g., a CRUD-style
read/write block with a data store port) to prove the template pipeline end-to-end.

## Glossary

- **Capability_Template**: A named, deterministic recipe in the kernel that, given a block name
  and a target, emits a complete governed scaffold (IR file, generated artifacts, impl stub,
  structural tests, containment config) for a specific capability shape.
- **Template_Registry**: The kernel-level registry that maps template identifiers to their
  corresponding Capability_Template implementations. Provides deterministic lookup and
  enumeration.
- **Template_Pack**: The complete set of files emitted by a single Capability_Template
  invocation: IR file, generated source tree, structural tests, impl stub, surface manifest,
  wiring manifest, and containment artifacts.
- **Capability_Shape**: A named pattern of ports, effects, operations, and invariants that
  represents a common architectural building block (e.g., "read-store", "write-store",
  "read-write-store").
- **Two-Tree_Model**: BEAR's ownership split where generated artifacts live under
  `build/generated/bear/` (BEAR-owned, deterministic, regenerable) and implementation code
  lives under `src/main/java/blocks/<block>/impl/` (developer-owned, never overwritten).
- **Template_Emitter**: The kernel component that takes a resolved Capability_Template and
  a set of template parameters (block name, target) and produces the Template_Pack on disk.
- **Scaffold_Command**: The CLI command (e.g., `bear scaffold`) that invokes template
  selection and emission through the deterministic kernel pipeline.
- **IR_File**: The `spec/<block>.ir.yaml` file that declares the block's operations, effects,
  invariants, and boundary authority per the frozen v1 schema.
- **Structural_Test**: A generated JUnit test that verifies compile-time structural properties
  of the generated wrapper (direction tests, reach tests) without executing business logic.
- **Block_Key**: The canonical key used to identify a block within `bear.blocks.yaml` and
  across the compile/check pipeline.

---

## Requirements

### Requirement 1: Template registry provides deterministic template lookup

**User Story:** As a developer, I want to discover available capability templates by name,
so that I can select the right starting point for a new governed block.

#### Acceptance Criteria

1. THE Template_Registry SHALL expose a deterministic, sorted list of available
   Capability_Template identifiers.

2. WHEN a valid template identifier is provided, THE Template_Registry SHALL return the
   corresponding Capability_Template definition.

3. WHEN an unknown template identifier is provided, THE Template_Registry SHALL return an
   explicit "not found" result without throwing an unchecked exception.

4. THE Template_Registry SHALL produce identical enumeration results across repeated
   invocations with no registry mutations (idempotence property).

5. THE Template_Registry SHALL reside in the `kernel/` module and contain no LLM-specific
   or agent-specific logic.

### Requirement 2: Template emission produces a complete, deterministic scaffold

**User Story:** As a developer, I want a capability template to generate all the files I
need for a governed block in one step, so that I can start implementing business logic
immediately without manually authoring IR or running compile separately.

#### Acceptance Criteria

1. WHEN a Capability_Template is emitted for a given block name and target, THE
   Template_Emitter SHALL produce a valid IR_File that conforms to the frozen v1 schema.

2. WHEN a Capability_Template is emitted, THE Template_Emitter SHALL produce the same
   generated artifacts that `bear compile` would produce for the emitted IR_File
   (compile-equivalence property).

3. WHEN a Capability_Template is emitted, THE Template_Emitter SHALL produce an impl stub
   under the developer-owned tree (`src/main/java/blocks/<block>/impl/`) that compiles
   against the generated skeleton.

4. WHEN a Capability_Template is emitted, THE Template_Emitter SHALL produce structural
   tests (direction tests, reach tests) for each operation in the emitted IR.

5. WHEN a Capability_Template is emitted twice with the same block name, target, and
   template identifier, THE Template_Emitter SHALL produce byte-identical output
   (determinism property).

6. IF an impl stub already exists at the target path, THEN THE Template_Emitter SHALL
   preserve the existing file and skip stub generation for that block.

### Requirement 3: Emitted artifacts preserve the two-tree ownership model

**User Story:** As a developer, I want template-generated files to follow the same ownership
rules as `bear compile` output, so that `bear check` and `bear fix` work correctly on
scaffolded blocks.

#### Acceptance Criteria

1. THE Template_Emitter SHALL place all generated artifacts (wrappers, models, ports,
   runtime classes, structural tests, surface manifests, wiring manifests, containment
   config) under `build/generated/bear/` — the BEAR-owned tree.

2. THE Template_Emitter SHALL place the impl stub under the developer-owned tree
   (`src/main/java/blocks/<block>/impl/`) and the IR_File under `spec/`.

3. WHEN `bear check` is run on a project containing only template-emitted artifacts with
   no manual modifications, THE check command SHALL exit `0` (clean pass).

4. WHEN `bear compile` is run on the IR_File emitted by a template, THE compile command
   SHALL produce output identical to the template's generated artifacts
   (round-trip property).

5. THE Template_Emitter SHALL emit a valid `bear.blocks.yaml` entry (or update an existing
   one) that registers the new block with the correct `projectRoot`, IR file path, and
   block key.

### Requirement 4: At least one useful capability shape is provided

**User Story:** As a developer, I want at least one practical template that covers a common
capability pattern, so that the template system is immediately useful for real projects.

#### Acceptance Criteria

1. THE Template_Registry SHALL include at least one Capability_Template that generates a
   block with a data-store port (read and/or write operations).

2. WHEN the provided Capability_Template is emitted, THE emitted IR_File SHALL declare at
   least one operation with typed inputs and outputs, at least one effect port, and
   operation-level `uses.allow` entries that reference the declared port.

3. WHEN the provided Capability_Template is emitted, THE emitted structural tests SHALL
   include at least one direction test and at least one reach test per operation.

4. THE provided Capability_Template SHALL produce a scaffold where the impl stub contains
   method signatures that match the generated Logic interface, enabling the developer to
   fill in business logic without modifying generated code.

### Requirement 5: Template output integrates with existing compile, check, and structural-test contracts

**User Story:** As a developer, I want template-scaffolded blocks to pass all existing BEAR
gates without special handling, so that templates are first-class citizens in the governance
pipeline.

#### Acceptance Criteria

1. WHEN `bear validate` is run on the IR_File emitted by a template, THE validate command
   SHALL exit `0` with no validation errors.

2. WHEN `bear compile` is run on the IR_File emitted by a template, THE compile command
   SHALL exit `0` and produce no drift relative to the template-emitted artifacts.

3. WHEN `bear check` is run on a template-scaffolded project, THE check command SHALL
   execute structural tests (direction and reach) and report PASS for all generated tests.

4. WHEN `bear pr-check` is run on a branch that adds a template-scaffolded block, THE
   pr-check command SHALL correctly classify the new block's IR as boundary-expanding and
   produce the expected governance signal.

5. IF the template emits containment artifacts (containment-required.json,
   bear-containment.gradle), THEN `bear check` SHALL verify containment markers
   successfully for the scaffolded block.

### Requirement 6: Scaffold command provides a CLI entry point for template emission

**User Story:** As a developer or agent, I want a CLI command to scaffold a new block from
a template, so that I can invoke template emission from the command line or an automated
workflow.

#### Acceptance Criteria

1. WHEN `bear scaffold --template <id> --block <name>` is invoked with a valid template
   identifier and block name, THE Scaffold_Command SHALL emit the Template_Pack to the
   current project root and exit `0`.

2. WHEN `bear scaffold --template <id> --block <name>` is invoked with an unknown template
   identifier, THE Scaffold_Command SHALL exit with a non-zero code from the frozen exit
   code set and emit a deterministic error message identifying the unknown template.

3. WHEN `bear scaffold --template <id> --block <name>` is invoked with a block name that
   already exists in `bear.blocks.yaml`, THE Scaffold_Command SHALL exit with a non-zero
   code and emit a deterministic error message indicating the block already exists.

4. WHEN `bear scaffold --list` is invoked, THE Scaffold_Command SHALL print the sorted
   list of available template identifiers and exit `0`.

5. THE Scaffold_Command SHALL use only existing frozen exit codes for all failure modes
   (no new exit codes introduced).

### Requirement 7: No regression to existing deterministic contracts

**User Story:** As a BEAR user, I want the template feature to leave all existing exit
codes, failure envelopes, IR schemas, and command behaviors unchanged, so that my existing
workflows are not broken.

#### Acceptance Criteria

1. THE Template_Registry and Template_Emitter SHALL NOT modify the frozen v1 IR schema.

2. THE Scaffold_Command SHALL NOT introduce new exit codes beyond the frozen set
   (`0, 2, 3, 4, 5, 6, 7, 64, 70, 74`).

3. WHEN `bear compile`, `bear check`, or `bear pr-check` is run on a project with no
   template-scaffolded blocks, THE commands SHALL produce output identical to
   pre-template behavior (no regression).

4. THE Template_Emitter SHALL NOT place any files outside the standard BEAR-owned and
   developer-owned directory trees defined by the Two-Tree_Model.

5. THE Template_Registry SHALL NOT introduce runtime framework dependencies into the
   `kernel/` module.

### Requirement 8: Test coverage for template pipeline

**User Story:** As a BEAR maintainer, I want tests that verify template registration,
emission determinism, and integration with the compile/check pipeline, so that regressions
in template behavior are caught before release.

#### Acceptance Criteria

1. THE test suite SHALL include at least one test that verifies the Template_Registry
   returns a sorted, stable list of template identifiers across repeated calls.

2. THE test suite SHALL include at least one test that verifies a Capability_Template
   emits a valid v1 IR file that passes `bear validate`.

3. THE test suite SHALL include at least one test that verifies template emission
   determinism: emitting the same template twice with the same parameters produces
   byte-identical output.

4. THE test suite SHALL include at least one round-trip test that verifies `bear compile`
   on a template-emitted IR produces artifacts identical to the template's own output
   (compile-equivalence).

5. THE test suite SHALL include at least one integration test that verifies `bear check`
   passes on a freshly scaffolded project with no manual modifications.

6. WHERE the template emits containment artifacts, THE test suite SHALL verify that
   containment markers are correctly generated and verifiable by `bear check`.
