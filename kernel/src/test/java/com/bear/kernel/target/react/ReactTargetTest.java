package com.bear.kernel.target.react;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReactTarget covering interface contract, allowedDeps guard, and drift gate.
 */
class ReactTargetTest {

    private ReactTarget target;

    @BeforeEach
    void setUp() {
        target = new ReactTarget();
    }

    // --- Interface contract tests ---

    @Test
    void targetId_returnsReact() {
        assertEquals(TargetId.REACT, target.targetId());
    }

    @Test
    void defaultProfile_returnsFeatureUi() {
        GovernanceProfile profile = target.defaultProfile();
        assertEquals(TargetId.REACT, profile.target());
        assertEquals("feature-ui", profile.profileId());
        assertEquals("react/feature-ui", profile.toString());
    }

    @Test
    void ownedGeneratedPrefixes_returnsTypesAndWiringPaths() {
        Set<String> prefixes = target.ownedGeneratedPrefixes("user-dashboard");
        assertEquals(2, prefixes.size());
        assertTrue(prefixes.contains("build/generated/bear/types/user-dashboard/"));
        assertTrue(prefixes.contains("build/generated/bear/wiring/user-dashboard.wiring.json"));
    }

    @Test
    void ownedGeneratedPrefixes_convertsPascalCaseToKebab() {
        Set<String> prefixes = target.ownedGeneratedPrefixes("UserDashboard");
        assertTrue(prefixes.contains("build/generated/bear/types/user-dashboard/"));
        assertTrue(prefixes.contains("build/generated/bear/wiring/user-dashboard.wiring.json"));
    }

    @Test
    void considerContainmentSurfaces_returnsFalse() {
        assertFalse(target.considerContainmentSurfaces(null, null));
    }

    @Test
    void sharedContainmentInScope_returnsFalse() {
        assertFalse(target.sharedContainmentInScope(null));
    }

    // --- JVM-specific stubs return null ---

    @Test
    void containmentSkipInfoLine_returnsNull() {
        assertNull(target.containmentSkipInfoLine("label", Path.of("."), false));
    }

    @Test
    void preflightContainmentIfRequired_returnsNull() throws IOException {
        assertNull(target.preflightContainmentIfRequired(Path.of("."), false));
    }

    @Test
    void verifyContainmentMarkersIfRequired_returnsNull() throws IOException {
        assertNull(target.verifyContainmentMarkersIfRequired(Path.of("."), false));
    }

    // --- No-op stubs return empty lists ---

    @Test
    void scanUndeclaredReach_returnsEmptyList() throws Exception {
        List<UndeclaredReachFinding> findings = target.scanUndeclaredReach(Path.of("."));
        assertTrue(findings.isEmpty());
    }

    @Test
    void scanForbiddenReflectionDispatch_returnsEmptyList() throws IOException {
        List<UndeclaredReachFinding> findings = target.scanForbiddenReflectionDispatch(Path.of("."), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void scanPortImplContainmentBypass_returnsEmptyList() throws Exception {
        List<BoundaryBypassFinding> findings = target.scanPortImplContainmentBypass(Path.of("."), List.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void scanBlockPortBindings_returnsEmptyList() throws IOException {
        List<BoundaryBypassFinding> findings = target.scanBlockPortBindings(Path.of("."), List.of(), Set.of());
        assertTrue(findings.isEmpty());
    }

    @Test
    void scanMultiBlockPortImplAllowedSignals_returnsEmptyList() throws Exception {
        List<MultiBlockPortImplAllowedSignal> signals = target.scanMultiBlockPortImplAllowedSignals(Path.of("."), List.of());
        assertTrue(signals.isEmpty());
    }


    // --- allowedDeps guard tests ---

    @Test
    void blockDeclaresAllowedDeps_returnsFalse_whenIrHasNoAllowedDeps(@TempDir Path tempDir) throws IOException {
        // Create IR file without impl.allowedDeps
        String irContent = """
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
            """;
        Path irFile = tempDir.resolve("ir.bear.yaml");
        Files.writeString(irFile, irContent);

        assertFalse(target.blockDeclaresAllowedDeps(irFile));
    }

    @Test
    void blockDeclaresAllowedDeps_returnsTrue_whenIrHasAllowedDeps(@TempDir Path tempDir) throws IOException {
        // Create IR file with impl.allowedDeps
        String irContent = """
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
                  - maven: com.example:library
                    version: "1.0.0"
            """;
        Path irFile = tempDir.resolve("ir.bear.yaml");
        Files.writeString(irFile, irContent);

        assertTrue(target.blockDeclaresAllowedDeps(irFile));
    }

    @Test
    void blockDeclaresAllowedDeps_returnsFalse_whenIrFileDoesNotExist(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("nonexistent.bear.yaml");
        assertFalse(target.blockDeclaresAllowedDeps(nonExistent));
    }

    @Test
    void blockDeclaresAllowedDeps_returnsFalse_whenIrFileIsInvalid(@TempDir Path tempDir) throws IOException {
        Path irFile = tempDir.resolve("invalid.bear.yaml");
        Files.writeString(irFile, "not valid yaml: [");
        assertFalse(target.blockDeclaresAllowedDeps(irFile));
    }

    // --- prepareCheckWorkspace tests ---

    @Test
    void prepareCheckWorkspace_createsSharedDir_whenPresentInProjectRoot(@TempDir Path tempDir) throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path tempRoot = tempDir.resolve("temp");
        Files.createDirectories(projectRoot.resolve("src/shared"));
        Files.createDirectories(tempRoot);

        target.prepareCheckWorkspace(projectRoot, tempRoot);

        assertTrue(Files.isDirectory(tempRoot.resolve("src/shared")));
    }

    @Test
    void prepareCheckWorkspace_doesNotCreateSharedDir_whenAbsentInProjectRoot(@TempDir Path tempDir) throws IOException {
        Path projectRoot = tempDir.resolve("project");
        Path tempRoot = tempDir.resolve("temp");
        Files.createDirectories(projectRoot);
        Files.createDirectories(tempRoot);

        target.prepareCheckWorkspace(projectRoot, tempRoot);

        assertFalse(Files.exists(tempRoot.resolve("src/shared")));
    }

    // --- compile tests ---

    @Test
    void compile_generatesAllArtifacts(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";

        target.compile(ir, tempDir, blockKey);

        // Check types directory
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");
        assertTrue(Files.exists(typesDir.resolve("UserDashboardFeaturePorts.ts")));
        assertTrue(Files.exists(typesDir.resolve("UserDashboardFeatureLogic.ts")));
        assertTrue(Files.exists(typesDir.resolve("UserDashboardFeatureWrapper.ts")));

        // Check wiring manifest
        Path wiringFile = tempDir.resolve("build/generated/bear/wiring/user-dashboard.wiring.json");
        assertTrue(Files.exists(wiringFile));

        // Check user impl skeleton
        Path implFile = tempDir.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx");
        assertTrue(Files.exists(implFile));
    }

    @Test
    void compile_doesNotOverwriteExistingUserImpl(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";

        // Create existing user impl
        Path implDir = tempDir.resolve("src/features/user-dashboard/impl");
        Files.createDirectories(implDir);
        Path implFile = implDir.resolve("UserDashboardFeatureImpl.tsx");
        String existingContent = "// User's custom implementation";
        Files.writeString(implFile, existingContent);

        target.compile(ir, tempDir, blockKey);

        // Verify user impl was not overwritten
        assertEquals(existingContent, Files.readString(implFile));
    }

    // --- generateWiringOnly tests ---

    @Test
    void generateWiringOnly_generatesOnlyWiringManifest(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";
        Path outputRoot = tempDir.resolve("output");

        target.generateWiringOnly(ir, tempDir, outputRoot, blockKey);

        // Check wiring manifest exists
        Path wiringFile = outputRoot.resolve("wiring/user-dashboard.wiring.json");
        assertTrue(Files.exists(wiringFile));

        // Check types directory does NOT exist
        assertFalse(Files.exists(outputRoot.resolve("build/generated/bear/types")));

        // Check user impl does NOT exist
        assertFalse(Files.exists(outputRoot.resolve("src/features/user-dashboard/impl")));
    }


    // --- Drift gate tests ---

    @Test
    void checkDrift_returnsEmpty_whenArtifactsMatchFreshCompile(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";

        // Compile to workspace
        target.compile(ir, tempDir, blockKey);

        // Check drift - should be empty since artifacts match
        List<TargetCheckIssue> findings = target.checkDrift(ir, tempDir, blockKey);
        assertTrue(findings.isEmpty(), "Expected no drift findings for freshly compiled workspace");
    }

    @Test
    void checkDrift_returnsDriftDetected_whenArtifactModified(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";

        // Compile to workspace
        target.compile(ir, tempDir, blockKey);

        // Modify a generated artifact
        Path portsFile = tempDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts.ts");
        String original = Files.readString(portsFile);
        Files.writeString(portsFile, original + "\n// Modified by user");

        // Check drift - should detect the modification
        List<TargetCheckIssue> findings = target.checkDrift(ir, tempDir, blockKey);
        assertFalse(findings.isEmpty(), "Expected drift findings for modified artifact");
        
        boolean foundDrift = findings.stream()
            .anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_DETECTED 
                && f.path().contains("UserDashboardFeaturePorts.ts"));
        assertTrue(foundDrift, "Expected DRIFT_DETECTED for modified FeaturePorts.ts");
    }

    @Test
    void checkDrift_returnsMissingBaseline_whenArtifactDeleted(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";

        // Compile to workspace
        target.compile(ir, tempDir, blockKey);

        // Delete a generated artifact
        Path portsFile = tempDir.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts.ts");
        Files.delete(portsFile);

        // Check drift - should detect the missing file
        List<TargetCheckIssue> findings = target.checkDrift(ir, tempDir, blockKey);
        assertFalse(findings.isEmpty(), "Expected drift findings for missing artifact");
        
        boolean foundMissing = findings.stream()
            .anyMatch(f -> f.kind() == TargetCheckIssueKind.DRIFT_MISSING_BASELINE 
                && f.path().contains("UserDashboardFeaturePorts.ts"));
        assertTrue(foundMissing, "Expected DRIFT_MISSING_BASELINE for deleted FeaturePorts.ts");
    }

    @Test
    void checkDrift_excludesUserImpl_fromDriftChecking(@TempDir Path tempDir) throws IOException {
        BearIr ir = createMinimalIr();
        String blockKey = "user-dashboard";

        // Compile to workspace
        target.compile(ir, tempDir, blockKey);

        // Modify user impl (should NOT trigger drift)
        Path implFile = tempDir.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx");
        String original = Files.readString(implFile);
        Files.writeString(implFile, original + "\n// User's custom code");

        // Check drift - should be empty since user impl is excluded
        List<TargetCheckIssue> findings = target.checkDrift(ir, tempDir, blockKey);
        assertTrue(findings.isEmpty(), "User impl modifications should not trigger drift");
    }

    // --- TargetRegistry integration tests ---

    @Test
    void targetRegistry_containsReactTarget(@TempDir Path tempDir) throws Exception {
        TargetRegistry registry = TargetRegistry.defaultRegistry();
        assertNotNull(registry);

        // Verify React is wired in by resolving a minimal React fixture
        Path fixture = tempDir.resolve("react-project");
        Files.createDirectories(fixture);
        Files.writeString(fixture.resolve("package.json"), """
            {
              "name": "test",
              "type": "module",
              "packageManager": "pnpm@9.0.0",
              "dependencies": { "react": "^18.0.0", "react-dom": "^18.0.0" }
            }
            """);
        Files.writeString(fixture.resolve("vite.config.ts"), "export default {}");
        Files.writeString(fixture.resolve("pnpm-lock.yaml"), "lockfileVersion: '6.0'");
        Files.writeString(fixture.resolve("tsconfig.json"), "{}");

        Target resolved = registry.resolve(fixture);
        assertEquals(TargetId.REACT, resolved.targetId(),
            "React project should resolve to ReactTarget via defaultRegistry()");
    }

    // --- Helper methods ---

    private BearIr createMinimalIr() {
        return new BearIr(
            "v1",
            new BearIr.Block(
                "UserDashboard",
                BearIr.BlockKind.LOGIC,
                List.of(
                    new BearIr.Operation(
                        "GetUser",
                        new BearIr.Contract(
                            List.of(new BearIr.Field("user-id", BearIr.FieldType.STRING)),
                            List.of(new BearIr.Field("user-name", BearIr.FieldType.STRING))
                        ),
                        new BearIr.Effects(List.of()),
                        null,
                        null
                    )
                ),
                new BearIr.Effects(List.of()),
                new BearIr.Impl(List.of()),
                null,
                null
            )
        );
    }
}
