# Design: App Test JVM Target Pin Fix

## Approach

Centralize JVM target pinning in a shared test utility,
`app/src/test/java/com/bear/app/TestTargetPins.java`, exposing a
package-private `pinJvm(Path projectRoot)` method.

All affected tests call `TestTargetPins.pinJvm(...)` in every test method
that creates a bare `@TempDir` and invokes compile/fix/check/pr-check.

```java
package com.bear.app;

import com.bear.kernel.target.TargetId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestTargetPins {
    private TestTargetPins() {}

    static void pinJvm(Path projectRoot) throws IOException {
        Path bearDir = projectRoot.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"),
                TargetId.JVM.value() + "\n", StandardCharsets.UTF_8);
    }
}
```

## Per-Class Strategy

### BearCliTest (~160 failures)
- Call `TestTargetPins.pinJvm` before first CLI invocation in each `@TempDir` test
- Special case: `createMultiBlockFixture` creates 3 project roots — pin each
- Tests that don't use `@TempDir` (e.g., `unknownCommandIsUsageError`) need no change
- Tests that test missing-file scenarios (e.g., `checkMissingIrReturnsIoExitCode`)
  still need the pin because target resolution runs before the IO error path

### BearCliAgentModeTest (14 failures)
- Call `TestTargetPins.pinJvm` before CLI invocations in all test methods using `@TempDir`

### SingleFileIndexInferenceTest (5 failures) — already fixed
- Pin added inside `writeBlockPortFixture` helper

### TargetSeamParityTest (2 failures) — already fixed
- Pin added before `executeCompile` calls

## Production Code Scope

This design makes zero changes to:
- `app/src/main/java/` (command pipeline)
- Exit code semantics
- Output line shapes

The kernel file diffs included in this PR are merge conflict-marker resolutions
(removing `<<<<<<<`, `=======`, `>>>>>>>` markers) inherited from the base branch,
not intentional new production logic. They are included because leaving them would
prevent compilation.
