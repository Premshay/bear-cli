package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.ManifestParseException;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.NodeManifestParser;
import com.bear.kernel.target.node.NodeTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for NodeManifestParser.
 * Feature: phase-c-node-runtime-execution
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs.
 */
class ManifestParsingProperties {

    // -------------------------------------------------------------------------
    // Property 1: Manifest parsing populates all fields
    // Feature: phase-c-node-runtime-execution, Property 1: Manifest parsing populates all fields
    // Validates: Requirements 1.1, 1.2
    // -------------------------------------------------------------------------

    @Test
    void manifestParsingPopulatesAllFields_userAuth(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertManifestParsingPopulatesAllFields("user-auth", "build/generated/bear/types/user-auth", "src/blocks/user-auth/impl", tempDir);
    }

    @Test
    void manifestParsingPopulatesAllFields_payment(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertManifestParsingPopulatesAllFields("payment", "build/generated/bear/types/payment", "src/blocks/payment/impl", tempDir);
    }

    @Test
    void manifestParsingPopulatesAllFields_orderManager(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertManifestParsingPopulatesAllFields("order-manager", "build/generated/bear/types/order-manager", "src/blocks/order-manager/impl", tempDir);
    }

    private void assertManifestParsingPopulatesAllFields(String blockKey, String generatedPackage, String implPackage, Path tempDir) throws IOException, ManifestParseException {
        String json = String.format("""
            {
              "version": "1",
              "blockKey": "%s",
              "targetId": "node",
              "generatedPackage": "%s",
              "implPackage": "%s",
              "wrappers": [{"operation": "op1", "wrapperClass": "Wrapper1"}],
              "ports": [{"name": "port1", "kind": "EXTERNAL"}]
            }
            """, blockKey, generatedPackage, implPackage);

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        // All key fields should be non-null and non-empty
        assertNotNull(manifest.schemaVersion());
        assertFalse(manifest.schemaVersion().isEmpty());
        assertEquals("1", manifest.schemaVersion());

        assertNotNull(manifest.blockKey());
        assertFalse(manifest.blockKey().isEmpty());
        assertEquals(blockKey, manifest.blockKey());

        assertNotNull(manifest.entrypointFqcn());
        assertFalse(manifest.entrypointFqcn().isEmpty());
        assertEquals("node", manifest.entrypointFqcn());

        assertNotNull(manifest.logicInterfaceFqcn());
        assertFalse(manifest.logicInterfaceFqcn().isEmpty());
        assertEquals(generatedPackage, manifest.logicInterfaceFqcn());

        assertNotNull(manifest.implFqcn());
        assertFalse(manifest.implFqcn().isEmpty());
        assertEquals(implPackage, manifest.implFqcn());
    }

    // -------------------------------------------------------------------------
    // Property 2: Manifest round-trip stability
    // Feature: phase-c-node-runtime-execution, Property 2: Manifest round-trip stability
    // Validates: Requirement 1.9
    // -------------------------------------------------------------------------

    @Test
    void manifestRoundTripStability_userAuth(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertManifestRoundTripStability("user-auth", "build/generated/bear/types/user-auth", "src/blocks/user-auth/impl", tempDir);
    }

    @Test
    void manifestRoundTripStability_payment(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertManifestRoundTripStability("payment", "build/generated/bear/types/payment", "src/blocks/payment/impl", tempDir);
    }

    private void assertManifestRoundTripStability(String blockKey, String generatedPackage, String implPackage, Path tempDir) throws IOException, ManifestParseException {
        // First parse
        String json1 = String.format("""
            {
              "version": "1",
              "blockKey": "%s",
              "targetId": "node",
              "generatedPackage": "%s",
              "implPackage": "%s"
            }
            """, blockKey, generatedPackage, implPackage);

        Path manifestPath1 = tempDir.resolve("test1.wiring.json");
        Files.writeString(manifestPath1, json1);
        WiringManifest manifest1 = NodeManifestParser.parse(manifestPath1);

        // Serialize key fields back to JSON
        String json2 = String.format("""
            {
              "version": "%s",
              "blockKey": "%s",
              "targetId": "%s",
              "generatedPackage": "%s",
              "implPackage": "%s"
            }
            """,
            manifest1.schemaVersion(),
            manifest1.blockKey(),
            manifest1.entrypointFqcn(),
            manifest1.logicInterfaceFqcn(),
            manifest1.implFqcn()
        );

        // Second parse
        Path manifestPath2 = tempDir.resolve("test2.wiring.json");
        Files.writeString(manifestPath2, json2);
        WiringManifest manifest2 = NodeManifestParser.parse(manifestPath2);

        // Key fields should be identical
        assertEquals(manifest1.schemaVersion(), manifest2.schemaVersion());
        assertEquals(manifest1.blockKey(), manifest2.blockKey());
        assertEquals(manifest1.entrypointFqcn(), manifest2.entrypointFqcn());
        assertEquals(manifest1.logicInterfaceFqcn(), manifest2.logicInterfaceFqcn());
        assertEquals(manifest1.implFqcn(), manifest2.implFqcn());
    }

    // -------------------------------------------------------------------------
    // Property 3: Malformed JSON rejected
    // Feature: phase-c-node-runtime-execution, Property 3: Malformed JSON rejected
    // Validates: Requirement 1.5
    // -------------------------------------------------------------------------

    @Test
    void malformedJsonRejected_noOpeningBrace(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("\"version\": \"1\", \"blockKey\": \"test\"}", tempDir);
    }

    @Test
    void malformedJsonRejected_noClosingBrace(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("{\"version\": \"1\", \"blockKey\": \"test\"", tempDir);
    }

    @Test
    void malformedJsonRejected_emptyString(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("", tempDir);
    }

    @Test
    void malformedJsonRejected_whitespaceOnly(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("   ", tempDir);
    }

    @Test
    void malformedJsonRejected_arrayInsteadOfObject(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("[]", tempDir);
    }

    @Test
    void malformedJsonRejected_plainText(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("not json at all", tempDir);
    }

    @Test
    void malformedJsonRejected_startsWithBracket(@TempDir Path tempDir) throws IOException {
        assertMalformedJsonRejected("[\"version\": \"1\"]", tempDir);
    }

    private void assertMalformedJsonRejected(String malformedJson, Path tempDir) throws IOException {
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, malformedJson);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    // -------------------------------------------------------------------------
    // Property 4: Unsupported version rejected
    // Feature: phase-c-node-runtime-execution, Property 4: Unsupported version rejected
    // Validates: Requirement 1.6
    // -------------------------------------------------------------------------

    @Test
    void unsupportedVersionRejected_version2(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("2", tempDir);
    }

    @Test
    void unsupportedVersionRejected_version0(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("0", tempDir);
    }

    @Test
    void unsupportedVersionRejected_versionV1(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("v1", tempDir);
    }

    @Test
    void unsupportedVersionRejected_versionV3(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("v3", tempDir);
    }

    @Test
    void unsupportedVersionRejected_version1dot0(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("1.0", tempDir);
    }

    @Test
    void unsupportedVersionRejected_emptyVersion(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("", tempDir);
    }

    @Test
    void unsupportedVersionRejected_randomString(@TempDir Path tempDir) throws IOException {
        assertUnsupportedVersionRejected("abc", tempDir);
    }

    private void assertUnsupportedVersionRejected(String version, Path tempDir) throws IOException {
        String json = String.format("""
            {
              "version": "%s",
              "blockKey": "test-block",
              "targetId": "node"
            }
            """, version);

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("UNSUPPORTED_WIRING_SCHEMA_VERSION", ex.reasonCode());
    }

    // -------------------------------------------------------------------------
    // Property 18: NodeTarget.parseWiringManifest() delegates to NodeManifestParser
    // Feature: phase-c-node-runtime-execution, Property 18: NodeTarget.parseWiringManifest() delegates to NodeManifestParser
    // Validates: Requirements 1.7, 1.8
    // -------------------------------------------------------------------------

    @Test
    void nodeTargetDelegatesToNodeManifestParser_userAuth(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertNodeTargetDelegatesToNodeManifestParser("user-auth", tempDir);
    }

    @Test
    void nodeTargetDelegatesToNodeManifestParser_payment(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertNodeTargetDelegatesToNodeManifestParser("payment", tempDir);
    }

    @Test
    void nodeTargetDelegatesToNodeManifestParser_orderManager(@TempDir Path tempDir) throws IOException, ManifestParseException {
        assertNodeTargetDelegatesToNodeManifestParser("order-manager", tempDir);
    }

    private void assertNodeTargetDelegatesToNodeManifestParser(String blockKey, Path tempDir) throws IOException, ManifestParseException {
        // Create a valid Node manifest (version="1", not JVM's "v3")
        String json = String.format("""
            {
              "version": "1",
              "blockKey": "%s",
              "targetId": "node"
            }
            """, blockKey);

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        // Parse via NodeTarget
        NodeTarget target = new NodeTarget();
        WiringManifest manifest = target.parseWiringManifest(manifestPath);

        // Verify it used the Node parser (schemaVersion="1", entrypointFqcn="node")
        // JVM parser would require schemaVersion="v3" and different field names
        assertEquals("1", manifest.schemaVersion(),
            "NodeTarget should use Node parser (version=1), not JVM parser (schemaVersion=v3)");
        assertEquals("node", manifest.entrypointFqcn(),
            "NodeTarget should preserve targetId as entrypointFqcn");
        assertEquals(blockKey, manifest.blockKey());
    }

    // -------------------------------------------------------------------------
    // Additional property: Missing required fields produce correct error codes
    // -------------------------------------------------------------------------

    @Test
    void missingVersionProducesMissingKeyVersion_userAuth(@TempDir Path tempDir) throws IOException {
        assertMissingVersionProducesMissingKeyVersion("user-auth", tempDir);
    }

    @Test
    void missingVersionProducesMissingKeyVersion_payment(@TempDir Path tempDir) throws IOException {
        assertMissingVersionProducesMissingKeyVersion("payment", tempDir);
    }

    private void assertMissingVersionProducesMissingKeyVersion(String blockKey, Path tempDir) throws IOException {
        String json = String.format("""
            {
              "blockKey": "%s",
              "targetId": "node"
            }
            """, blockKey);

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MISSING_KEY_version", ex.reasonCode());
    }

    @Test
    void missingBlockKeyProducesMissingKeyBlockKey(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "version": "1",
              "targetId": "node"
            }
            """;

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MISSING_KEY_blockKey", ex.reasonCode());
    }

    // -------------------------------------------------------------------------
    // Additional property: Empty arrays handled correctly
    // -------------------------------------------------------------------------

    @Test
    void emptyArraysReturnEmptyLists(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "test-block",
              "targetId": "node",
              "wrappers": [],
              "ports": []
            }
            """;

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals(List.of(), manifest.governedSourceRoots());
        assertEquals(List.of(), manifest.requiredEffectPorts());
    }

    @Test
    void missingArraysReturnEmptyLists(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "test-block",
              "targetId": "node"
            }
            """;

        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals(List.of(), manifest.governedSourceRoots());
        assertEquals(List.of(), manifest.requiredEffectPorts());
    }

    // -------------------------------------------------------------------------
    // Property 5: Check workspace preparation with `_shared`
    // Feature: phase-c-node-runtime-execution, Property 5: Check workspace preparation with `_shared`
    // Validates: Requirement 2.1
    // -------------------------------------------------------------------------

    @Test
    void checkWorkspacePreparationWithShared_userAuth(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        assertCheckWorkspacePreparationWithShared("user-auth", projectRoot, tempRoot);
    }

    @Test
    void checkWorkspacePreparationWithShared_payment(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        assertCheckWorkspacePreparationWithShared("payment", projectRoot, tempRoot);
    }

    @Test
    void checkWorkspacePreparationWithShared_orderManager(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        assertCheckWorkspacePreparationWithShared("order-manager", projectRoot, tempRoot);
    }

    private void assertCheckWorkspacePreparationWithShared(String blockKey, Path projectRoot, Path tempRoot) throws IOException {
        // Create _shared directory in project root
        Path sharedDir = Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.writeString(sharedDir.resolve("utils.ts"), "// shared utils for " + blockKey + "\n");

        NodeTarget target = new NodeTarget();
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify _shared directory was created in tempRoot
        assertTrue(Files.isDirectory(tempRoot.resolve("src/blocks/_shared")),
            "prepareCheckWorkspace should create src/blocks/_shared in tempRoot when present in projectRoot");
    }

    // -------------------------------------------------------------------------
    // Property 6: JVM stub methods never throw
    // Feature: phase-c-node-runtime-execution, Property 6: JVM stub methods never throw
    // Validates: Requirements 3.1–3.9
    // -------------------------------------------------------------------------

    @Test
    void jvmStubMethodsNeverThrow_containmentSkipInfoLine() {
        NodeTarget target = new NodeTarget();
        // Should return null without throwing
        assertNull(target.containmentSkipInfoLine("test", Path.of("."), false));
        assertNull(target.containmentSkipInfoLine("project", Path.of("/tmp/project"), true));
        assertNull(target.containmentSkipInfoLine("", Path.of("relative/path"), false));
    }

    @Test
    void jvmStubMethodsNeverThrow_preflightContainmentIfRequired() throws IOException {
        NodeTarget target = new NodeTarget();
        // Should return null without throwing
        assertNull(target.preflightContainmentIfRequired(Path.of("."), false));
        assertNull(target.preflightContainmentIfRequired(Path.of("/tmp/project"), true));
        assertNull(target.preflightContainmentIfRequired(Path.of("relative/path"), false));
    }

    @Test
    void jvmStubMethodsNeverThrow_verifyContainmentMarkersIfRequired() throws IOException {
        NodeTarget target = new NodeTarget();
        // Should return null without throwing
        assertNull(target.verifyContainmentMarkersIfRequired(Path.of("."), false));
        assertNull(target.verifyContainmentMarkersIfRequired(Path.of("/tmp/project"), true));
        assertNull(target.verifyContainmentMarkersIfRequired(Path.of("relative/path"), false));
    }

    @Test
    void jvmStubMethodsNeverThrow_scanPortImplContainmentBypass() throws IOException, ManifestParseException {
        NodeTarget target = new NodeTarget();
        // Should return List.of() without throwing
        assertEquals(List.of(), target.scanPortImplContainmentBypass(Path.of("."), List.of()));
        assertEquals(List.of(), target.scanPortImplContainmentBypass(Path.of("/tmp/project"), List.of()));
    }

    @Test
    void jvmStubMethodsNeverThrow_scanBlockPortBindings() throws IOException {
        NodeTarget target = new NodeTarget();
        // Should return List.of() without throwing
        assertEquals(List.of(), target.scanBlockPortBindings(Path.of("."), List.of(), java.util.Set.of()));
        assertEquals(List.of(), target.scanBlockPortBindings(Path.of("/tmp/project"), List.of(), java.util.Set.of("wrapper1")));
    }

    @Test
    void jvmStubMethodsNeverThrow_scanMultiBlockPortImplAllowedSignals() throws IOException, ManifestParseException {
        NodeTarget target = new NodeTarget();
        // Should return List.of() without throwing
        assertEquals(List.of(), target.scanMultiBlockPortImplAllowedSignals(Path.of("."), List.of()));
        assertEquals(List.of(), target.scanMultiBlockPortImplAllowedSignals(Path.of("/tmp/project"), List.of()));
    }

    @Test
    void jvmStubMethodsNeverThrow_scanUndeclaredReach() throws Exception {
        NodeTarget target = new NodeTarget();
        // Should return List.of() without throwing
        assertEquals(List.of(), target.scanUndeclaredReach(Path.of(".")));
        assertEquals(List.of(), target.scanUndeclaredReach(Path.of("/tmp/project")));
    }

    @Test
    void jvmStubMethodsNeverThrow_scanForbiddenReflectionDispatch() throws IOException {
        NodeTarget target = new NodeTarget();
        // Should return List.of() without throwing
        assertEquals(List.of(), target.scanForbiddenReflectionDispatch(Path.of("."), List.of()));
        assertEquals(List.of(), target.scanForbiddenReflectionDispatch(Path.of("/tmp/project"), List.of()));
    }

    // -------------------------------------------------------------------------
    // Property 21: `prepareCheckWorkspace()` no-op when `_shared` absent
    // Feature: phase-c-node-runtime-execution, Property 21: `prepareCheckWorkspace()` no-op when `_shared` absent
    // Validates: Requirement 2.2
    // -------------------------------------------------------------------------

    @Test
    void prepareCheckWorkspaceNoOpWhenSharedAbsent_userAuth(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        assertPrepareCheckWorkspaceNoOpWhenSharedAbsent("user-auth", projectRoot, tempRoot);
    }

    @Test
    void prepareCheckWorkspaceNoOpWhenSharedAbsent_payment(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        assertPrepareCheckWorkspaceNoOpWhenSharedAbsent("payment", projectRoot, tempRoot);
    }

    @Test
    void prepareCheckWorkspaceNoOpWhenSharedAbsent_orderManager(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        assertPrepareCheckWorkspaceNoOpWhenSharedAbsent("order-manager", projectRoot, tempRoot);
    }

    private void assertPrepareCheckWorkspaceNoOpWhenSharedAbsent(String blockKey, Path projectRoot, Path tempRoot) throws IOException {
        // Create block directory but NO _shared directory
        Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));

        NodeTarget target = new NodeTarget();
        // Should complete without error
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify _shared directory was NOT created in tempRoot
        assertFalse(Files.exists(tempRoot.resolve("src/blocks/_shared")),
            "prepareCheckWorkspace should not create _shared directory when absent in projectRoot");
    }
}
