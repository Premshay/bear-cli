package com.bear.app;

import com.bear.kernel.target.BoundaryBypassFinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TasteInvariantScannerTest {

    // ── Helper ──────────────────────────────────────────────────────────────────

    private static void createGeneratedZone(Path root) throws IOException {
        Files.createDirectories(root.resolve("build/generated/bear/surfaces"));
        Files.createDirectories(root.resolve("build/generated/bear/wiring"));
        Files.createDirectories(root.resolve("build/generated/bear/src/main/java"));
        Files.createDirectories(root.resolve("build/generated/bear/gradle"));
    }

    // ── 4.2  Positive: surface naming violation ─────────────────────────────────

    @Test
    void surfaceNamingViolationDetected(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.writeString(tempDir.resolve("build/generated/bear/surfaces/BadName.json"), "{}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> surfaceFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SURFACE_NAMING_VIOLATION))
                .toList();
        assertEquals(1, surfaceFindings.size());
        assertTrue(surfaceFindings.get(0).path().contains("BadName.json"));
    }

    // ── 4.3  Negative: valid surface naming ─────────────────────────────────────

    @Test
    void validSurfaceNamingProducesNoFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.writeString(tempDir.resolve("build/generated/bear/surfaces/my-block.surface.json"), "{}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> surfaceFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SURFACE_NAMING_VIOLATION))
                .toList();
        assertEquals(0, surfaceFindings.size());
    }

    // ── 4.4  Positive: wiring naming violation ──────────────────────────────────

    @Test
    void wiringNamingViolationDetected(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.writeString(tempDir.resolve("build/generated/bear/wiring/BadWiring.json"), "{}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> wiringFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_WIRING_NAMING_VIOLATION))
                .toList();
        assertEquals(1, wiringFindings.size());
        assertTrue(wiringFindings.get(0).path().contains("BadWiring.json"));
    }

    // ── 4.5  Negative: valid wiring naming ──────────────────────────────────────

    @Test
    void validWiringNamingProducesNoFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.writeString(tempDir.resolve("build/generated/bear/wiring/my-block.wiring.json"), "{}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> wiringFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_WIRING_NAMING_VIOLATION))
                .toList();
        assertEquals(0, wiringFindings.size());
    }

    // ── 4.6  Positive: zone sprawl (unexpected directory) ───────────────────────

    @Test
    void zoneSprawlDetectsUnexpectedDirectory(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.createDirectories(tempDir.resolve("build/generated/bear/unexpected"));

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> sprawlFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .toList();
        assertEquals(1, sprawlFindings.size());
        assertTrue(sprawlFindings.get(0).path().contains("unexpected"));
    }

    // ── 4.7  Negative: valid zone structure ─────────────────────────────────────

    @Test
    void validZoneStructureProducesNoSprawlFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        // Put valid files in each expected directory to avoid other rule violations
        Files.writeString(tempDir.resolve("build/generated/bear/surfaces/my-block.surface.json"), "{}");
        Files.writeString(tempDir.resolve("build/generated/bear/wiring/my-block.wiring.json"), "{}");
        Files.writeString(tempDir.resolve("build/generated/bear/gradle/bear-containment.gradle"), "");
        // Valid source file
        Path srcDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("MyblockLogic.java"),
                "package com.bear.generated.myblock;\npublic class MyblockLogic {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> sprawlFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .toList();
        assertEquals(0, sprawlFindings.size());
    }

    // ── 4.8  Positive: source structure violation ───────────────────────────────

    @Test
    void sourceStructureViolationDetected(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Path wrongPkg = tempDir.resolve("build/generated/bear/src/main/java/wrong/package");
        Files.createDirectories(wrongPkg);
        Files.writeString(wrongPkg.resolve("Foo.java"),
                "package wrong.pkg;\npublic class Foo {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> structFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SOURCE_STRUCTURE_VIOLATION))
                .toList();
        assertEquals(1, structFindings.size());
        assertTrue(structFindings.get(0).path().contains("wrong"));
    }

    // ── 4.9  Negative: valid source structure ───────────────────────────────────

    @Test
    void validSourceStructureProducesNoFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Path validPkg = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(validPkg);
        Files.writeString(validPkg.resolve("MyblockLogic.java"),
                "package com.bear.generated.myblock;\npublic class MyblockLogic {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> structFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SOURCE_STRUCTURE_VIOLATION))
                .toList();
        assertEquals(0, structFindings.size());
    }

    // ── 4.10 Positive: unexpected source file ───────────────────────────────────

    @Test
    void unexpectedSourceFileDetected(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Path blockPkg = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(blockPkg);
        Files.writeString(blockPkg.resolve("RandomFile.java"),
                "package com.bear.generated.myblock;\npublic class RandomFile {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> fileFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SOURCE_UNEXPECTED_FILE))
                .toList();
        assertEquals(1, fileFindings.size());
        assertTrue(fileFindings.get(0).path().contains("RandomFile.java"));
    }

    // ── 4.11 Negative: valid source file types ─────────────────────────────────

    @Test
    void validSourceFileTypesProduceNoFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Path blockPkg = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(blockPkg);
        Files.writeString(blockPkg.resolve("MyblockLogic.java"),
                "package com.bear.generated.myblock;\npublic class MyblockLogic {}");
        Files.writeString(blockPkg.resolve("Myblock_GetData.java"),
                "package com.bear.generated.myblock;\npublic class Myblock_GetData {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> fileFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SOURCE_UNEXPECTED_FILE))
                .toList();
        assertEquals(0, fileFindings.size());
    }

    // ── 4.12 Positive: forbidden dependency ─────────────────────────────────────

    @Test
    void forbiddenDependencyDetected(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Path blockPkg = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(blockPkg);
        Files.writeString(blockPkg.resolve("MyblockLogic.java"),
                "package com.bear.generated.myblock;\n"
                + "import blocks.myblock.impl.SomeClass;\n"
                + "public class MyblockLogic {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> depFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_FORBIDDEN_DEPENDENCY))
                .toList();
        assertEquals(1, depFindings.size());
        assertTrue(depFindings.get(0).detail().contains("blocks.myblock.impl.SomeClass"));
    }

    // ── 4.13 Negative: allowed imports ──────────────────────────────────────────

    @Test
    void allowedImportsProduceNoForbiddenDependencyFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Path blockPkg = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(blockPkg);
        Files.writeString(blockPkg.resolve("MyblockLogic.java"),
                "package com.bear.generated.myblock;\n"
                + "import blocks._shared.pure.SomeType;\n"
                + "public class MyblockLogic {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> depFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_FORBIDDEN_DEPENDENCY))
                .toList();
        assertEquals(0, depFindings.size());
    }

    // ── 4.14 Allowlist suppression ──────────────────────────────────────────────

    @Test
    void allowlistSuppressesFindings(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.createDirectories(tempDir.resolve("build/generated/bear/unexpected"));

        // Without allowlist — finding emitted
        List<BoundaryBypassFinding> withoutAllowlist =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());
        List<BoundaryBypassFinding> sprawlWithout = withoutAllowlist.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .toList();
        assertEquals(1, sprawlWithout.size());

        // With allowlist — finding suppressed
        Set<String> allowlist = Set.of("build/generated/bear/unexpected");
        List<BoundaryBypassFinding> withAllowlist =
                TasteInvariantScanner.scanTasteInvariants(tempDir, allowlist);
        List<BoundaryBypassFinding> sprawlWith = withAllowlist.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .toList();
        assertEquals(0, sprawlWith.size());
    }

    // ── 4.15 Deterministic output ───────────────────────────────────────────────

    @Test
    void scanIsDeterministic(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.writeString(tempDir.resolve("build/generated/bear/surfaces/BadName.json"), "{}");
        Files.writeString(tempDir.resolve("build/generated/bear/wiring/BadWiring.json"), "{}");
        Files.createDirectories(tempDir.resolve("build/generated/bear/unexpected"));

        List<BoundaryBypassFinding> first =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());
        List<BoundaryBypassFinding> second =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        assertEquals(first, second);
    }

    // ── 4.16 Scope lock: exactly 23 rule IDs (17 existing + 6 taste) ───────────

    @Test
    void scopeLockRegistryContainsTasteRuleIds() {
        Set<String> ruleIds = GovernanceRuleRegistry.PUBLIC_RULE_IDS;
        assertEquals(23, ruleIds.size());

        // Verify all 6 taste-invariant rule IDs are present
        assertTrue(ruleIds.contains(TasteInvariantScanner.GENERATED_SURFACE_NAMING_VIOLATION));
        assertTrue(ruleIds.contains(TasteInvariantScanner.GENERATED_WIRING_NAMING_VIOLATION));
        assertTrue(ruleIds.contains(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH));
        assertTrue(ruleIds.contains(TasteInvariantScanner.GENERATED_SOURCE_STRUCTURE_VIOLATION));
        assertTrue(ruleIds.contains(TasteInvariantScanner.GENERATED_SOURCE_UNEXPECTED_FILE));
        assertTrue(ruleIds.contains(TasteInvariantScanner.GENERATED_FORBIDDEN_DEPENDENCY));
    }

    // ── 4.17 Stable remediation text ────────────────────────────────────────────

    @Test
    void remediationTextIsStableAndDeterministic(@TempDir Path tempDir) throws Exception {
        createGeneratedZone(tempDir);
        Files.writeString(tempDir.resolve("build/generated/bear/surfaces/BadName.json"), "{}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        for (BoundaryBypassFinding f : findings) {
            // No timestamps (ISO-8601 patterns)
            assertFalse(f.detail().matches(".*\\d{4}-\\d{2}-\\d{2}.*"),
                    "detail should not contain timestamps: " + f.detail());
            // No UUIDs
            assertFalse(f.detail().matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*"),
                    "detail should not contain UUIDs: " + f.detail());
            // No random hex IDs (8+ hex chars)
            assertFalse(f.detail().matches(".*[0-9a-f]{8,}.*"),
                    "detail should not contain random hex IDs: " + f.detail());
            // Detail is a non-empty stable string
            assertFalse(f.detail().isBlank(), "detail should not be blank");
        }
    }
}
