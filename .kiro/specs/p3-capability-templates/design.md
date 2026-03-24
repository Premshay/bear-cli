# Design: P3 Capability Templates

## Overview

Capability templates solve the cold-start problem for new governed blocks. Today a developer
must hand-author an IR file, understand port naming conventions, effect declarations, and the
two-tree ownership model before running `bear compile`. Templates collapse that into a single
`bear scaffold` invocation that emits a complete, governed scaffold — IR file, generated
artifacts, impl stub, structural tests, and `bear.blocks.yaml` registration — for a named
capability shape.

The design has three layers:

1. **Kernel layer** (`kernel/`) — `CapabilityTemplate` interface, `TemplateParams` record,
   `TemplatePack` record, `CapabilityTemplateRegistry`, and the initial `ReadStoreTemplate`
   implementation. No agent logic, no new IR semantics.

2. **App layer** (`app/`) — `ScaffoldCommandService` and the `scaffold` entry in
   `BearCli.COMMAND_HANDLERS`. The service resolves the target, delegates to the kernel
   registry, calls `target.compile()` on the emitted IR, and updates `bear.blocks.yaml`.

3. **Emission strategy** — templates write the IR file to disk, then call the resolved
   `Target.compile()` to produce all generated artifacts. This guarantees compile-equivalence
   by construction: the template never duplicates generation logic.

No new exit codes. No IR schema changes. No new kernel dependencies.

---

## Architecture

```
bear scaffold --template read-store --block MyBlock
        │
        ▼
BearCli.COMMAND_HANDLERS["scaffold"]
        │
        ▼
ScaffoldCommandService.execute(args)
        │
        ├─► CapabilityTemplateRegistry.find(templateId)
        │         │
        │         └─► ReadStoreTemplate (or future templates)
        │
        ├─► TargetRegistry.resolve(projectRoot)   ← existing kernel
        │
        ├─► template.emit(TemplateParams, projectRoot)
        │         │
        │         ├─ writes  spec/<block>.ir.yaml
        │         └─ returns TemplatePack (paths only; no generated artifacts yet)
        │
        ├─► target.compile(parsedIr, projectRoot, blockKey)
        │         │                                          ← existing kernel path
        │         └─ writes build/generated/bear/...
        │              writes src/main/java/blocks/<block>/impl/<Block>Impl.java (if absent)
        │
        ├─► BlocksYamlUpdater.appendEntry(projectRoot, blockName, irPath)
        │         │
        │         └─ reads existing bear.blocks.yaml (if present), appends entry, writes back
        │
        └─► exit 0 / emit failure envelope on error
```

The kernel `CapabilityTemplateRegistry` is a pure, stateless registry — no I/O, no target
resolution. All I/O is orchestrated by `ScaffoldCommandService` in `app/`.

---

## Components and Interfaces

### `CapabilityTemplate` (kernel)

**Package:** `com.bear.kernel.template`

```java
public interface CapabilityTemplate {
    /** Stable, kebab-case identifier (e.g. "read-store"). */
    String id();

    /** One-line human description shown by --list. */
    String description();

    /**
     * Writes the IR file to disk and returns the TemplatePack describing
     * all paths that will be populated (IR path + paths compile() will produce).
     * Does NOT call compile(); the caller is responsible for that.
     */
    TemplatePack emit(TemplateParams params, Path projectRoot) throws IOException;
}
```

### `TemplateParams` (kernel)

```java
public record TemplateParams(String blockName, TargetId targetId) {}
```

`blockName` must match `[a-z][a-z0-9-]*` (same pattern as `BlockIndexParser`).
`targetId` is used by the template to tailor the impl stub language/style.

### `TemplatePack` (kernel)

```java
public record TemplatePack(
    Path irPath,           // spec/<block>.ir.yaml
    Path implStubPath,     // src/main/java/blocks/<block>/impl/<Block>Impl.java
    List<Path> generatedPaths  // populated after compile(); empty from emit()
) {}
```

`emit()` returns a `TemplatePack` with `generatedPaths` empty. After `target.compile()`,
`ScaffoldCommandService` does not need to enumerate generated paths — the compile step
handles them. `TemplatePack` is a value object for test assertions and future tooling.

### `CapabilityTemplateRegistry` (kernel)

**Package:** `com.bear.kernel.template`

```java
public final class CapabilityTemplateRegistry {
    private static final List<CapabilityTemplate> TEMPLATES = List.of(
        new ReadStoreTemplate()
        // future templates appended here
    );

    private CapabilityTemplateRegistry() {}

    /** Returns templates sorted by id, stable across calls. */
    public static List<CapabilityTemplate> list() { ... }

    /** Returns Optional.empty() for unknown ids — never throws. */
    public static Optional<CapabilityTemplate> find(String id) { ... }
}
```

`list()` returns an unmodifiable list sorted by `id()`. `find()` returns
`Optional.empty()` for unknown identifiers — no unchecked exceptions.

### `ReadStoreTemplate` (kernel)

**Package:** `com.bear.kernel.template`

The initial capability shape: a block with one read operation (`Get<Block>`) and one
data-store port (`<block>Store`, ops `[get]`). Emits a minimal but complete v1 IR:

```yaml
version: v1
block:
  name: <BlockName>
  kind: logic
  operations:
    - name: Get<BlockName>
      contract:
        inputs:
          - name: id
            type: string
        outputs:
          - name: result
            type: string
      uses:
        allow:
          - port: <blockName>Store
            ops: [get]
      idempotency:
        mode: none
  effects:
    allow:
      - port: <blockName>Store
        ops: [get]
```

`emit()` writes this YAML to `spec/<blockName>.ir.yaml` under `projectRoot`, creating
the `spec/` directory if absent. Returns a `TemplatePack` with `irPath` set.

The impl stub is written by `JvmTarget.compile()` (or the resolved target's compile),
not by the template itself — this is the compile-equivalence guarantee.

### `ScaffoldCommandService` (app)

**Package:** `com.bear.app`

Handles `bear scaffold` subcommands:

- `--template <id> --block <name> [--project <path>]` — emit scaffold
- `--list` — print sorted template ids

**Emit flow:**

1. Parse args; validate `--template` and `--block` are present.
2. Resolve `projectRoot` (default: current working directory).
3. Call `CapabilityTemplateRegistry.find(templateId)` — if empty, fail with
   `EXIT_USAGE` / `USAGE_UNKNOWN_TEMPLATE`.
4. Check `bear.blocks.yaml` for existing block name — if found, fail with
   `EXIT_USAGE` / `BLOCK_ALREADY_EXISTS`.
5. Resolve target via `TargetRegistry.resolve(projectRoot)`.
6. Call `template.emit(params, projectRoot)` — writes IR file.
7. Parse and validate the emitted IR via `IR_PIPELINE.parseValidateNormalize(irPath)`.
8. Call `target.compile(ir, projectRoot, blockName)` — writes generated artifacts and
   impl stub (skipping stub if already present — handled by `JvmTarget`).
9. Call `BlocksYamlUpdater.appendEntry(projectRoot, blockName, irRelPath)` — updates
   `bear.blocks.yaml`.
10. Print `scaffold: OK` to stdout; exit 0.

### `BlocksYamlUpdater` (app)

**Package:** `com.bear.app`

Minimal helper that appends a new block entry to `bear.blocks.yaml`:

- If `bear.blocks.yaml` does not exist, creates it with `version: v1` and the new entry.
- If it exists, reads it with `BlockIndexParser`, verifies the block name is not already
  present (double-check), then appends the new entry as raw YAML text and writes back.
- Uses `projectRoot`-relative paths for `ir` and `projectRoot` fields.

This is a simple append — no full YAML re-serialization to avoid disturbing existing
formatting or comments.

### `BearCli` changes (app)

Add `"scaffold"` to `COMMAND_HANDLERS`:

```java
"scaffold", BearCliCommandHandlers::runScaffold
```

`runScaffold` delegates to `ScaffoldCommandService`. No other changes to `BearCli`.

---

## Data Models

### `TemplateParams`

| Field | Type | Description |
|---|---|---|
| `blockName` | `String` | Block name, matches `[a-z][a-z0-9-]*` |
| `targetId` | `TargetId` | Resolved target for impl stub style |

### `TemplatePack`

| Field | Type | Description |
|---|---|---|
| `irPath` | `Path` | Absolute path to emitted IR file |
| `implStubPath` | `Path` | Expected impl stub path (may not exist yet) |
| `generatedPaths` | `List<Path>` | Empty from `emit()`; informational only |

### `ScaffoldResult` (internal to app)

Mirrors the pattern of `CompileResult`, `CheckResult`, etc.:

| Field | Type | Description |
|---|---|---|
| `exitCode` | `int` | 0 on success |
| `stdoutLines` | `List<String>` | e.g. `["scaffold: OK"]` |
| `stderrLines` | `List<String>` | Error lines on failure |
| `failureCode` | `String` | e.g. `USAGE_UNKNOWN_TEMPLATE` |
| `failurePath` | `String` | e.g. `scaffold.template` |
| `failureRemediation` | `String` | Human-readable fix hint |

### `bear.blocks.yaml` entry format (unchanged v1 schema)

```yaml
- name: my-block
  ir: spec/my-block.ir.yaml
  projectRoot: .
```

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid
executions of a system — essentially, a formal statement about what the system should do.
Properties serve as the bridge between human-readable specifications and
machine-verifiable correctness guarantees.*

### Property 1: Registry enumeration is stable and sorted

*For any* invocation of `CapabilityTemplateRegistry.list()`, the returned list of
template identifiers should be in non-decreasing lexicographic order, and calling
`list()` multiple times without registry mutations should return identical results.

**Validates: Requirements 1.1, 1.4**

### Property 2: Registry lookup round-trip

*For any* template identifier returned by `CapabilityTemplateRegistry.list()`,
calling `CapabilityTemplateRegistry.find(id)` should return a non-empty `Optional`
whose `id()` equals the queried identifier.

**Validates: Requirements 1.2**

### Property 3: Registry not-found is explicit

*For any* string that is not a registered template identifier,
`CapabilityTemplateRegistry.find(id)` should return `Optional.empty()` without
throwing any exception.

**Validates: Requirements 1.3**

### Property 4: Emitted IR is valid v1

*For any* valid block name, calling `ReadStoreTemplate.emit(params, projectRoot)`
should produce an IR file that `BearIrParser` parses and `BearIrValidator` validates
without errors, and that contains at least one operation with typed inputs and outputs,
at least one effect port, and operation-level `uses.allow` entries referencing the
declared port.

**Validates: Requirements 2.1, 4.2, 5.1**

### Property 5: Compile-equivalence round-trip

*For any* valid block name, the generated artifacts produced by
`ScaffoldCommandService` (which calls `target.compile()` on the template-emitted IR)
should be byte-identical to the artifacts produced by running `bear compile` on that
same IR file in a fresh project directory.

**Validates: Requirements 2.2, 3.4, 5.2**

### Property 6: Emission determinism

*For any* `TemplateParams` (block name + target id), calling
`ReadStoreTemplate.emit(params, projectRoot)` twice in identical empty directories
should produce byte-identical IR files.

**Validates: Requirements 2.5**

### Property 7: Impl stub preservation

*For any* existing impl stub file with arbitrary content, calling
`ScaffoldCommandService.execute()` (or `target.compile()`) on the same block should
leave the existing stub file byte-identical — the scaffold must not overwrite
developer-owned files.

**Validates: Requirements 2.6**

### Property 8: File placement invariant

*For any* emitted template, all generated artifact paths (wrappers, models, ports,
structural tests, manifests, containment config) should be under
`build/generated/bear/`, the impl stub should be under
`src/main/java/blocks/<block>/impl/`, and the IR file should be under `spec/`. No
emitted file should appear outside these three trees.

**Validates: Requirements 3.1, 3.2, 7.4**

### Property 9: bear.blocks.yaml entry correctness

*For any* valid block name and project root, after `ScaffoldCommandService` completes
successfully, `BlockIndexParser.parse()` on the resulting `bear.blocks.yaml` should
return a `BlockIndex` containing exactly one entry for the new block with the correct
`name`, `ir` path, and `projectRoot` fields.

**Validates: Requirements 3.5**

### Property 10: bear check passes on freshly scaffolded project

*For any* valid block name, running `bear check` on a project immediately after
`bear scaffold` (with no manual modifications) should exit `0` with no drift, no
undeclared reach, and all structural tests passing.

**Validates: Requirements 3.3, 5.3, 5.5**

### Property 11: Scaffold command happy path

*For any* valid template identifier and valid block name not already in
`bear.blocks.yaml`, `bear scaffold --template <id> --block <name>` should exit `0`
and emit `scaffold: OK` to stdout.

**Validates: Requirements 6.1**

### Property 12: Scaffold refuses unknown template

*For any* string that is not a registered template identifier, `bear scaffold
--template <unknown>` should exit `64` (`EXIT_USAGE`) and emit a deterministic error
message identifying the unknown template identifier.

**Validates: Requirements 6.2**

### Property 13: Scaffold refuses existing block

*For any* block name already registered in `bear.blocks.yaml`, `bear scaffold
--template <id> --block <existing>` should exit `64` (`EXIT_USAGE`) and emit a
deterministic error message indicating the block already exists.

**Validates: Requirements 6.3**

---

## Error Handling

All scaffold failures use existing frozen exit codes. No new codes are introduced.

| Condition | Exit | CODE | PATH |
|---|---|---|---|
| Unknown template identifier | 64 | `USAGE_UNKNOWN_TEMPLATE` | `scaffold.template` |
| Block already in bear.blocks.yaml | 64 | `BLOCK_ALREADY_EXISTS` | `bear.blocks.yaml` |
| Invalid block name format | 64 | `USAGE_INVALID_ARGS` | `scaffold.block` |
| Missing required flag | 64 | `USAGE_INVALID_ARGS` | `scaffold.args` |
| IR write failure | 74 | `IO_ERROR` | `spec/<block>.ir.yaml` |
| Compile failure after emit | 2 | `IR_VALIDATION` | (from compile) |
| bear.blocks.yaml write failure | 74 | `IO_ERROR` | `bear.blocks.yaml` |
| Target not detected | per TargetRegistry | (from TargetRegistry) | `project.root` |
| Internal error | 70 | `INTERNAL_ERROR` | `internal` |

The failure envelope format is unchanged: `CODE/PATH/REMEDIATION` on the last three
stderr lines, consistent with all other commands.

`USAGE_UNKNOWN_TEMPLATE` and `BLOCK_ALREADY_EXISTS` are new string constants added to
`CliCodes` — they are string codes, not new integer exit codes. The integer exit codes
remain the frozen set `{0, 2, 3, 4, 5, 6, 7, 64, 70, 74}`.

---

## Testing Strategy

### Dual testing approach

Unit tests verify specific examples, edge cases, and error conditions. Property tests
verify universal properties across many generated inputs. Both are required.

### Kernel unit tests

**File:** `kernel/src/test/java/com/bear/kernel/template/CapabilityTemplateRegistryTest.java`

| Test | Kind | Validates |
|---|---|---|
| `listIsSortedAndStable` | property | P1, Req 1.1, 1.4 |
| `findKnownTemplateReturnsPresent` | property | P2, Req 1.2 |
| `findUnknownTemplateReturnsEmpty` | property | P3, Req 1.3 |
| `findNeverThrowsForArbitraryInput` | property | P3, Req 1.3 |

**File:** `kernel/src/test/java/com/bear/kernel/template/ReadStoreTemplateTest.java`

| Test | Kind | Validates |
|---|---|---|
| `emittedIrParsesAndValidates` | unit | P4, Req 2.1, 4.2 |
| `emittedIrHasDataStorePort` | unit | P4, Req 4.1, 4.2 |
| `emitIsDeterministic` | property | P6, Req 2.5 |
| `emittedIrPathIsUnderSpecDir` | unit | P8, Req 3.2 |

### App unit tests

**File:** `app/src/test/java/com/bear/app/ScaffoldCommandServiceTest.java`

| Test | Kind | Validates |
|---|---|---|
| `unknownTemplateExitsUsage` | unit | P12, Req 6.2 |
| `existingBlockExitsUsage` | unit | P13, Req 6.3 |
| `listPrintsSortedTemplateIds` | unit | P1, Req 6.4 |
| `missingTemplateFlagExitsUsage` | unit | Req 6.5 |
| `missingBlockFlagExitsUsage` | unit | Req 6.5 |

### Integration tests

**File:** `app/src/test/java/com/bear/app/ScaffoldIntegrationTest.java`

Uses `@TempDir`, in-process CLI invocation, and a real compiled JVM project fixture
(same pattern as `AllModeContractTest`).

| Test | Kind | Validates |
|---|---|---|
| `scaffoldHappyPathExitsZero` | integration | P11, Req 6.1 |
| `scaffoldWritesIrAndBlocksYaml` | integration | P9, Req 3.5 |
| `scaffoldFilePlacementInvariant` | integration | P8, Req 3.1, 3.2 |
| `scaffoldThenCheckExitsZero` | integration | P10, Req 3.3, 5.3 |
| `scaffoldThenCompileIsEquivalent` | integration | P5, Req 2.2, 3.4 |
| `scaffoldPreservesExistingImplStub` | integration | P7, Req 2.6 |
| `scaffoldThenPrCheckIsBoundaryExpanding` | integration | Req 5.4 |
| `existingCommandsUnaffectedByScaffoldAddition` | integration | Req 7.3 |

### Property-based testing

Property tests use plain JUnit 5 with multiple concrete inputs (not a randomized
generator library) — consistent with the existing codebase pattern. Each property test
constructs several representative inputs and asserts the property holds for all of them.

Property-based testing library: plain JUnit 5 parameterized tests
(`@ParameterizedTest` + `@MethodSource`) with minimum 5 distinct inputs per property.

Tag format: `Feature: p3-capability-templates, Property N: <property_text>`

| Property | Test method | Kind |
|---|---|---|
| P1: Registry sorted + stable | `listIsSortedAndStable` | JUnit 5 parameterized |
| P3: find never throws | `findNeverThrowsForArbitraryInput` | JUnit 5 parameterized |
| P6: Emit determinism | `emitIsDeterministic` | JUnit 5 parameterized (block names) |

Properties P5, P7, P8, P9, P10, P11, P12, P13 are validated by integration tests
(deterministic fixtures) because they require a compiled project on disk.

### Unit test balance

- Unit tests: specific examples, error conditions, edge cases (unknown template, block
  exists, missing flags).
- Property tests: ordering, not-found safety, and determinism properties where the
  input space is meaningful.
- Integration tests: end-to-end CLI invocations with real compiled fixtures.
- Avoid duplicating coverage already provided by `BearCliCommandHandlers` tests for
  compile/check (the compile-equivalence test reuses the same fixture infrastructure).

### Regression guard

All existing tests must remain green. The `scaffold` command is additive — it is
registered in `COMMAND_HANDLERS` alongside existing commands with no changes to their
handlers. The `CapabilityTemplateRegistry` is a new kernel class with no dependencies
on existing kernel classes, so it cannot introduce regressions in the kernel.
