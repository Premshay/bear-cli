package com.bear.app;

import com.bear.kernel.target.TargetId;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared test utility for pinning target identity in bare temp directories.
 *
 * After multi-target TargetRegistry removed the implicit JVM fallback,
 * tests that create bare @TempDir project roots must explicitly pin a target
 * so TargetRegistry.resolve() succeeds. This utility centralises the pin
 * format so it can't drift across test classes.
 */
final class TestTargetPins {
    private TestTargetPins() {}

    /**
     * Writes a {@code .bear/target.id} file containing {@code "jvm\n"} under
     * the given project root, creating the {@code .bear/} directory if needed.
     */
    static void pinJvm(Path projectRoot) throws Exception {
        Path bearDir = projectRoot.resolve(".bear");
        Files.createDirectories(bearDir);
        Files.writeString(bearDir.resolve("target.id"),
                TargetId.JVM.value() + "\n", StandardCharsets.UTF_8);
    }
}
