package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HygieneScannerTest {
    @Test
    void scanUnexpectedPathsFlagsDefaultSeedAndRespectsAllowlist(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve(".g"));
        Files.createDirectories(tempDir.resolve(".bear-gradle-user-home"));

        List<String> findings = HygieneScanner.scanUnexpectedPaths(tempDir, Set.of());
        assertEquals(List.of(".g"), findings);

        List<String> allowlisted = HygieneScanner.scanUnexpectedPaths(tempDir, Set.of(".g"));
        assertEquals(List.of(), allowlisted);
    }
}
