package com.bear.kernel.target.react;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Target implementation for React/TypeScript frontend projects.
 * Supports two sub-profiles: Vite+React and Next.js App Router.
 * 
 * Follows PythonTarget as the structural model: JVM-specific methods return null
 * or List.of(), no containment markers, no reflection dispatch scanning.
 */
public class ReactTarget implements Target {

    @Override
    public TargetId targetId() {
        return TargetId.REACT;
    }

    @Override
    public GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(TargetId.REACT, "feature-ui");
    }

    @Override
    public void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        ReactArtifactGenerator generator = new ReactArtifactGenerator();
        ReactManifestGenerator manifestGenerator = new ReactManifestGenerator();

        String blockKeyKebab = toKebabCase(blockKey);
        Path typesDir = projectRoot.resolve("build/generated/bear/types/" + blockKeyKebab);
        Path wiringDir = projectRoot.resolve("build/generated/bear/wiring");

        // Generate FeaturePorts.ts
        generator.generatePorts(ir, typesDir, blockKey);

        // Generate FeatureLogic.ts
        generator.generateLogic(ir, typesDir, blockKey);

        // Generate FeatureWrapper.ts
        generator.generateWrapper(ir, typesDir, blockKey);

        // Generate wiring.json
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);

        // Create user impl skeleton if absent
        Path implDir = projectRoot.resolve("src/features/" + blockKeyKebab + "/impl");
        generator.generateUserImplSkeleton(ir, implDir, blockKey);
    }

    @Override
    public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) throws IOException {
        ReactManifestGenerator manifestGenerator = new ReactManifestGenerator();
        Path wiringDir = outputRoot.resolve("wiring");
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);
    }

    @Override
    public WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        return TargetManifestParsers.parseWiringManifest(path);
    }

    @Override
    public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException {
        // Create src/shared/ in tempRoot if present in projectRoot
        Path sharedDir = projectRoot.resolve("src/shared");
        if (Files.isDirectory(sharedDir)) {
            Files.createDirectories(tempRoot.resolve("src/shared"));
        }
    }

    @Override
    public Set<String> ownedGeneratedPrefixes(String blockName) {
        String blockKey = toKebabCase(blockName);
        return Set.of(
            "build/generated/bear/types/" + blockKey + "/",
            "build/generated/bear/wiring/" + blockKey + ".wiring.json"
        );
    }


    @Override
    public boolean considerContainmentSurfaces(BearIr ir, Path projectRoot) {
        return false; // impl.allowedDeps unsupported for React target
    }

    @Override
    public boolean sharedContainmentInScope(Path projectRoot) {
        return false; // No shared policy for React target
    }

    @Override
    public boolean blockDeclaresAllowedDeps(Path irFile) {
        try {
            BearIr ir = parseIr(irFile);
            return ir.block().impl() != null
                && ir.block().impl().allowedDeps() != null
                && !ir.block().impl().allowedDeps().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // --- JVM-specific stubs (return null) ---

    @Override
    public String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        return null; // JVM-style containment markers not applicable to React
    }

    @Override
    public TargetCheckIssue preflightContainmentIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        return null; // JVM-style containment markers not applicable to React
    }

    @Override
    public TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        return null; // JVM-style containment markers not applicable to React
    }

    // --- No-op stubs (return List.of()) ---

    @Override
    public List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException, PolicyValidationException {
        return List.of(); // No covered power surfaces in first slice
    }

    @Override
    public List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException {
        return List.of(); // No dynamic execution scanning for React
    }

    @Override
    public List<BoundaryBypassFinding> scanBoundaryBypass(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> reflectionAllowlist
    ) throws IOException, ManifestParseException, PolicyValidationException {
        List<BoundaryBypassFinding> findings = new ArrayList<>();

        // Scan import containment
        ReactImportContainmentScanner importScanner = new ReactImportContainmentScanner();
        findings.addAll(importScanner.scan(projectRoot, wiringManifests));

        // Scan API boundary (fetch/XHR detection)
        ReactApiBoundaryScanner apiScanner = new ReactApiBoundaryScanner();
        findings.addAll(apiScanner.scan(projectRoot, wiringManifests));

        // Sort findings by path for deterministic output
        findings.sort(Comparator.comparing(BoundaryBypassFinding::path));

        return findings;
    }

    @Override
    public List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException, ManifestParseException {
        return List.of(); // JVM-specific port binding checks not applicable to React
    }

    @Override
    public List<BoundaryBypassFinding> scanBlockPortBindings(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> inboundTargetWrapperFqcns
    ) throws IOException {
        return List.of(); // JVM-specific port binding checks not applicable to React
    }

    @Override
    public List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
            Path projectRoot,
            List<WiringManifest> wiringManifests
    ) throws IOException, ManifestParseException {
        return List.of(); // JVM-specific port binding checks not applicable to React
    }

    @Override
    public ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath) throws IOException, InterruptedException {
        return ReactProjectVerificationRunner.run(projectRoot);
    }


    // --- Drift gate ---

    /**
     * Checks for drift in generated artifacts by compiling to a temp directory
     * and performing byte-for-byte comparison against the workspace.
     * User-owned impl files under src/features/{blockKey}/impl/ are excluded from drift checking.
     *
     * @param ir the block IR
     * @param projectRoot the workspace project root
     * @param blockKey the block key (kebab-case)
     * @return list of drift findings (empty if no drift)
     */
    public List<TargetCheckIssue> checkDrift(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        String blockKeyKebab = toKebabCase(blockKey);
        List<TargetCheckIssue> findings = new ArrayList<>();

        // Generate fresh artifacts to temp directory
        Path tempRoot = Files.createTempDirectory("bear-react-drift-");
        try {
            // Compile to temp (this creates types and wiring, but NOT user impl since we don't copy existing)
            ReactArtifactGenerator generator = new ReactArtifactGenerator();
            ReactManifestGenerator manifestGenerator = new ReactManifestGenerator();

            Path typesDir = tempRoot.resolve("build/generated/bear/types/" + blockKeyKebab);
            Path wiringDir = tempRoot.resolve("build/generated/bear/wiring");

            generator.generatePorts(ir, typesDir, blockKey);
            generator.generateLogic(ir, typesDir, blockKey);
            generator.generateWrapper(ir, typesDir, blockKey);
            manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);

            // Collect generated artifact paths from the fresh compile (source of truth)
            List<String> generatedPaths = collectGeneratedArtifactPaths(blockKeyKebab, tempRoot);

            for (String relPath : generatedPaths) {
                Path workspacePath = projectRoot.resolve(relPath);
                Path freshPath = tempRoot.resolve(relPath);

                if (!Files.isRegularFile(workspacePath)) {
                    findings.add(new TargetCheckIssue(
                        TargetCheckIssueKind.DRIFT_MISSING_BASELINE,
                        relPath,
                        "Run `bear compile` to generate missing baseline artifacts.",
                        "drift: MISSING_BASELINE: " + relPath
                    ));
                    continue;
                }

                byte[] workspaceBytes = Files.readAllBytes(workspacePath);
                byte[] freshBytes = Files.readAllBytes(freshPath);

                if (!Arrays.equals(workspaceBytes, freshBytes)) {
                    findings.add(new TargetCheckIssue(
                        TargetCheckIssueKind.DRIFT_DETECTED,
                        relPath,
                        "Run `bear compile` to regenerate drifted artifacts.",
                        "drift: CHANGED: " + relPath
                    ));
                }
            }
        } finally {
            deleteDirectoryQuietly(tempRoot);
        }

        return findings;
    }

    /**
     * Collects the relative paths of all generated artifacts for a block.
     * Excludes user-owned impl files under src/features/{blockKey}/impl/.
     */
    private List<String> collectGeneratedArtifactPaths(String blockKeyKebab, Path projectRoot) throws IOException {
        List<String> paths = new ArrayList<>();

        // Types directory: build/generated/bear/types/<blockKey>/*.ts
        Path typesDir = projectRoot.resolve("build/generated/bear/types/" + blockKeyKebab);
        if (Files.isDirectory(typesDir)) {
            try (var stream = Files.walk(typesDir)) {
                stream.filter(Files::isRegularFile)
                    .map(p -> projectRoot.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .forEach(paths::add);
            }
        }

        // Wiring manifest: build/generated/bear/wiring/<blockKey>.wiring.json
        String wiringPath = "build/generated/bear/wiring/" + blockKeyKebab + ".wiring.json";
        paths.add(wiringPath);

        return paths;
    }

    private void deleteDirectoryQuietly(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {}
    }

    // --- Helper methods ---

    private BearIr parseIr(Path irFile) throws IOException, BearIrValidationException {
        return new BearIrParser().parse(irFile);
    }

    private String toKebabCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
