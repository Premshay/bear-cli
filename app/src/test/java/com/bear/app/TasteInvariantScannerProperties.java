package com.bear.app;

import com.bear.kernel.target.BoundaryBypassFinding;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for TasteInvariantScanner (implemented as parameterized JUnit 5 tests).
 * Feature: p2-minimal-taste-invariants-rule-pack
 *
 * Property 1: Surface/Wiring Naming Pattern Validation
 * Property 2: Zone Sprawl Detection
 * Property 3: Source Package Structure Validation
 * Property 4: Source File Type Validation
 * Property 5: Forbidden Dependency Detection
 * Property 6: Allowlist Suppression
 * Property 7: Scan Determinism
 * Property 8: Failure Envelope Format
 * Property 9: Read-Only Scan
 */
class TasteInvariantScannerProperties {

    private static final Pattern SURFACE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*\\.surface\\.json$");
    private static final Pattern WIRING_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*\\.wiring\\.json$");
    private static final Pattern BLOCK_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Set<String> KNOWN_CATEGORIES = Set.of("surfaces", "wiring", "src", "gradle", "config", ".staging");

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static void createGeneratedZone(Path root) throws IOException {
        Files.createDirectories(root.resolve("build/generated/bear/surfaces"));
        Files.createDirectories(root.resolve("build/generated/bear/wiring"));
        Files.createDirectories(root.resolve("build/generated/bear/src/main/java"));
        Files.createDirectories(root.resolve("build/generated/bear/gradle"));
    }

    private static String randomBlockKey(Random rng) {
        int len = rng.nextInt(8) + 2;
        StringBuilder sb = new StringBuilder();
        sb.append((char) ('a' + rng.nextInt(26)));
        for (int i = 1; i < len; i++) {
            int choice = rng.nextInt(36);
            if (choice < 26) sb.append((char) ('a' + choice));
            else sb.append((char) ('0' + (choice - 26)));
        }
        return sb.toString();
    }

    private static String randomInvalidFilename(Random rng) {
        int kind = rng.nextInt(5);
        return switch (kind) {
            case 0 -> // Uppercase start
                    Character.toUpperCase((char) ('a' + rng.nextInt(26))) + randomBlockKey(rng) + ".surface.json";
            case 1 -> // Digit start
                    "" + rng.nextInt(10) + randomBlockKey(rng) + ".surface.json";
            case 2 -> // Wrong extension
                    randomBlockKey(rng) + ".json";
            case 3 -> // Underscore in name
                    randomBlockKey(rng) + "_bad.surface.json";
            default -> // No extension at all
                    "BadName";
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Property 1: Surface/Wiring Naming Pattern Validation ─────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 1: Surface/Wiring Naming Pattern Validation
    // Validates: Requirements 1.1, 1.2, 1.3, 1.4

    /**
     * For any file placed in surfaces/ or wiring/, the scanner emits a naming
     * violation finding if and only if the filename does not match the expected pattern.
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 1: Surface/Wiring Naming Pattern Validation
     * Validates: Requirements 1.1, 1.2, 1.3, 1.4
     */
    @ParameterizedTest(name = "iteration {0}: filename={1}, isSurface={2}, expectViolation={3}")
    @MethodSource("surfaceWiringNamingCases")
    void surfaceWiringNamingPatternValidation(
            int iteration,
            String filename,
            boolean isSurface,
            boolean expectViolation,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        String subdir = isSurface ? "surfaces" : "wiring";
        Files.writeString(tempDir.resolve("build/generated/bear/" + subdir + "/" + filename), "{}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        String expectedRule = isSurface
                ? TasteInvariantScanner.GENERATED_SURFACE_NAMING_VIOLATION
                : TasteInvariantScanner.GENERATED_WIRING_NAMING_VIOLATION;

        List<BoundaryBypassFinding> relevant = findings.stream()
                .filter(f -> f.rule().equals(expectedRule))
                .filter(f -> f.path().contains(filename))
                .toList();

        if (expectViolation) {
            assertFalse(relevant.isEmpty(),
                    "Expected naming violation for '" + filename + "' in " + subdir + "/");
        } else {
            assertTrue(relevant.isEmpty(),
                    "Expected no naming violation for '" + filename + "' in " + subdir + "/");
        }
    }

    static Stream<Arguments> surfaceWiringNamingCases() {
        Random rng = new Random(42);
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        for (int i = 0; i < 60; i++) {
            // Valid surface filename
            String validKey = randomBlockKey(rng);
            builder.add(Arguments.of(iteration++, validKey + ".surface.json", true, false));
        }
        for (int i = 0; i < 20; i++) {
            // Invalid surface filename
            String invalid = randomInvalidFilename(rng);
            // Make sure it's actually invalid for surface pattern
            boolean matchesSurface = SURFACE_PATTERN.matcher(invalid).matches();
            builder.add(Arguments.of(iteration++, invalid, true, !matchesSurface));
        }
        for (int i = 0; i < 20; i++) {
            // Valid wiring filename
            String validKey = randomBlockKey(rng);
            builder.add(Arguments.of(iteration++, validKey + ".wiring.json", false, false));
        }
        for (int i = 0; i < 20; i++) {
            // Invalid wiring filename — reuse surface-style invalids
            String invalid = randomInvalidFilename(rng);
            boolean matchesWiring = WIRING_PATTERN.matcher(invalid).matches();
            builder.add(Arguments.of(iteration++, invalid, false, !matchesWiring));
        }

        return builder.build();
    }

    // ── Property 2: Zone Sprawl Detection ─────────────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 2: Zone Sprawl Detection
    // Validates: Requirements 2.1, 2.2

    /**
     * For any directory name placed directly under build/generated/bear/, the scanner
     * emits a zone sprawl finding if and only if the name is not in {surfaces, wiring, src, gradle}.
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 2: Zone Sprawl Detection
     * Validates: Requirements 2.1, 2.2
     */
    @ParameterizedTest(name = "iteration {0}: dirName={1}, expectViolation={2}")
    @MethodSource("zoneSprawlCases")
    void zoneSprawlDetection(
            int iteration,
            String dirName,
            boolean expectViolation,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Files.createDirectories(tempDir.resolve("build/generated/bear/" + dirName));

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> sprawlFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .filter(f -> f.path().contains(dirName))
                .toList();

        if (expectViolation) {
            assertFalse(sprawlFindings.isEmpty(),
                    "Expected zone sprawl finding for directory '" + dirName + "'");
        } else {
            assertTrue(sprawlFindings.isEmpty(),
                    "Expected no zone sprawl finding for directory '" + dirName + "'");
        }
    }

    static Stream<Arguments> zoneSprawlCases() {
        Random rng = new Random(43);
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        // Known valid categories — no violation
        for (String known : List.of("surfaces", "wiring", "src", "gradle", "config", ".staging")) {
            builder.add(Arguments.of(iteration++, known, false));
        }

        // Generate 100+ arbitrary directory names — violation iff not in known set
        for (int i = 0; i < 110; i++) {
            String name = randomBlockKey(rng);
            // Avoid collision with known categories
            boolean isKnown = KNOWN_CATEGORIES.contains(name);
            builder.add(Arguments.of(iteration++, name, !isKnown));
        }

        return builder.build();
    }

    // ── Property 3: Source Package Structure Validation ───────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 3: Source Package Structure Validation
    // Validates: Requirements 3.1, 3.2

    /**
     * For any Java source file under build/generated/bear/src/main/java/, the scanner
     * emits a structure violation finding if and only if the file is not under
     * com/bear/generated/{validBlockKey}/.
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 3: Source Package Structure Validation
     * Validates: Requirements 3.1, 3.2
     */
    @ParameterizedTest(name = "iteration {0}: pkgPath={1}, expectViolation={2}")
    @MethodSource("sourcePackageStructureCases")
    void sourcePackageStructureValidation(
            int iteration,
            String pkgPath,
            String javaFilename,
            boolean expectViolation,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Path fullDir = tempDir.resolve("build/generated/bear/src/main/java/" + pkgPath);
        Files.createDirectories(fullDir);
        Files.writeString(fullDir.resolve(javaFilename),
                "package placeholder;\npublic class " + javaFilename.replace(".java", "") + " {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> structFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SOURCE_STRUCTURE_VIOLATION))
                .toList();

        if (expectViolation) {
            assertFalse(structFindings.isEmpty(),
                    "Expected structure violation for package path '" + pkgPath + "'");
        } else {
            assertTrue(structFindings.isEmpty(),
                    "Expected no structure violation for package path '" + pkgPath + "'");
        }
    }

    static Stream<Arguments> sourcePackageStructureCases() {
        Random rng = new Random(44);
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        // Valid paths: com/bear/generated/{validBlockKey}/
        for (int i = 0; i < 50; i++) {
            String blockKey = randomBlockKey(rng);
            String blockName = capitalize(blockKey.replace("-", ""));
            builder.add(Arguments.of(iteration++,
                    "com/bear/generated/" + blockKey,
                    blockName + "Logic.java",
                    false));
        }

        // Valid path: com/bear/generated/runtime/
        for (int i = 0; i < 10; i++) {
            builder.add(Arguments.of(iteration++,
                    "com/bear/generated/runtime",
                    "BearRuntime" + i + ".java",
                    false));
        }

        // Invalid: wrong root package
        for (int i = 0; i < 20; i++) {
            String wrongPkg = randomBlockKey(rng) + "/" + randomBlockKey(rng);
            builder.add(Arguments.of(iteration++,
                    wrongPkg,
                    "Foo.java",
                    true));
        }

        // Invalid: com/bear/ but not generated
        for (int i = 0; i < 15; i++) {
            String wrongSub = randomBlockKey(rng);
            builder.add(Arguments.of(iteration++,
                    "com/bear/" + wrongSub + "/" + randomBlockKey(rng),
                    "Bar.java",
                    true));
        }

        // Invalid: com/bear/generated/{invalidBlockKey}/ (uppercase)
        for (int i = 0; i < 15; i++) {
            String invalidKey = "A" + randomBlockKey(rng);
            String blockName = capitalize(invalidKey.replace("-", ""));
            builder.add(Arguments.of(iteration++,
                    "com/bear/generated/" + invalidKey,
                    blockName + "Logic.java",
                    true));
        }

        return builder.build();
    }

    // ── Property 4: Source File Type Validation ───────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 4: Source File Type Validation
    // Validates: Requirements 3.3, 3.4

    /**
     * For any Java file in a block's generated source directory, the scanner emits
     * an unexpected file finding if and only if the filename does not match expected
     * patterns ({Block}Logic.java, {Block}_{Op}.java, {Block}_{Port}BlockClient.java,
     * BearValue.java, or Bear*.java for runtime).
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 4: Source File Type Validation
     * Validates: Requirements 3.3, 3.4
     */
    @ParameterizedTest(name = "iteration {0}: blockKey={1}, filename={2}, expectViolation={3}")
    @MethodSource("sourceFileTypeCases")
    void sourceFileTypeValidation(
            int iteration,
            String blockKey,
            String filename,
            boolean expectViolation,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Path blockDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/" + blockKey);
        Files.createDirectories(blockDir);
        Files.writeString(blockDir.resolve(filename),
                "package com.bear.generated." + blockKey.replace("-", "") + ";\npublic class "
                        + filename.replace(".java", "") + " {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> fileFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_SOURCE_UNEXPECTED_FILE))
                .filter(f -> f.path().contains(filename))
                .toList();

        if (expectViolation) {
            assertFalse(fileFindings.isEmpty(),
                    "Expected unexpected-file finding for '" + filename + "' in block '" + blockKey + "'");
        } else {
            assertTrue(fileFindings.isEmpty(),
                    "Expected no unexpected-file finding for '" + filename + "' in block '" + blockKey + "'");
        }
    }

    static Stream<Arguments> sourceFileTypeCases() {
        Random rng = new Random(45);
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        // Valid: {Block}Logic.java
        for (int i = 0; i < 20; i++) {
            String blockKey = randomBlockKey(rng);
            String blockName = capitalize(blockKey.replace("-", ""));
            builder.add(Arguments.of(iteration++, blockKey, blockName + "Logic.java", false));
        }

        // Valid: {Block}_{Op}.java (wrapper)
        for (int i = 0; i < 20; i++) {
            String blockKey = randomBlockKey(rng);
            String blockName = capitalize(blockKey.replace("-", ""));
            String opName = "Get" + capitalize(randomBlockKey(rng));
            builder.add(Arguments.of(iteration++, blockKey, blockName + "_" + opName + ".java", false));
        }

        // Valid: {Block}_{Port}BlockClient.java
        for (int i = 0; i < 15; i++) {
            String blockKey = randomBlockKey(rng);
            String blockName = capitalize(blockKey.replace("-", ""));
            String portName = capitalize(randomBlockKey(rng));
            builder.add(Arguments.of(iteration++, blockKey, blockName + "_" + portName + "BlockClient.java", false));
        }

        // Valid: BearValue.java
        for (int i = 0; i < 5; i++) {
            String blockKey = randomBlockKey(rng);
            builder.add(Arguments.of(iteration++, blockKey, "BearValue.java", false));
        }

        // Valid: runtime Bear*.java
        for (int i = 0; i < 15; i++) {
            String className = "Bear" + capitalize(randomBlockKey(rng));
            builder.add(Arguments.of(iteration++, "runtime", className + ".java", false));
        }

        // Invalid: random filenames in block packages
        for (int i = 0; i < 25; i++) {
            String blockKey = randomBlockKey(rng);
            String randomName = capitalize(randomBlockKey(rng)) + "Helper.java";
            builder.add(Arguments.of(iteration++, blockKey, randomName, true));
        }

        // Invalid: runtime file not starting with Bear
        for (int i = 0; i < 10; i++) {
            String className = capitalize(randomBlockKey(rng)) + "Util";
            builder.add(Arguments.of(iteration++, "runtime", className + ".java", true));
        }

        return builder.build();
    }

    // ── Property 5: Forbidden Dependency Detection ────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 5: Forbidden Dependency Detection
    // Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5

    /**
     * For any generated Java source file, the scanner emits a forbidden dependency
     * finding if and only if the file imports from blocks.*.impl.* and the import
     * is not from an allowed package (blocks._shared.pure.*, blocks._shared.state.*).
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 5: Forbidden Dependency Detection
     * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5
     */
    @ParameterizedTest(name = "iteration {0}: import={1}, expectViolation={2}")
    @MethodSource("forbiddenDependencyCases")
    void forbiddenDependencyDetection(
            int iteration,
            String importStatement,
            boolean expectViolation,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Path blockDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
        Files.createDirectories(blockDir);
        Files.writeString(blockDir.resolve("MyblockLogic.java"),
                "package com.bear.generated.myblock;\n"
                        + "import " + importStatement + ";\n"
                        + "public class MyblockLogic {}");

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        List<BoundaryBypassFinding> depFindings = findings.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_FORBIDDEN_DEPENDENCY))
                .toList();

        if (expectViolation) {
            assertFalse(depFindings.isEmpty(),
                    "Expected forbidden dependency finding for import '" + importStatement + "'");
            assertTrue(depFindings.stream().anyMatch(f -> f.detail().contains(importStatement)),
                    "Finding detail should contain the import: " + importStatement);
        } else {
            List<BoundaryBypassFinding> matchingFindings = depFindings.stream()
                    .filter(f -> f.detail().contains(importStatement))
                    .toList();
            assertTrue(matchingFindings.isEmpty(),
                    "Expected no forbidden dependency finding for import '" + importStatement + "'");
        }
    }

    static Stream<Arguments> forbiddenDependencyCases() {
        Random rng = new Random(46);
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        // Forbidden: blocks.{block}.impl.{Class}
        for (int i = 0; i < 40; i++) {
            String blockName = randomBlockKey(rng).replace("-", "_");
            String className = capitalize(randomBlockKey(rng));
            builder.add(Arguments.of(iteration++,
                    "blocks." + blockName + ".impl." + className,
                    true));
        }

        // Allowed: blocks._shared.pure.{Class}
        for (int i = 0; i < 20; i++) {
            String className = capitalize(randomBlockKey(rng));
            builder.add(Arguments.of(iteration++,
                    "blocks._shared.pure." + className,
                    false));
        }

        // Allowed: blocks._shared.state.{Class}
        for (int i = 0; i < 20; i++) {
            String className = capitalize(randomBlockKey(rng));
            builder.add(Arguments.of(iteration++,
                    "blocks._shared.state." + className,
                    false));
        }

        // Not forbidden: standard library imports
        for (int i = 0; i < 15; i++) {
            builder.add(Arguments.of(iteration++, "java.util.List", false));
        }

        // Not forbidden: other non-blocks imports
        for (int i = 0; i < 15; i++) {
            String pkg = randomBlockKey(rng);
            builder.add(Arguments.of(iteration++,
                    "com.example." + pkg + ".SomeClass",
                    false));
        }

        return builder.build();
    }

    // ── Property 6: Allowlist Suppression ─────────────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 6: Allowlist Suppression
    // Validates: Requirements 2.3, 2.4, 7.1, 7.2

    /**
     * For any path that matches an entry in the allowlist, the scanner shall not
     * emit any findings for that path, regardless of which rules would otherwise apply.
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 6: Allowlist Suppression
     * Validates: Requirements 2.3, 2.4, 7.1, 7.2
     */
    @ParameterizedTest(name = "iteration {0}: dirName={1}")
    @MethodSource("allowlistSuppressionCases")
    void allowlistSuppression(
            int iteration,
            String dirName,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        // Create an unexpected directory (would normally trigger zone sprawl)
        Files.createDirectories(tempDir.resolve("build/generated/bear/" + dirName));

        String allowlistedPath = "build/generated/bear/" + dirName;

        // Without allowlist — finding emitted
        List<BoundaryBypassFinding> withoutAllowlist =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());
        List<BoundaryBypassFinding> sprawlWithout = withoutAllowlist.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .filter(f -> f.path().equals(allowlistedPath))
                .toList();
        assertFalse(sprawlWithout.isEmpty(),
                "Expected zone sprawl finding for '" + dirName + "' without allowlist");

        // With allowlist — finding suppressed
        List<BoundaryBypassFinding> withAllowlist =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of(allowlistedPath));
        List<BoundaryBypassFinding> sprawlWith = withAllowlist.stream()
                .filter(f -> f.rule().equals(TasteInvariantScanner.GENERATED_ZONE_UNEXPECTED_PATH))
                .filter(f -> f.path().equals(allowlistedPath))
                .toList();
        assertTrue(sprawlWith.isEmpty(),
                "Expected no zone sprawl finding for '" + dirName + "' with allowlist");
    }

    static Stream<Arguments> allowlistSuppressionCases() {
        Random rng = new Random(47);
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        for (int i = 0; i < 110; i++) {
            String dirName;
            do {
                dirName = randomBlockKey(rng);
            } while (KNOWN_CATEGORIES.contains(dirName));
            builder.add(Arguments.of(iteration++, dirName));
        }

        return builder.build();
    }

    // ── Property 7: Scan Determinism ──────────────────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 7: Scan Determinism
    // Validates: Requirements 1.5, 2.5, 3.5, 5.3, 8.3

    /**
     * For any generated zone configuration, scanning twice with identical inputs
     * shall produce identical findings in identical order.
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 7: Scan Determinism
     * Validates: Requirements 1.5, 2.5, 3.5, 5.3, 8.3
     */
    @ParameterizedTest(name = "iteration {0}: violations={1}")
    @MethodSource("scanDeterminismCases")
    void scanDeterminism(
            int iteration,
            String violationDescription,
            int violationKind,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Random rng = new Random(48 + iteration);

        // Create a mix of violations based on the kind
        switch (violationKind % 5) {
            case 0 -> // Surface naming violation
                    Files.writeString(tempDir.resolve("build/generated/bear/surfaces/Bad" + iteration + ".json"), "{}");
            case 1 -> // Wiring naming violation
                    Files.writeString(tempDir.resolve("build/generated/bear/wiring/Bad" + iteration + ".json"), "{}");
            case 2 -> { // Zone sprawl
                String dirName;
                do {
                    dirName = randomBlockKey(rng);
                } while (KNOWN_CATEGORIES.contains(dirName));
                Files.createDirectories(tempDir.resolve("build/generated/bear/" + dirName));
            }
            case 3 -> { // Source structure violation
                Path wrongPkg = tempDir.resolve("build/generated/bear/src/main/java/wrong/pkg" + iteration);
                Files.createDirectories(wrongPkg);
                Files.writeString(wrongPkg.resolve("Foo.java"), "package wrong.pkg;\npublic class Foo {}");
            }
            case 4 -> { // Forbidden dependency
                Path blockDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
                Files.createDirectories(blockDir);
                Files.writeString(blockDir.resolve("MyblockLogic.java"),
                        "package com.bear.generated.myblock;\nimport blocks.other.impl.Bad;\npublic class MyblockLogic {}");
            }
        }

        List<BoundaryBypassFinding> first =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());
        List<BoundaryBypassFinding> second =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        assertEquals(first, second,
                "Two scans of the same zone must produce identical findings");
        assertEquals(first.size(), second.size(),
                "Finding count must be identical across scans");
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).rule(), second.get(i).rule(),
                    "Finding rule at index " + i + " must match");
            assertEquals(first.get(i).path(), second.get(i).path(),
                    "Finding path at index " + i + " must match");
            assertEquals(first.get(i).detail(), second.get(i).detail(),
                    "Finding detail at index " + i + " must match");
        }
    }

    static Stream<Arguments> scanDeterminismCases() {
        Stream.Builder<Arguments> builder = Stream.builder();
        String[] descriptions = {
                "surface naming", "wiring naming", "zone sprawl",
                "source structure", "forbidden dependency"
        };
        int iteration = 0;

        // Generate 100+ cases cycling through violation kinds
        for (int cycle = 0; cycle < 21; cycle++) {
            for (int kind = 0; kind < 5; kind++) {
                builder.add(Arguments.of(iteration++, descriptions[kind], kind));
            }
        }

        return builder.build();
    }

    // ── Property 8: Failure Envelope Format ───────────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 8: Failure Envelope Format
    // Validates: Requirements 5.1

    /**
     * For all findings from various violations, each finding has non-null, non-empty
     * rule, path, and detail fields. Detail is deterministic (no timestamps, UUIDs,
     * or random content).
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 8: Failure Envelope Format
     * Validates: Requirements 5.1
     */
    @ParameterizedTest(name = "iteration {0}: violationKind={1}")
    @MethodSource("failureEnvelopeFormatCases")
    void failureEnvelopeFormat(
            int iteration,
            String violationDescription,
            int violationKind,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Random rng = new Random(49 + iteration);

        // Create a violation to produce findings
        switch (violationKind % 6) {
            case 0 ->
                    Files.writeString(tempDir.resolve("build/generated/bear/surfaces/Bad" + iteration + ".json"), "{}");
            case 1 ->
                    Files.writeString(tempDir.resolve("build/generated/bear/wiring/Bad" + iteration + ".json"), "{}");
            case 2 -> {
                String dirName;
                do {
                    dirName = randomBlockKey(rng);
                } while (KNOWN_CATEGORIES.contains(dirName));
                Files.createDirectories(tempDir.resolve("build/generated/bear/" + dirName));
            }
            case 3 -> {
                Path wrongPkg = tempDir.resolve("build/generated/bear/src/main/java/wrong/pkg" + iteration);
                Files.createDirectories(wrongPkg);
                Files.writeString(wrongPkg.resolve("Foo.java"), "package wrong.pkg;\npublic class Foo {}");
            }
            case 4 -> {
                Path blockDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
                Files.createDirectories(blockDir);
                Files.writeString(blockDir.resolve("RandomFile" + iteration + ".java"),
                        "package com.bear.generated.myblock;\npublic class RandomFile" + iteration + " {}");
            }
            case 5 -> {
                Path blockDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
                Files.createDirectories(blockDir);
                Files.writeString(blockDir.resolve("MyblockLogic.java"),
                        "package com.bear.generated.myblock;\nimport blocks.other.impl.Bad;\npublic class MyblockLogic {}");
            }
        }

        List<BoundaryBypassFinding> findings =
                TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        assertFalse(findings.isEmpty(),
                "Expected at least one finding for violation kind " + violationKind);

        for (BoundaryBypassFinding f : findings) {
            // Non-null, non-empty fields
            assertNotNull(f.rule(), "Finding rule must not be null");
            assertNotNull(f.path(), "Finding path must not be null");
            assertNotNull(f.detail(), "Finding detail must not be null");
            assertFalse(f.rule().isBlank(), "Finding rule must not be blank");
            assertFalse(f.path().isBlank(), "Finding path must not be blank");
            assertFalse(f.detail().isBlank(), "Finding detail must not be blank");

            // Deterministic: no timestamps
            assertFalse(f.detail().matches(".*\\d{4}-\\d{2}-\\d{2}.*"),
                    "detail should not contain timestamps: " + f.detail());
            // No UUIDs
            assertFalse(f.detail().matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*"),
                    "detail should not contain UUIDs: " + f.detail());
        }
    }

    static Stream<Arguments> failureEnvelopeFormatCases() {
        Stream.Builder<Arguments> builder = Stream.builder();
        String[] descriptions = {
                "surface naming", "wiring naming", "zone sprawl",
                "source structure", "unexpected file", "forbidden dependency"
        };
        int iteration = 0;

        // Generate 100+ cases cycling through all 6 violation kinds
        for (int cycle = 0; cycle < 18; cycle++) {
            for (int kind = 0; kind < 6; kind++) {
                builder.add(Arguments.of(iteration++, descriptions[kind], kind));
            }
        }

        return builder.build();
    }

    // ── Property 9: Read-Only Scan ────────────────────────────────────────────
    // Feature: p2-minimal-taste-invariants-rule-pack, Property 9: Read-Only Scan
    // Validates: Requirements 8.4

    /**
     * For any scan execution, the scanner shall not modify any files in the project
     * directory; it is a read-only operation. File timestamps and sizes remain unchanged.
     *
     * Feature: p2-minimal-taste-invariants-rule-pack, Property 9: Read-Only Scan
     * Validates: Requirements 8.4
     */
    @ParameterizedTest(name = "iteration {0}: violationKind={1}")
    @MethodSource("readOnlyScanCases")
    void readOnlyScan(
            int iteration,
            String violationDescription,
            int violationKind,
            @TempDir Path tempDir) throws IOException {

        createGeneratedZone(tempDir);
        Random rng = new Random(50 + iteration);

        // Create some content to scan
        Files.writeString(tempDir.resolve("build/generated/bear/surfaces/my-block.surface.json"), "{}");
        Files.writeString(tempDir.resolve("build/generated/bear/wiring/my-block.wiring.json"), "{}");
        Files.writeString(tempDir.resolve("build/generated/bear/gradle/bear-containment.gradle"), "");

        // Add a violation
        switch (violationKind % 3) {
            case 0 ->
                    Files.writeString(tempDir.resolve("build/generated/bear/surfaces/Bad.json"), "{}");
            case 1 -> {
                String dirName;
                do {
                    dirName = randomBlockKey(rng);
                } while (KNOWN_CATEGORIES.contains(dirName));
                Files.createDirectories(tempDir.resolve("build/generated/bear/" + dirName));
            }
            case 2 -> {
                Path blockDir = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/myblock");
                Files.createDirectories(blockDir);
                Files.writeString(blockDir.resolve("MyblockLogic.java"),
                        "package com.bear.generated.myblock;\nimport blocks.other.impl.Bad;\npublic class MyblockLogic {}");
            }
        }

        // Record file timestamps and sizes before scan
        Map<Path, FileSnapshot> before = snapshotFiles(tempDir);

        // Perform scan
        TasteInvariantScanner.scanTasteInvariants(tempDir, Set.of());

        // Record file timestamps and sizes after scan
        Map<Path, FileSnapshot> after = snapshotFiles(tempDir);

        // Verify no files were modified, created, or deleted
        assertEquals(before.keySet(), after.keySet(),
                "Set of files should not change after scan");
        for (Map.Entry<Path, FileSnapshot> entry : before.entrySet()) {
            Path file = entry.getKey();
            FileSnapshot beforeSnap = entry.getValue();
            FileSnapshot afterSnap = after.get(file);
            assertEquals(beforeSnap.size(), afterSnap.size(),
                    "File size should not change: " + file);
            assertEquals(beforeSnap.lastModified(), afterSnap.lastModified(),
                    "File timestamp should not change: " + file);
        }
    }

    static Stream<Arguments> readOnlyScanCases() {
        String[] descriptions = {"surface naming", "zone sprawl", "forbidden dependency"};
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;

        for (int cycle = 0; cycle < 35; cycle++) {
            for (int kind = 0; kind < 3; kind++) {
                builder.add(Arguments.of(iteration++, descriptions[kind], kind));
            }
        }

        return builder.build();
    }

    private record FileSnapshot(long size, FileTime lastModified) {}

    private static Map<Path, FileSnapshot> snapshotFiles(Path root) throws IOException {
        Map<Path, FileSnapshot> snapshot = new HashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                snapshot.put(root.relativize(file),
                        new FileSnapshot(attrs.size(), attrs.lastModifiedTime()));
                return FileVisitResult.CONTINUE;
            }
        });
        return snapshot;
    }
}
