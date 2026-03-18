# Design: App Test JVM Target Pin Fix

## Approach

Add a static `pinJvmTarget(Path projectRoot)` helper to each affected test class.
Call it in every test method that creates a bare `@TempDir` and invokes
compile/fix/check/pr-check.

```java
private static void pinJvmTarget(Path projectRoot) throws Exception {
    Path bearDir = projectRoot.resolve(".bear");
    java.nio.file.Files.createDirectories(bearDir);
    java.nio.file.Files.writeString(
        bearDir.resolve("target.id"), "jvm\n",
        java.nio.charset.StandardCharsets.UTF_8
    );
}
```

## Per-Class Strategy

### BearCliTest (~160 failures)
- Add `pinJvmTarget` helper
- Call before first CLI invocation in each `@TempDir` test
- Special case: `createMultiBlockFixture` creates 3 project roots — pin each
- Tests that don't use `@TempDir` (e.g., `unknownCommandIsUsageError`) need no change
- Tests that test missing-file scenarios (e.g., `checkMissingIrReturnsIoExitCode`)
  still need the pin because target resolution runs before the IO error path

### BearCliAgentModeTest (14 failures)
- Add `pinJvmTarget` helper
- Call before CLI invocations in all test methods using `@TempDir`

### SingleFileIndexInferenceTest (5 failures) — already fixed
- Pin added inside `writeBlockPortFixture` helper

### TargetSeamParityTest (2 failures) — already fixed
- Pin added before `executeCompile` calls

## Automation Script

Given ~160 test methods in BearCliTest alone, a Python script will:
1. Parse the Java file
2. Find test methods with `@TempDir Path tempDir` (or `@TempDir Path repoRoot`, etc.)
3. Insert `pinJvmTarget(tempDir)` after the first line of the method body
4. Handle `createMultiBlockFixture` specially (pin each of the 3 roots)

## No Production Changes

This design intentionally makes zero changes to:
- `app/src/main/java/` (command pipeline)
- `kernel/src/main/java/` (target resolution)
- Exit code semantics
- Output line shapes
