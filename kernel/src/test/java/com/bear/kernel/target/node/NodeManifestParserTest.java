package com.bear.kernel.target.node;

import com.bear.kernel.target.ManifestParseException;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NodeManifestParser.
 * Requirements: 1.1–1.6
 */
class NodeManifestParserTest {

    // --- Valid manifest → all fields populated ---

    @Test
    void validManifest_allFieldsPopulated(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "user-auth",
              "targetId": "node",
              "generatedPackage": "build/generated/bear/types/user-auth",
              "implPackage": "src/blocks/user-auth/impl",
              "wrappers": [
                { "operation": "login", "wrapperClass": "UserAuth_Login", "wrapperPath": "..." }
              ],
              "ports": [
                { "name": "db", "kind": "EXTERNAL", "interface": "DbPort" }
              ]
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals("1", manifest.schemaVersion());
        assertEquals("user-auth", manifest.blockKey());
        assertEquals("node", manifest.entrypointFqcn());
        assertEquals("build/generated/bear/types/user-auth", manifest.logicInterfaceFqcn());
        assertEquals("src/blocks/user-auth/impl", manifest.implFqcn());
        assertEquals(1, manifest.governedSourceRoots().size());
        assertTrue(manifest.governedSourceRoots().get(0).contains("login"));
        assertEquals(1, manifest.requiredEffectPorts().size());
        assertTrue(manifest.requiredEffectPorts().get(0).contains("db"));
    }

    // --- version missing → MISSING_KEY_version ---

    @Test
    void versionMissing_throwsMissingKeyVersion(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "blockKey": "user-auth",
              "targetId": "node"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MISSING_KEY_version", ex.reasonCode());
    }

    // --- blockKey missing → MISSING_KEY_blockKey ---

    @Test
    void blockKeyMissing_throwsMissingKeyBlockKey(@TempDir Path tempDir) throws IOException {
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

    // --- version = "2" → UNSUPPORTED_WIRING_SCHEMA_VERSION ---

    @Test
    void versionTwo_throwsUnsupportedVersion(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "version": "2",
              "blockKey": "user-auth",
              "targetId": "node"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("UNSUPPORTED_WIRING_SCHEMA_VERSION", ex.reasonCode());
    }

    // --- malformed JSON → MALFORMED_JSON ---

    @Test
    void malformedJson_noOpeningBrace_throwsMalformedJson(@TempDir Path tempDir) throws IOException {
        String json = "\"version\": \"1\", \"blockKey\": \"test\"}";
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    @Test
    void malformedJson_noClosingBrace_throwsMalformedJson(@TempDir Path tempDir) throws IOException {
        String json = "{\"version\": \"1\", \"blockKey\": \"test\"";
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    @Test
    void malformedJson_emptyString_throwsMalformedJson(@TempDir Path tempDir) throws IOException {
        String json = "";
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    @Test
    void malformedJson_plainText_throwsMalformedJson(@TempDir Path tempDir) throws IOException {
        String json = "not json at all";
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    // --- empty wrappers/ports → List.of() ---

    @Test
    void emptyWrappersAndPorts_returnsEmptyLists(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "user-auth",
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
    void missingWrappersAndPorts_returnsEmptyLists(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "user-auth",
              "targetId": "node"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals(List.of(), manifest.governedSourceRoots());
        assertEquals(List.of(), manifest.requiredEffectPorts());
    }

    // --- optional fields absent → empty strings ---

    @Test
    void optionalFieldsAbsent_returnsEmptyStrings(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "user-auth"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals("1", manifest.schemaVersion());
        assertEquals("user-auth", manifest.blockKey());
        assertEquals("", manifest.entrypointFqcn());
        assertEquals("", manifest.logicInterfaceFqcn());
        assertEquals("", manifest.implFqcn());
        assertEquals("", manifest.implSourcePath());
        assertEquals("", manifest.blockRootSourceDir());
    }

    // --- multiple wrappers and ports ---

    @Test
    void multipleWrappersAndPorts_allParsed(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "payment",
              "targetId": "node",
              "wrappers": [
                { "operation": "charge", "wrapperClass": "Payment_Charge" },
                { "operation": "refund", "wrapperClass": "Payment_Refund" },
                { "operation": "verify", "wrapperClass": "Payment_Verify" }
              ],
              "ports": [
                { "name": "stripe", "kind": "EXTERNAL" },
                { "name": "audit", "kind": "BLOCK" }
              ]
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals(3, manifest.governedSourceRoots().size());
        assertEquals(2, manifest.requiredEffectPorts().size());
    }

    // --- version with different invalid values ---

    @Test
    void versionEmpty_throwsUnsupportedVersion(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "version": "",
              "blockKey": "user-auth"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("UNSUPPORTED_WIRING_SCHEMA_VERSION", ex.reasonCode());
    }

    @Test
    void versionV3_throwsUnsupportedVersion(@TempDir Path tempDir) throws IOException {
        String json = """
            {
              "version": "v3",
              "blockKey": "user-auth"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> NodeManifestParser.parse(manifestPath));
        assertEquals("UNSUPPORTED_WIRING_SCHEMA_VERSION", ex.reasonCode());
    }

    // --- JSON with escaped characters ---

    @Test
    void escapedCharactersInBlockKey_parsedCorrectly(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "user\\nauth",
              "targetId": "node"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals("user\nauth", manifest.blockKey());
    }

    // --- whitespace handling ---

    @Test
    void extraWhitespace_parsedCorrectly(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version"  :  "1"  ,
              "blockKey" :  "user-auth"  ,
              "targetId" :  "node"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        assertEquals("1", manifest.schemaVersion());
        assertEquals("user-auth", manifest.blockKey());
        assertEquals("node", manifest.entrypointFqcn());
    }

    // --- parseJson direct method test ---

    @Test
    void parseJson_validJson_returnsManifest() throws ManifestParseException {
        String json = """
            {"version": "1", "blockKey": "test", "targetId": "node"}
            """;

        WiringManifest manifest = NodeManifestParser.parseJson(json.trim());

        assertEquals("1", manifest.schemaVersion());
        assertEquals("test", manifest.blockKey());
        assertEquals("node", manifest.entrypointFqcn());
    }

    // --- absent fields default to empty ---

    @Test
    void absentFields_defaultToEmptyOrEmptyList(@TempDir Path tempDir) throws IOException, ManifestParseException {
        String json = """
            {
              "version": "1",
              "blockKey": "minimal"
            }
            """;
        Path manifestPath = tempDir.resolve("test.wiring.json");
        Files.writeString(manifestPath, json);

        WiringManifest manifest = NodeManifestParser.parse(manifestPath);

        // Required fields
        assertEquals("1", manifest.schemaVersion());
        assertEquals("minimal", manifest.blockKey());

        // Optional string fields default to empty
        assertEquals("", manifest.entrypointFqcn());
        assertEquals("", manifest.logicInterfaceFqcn());
        assertEquals("", manifest.implFqcn());
        assertEquals("", manifest.implSourcePath());
        assertEquals("", manifest.blockRootSourceDir());

        // Optional list fields default to empty list
        assertEquals(List.of(), manifest.governedSourceRoots());
        assertEquals(List.of(), manifest.requiredEffectPorts());
        assertEquals(List.of(), manifest.constructorPortParams());
        assertEquals(List.of(), manifest.logicRequiredPorts());
        assertEquals(List.of(), manifest.wrapperOwnedSemanticPorts());
        assertEquals(List.of(), manifest.wrapperOwnedSemanticChecks());
    }
}
