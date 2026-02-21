package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftAnalyzerTest {
    @Test
    void computeDriftDetectsAddedRemovedChangedAndSortsDeterministically(@TempDir Path tempDir) throws Exception {
        Path baseline = tempDir.resolve("baseline");
        Path candidate = tempDir.resolve("candidate");
        Files.createDirectories(baseline);
        Files.createDirectories(candidate);

        Files.createDirectories(baseline.resolve("a"));
        Files.writeString(baseline.resolve("a/changed.txt"), "old");
        Files.writeString(baseline.resolve("a/only-baseline.txt"), "baseline-only");

        Files.createDirectories(candidate.resolve("a"));
        Files.writeString(candidate.resolve("a/changed.txt"), "new");
        Files.writeString(candidate.resolve("a/only-candidate.txt"), "candidate-only");

        List<DriftItem> drift = DriftAnalyzer.computeDrift(baseline, candidate, path -> true);

        assertEquals(3, drift.size());
        assertEquals("a/changed.txt", drift.get(0).path());
        assertEquals(DriftType.CHANGED, drift.get(0).type());
        assertEquals("a/only-baseline.txt", drift.get(1).path());
        assertEquals(DriftType.ADDED, drift.get(1).type());
        assertEquals("a/only-candidate.txt", drift.get(2).path());
        assertEquals(DriftType.REMOVED, drift.get(2).type());
    }

    @Test
    void hasOwnedBaselineFilesReturnsTrueForMarker(@TempDir Path tempDir) throws Exception {
        Path baseline = tempDir.resolve("build/generated/bear");
        Path marker = baseline.resolve("surfaces/x.surface.json");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "{}");

        assertTrue(DriftAnalyzer.hasOwnedBaselineFiles(
            baseline,
            Set.of("surfaces/", "src/main/java/"),
            "surfaces/x.surface.json"
        ));
    }
}
