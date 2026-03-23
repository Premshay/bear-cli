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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static scanner for import boundary enforcement in governed .ts/.tsx files.
 * Orchestrates governed roots computation, file collection, and per-file scanning.
 */
public class ReactImportContainmentScanner {

    private final ReactImportSpecifierExtractor extractor = new ReactImportSpecifierExtractor();
    private final ReactImportBoundaryResolver resolver = new ReactImportBoundaryResolver();

    // Pattern to detect "use client" or "use server" as first statement
    // Uses \A anchor to match only at file start (not line start with MULTILINE)
    private static final Pattern DIRECTIVE_PATTERN = Pattern.compile(
        "\\A\\s*(?:/\\*[\\s\\S]*?\\*/\\s*|//[^\\n]*\\n\\s*)*['\"]use (client|server)['\"];?"
    );

    /**
     * Scans all governed files for import boundary violations.
     *
     * @param projectRoot the project root directory
     * @param wiringManifests list of wiring manifests for governed blocks
     * @return list of boundary bypass findings, sorted by file path
     */
    public List<BoundaryBypassFinding> scan(Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
        List<BoundaryBypassFinding> findings = new ArrayList<>();

        // 1. Compute governed roots
        Set<Path> featureRoots = computeFeatureRoots(projectRoot, wiringManifests);
        Path sharedRoot = computeSharedRoot(projectRoot);
        Path generatedRoot = projectRoot.resolve("build/generated/bear");

        // 2. Detect sub-profile
        ReactProjectShape subProfile = detectSubProfile(projectRoot);

        // 3. Collect and scan governed files
        for (Path featureRoot : featureRoots) {
            List<Path> governedFiles = collectGovernedFiles(featureRoot);
            for (Path file : governedFiles) {
                findings.addAll(scanFile(file, featureRoot, projectRoot, subProfile, sharedRoot, generatedRoot));
            }
        }

        // 4. Scan shared root if present (shared files cannot import from feature roots)
        if (sharedRoot != null && Files.isDirectory(sharedRoot)) {
            List<Path> sharedFiles = collectGovernedFiles(sharedRoot);
            for (Path file : sharedFiles) {
                findings.addAll(scanSharedFile(file, projectRoot, subProfile, sharedRoot, generatedRoot, featureRoots));
            }
        }

        // 5. Sort findings by file path
        findings.sort(Comparator.comparing(BoundaryBypassFinding::path));

        return findings;
    }

    /**
     * Computes feature roots from wiring manifests.
     * Each manifest's blockKey maps to src/features/{blockKey}/
     */
    private Set<Path> computeFeatureRoots(Path projectRoot, List<WiringManifest> wiringManifests) {
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
     * Computes the shared root if it exists.
     */
    private Path computeSharedRoot(Path projectRoot) {
        Path sharedRoot = projectRoot.resolve("src/shared");
        return Files.isDirectory(sharedRoot) ? sharedRoot : null;
    }

    /**
     * Detects the sub-profile by checking for next.config.* presence.
     * Lightweight re-detection at scan time.
     */
    public ReactProjectShape detectSubProfile(Path projectRoot) {
        if (Files.exists(projectRoot.resolve("next.config.js")) ||
            Files.exists(projectRoot.resolve("next.config.mjs")) ||
            Files.exists(projectRoot.resolve("next.config.ts"))) {
            return ReactProjectShape.NEXTJS_APP_ROUTER;
        }
        return ReactProjectShape.VITE_REACT;
    }

    /**
     * Collects governed .ts and .tsx files from a root directory.
     * Excludes test files (*.test.ts, *.test.tsx, *.spec.ts, *.spec.tsx).
     */
    private List<Path> collectGovernedFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(this::isGovernedFile)
                .toList();
        }
    }

    /**
     * Checks if a file is a governed TypeScript file.
     */
    private boolean isGovernedFile(Path file) {
        String fileName = file.getFileName().toString();

        // Must be .ts or .tsx
        if (!fileName.endsWith(".ts") && !fileName.endsWith(".tsx")) {
            return false;
        }

        // Exclude test files
        if (fileName.endsWith(".test.ts") || fileName.endsWith(".test.tsx") ||
            fileName.endsWith(".spec.ts") || fileName.endsWith(".spec.tsx")) {
            return false;
        }

        return true;
    }

    /**
     * Scans a single file for import boundary violations.
     */
    private List<BoundaryBypassFinding> scanFile(
            Path file,
            Path featureRoot,
            Path projectRoot,
            ReactProjectShape subProfile,
            Path sharedRoot,
            Path generatedRoot
    ) throws IOException {
        List<BoundaryBypassFinding> findings = new ArrayList<>();

        String content = Files.readString(file);
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');

        // Detect directive (metadata signal only, no enforcement)
        String directive = detectDirective(content);
        // directive is recorded but not used for enforcement in this phase

        // Extract imports
        List<ReactImportSpecifierExtractor.ImportSpecifier> imports = extractor.extractImports(relativePath, content);

        // Resolve each import
        for (ReactImportSpecifierExtractor.ImportSpecifier imp : imports) {
            ReactImportBoundaryResolver.BoundaryDecision decision = resolver.resolve(
                file,
                imp.specifier(),
                featureRoot,
                projectRoot,
                subProfile,
                sharedRoot,
                generatedRoot
            );

            if (!decision.isAllowed()) {
                findings.add(new BoundaryBypassFinding(
                    decision.code(),
                    relativePath,
                    "line " + imp.lineNumber() + ": import '" + imp.specifier() + "' — " + decision.reason()
                ));
            }
        }

        return findings;
    }

    /**
     * Scans a shared file for import boundary violations.
     * Shared files cannot import from feature roots.
     */
    private List<BoundaryBypassFinding> scanSharedFile(
            Path file,
            Path projectRoot,
            ReactProjectShape subProfile,
            Path sharedRoot,
            Path generatedRoot,
            Set<Path> featureRoots
    ) throws IOException {
        List<BoundaryBypassFinding> findings = new ArrayList<>();

        String content = Files.readString(file);
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');

        // Extract imports
        List<ReactImportSpecifierExtractor.ImportSpecifier> imports = extractor.extractImports(relativePath, content);

        // Resolve each import — shared files use sharedRoot as their "feature root"
        // but we need special handling to prevent imports into feature roots
        for (ReactImportSpecifierExtractor.ImportSpecifier imp : imports) {
            // First check if it's a relative import that might reach a feature root
            if (imp.specifier().startsWith("./") || imp.specifier().startsWith("../")) {
                Path resolved = file.getParent().resolve(imp.specifier()).normalize();
                
                // Check if resolved path is within any feature root
                boolean reachesFeature = false;
                for (Path featureRoot : featureRoots) {
                    if (isWithinDirectory(resolved, featureRoot)) {
                        findings.add(new BoundaryBypassFinding(
                            "BOUNDARY_BYPASS",
                            relativePath,
                            "line " + imp.lineNumber() + ": import '" + imp.specifier() + 
                            "' — shared code cannot import from feature roots"
                        ));
                        reachesFeature = true;
                        break;
                    }
                }
                if (reachesFeature) {
                    continue;
                }

                // Also run standard boundary resolution so shared code can't escape to ungoverned areas
                ReactImportBoundaryResolver.BoundaryDecision decision = resolver.resolve(
                    file, imp.specifier(), sharedRoot, projectRoot, subProfile, sharedRoot, generatedRoot
                );
                if (!decision.isAllowed()) {
                    findings.add(new BoundaryBypassFinding(
                        decision.code(), relativePath,
                        "line " + imp.lineNumber() + ": import '" + imp.specifier() + "' — " + decision.reason()
                    ));
                }
                continue;
            }

            // Handle @/* alias in shared files
            if (imp.specifier().startsWith("@/")) {
                if (subProfile != ReactProjectShape.NEXTJS_APP_ROUTER) {
                    findings.add(new BoundaryBypassFinding(
                        "BOUNDARY_BYPASS",
                        relativePath,
                        "line " + imp.lineNumber() + ": import '" + imp.specifier() + 
                        "' — @/* path aliases are not supported in vite-react sub-profile"
                    ));
                    continue;
                }

                // Resolve @/ to ./src/
                String pathAfterAlias = imp.specifier().substring(2);
                Path resolved = projectRoot.resolve("src").resolve(pathAfterAlias).normalize();

                // Check if resolved path is within any feature root
                boolean reachesFeature = false;
                for (Path featureRoot : featureRoots) {
                    if (isWithinDirectory(resolved, featureRoot)) {
                        findings.add(new BoundaryBypassFinding(
                            "BOUNDARY_BYPASS",
                            relativePath,
                            "line " + imp.lineNumber() + ": import '" + imp.specifier() + 
                            "' — shared code cannot import from feature roots"
                        ));
                        reachesFeature = true;
                        break;
                    }
                }
                if (reachesFeature) {
                    continue;
                }

                // Also check the resolved path is within shared or generated roots
                if (!isWithinDirectory(resolved, sharedRoot) &&
                    !isWithinDirectory(resolved, projectRoot.resolve("build/generated/bear"))) {
                    findings.add(new BoundaryBypassFinding(
                        "BOUNDARY_BYPASS",
                        relativePath,
                        "line " + imp.lineNumber() + ": import '" + imp.specifier() +
                        "' — shared code @/ alias resolves outside governed roots"
                    ));
                }
                continue;
            }

            // For other specifiers, use standard resolution
            ReactImportBoundaryResolver.BoundaryDecision decision = resolver.resolve(
                file,
                imp.specifier(),
                sharedRoot, // shared files use sharedRoot as their boundary
                projectRoot,
                subProfile,
                sharedRoot,
                generatedRoot
            );

            if (!decision.isAllowed()) {
                findings.add(new BoundaryBypassFinding(
                    decision.code(),
                    relativePath,
                    "line " + imp.lineNumber() + ": import '" + imp.specifier() + "' — " + decision.reason()
                ));
            }
        }

        return findings;
    }

    /**
     * Detects "use client" or "use server" directive as the first statement.
     * Returns the directive type ("client" or "server") or null if not present.
     */
    public String detectDirective(String content) {
        Matcher matcher = DIRECTIVE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1); // "client" or "server"
        }
        return null;
    }

    /**
     * Checks if a path is within a directory.
     */
    private boolean isWithinDirectory(Path path, Path directory) {
        return path.normalize().startsWith(directory.normalize());
    }
}
