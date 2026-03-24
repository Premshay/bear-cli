package com.bear.kernel.target.node;

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

public class NodeTarget implements Target {

    @Override
    public TargetId targetId() {
        return TargetId.NODE;
    }

    @Override
    public GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(TargetId.NODE, "backend-service");
    }

    @Override
    public void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        // Phase B: Generate TypeScript artifacts
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        TypeScriptManifestGenerator manifestGenerator = new TypeScriptManifestGenerator();

        String blockKeyKebab = toKebabCase(blockKey);
        Path typesDir = projectRoot.resolve("build/generated/bear/types/" + blockKeyKebab);
        Path wiringDir = projectRoot.resolve("build/generated/bear/wiring");

        // Generate Ports.ts
        generator.generatePorts(ir, typesDir, blockKey);

        // Generate Logic.ts
        generator.generateLogic(ir, typesDir, blockKey);

        // Generate Wrapper.ts
        generator.generateWrapper(ir, typesDir, blockKey);

        // Generate wiring.json
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);

        // Create user impl skeleton if absent (check both PascalCase and legacy kebab-case names)
        Path implDir = projectRoot.resolve("src/blocks/" + blockKeyKebab + "/impl");
        String blockName = TypeScriptLexicalSupport.deriveBlockName(blockKey);
        Path implFile = implDir.resolve(blockName + "Impl.ts");
        Path legacyImplFile = implDir.resolve(blockKey + "Impl.ts");
        if (!java.nio.file.Files.exists(implFile) && !java.nio.file.Files.exists(legacyImplFile)) {
            generator.generateUserImplSkeleton(ir, implDir, blockKey);
        }
    }

    @Override
    public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) throws IOException {
        // Generate only wiring manifest (for pr-check)
        TypeScriptManifestGenerator manifestGenerator = new TypeScriptManifestGenerator();
        String blockKeyKebab = toKebabCase(blockKey);
        Path wiringDir = outputRoot.resolve("wiring");
        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);
    }

    @Override
    public WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        return NodeManifestParser.parse(path);
    }

    @Override
    public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException {
        Path sharedDir = projectRoot.resolve("src/blocks/_shared");
        if (Files.isDirectory(sharedDir)) {
            Files.createDirectories(tempRoot.resolve("src/blocks/_shared"));
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
        return false; // impl.allowedDeps unsupported in Phase B
    }

    @Override
    public boolean sharedContainmentInScope(Path projectRoot) {
        return false; // No shared policy in Phase B
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

    @Override
    public String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        return null; // JVM-style containment markers not applicable to Node
    }

    @Override
    public TargetCheckIssue preflightContainmentIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        return null; // JVM-style containment markers not applicable to Node
    }

    @Override
    public TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot, boolean considerContainmentSurfaces) throws IOException {
        return null; // JVM-style containment markers not applicable to Node
    }

    @Override
    public List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException, PolicyValidationException {
        return List.of(); // No covered power surfaces in first Node runtime slice
    }

    @Override
    public List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException {
        return List.of(); // No dynamic execution scanning in first Node runtime slice
    }

    @Override
    public List<BoundaryBypassFinding> scanBoundaryBypass(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> reflectionAllowlist
    ) throws IOException, ManifestParseException, PolicyValidationException {
        return NodeImportContainmentScanner.scan(projectRoot, wiringManifests);
    }

    @Override
    public List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot, List<WiringManifest> wiringManifests)
            throws IOException, ManifestParseException {
        return List.of(); // JVM-specific port binding checks not applicable to Node
    }

    @Override
    public List<BoundaryBypassFinding> scanBlockPortBindings(
            Path projectRoot,
            List<WiringManifest> wiringManifests,
            Set<String> inboundTargetWrapperFqcns
    ) throws IOException {
        return List.of(); // JVM-specific port binding checks not applicable to Node
    }

    @Override
    public List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
            Path projectRoot,
            List<WiringManifest> wiringManifests
    ) throws IOException, ManifestParseException {
        return List.of(); // JVM-specific port binding checks not applicable to Node
    }

    @Override
    public ProjectTestResult runProjectVerification(Path projectRoot, String initScriptRelativePath) throws IOException, InterruptedException {
        // initScriptRelativePath is JVM-specific (Gradle init script); Node ignores it
        return NodeProjectVerificationRunner.run(projectRoot);
    }

    /**
     * Checks for drift in generated artifacts by compiling to a temp directory
     * and performing byte-for-byte comparison against the workspace.
     * User-owned impl files are excluded from drift checking.
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
        Path tempRoot = Files.createTempDirectory("bear-node-drift-");
        try {
            compile(ir, tempRoot, blockKey);

            // Collect generated artifact paths from the fresh compile (source of truth)
            // These are the paths that SHOULD exist in the workspace
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
     * Excludes user-owned impl files.
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

    // Helper methods

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
