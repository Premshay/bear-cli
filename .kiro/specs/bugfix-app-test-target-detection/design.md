# Design: App Test JVM Target Pin Fix

## Approach

Centralize JVM target pinning logic in a single test utility,
`app/src/test/java/com/bear/app/TestTargetPins.java`, exposing a static
`pinJvm(Path projectRoot)` helper.

All affected tests should call `TestTargetPins.pinJvm(...)` in every test method
that creates a bare `@TempDir` and invokes compile/fix/check/pr-check.

```java
package com.bear.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestTargetPins {
    private TestTargetPins() {}

    public static void pinJvm(Path projectRoot) throws IOException {
        Path bearDir = projectRoot.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(
            bearDir.resolve("target.id"),
            "jvm\n",
            StandardCharsets.UTF_8
        );
    }
}
```

## Per-Class Strategy

### BearCliTest (~160 failures)
- Call `TestTargetPins.pinJvm(projectRoot)` before the first CLI invocation
  in each `@TempDir` test
- Special case: `createMultiBlockFixture` creates 3 project roots — pin each
- Tests that don't use `@TempDir` (e.g., `unknownCommandIsUsageError`) need no change
- Tests that test missing-file scenarios (e.g., `checkMissingIrReturnsIoExitCode`)
  still need the pin because target resolution runs before the IO error path

### BearCliAgentModeTest (14 failures)
- Call `TestTargetPins.pinJvm(projectRoot)` before CLI invocations in all
  test methods using `@TempDir`

### SingleFileIndexInferenceTest (5 failures) — already fixed
- Pin added inside `writeBlockPortFixture` helper (which delegates to
  `TestTargetPins.pinJvm`)

### TargetSeamParityTest (2 failures) — already fixed
- Pin added before `executeCompile` calls (delegating to
  `TestTargetPins.pinJvm`)

## Automation Script

Given ~160 test methods in BearCliTest alone, a Python script will:
1. Parse the Java file
2. Find test methods with `@TempDir Path tempDir` (or `@TempDir Path repoRoot`, etc.)
3. Insert a call to `TestTargetPins.pinJvm(tempDir)` (or the appropriate
   `@TempDir` variable) after the first line of the method body
4. Handle `createMultiBlockFixture` specially (pin each of the 3 roots)

## No Production Changes

This design intentionally makes zero changes to:
- `app/src/main/java/` (command pipeline)
- `kernel/src/main/java/` (target resolution)
- Exit code semantics
- Output line shapes
