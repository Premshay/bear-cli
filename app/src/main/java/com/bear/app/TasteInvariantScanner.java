package com.bear.app;

import com.bear.kernel.target.BoundaryBypassFinding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class TasteInvariantScanner {

    // Rule ID constants
    static final String GENERATED_SURFACE_NAMING_VIOLATION = "GENERATED_SURFACE_NAMING_VIOLATION";
    static final String GENERATED_WIRING_NAMING_VIOLATION = "GENERATED_WIRING_NAMING_VIOLATION";
    static final String GENERATED_ZONE_UNEXPECTED_PATH = "GENERATED_ZONE_UNEXPECTED_PATH";
    static final String GENERATED_SOURCE_STRUCTURE_VIOLATION = "GENERATED_SOURCE_STRUCTURE_VIOLATION";
    static final String GENERATED_SOURCE_UNEXPECTED_FILE = "GENERATED_SOURCE_UNEXPECTED_FILE";
    static final String GENERATED_FORBIDDEN_DEPENDENCY = "GENERATED_FORBIDDEN_DEPENDENCY";

    // Allowlist path constant
    static final String TASTE_INVARIANTS_ALLOWLIST_PATH = "bear-policy/taste-invariants-allowlist.txt";

    private static final String GENERATED_BEAR_ROOT = "build/generated/bear";
    private static final Pattern SURFACE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*\\.surface\\.json$");
    private static final Pattern WIRING_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*\\.wiring\\.json$");
    private static final Pattern BLOCK_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+([a-zA-Z0-9_.]+);");
    private static final Pattern FORBIDDEN_IMPORT_PATTERN = Pattern.compile("^blocks\\.[a-z][a-z0-9_]*\\.impl\\.");
    private static final Pattern ALLOWED_SHARED_PURE = Pattern.compile("^blocks\\._shared\\.pure\\.");
    private static final Pattern ALLOWED_SHARED_STATE = Pattern.compile("^blocks\\._shared\\.state\\.");
    private static final Set<String> KNOWN_CATEGORIES = Set.of("surfaces", "wiring", "src", "gradle", "config", ".staging");
    private static final String EXPECTED_PREFIX = "com/bear/generated/";
    private static final Pattern GENERATED_FILE_PATTERN = Pattern.compile("^[A-Z][A-Za-z0-9]*(_[A-Z][A-Za-z0-9]*)*$");

    private TasteInvariantScanner() {
    }

    /**
     * Scans the generated zone for taste-invariant violations.
     *
     * @param projectRoot the project root directory
     * @param allowlist   paths to skip (from taste-invariants-allowlist.txt)
     * @return list of findings sorted by path, then rule, then detail
     */
    static List<BoundaryBypassFinding> scanTasteInvariants(
        Path projectRoot,
        Set<String> allowlist
    ) throws IOException {
        Path generatedBearRoot = projectRoot.resolve(GENERATED_BEAR_ROOT);
        if (!Files.isDirectory(generatedBearRoot)) {
            return List.of();
        }

        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();

        Path surfacesDir = generatedBearRoot.resolve("surfaces");
        if (Files.isDirectory(surfacesDir)) {
            findings.addAll(validateSurfaceNaming(projectRoot, surfacesDir));
        }

        Path wiringDir = generatedBearRoot.resolve("wiring");
        if (Files.isDirectory(wiringDir)) {
            findings.addAll(validateWiringNaming(projectRoot, wiringDir));
        }

        findings.addAll(detectZoneSprawl(projectRoot, generatedBearRoot, allowlist));

        Path srcRoot = generatedBearRoot.resolve("src/main/java");
        if (Files.isDirectory(srcRoot)) {
            findings.addAll(validateSourceStructure(projectRoot, srcRoot));
            Path generatedPkgRoot = srcRoot.resolve("com/bear/generated");
            if (Files.isDirectory(generatedPkgRoot)) {
                findings.addAll(validateSourceFileTypes(projectRoot, srcRoot, generatedPkgRoot));
            }
            findings.addAll(detectForbiddenDependencies(projectRoot, srcRoot));
        }

        findings.sort(Comparator.comparing(BoundaryBypassFinding::path)
            .thenComparing(BoundaryBypassFinding::rule)
            .thenComparing(BoundaryBypassFinding::detail));
        return List.copyOf(findings);
    }

    // ── Rule 1: Surface Naming Validation ──────────────────────────────────────

    private static List<BoundaryBypassFinding> validateSurfaceNaming(
        Path projectRoot, Path surfacesDir
    ) throws IOException {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.list(surfacesDir)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                if (Files.isDirectory(entry)) {
                    continue;
                }
                String filename = entry.getFileName().toString();
                if (!SURFACE_PATTERN.matcher(filename).matches()) {
                    String relPath = projectRoot.relativize(entry).toString().replace('\\', '/');
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_SURFACE_NAMING_VIOLATION,
                        relPath,
                        "surface file must match pattern {blockKey}.surface.json"
                    ));
                }
            }
        }
        return findings;
    }

    // ── Rule 2: Wiring Naming Validation ───────────────────────────────────────

    private static List<BoundaryBypassFinding> validateWiringNaming(
        Path projectRoot, Path wiringDir
    ) throws IOException {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.list(wiringDir)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                if (Files.isDirectory(entry)) {
                    continue;
                }
                String filename = entry.getFileName().toString();
                if (!WIRING_PATTERN.matcher(filename).matches()) {
                    String relPath = projectRoot.relativize(entry).toString().replace('\\', '/');
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_WIRING_NAMING_VIOLATION,
                        relPath,
                        "wiring file must match pattern {blockKey}.wiring.json"
                    ));
                }
            }
        }
        return findings;
    }

    // ── Rule 3: Zone Sprawl Detection ──────────────────────────────────────────

    private static List<BoundaryBypassFinding> detectZoneSprawl(
        Path projectRoot, Path generatedBearRoot, Set<String> allowlist
    ) throws IOException {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(generatedBearRoot)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                if (entry.equals(generatedBearRoot)) {
                    continue;
                }
                String relPath = projectRoot.relativize(entry).toString().replace('\\', '/');
                if (allowlist.contains(relPath)) {
                    continue;
                }

                Path relative = generatedBearRoot.relativize(entry);
                String firstSegment = relative.getName(0).toString();

                // Direct children of generated bear root
                if (relative.getNameCount() == 1) {
                    if (!KNOWN_CATEGORIES.contains(firstSegment)) {
                        findings.add(new BoundaryBypassFinding(
                            GENERATED_ZONE_UNEXPECTED_PATH,
                            relPath,
                            "unexpected path in generated zone; expected: surfaces/, wiring/, src/, gradle/, config/, .staging/"
                        ));
                    }
                    continue;
                }

                // Check surfaces/ contents — no directories allowed
                if ("surfaces".equals(firstSegment) && Files.isDirectory(entry)) {
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_ZONE_UNEXPECTED_PATH,
                        relPath,
                        "unexpected directory in surfaces/; only .surface.json files expected"
                    ));
                    continue;
                }

                // Check wiring/ contents — no directories allowed
                if ("wiring".equals(firstSegment) && Files.isDirectory(entry)) {
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_ZONE_UNEXPECTED_PATH,
                        relPath,
                        "unexpected directory in wiring/; only .wiring.json files expected"
                    ));
                    continue;
                }

                // Check gradle/ contents — only bear-containment.gradle allowed
                if ("gradle".equals(firstSegment) && Files.isRegularFile(entry)) {
                    String fileName = entry.getFileName().toString();
                    if (!"bear-containment.gradle".equals(fileName)) {
                        findings.add(new BoundaryBypassFinding(
                            GENERATED_ZONE_UNEXPECTED_PATH,
                            relPath,
                            "unexpected file in gradle/; only bear-containment.gradle expected"
                        ));
                    }
                }
            }
        }
        return findings;
    }

    // ── Rule 4: Source Structure Validation ─────────────────────────────────────

    private static List<BoundaryBypassFinding> validateSourceStructure(
        Path projectRoot, Path srcRoot
    ) throws IOException {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(srcRoot)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String filename = entry.getFileName().toString();
                if (!filename.endsWith(".java")) {
                    continue;
                }
                String relFromSrc = srcRoot.relativize(entry).toString().replace('\\', '/');
                String fullRelPath = GENERATED_BEAR_ROOT + "/src/main/java/" + relFromSrc;

                if (!relFromSrc.startsWith(EXPECTED_PREFIX)) {
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_SOURCE_STRUCTURE_VIOLATION,
                        fullRelPath,
                        "generated source must be under com.bear.generated.{block}/ package"
                    ));
                    continue;
                }

                String afterPrefix = relFromSrc.substring(EXPECTED_PREFIX.length());
                String[] parts = afterPrefix.split("/");
                if (parts.length < 2) {
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_SOURCE_STRUCTURE_VIOLATION,
                        fullRelPath,
                        "generated source must be in a block-specific package"
                    ));
                    continue;
                }

                String blockDir = parts[0];
                if (!"runtime".equals(blockDir) && !isValidBlockKey(blockDir)) {
                    findings.add(new BoundaryBypassFinding(
                        GENERATED_SOURCE_STRUCTURE_VIOLATION,
                        fullRelPath,
                        "invalid block package name: " + blockDir
                    ));
                }
            }
        }
        return findings;
    }

    // ── Rule 5: Source File Type Validation ─────────────────────────────────────

    private static List<BoundaryBypassFinding> validateSourceFileTypes(
        Path projectRoot, Path srcRoot, Path generatedPkgRoot
    ) throws IOException {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(generatedPkgRoot)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String filename = entry.getFileName().toString();
                if (!filename.endsWith(".java")) {
                    continue;
                }

                String relFromGenerated = generatedPkgRoot.relativize(entry).toString().replace('\\', '/');
                String[] parts = relFromGenerated.split("/");
                if (parts.length < 2) {
                    continue; // handled by structure validation
                }

                String blockDir = parts[0];
                String relFromSrc = srcRoot.relativize(entry).toString().replace('\\', '/');
                String fullRelPath = GENERATED_BEAR_ROOT + "/src/main/java/" + relFromSrc;

                if ("runtime".equals(blockDir)) {
                    if (!filename.startsWith("Bear")) {
                        findings.add(new BoundaryBypassFinding(
                            GENERATED_SOURCE_UNEXPECTED_FILE,
                            fullRelPath,
                            "runtime class must start with 'Bear' prefix"
                        ));
                    }
                } else {
                    if (!isExpectedGeneratedFile(filename)) {
                        findings.add(new BoundaryBypassFinding(
                            GENERATED_SOURCE_UNEXPECTED_FILE,
                            fullRelPath,
                            "unexpected file in block package; expected: {Block}Logic.java, {Block}_{Op}.java, or {Block}_{Port}BlockClient.java"
                        ));
                    }
                }
            }
        }
        return findings;
    }

    // ── Rule 6: Forbidden Dependency Detection ─────────────────────────────────

    private static List<BoundaryBypassFinding> detectForbiddenDependencies(
        Path projectRoot, Path srcRoot
    ) throws IOException {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(srcRoot)) {
            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String filename = entry.getFileName().toString();
                if (!filename.endsWith(".java")) {
                    continue;
                }

                String relFromSrc = srcRoot.relativize(entry).toString().replace('\\', '/');
                String fullRelPath = GENERATED_BEAR_ROOT + "/src/main/java/" + relFromSrc;

                List<String> lines = Files.readAllLines(entry, StandardCharsets.UTF_8);
                for (String line : lines) {
                    Matcher m = IMPORT_PATTERN.matcher(line.trim());
                    if (!m.matches()) {
                        continue;
                    }
                    String importFqcn = m.group(1);

                    if (ALLOWED_SHARED_PURE.matcher(importFqcn).find()
                        || ALLOWED_SHARED_STATE.matcher(importFqcn).find()) {
                        continue;
                    }

                    if (FORBIDDEN_IMPORT_PATTERN.matcher(importFqcn).find()) {
                        findings.add(new BoundaryBypassFinding(
                            GENERATED_FORBIDDEN_DEPENDENCY,
                            fullRelPath,
                            "forbidden import from impl package: " + importFqcn
                        ));
                    }
                }
            }
        }
        return findings;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static boolean isValidBlockKey(String name) {
        return BLOCK_KEY_PATTERN.matcher(name).matches();
    }

    /**
     * Validates that a filename matches any known BEAR-generated file pattern.
     * This is block-name-agnostic because multi-segment package names make it
     * impossible to reliably reconstruct the block name from directory structure.
     *
     * Accepted patterns:
     * - {PascalName}Logic.java (logic interface)
     * - {PascalName}_{PascalOp}.java (wrapper)
     * - {PascalName}_{PascalOp}Request.java (request model)
     * - {PascalName}_{PascalOp}Result.java (result model)
     * - {PascalName}_{PascalPort}BlockClient.java (block port client)
     * - {PascalName}Port.java (port interface)
     * - {PascalName}StructuralDirectionTest.java (structural test)
     * - {PascalName}StructuralReachTest.java (structural test)
     * - BearValue.java (shared value type)
     */
    private static boolean isExpectedGeneratedFile(String filename) {
        // BearValue.java (shared value type)
        if ("BearValue.java".equals(filename)) {
            return true;
        }
        // Must be a PascalCase .java file
        if (!filename.endsWith(".java") || filename.length() <= 5) {
            return false;
        }
        String withoutExt = filename.substring(0, filename.length() - ".java".length());
        if (withoutExt.isEmpty() || !Character.isUpperCase(withoutExt.charAt(0))) {
            return false;
        }
        // Check known generated file suffixes/patterns
        // Logic interface: {Block}Logic
        if (withoutExt.endsWith("Logic")) {
            return true;
        }
        // Port interface: {Name}Port
        if (withoutExt.endsWith("Port")) {
            return true;
        }
        // Underscore-separated patterns: {Block}_{Op}, {Block}_{Op}Request, {Block}_{Op}Result, {Block}_{Port}BlockClient
        if (withoutExt.contains("_")) {
            return GENERATED_FILE_PATTERN.matcher(withoutExt).matches();
        }
        // Request/Result models: {Block}_{Op}Request or {Block}_{Op}Result (already covered by underscore check)
        // Structural tests
        if (withoutExt.endsWith("StructuralDirectionTest") || withoutExt.endsWith("StructuralReachTest")) {
            return true;
        }
        return false;
    }

    private static boolean isExpectedBlockFile(String filename, String blockName) {
        // {Block}Logic.java
        if (filename.equals(blockName + "Logic.java")) {
            return true;
        }
        // {Block}_{Op}.java (wrapper) — must start with Block_ and have uppercase after _
        if (filename.startsWith(blockName + "_") && filename.endsWith(".java")) {
            String afterPrefix = filename.substring((blockName + "_").length());
            String withoutExt = afterPrefix.substring(0, afterPrefix.length() - ".java".length());
            if (!withoutExt.isEmpty() && Character.isUpperCase(withoutExt.charAt(0))) {
                // Check it's not a BlockClient — those are handled separately
                if (withoutExt.endsWith("BlockClient")) {
                    return true; // {Block}_{Port}BlockClient.java
                }
                // Plain wrapper: {Block}_{Op}.java
                return Pattern.matches("[A-Z][A-Za-z0-9]*", withoutExt);
            }
        }
        // BearValue.java (shared value type)
        if ("BearValue.java".equals(filename)) {
            return true;
        }
        return false;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
