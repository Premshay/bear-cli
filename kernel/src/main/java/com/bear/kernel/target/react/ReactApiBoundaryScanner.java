package com.bear.kernel.target.react;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * PARTIAL scanner for API boundary enforcement in governed .tsx component files.
 * Detects direct fetch() and new XMLHttpRequest() calls.
 * 
 * <p>This scanner uses string matching, not AST analysis. It may produce false
 * positives for commented-out code or string literals. This is acceptable for
 * the first slice — the same PARTIAL approach used by Python's dynamic execution scanner.
 */
public class ReactApiBoundaryScanner {

    private static final String FETCH_PATTERN = "fetch(";
    private static final String XHR_PATTERN = "new XMLHttpRequest(";

    /**
     * Scans all governed .tsx component files for direct fetch() and XMLHttpRequest calls.
     *
     * @param projectRoot the project root directory
     * @param wiringManifests list of wiring manifests for governed blocks
     * @return list of boundary bypass findings, sorted by file path
     */
    public List<BoundaryBypassFinding> scan(Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
        List<BoundaryBypassFinding> findings = new ArrayList<>();

        // 1. Compute governed roots from wiring manifests
        Set<Path> governedRoots = computeGovernedRoots(projectRoot, wiringManifests);

        // 2. Collect .tsx component files (excluding .ts and test files)
        for (Path root : governedRoots) {
            List<Path> componentFiles = collectComponentFiles(root);
            
            // 3. Scan each .tsx file for fetch( and new XMLHttpRequest(
            for (Path file : componentFiles) {
                findings.addAll(scanFile(file, projectRoot));
            }
        }

        // 4. Sort findings by file path
        findings.sort(Comparator.comparing(BoundaryBypassFinding::path));

        return findings;
    }

    /**
     * Computes governed roots from wiring manifests.
     * Each manifest's blockKey maps to src/features/{blockKey}/
     */
    private Set<Path> computeGovernedRoots(Path projectRoot, List<WiringManifest> wiringManifests) {
        Set<Path> roots = new HashSet<>();
        for (WiringManifest manifest : wiringManifests) {
            Path featureRoot = projectRoot.resolve("src/features/" + manifest.blockKey());
            if (Files.isDirectory(featureRoot)) {
                roots.add(featureRoot);
            }
        }
        return roots;
    }

    /**
     * Collects governed .tsx component files from a root directory.
     * Excludes .ts files (non-component) and test files (*.test.tsx, *.spec.tsx).
     */
    private List<Path> collectComponentFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(this::isComponentFile)
                .toList();
        }
    }

    /**
     * Checks if a file is a governed .tsx component file.
     * Excludes .ts files and test files.
     */
    private boolean isComponentFile(Path file) {
        String fileName = file.getFileName().toString();

        // Must be .tsx (not .ts — non-component files like *Service.ts, *Api.ts are excluded)
        if (!fileName.endsWith(".tsx")) {
            return false;
        }

        // Exclude test files
        if (fileName.endsWith(".test.tsx") || fileName.endsWith(".spec.tsx")) {
            return false;
        }

        return true;
    }

    /**
     * Scans a single .tsx file for fetch() and XMLHttpRequest calls.
     */
    private List<BoundaryBypassFinding> scanFile(Path file, Path projectRoot) throws IOException {
        List<BoundaryBypassFinding> findings = new ArrayList<>();

        String content = Files.readString(file);
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');

        // Check for fetch( pattern
        if (content.contains(FETCH_PATTERN)) {
            findings.add(new BoundaryBypassFinding(
                "BOUNDARY_BYPASS",
                relativePath,
                "fetch("
            ));
        }

        // Check for new XMLHttpRequest( pattern
        if (content.contains(XHR_PATTERN)) {
            findings.add(new BoundaryBypassFinding(
                "BOUNDARY_BYPASS",
                relativePath,
                "new XMLHttpRequest("
            ));
        }

        return findings;
    }
}
