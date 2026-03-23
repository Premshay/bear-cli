package com.bear.kernel.target.react;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for the React target check pipeline.
 * Tests ReactTargetDetector, ReactTarget (compile, drift, containment),
 * and scanners working together against fixture projects.
 * 
 * Verifies exit codes:
 * - clean → 0
 * - boundary bypass → 7
 * - drift → 5
 * - allowedDeps → 64
 */
class ReactCheckIntegrationTest {

    private final ReactTarget target = new ReactTarget();
    private final ReactTargetDetector detector = new ReactTargetDetector();

    // ========== Clean Project Tests (Exit Code 0) ==========

    @Test
    void viteReactSingleBlock_cleanProject_noFindings() throws Exception {
        Path fixture = getFixturePath("vite-react-single-block");

        // Step 1: Detect
        DetectedTarget detection = detector.detect(fixture);
        assertEquals(DetectionStatus.SUPPORTED, detection.status());
        assertEquals(TargetId.REACT, detection.targetId());
        assertTrue(detection.reason().contains("vite-react"), 
            "Should detect vite-react sub-profile");

        // Step 2: Scan containment — should be clean
        WiringManifest manifest = makeManifest("user-dashboard");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());
        
        assertTrue(findings.isEmpty(), 
            "vite-react-single-block should have no boundary bypass findings");
    }

    @Test
    void nextjsSingleBlock_cleanProject_noFindings() throws Exception {
        Path fixture = getFixturePath("nextjs-single-block");

        // Step 1: Detect
        DetectedTarget detection = detector.detect(fixture);
        assertEquals(DetectionStatus.SUPPORTED, detection.status());
        assertEquals(TargetId.REACT, detection.targetId());
        assertTrue(detection.reason().contains("nextjs-app-router"), 
            "Should detect nextjs-app-router sub-profile");

        // Step 2: Scan containment — should be clean
        WiringManifest manifest = makeManifest("user-dashboard");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());
        
        assertTrue(findings.isEmpty(), 
            "nextjs-single-block should have no boundary bypass findings");
    }

    @Test
    void detectCompileCheck_cleanProject_noFindings(@TempDir Path workDir) throws Exception {
        copyFixtureTo("vite-react-single-block", workDir);

        // Step 1: Detect
        DetectedTarget detection = detector.detect(workDir);
        assertEquals(DetectionStatus.SUPPORTED, detection.status());
        assertEquals(TargetId.REACT, detection.targetId());

        // Step 2: Compile
        BearIr ir = createIrWithOp("UserDashboard", "GetUser");
        target.compile(ir, workDir, "user-dashboard");

        // Verify artifacts were generated
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts.ts")));
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeatureLogic.ts")));
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeatureWrapper.ts")));
        assertTrue(Files.exists(workDir.resolve("build/generated/bear/wiring/user-dashboard.wiring.json")));

        // Step 3: Check drift — should be clean
        List<TargetCheckIssue> driftFindings = target.checkDrift(ir, workDir, "user-dashboard");
        assertTrue(driftFindings.isEmpty(), "freshly compiled project should have no drift");

        // Step 4: Check containment — should be clean
        WiringManifest manifest = makeManifest("user-dashboard");
        List<BoundaryBypassFinding> containmentFindings = target.scanBoundaryBypass(workDir, List.of(manifest), Set.of());
        assertTrue(containmentFindings.isEmpty(), "vite-react-single-block should have no containment violations");
    }

    // ========== Boundary Bypass Tests (Exit Code 7) ==========

    @Test
    void boundaryBypassEscape_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-escape");

        // Detect
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        // Scan containment — should find boundary bypass (escape)
        WiringManifest manifest = makeManifest("user-dashboard");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-escape should produce findings");
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")),
            "finding should have BOUNDARY_BYPASS rule");
    }

    @Test
    void boundaryBypassSibling_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-sibling");

        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        WiringManifest userDashboard = makeManifest("user-dashboard");
        WiringManifest productCatalog = makeManifest("product-catalog");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(
            fixture, List.of(userDashboard, productCatalog), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-sibling should produce findings");
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")),
            "finding should have BOUNDARY_BYPASS rule");
    }

    @Test
    void boundaryBypassBareImport_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-bare-import");

        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        WiringManifest manifest = makeManifest("user-dashboard");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-bare-import should produce findings");
        assertTrue(findings.stream().anyMatch(f -> f.detail().contains("lodash")),
            "finding should reference the bare package import specifier");
    }

    @Test
    void boundaryBypassFetch_producesFindings() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-fetch");

        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        WiringManifest manifest = makeManifest("user-dashboard");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(fixture, List.of(manifest), Set.of());

        assertFalse(findings.isEmpty(), "boundary-bypass-fetch should produce findings");
        assertTrue(findings.stream().anyMatch(f -> f.detail().contains("fetch(")),
            "finding should reference the fetch() call");
    }

    @Test
    void nextjsAliasSibling_producesFindings() throws Exception {
        Path fixture = getFixturePath("nextjs-alias-sibling");

        assertEquals(DetectionStatus.SUPPORTED, detector.detect(fixture).status());

        WiringManifest userDashboard = makeManifest("user-dashboard");
        WiringManifest productCatalog = makeManifest("product-catalog");
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(
            fixture, List.of(userDashboard, productCatalog), Set.of());

        assertFalse(findings.isEmpty(), "nextjs-alias-sibling should produce findings");
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")),
            "finding should have BOUNDARY_BYPASS rule");
    }

    // ========== Drift Tests (Exit Code 5) ==========

    @Test
    void detectCompileModifyCheck_driftDetected(@TempDir Path workDir) throws Exception {
        copyFixtureTo("vite-react-single-block", workDir);

        // Detect + compile
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(workDir).status());

        BearIr ir = createIrWithOp("UserDashboard", "GetUser");
        target.compile(ir, workDir, "user-dashboard");

        // Modify a generated artifact
        Path portsFile = workDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts.ts");
        assertTrue(Files.exists(portsFile));
        Files.writeString(portsFile, "// tampered content\n");

        // Check drift — should detect drift (exit 5)
        List<TargetCheckIssue> findings = target.checkDrift(ir, workDir, "user-dashboard");
        assertFalse(findings.isEmpty(), "modified generated file should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_DETECTED),
            "should contain DRIFT_DETECTED finding");
    }

    @Test
    void missingGeneratedArtifact_driftMissingBaseline(@TempDir Path workDir) throws Exception {
        copyFixtureTo("vite-react-single-block", workDir);

        // Detect + compile
        assertEquals(DetectionStatus.SUPPORTED, detector.detect(workDir).status());

        BearIr ir = createIrWithOp("UserDashboard", "GetUser");
        target.compile(ir, workDir, "user-dashboard");

        // Delete a generated artifact
        Path portsFile = workDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts.ts");
        Files.delete(portsFile);

        // Check drift — should detect missing baseline (exit 5)
        List<TargetCheckIssue> findings = target.checkDrift(ir, workDir, "user-dashboard");
        assertFalse(findings.isEmpty(), "missing generated file should produce drift findings");
        assertTrue(findings.stream().anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_MISSING_BASELINE),
            "should contain DRIFT_MISSING_BASELINE finding");
    }

    // ========== allowedDeps Guard Tests (Exit Code 64) ==========

    @Test
    void allowedDeps_reactTarget_blockDeclaresAllowedDeps(@TempDir Path workDir) throws IOException {
        Path irFile = workDir.resolve("ir.bear.yaml");
        Files.writeString(irFile, """
            version: v1
            block:
              name: UserDashboard
              kind: logic
              operations:
                - name: GetUser
                  contract:
                    inputs:
                      - name: user-id
                        type: string
                    outputs:
                      - name: user-name
                        type: string
                  uses:
                    allow: []
              effects:
                allow: []
              impl:
                allowedDeps:
                  - maven: com.example:lib
                    version: "1.0"
            """);

        assertTrue(target.blockDeclaresAllowedDeps(irFile),
            "ReactTarget should detect impl.allowedDeps in IR file (triggers exit 64)");
    }

    @Test
    void allowedDeps_absent_passesGuard(@TempDir Path workDir) throws IOException {
        Path irFile = workDir.resolve("ir.bear.yaml");
        Files.writeString(irFile, """
            version: v1
            block:
              name: UserDashboard
              kind: logic
              operations:
                - name: GetUser
                  contract:
                    inputs:
                      - name: user-id
                        type: string
                    outputs:
                      - name: user-name
                        type: string
                  uses:
                    allow: []
              effects:
                allow: []
            """);

        assertFalse(target.blockDeclaresAllowedDeps(irFile),
            "ReactTarget should not detect allowedDeps when absent");
    }

    // ========== Detection Edge Cases ==========

    @Test
    void invalidWorkspace_detectedAsUnsupported() throws Exception {
        Path fixture = getFixturePath("invalid-workspace");

        DetectedTarget result = detector.detect(fixture);
        assertEquals(DetectionStatus.UNSUPPORTED, result.status(),
            "workspace project should be UNSUPPORTED");
    }

    @Test
    void invalidAmbiguousShape_detectedAsUnsupported() throws Exception {
        Path fixture = getFixturePath("invalid-ambiguous-shape");

        DetectedTarget result = detector.detect(fixture);
        assertEquals(DetectionStatus.UNSUPPORTED, result.status(),
            "ambiguous shape (both Vite and Next.js) should be UNSUPPORTED");
    }

    // ========== Target Registry Integration ==========

    @Test
    void reactProject_resolvesToReactTarget() throws Exception {
        Path fixture = getFixturePath("vite-react-single-block");

        TargetRegistry registry = TargetRegistry.defaultRegistry();
        Target resolved = registry.resolve(fixture);

        assertEquals(TargetId.REACT, resolved.targetId(),
            "React project should resolve to ReactTarget");
        assertInstanceOf(ReactTarget.class, resolved);
    }

    @Test
    void nextjsProject_resolvesToReactTarget() throws Exception {
        Path fixture = getFixturePath("nextjs-single-block");

        TargetRegistry registry = TargetRegistry.defaultRegistry();
        Target resolved = registry.resolve(fixture);

        assertEquals(TargetId.REACT, resolved.targetId(),
            "Next.js project should resolve to ReactTarget");
        assertInstanceOf(ReactTarget.class, resolved);
    }

    // ========== PR Check Contributor Tests ==========

    @Test
    void prCheckBoundaryExpanding_detectsDependencyChange() throws Exception {
        Path fixture = getFixturePath("check-pr-check-boundary-expanding");
        Path baseRoot = fixture.resolve("base");
        Path headRoot = fixture.resolve("head");

        ReactPrCheckContributor contributor = new ReactPrCheckContributor();
        var findings = contributor.computeDelta(baseRoot, headRoot);

        assertFalse(findings.isEmpty(), 
            "check-pr-check-boundary-expanding should detect dependency changes");
        assertTrue(findings.stream().anyMatch(f -> f.code().equals("BOUNDARY_EXPANDING")),
            "finding should have BOUNDARY_EXPANDING code (maps to exit 5)");
        assertTrue(findings.stream().anyMatch(f -> f.path().equals("package.json")),
            "finding should include package.json path");
    }

    @Test
    void prCheckNoDelta_noFindings(@TempDir Path tempDir) throws IOException {
        // Create identical base and head
        Path baseRoot = tempDir.resolve("base");
        Path headRoot = tempDir.resolve("head");
        Files.createDirectories(baseRoot);
        Files.createDirectories(headRoot);

        String packageJson = """
            {
              "name": "test",
              "dependencies": { "react": "^18.0.0" }
            }
            """;
        Files.writeString(baseRoot.resolve("package.json"), packageJson);
        Files.writeString(headRoot.resolve("package.json"), packageJson);

        ReactPrCheckContributor contributor = new ReactPrCheckContributor();
        var findings = contributor.computeDelta(baseRoot, headRoot);

        assertTrue(findings.isEmpty(), 
            "Identical snapshots should produce no findings");
    }

    // ========== Fixture Structure Validation ==========

    @Test
    void viteReactSingleBlockFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("vite-react-single-block");

        assertTrue(Files.exists(fixture.resolve("package.json")));
        assertTrue(Files.exists(fixture.resolve("pnpm-lock.yaml")));
        assertTrue(Files.exists(fixture.resolve("tsconfig.json")));
        assertTrue(Files.exists(fixture.resolve("vite.config.ts")));
        assertTrue(Files.isDirectory(fixture.resolve("src/features/user-dashboard")));
    }

    @Test
    void nextjsSingleBlockFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("nextjs-single-block");

        assertTrue(Files.exists(fixture.resolve("package.json")));
        assertTrue(Files.exists(fixture.resolve("pnpm-lock.yaml")));
        assertTrue(Files.exists(fixture.resolve("tsconfig.json")));
        assertTrue(Files.exists(fixture.resolve("next.config.js")));
        // Note: src/features directory may not exist in minimal detection fixture
    }

    @Test
    void boundaryBypassFetchFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-fetch");

        assertTrue(Files.exists(fixture.resolve("package.json")));
        assertTrue(Files.exists(fixture.resolve("vite.config.ts")));
        assertTrue(Files.isDirectory(fixture.resolve("src/features/user-dashboard")));
    }

    @Test
    void checkPrCheckBoundaryExpandingFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-pr-check-boundary-expanding");

        assertTrue(Files.exists(fixture.resolve("base/package.json")));
        assertTrue(Files.exists(fixture.resolve("head/package.json")));
        assertTrue(Files.exists(fixture.resolve("base/pnpm-lock.yaml")));
        assertTrue(Files.exists(fixture.resolve("head/pnpm-lock.yaml")));

        // Verify head has axios (the added dependency)
        String headPackageJson = Files.readString(fixture.resolve("head/package.json"));
        assertTrue(headPackageJson.contains("axios"), "Head should have axios dependency");

        // Verify base does NOT have axios
        String basePackageJson = Files.readString(fixture.resolve("base/package.json"));
        assertFalse(basePackageJson.contains("axios"), "Base should not have axios dependency");
    }

    // ========== Helper Methods ==========

    private Path getFixturePath(String fixtureName) throws URISyntaxException {
        return Paths.get(
            Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/react/" + fixtureName)
            ).toURI()
        );
    }

    private void copyFixtureTo(String fixtureName, Path targetDir) throws Exception {
        Path source = getFixturePath(fixtureName);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(targetDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private WiringManifest makeManifest(String blockKey) {
        String pascalCase = toPascalCase(blockKey);
        return new WiringManifest(
            "v3",
            blockKey,
            pascalCase + "FeatureWrapper",
            pascalCase + "FeatureLogic",
            pascalCase + "FeatureImpl",
            "src/features/" + blockKey + "/impl/" + pascalCase + "FeatureImpl.tsx",
            "src/features/" + blockKey,
            List.of("src/features/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private String toPascalCase(String kebabCase) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : kebabCase.toCharArray()) {
            if (c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private BearIr createIrWithOp(String blockName, String opName) {
        return new BearIr("v1", new BearIr.Block(
            blockName,
            BearIr.BlockKind.LOGIC,
            List.of(new BearIr.Operation(
                opName,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input", BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("result", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()), null, null
            )),
            new BearIr.Effects(List.of()),
            new BearIr.Impl(List.of()),
            null,
            null
        ));
    }
}
