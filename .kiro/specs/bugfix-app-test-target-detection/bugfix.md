# Bugfix: App Test Failures After Multi-Target TargetRegistry Introduction

> **⚠️ MAINTAINER REVIEW FLAG**: This bugfix is test-infrastructure-only.
> No production code is changed. No frozen contracts are affected.
> The root cause is that multi-target `TargetRegistry` removed the implicit
> JVM-only default, and existing app tests use bare `@TempDir` directories
> with no target identity. See "Maintainer Notes" at the bottom.

## Bug Condition

**C(X)**: App-module tests that use bare `@TempDir` directories (no build files,
no `.bear/target.id` pin) fail with exit 64 (`TARGET_NOT_DETECTED`) instead of
their expected exit codes, because `TargetRegistry.resolve()` cannot determine
the project target.

**Before multi-target**: A single JVM target existed with no detection step.
All bare temp dirs implicitly used JVM.

**After multi-target**: `TargetRegistry` requires explicit target identity via
either build-file detection (`build.gradle`, `package.json`, `pyproject.toml`)
or a `.bear/target.id` pin file. Bare temp dirs match no detector → exit 64.

## Scope

- **176 test failures** across 4 test classes (174 on clean run + 2 already fixed in working tree)
  - `BearCliTest`: ~160 failures
  - `BearCliAgentModeTest`: 14 failures
  - `SingleFileIndexInferenceTest`: 5 failures (already fixed in working tree)
  - `TargetSeamParityTest`: 2 failures (already fixed in working tree)
- **Zero production code changes**
- **Zero frozen contract changes** (exit codes, output shapes, command forms unchanged)
- Exit 64 for `TARGET_NOT_DETECTED` is semantically correct per `exit-codes.md`
  (it IS a usage error — the test didn't declare a target)

## What Was Considered and Rejected

**Pipeline reordering** (adding IR file existence check before `TARGET_REGISTRY.resolve()`):
Rejected because exit 64 is the correct semantic for "no target identity in a
bare directory." The tests are the ones that need updating, not the production
pipeline. Masking `TARGET_NOT_DETECTED` with an earlier `IO_ERROR` would hide
a real usage problem.

## Fix Strategy

Add `.bear/target.id` pin files (content: `jvm`) to all test temp directories
before invoking compile/fix/check/pr-check. This is the canonical multi-target
pattern documented in `TargetRegistry`'s own remediation message.

Implementation approach:
1. Add a shared `TestTargetPins.pinJvm(Path projectRoot)` helper utility
2. Call `TestTargetPins.pinJvm` after `@TempDir` creation, before first CLI invocation
3. For multi-block fixtures (`createMultiBlockFixture`), call `TestTargetPins.pinJvm` for each project root

## Acceptance Criteria

- [ ] AC-1: All app-module tests pass (0 failures out of ~451)
- [ ] AC-2: All kernel tests still pass (0 regressions)
- [ ] AC-3: No production code changes
- [ ] AC-4: No frozen contract violations

## Related: TypeScript Import Path (Separate Issue)

`TypeScriptArtifactGenerator.renderUserImplSkeleton()` generates
`import { ... } from '../BlockNameLogic'` but the Logic.ts file is at
`build/generated/bear/types/<key>/Logic.ts` while the skeleton is at
`src/blocks/<key>/impl/Impl.ts`. The relative path `../` is wrong.

This is a **separate kernel bug** — no existing tests fail because of it,
and it should be tracked independently. Not in scope for this bugfix.

---

## ⚠️ Maintainer Notes (PR Review Guidance)

**What this PR does**: Adds `.bear/target.id` JVM pin files to app-module test
infrastructure. This is a mechanical change required by the multi-target
`TargetRegistry` introduction.

**Why exit 64 is correct**: `TARGET_NOT_DETECTED` maps to exit 64 ("usage or
argument failure") per the frozen exit-code contract in `exit-codes.md`. A bare
temp dir with no build files and no pin file is genuinely a usage error — the
caller hasn't told BEAR what target to use. The previous behavior (implicit JVM
default) was a pre-multi-target simplification, not a contract guarantee.

**What to verify**:
1. The `pinJvmTarget` helper creates `.bear/target.id` with content `jvm\n`
2. Pin calls are placed before the first compile/fix/check/pr-check invocation
3. No production code in `app/src/main/` or `kernel/` is modified
4. Full test suite passes: `./gradlew :app:test :kernel:test`

**Separate issue to track**: TypeScript import path in
`TypeScriptArtifactGenerator.renderUserImplSkeleton()` generates wrong
relative path (`../` instead of `../../../../build/generated/bear/types/<key>/`).
No tests currently fail from this, but it will break real Node target users.
