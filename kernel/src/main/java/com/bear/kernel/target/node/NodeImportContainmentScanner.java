package com.bear.kernel.target.node;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class NodeImportContainmentScanner {

    /**
     * Scans for import boundary bypasses in governed source files.
     */
    public static List<BoundaryBypassFinding> scan(Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
        Set<Path> governedRoots = computeGovernedRoots(projectRoot, wiringManifests);
        List<Path> governedFiles = collectGovernedFiles(governedRoots);

        NodeImportSpecifierExtractor extractor = new NodeImportSpecifierExtractor();
        NodeDynamicImportDetector dynamicDetector = new NodeDynamicImportDetector();
        // Phase C: construct one NodePathAliasResolver per scan and pass it to NodeImportBoundaryResolver
        // The alias resolver caches tsconfig.json read for the duration of the scan
        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver(aliasResolver);

        List<BoundaryBypassFinding> findings = new ArrayList<>();
        for (Path file : governedFiles) {
            String content = Files.readString(file);
            List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(file.toString(), content);

            for (NodeImportSpecifierExtractor.ImportSpecifier specifier : specifiers) {
                BoundaryDecision decision = resolver.resolve(file, specifier.specifier(), governedRoots, projectRoot);
                if (decision.isFail()) {
                    findings.add(new BoundaryBypassFinding(
                        decision.failureReason(),
                        projectRoot.relativize(file).toString(),
                        "Import specifier: " + specifier.specifier()
                    ));
                }
            }

            // Phase C: dynamic imports are enforced (exit 7, CODE=DYNAMIC_IMPORT_FORBIDDEN)
            List<NodeDynamicImportDetector.DynamicImport> dynamicImports =
                dynamicDetector.detectDynamicImports(content);
            for (NodeDynamicImportDetector.DynamicImport di : dynamicImports) {
                findings.add(new BoundaryBypassFinding(
                    "DYNAMIC_IMPORT_FORBIDDEN",
                    projectRoot.relativize(file).toString(),
                    "Dynamic import specifier: " + di.specifier()
                ));
            }
        }

        // Sort findings by file path
        findings.sort(Comparator.comparing(
            (BoundaryBypassFinding f) -> f.path(),
            Comparator.naturalOrder()
        ));

        return findings;
    }

    private static Set<Path> computeGovernedRoots(Path projectRoot, List<WiringManifest> wiringManifests) {
        Set<Path> roots = new HashSet<>();

        // Add block roots from wiring manifests
        for (WiringManifest manifest : wiringManifests) {
            String blockKey = manifest.blockKey();
            Path blockRoot = projectRoot.resolve("src/blocks/" + blockKey);
            if (Files.exists(blockRoot)) {
                roots.add(blockRoot);
            }
        }

        // Add _shared root if present
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        if (Files.exists(sharedRoot)) {
            roots.add(sharedRoot);
        }

        return roots;
    }

    private static List<Path> collectGovernedFiles(Set<Path> governedRoots) throws IOException {
        List<Path> files = new ArrayList<>();

        for (Path root : governedRoots) {
            if (Files.exists(root)) {
                try (var stream = Files.walk(root)) {
                    stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".ts"))
                        .filter(path -> !path.toString().endsWith(".test.ts"))
                        .filter(path -> !path.toString().endsWith(".spec.ts"))
                        .forEach(files::add);
                }
            }
        }

        files.sort(Comparator.comparing(Path::toString));

        return files;
    }
}
