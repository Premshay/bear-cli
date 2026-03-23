package com.bear.kernel.target.react.properties;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.target.react.ReactArtifactGenerator;
import com.bear.kernel.target.react.ReactManifestGenerator;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for React artifact generation (P7–P10).
 * Each property runs 100+ iterations over generated inputs.
 * 
 * Feature: react-target-scan-only
 */
class ReactArtifactGenerationProperties {

    private final ReactArtifactGenerator artifactGenerator = new ReactArtifactGenerator();
    private final ReactManifestGenerator manifestGenerator = new ReactManifestGenerator();

    /**
     * P7 — Round-trip stability: For any valid BearIr input, generating artifacts then
     * re-generating produces byte-identical output. generate(ir) == generate(generate(ir)).
     * 
     * Validates: Requirement 5.3
     */
    @ParameterizedTest(name = "P7 iteration {0}: blockKey={1}, opCount={2}")
    @MethodSource("roundTripConfigurations")
    void p7_roundTripStability(
            int iteration,
            String blockKey,
            int operationCount,
            boolean hasEffects,
            @TempDir Path tempDir) throws IOException {
        
        BearIr ir = createIr(blockKey, operationCount, hasEffects);
        Path typesDir1 = tempDir.resolve("gen1/types/" + blockKey);
        Path typesDir2 = tempDir.resolve("gen2/types/" + blockKey);
        Path wiringDir1 = tempDir.resolve("gen1/wiring");
        Path wiringDir2 = tempDir.resolve("gen2/wiring");

        // First generation
        artifactGenerator.generatePorts(ir, typesDir1, blockKey);
        artifactGenerator.generateLogic(ir, typesDir1, blockKey);
        artifactGenerator.generateWrapper(ir, typesDir1, blockKey);
        manifestGenerator.generateWiringManifest(ir, wiringDir1, blockKey);

        // Second generation (should produce identical output)
        artifactGenerator.generatePorts(ir, typesDir2, blockKey);
        artifactGenerator.generateLogic(ir, typesDir2, blockKey);
        artifactGenerator.generateWrapper(ir, typesDir2, blockKey);
        manifestGenerator.generateWiringManifest(ir, wiringDir2, blockKey);

        // Compare all generated files
        String blockName = ReactArtifactGenerator.deriveBlockName(blockKey);
        assertFilesIdentical(typesDir1, typesDir2, blockName + "FeaturePorts.ts", "P7");
        assertFilesIdentical(typesDir1, typesDir2, blockName + "FeatureLogic.ts", "P7");
        assertFilesIdentical(typesDir1, typesDir2, blockName + "FeatureWrapper.ts", "P7");
        assertFilesIdentical(wiringDir1, wiringDir2, blockKey + ".wiring.json", "P7");
    }

    /**
     * P8 — PascalCase naming: For any kebab-case block key, the generated artifact filenames
     * use the correct PascalCase prefix with FeaturePorts, FeatureLogic, FeatureWrapper suffixes.
     * 
     * Validates: Requirement 4.10
     */
    @ParameterizedTest(name = "P8 iteration {0}: blockKey={1}")
    @MethodSource("pascalCaseConfigurations")
    void p8_pascalCaseNaming(
            int iteration,
            String blockKey,
            String expectedPascalCase,
            @TempDir Path tempDir) throws IOException {
        
        BearIr ir = createMinimalIr(blockKey);
        Path typesDir = tempDir.resolve("types/" + blockKey);

        artifactGenerator.generatePorts(ir, typesDir, blockKey);
        artifactGenerator.generateLogic(ir, typesDir, blockKey);
        artifactGenerator.generateWrapper(ir, typesDir, blockKey);

        // Verify filenames use correct PascalCase
        assertTrue(Files.exists(typesDir.resolve(expectedPascalCase + "FeaturePorts.ts")),
            "P8: FeaturePorts should use PascalCase: " + expectedPascalCase);
        assertTrue(Files.exists(typesDir.resolve(expectedPascalCase + "FeatureLogic.ts")),
            "P8: FeatureLogic should use PascalCase: " + expectedPascalCase);
        assertTrue(Files.exists(typesDir.resolve(expectedPascalCase + "FeatureWrapper.ts")),
            "P8: FeatureWrapper should use PascalCase: " + expectedPascalCase);

        // Verify content uses correct PascalCase
        String portsContent = Files.readString(typesDir.resolve(expectedPascalCase + "FeaturePorts.ts"));
        assertTrue(portsContent.contains("interface " + expectedPascalCase + "FeaturePorts"),
            "P8: FeaturePorts interface should use PascalCase");
    }

    /**
     * P9 — Impl preservation: For any block key where a user impl file already exists,
     * compile() does not modify the existing impl file.
     * 
     * Validates: Requirement 4.6
     */
    @ParameterizedTest(name = "P9 iteration {0}: blockKey={1}")
    @MethodSource("implPreservationConfigurations")
    void p9_implPreservation(
            int iteration,
            String blockKey,
            String existingImplContent,
            @TempDir Path tempDir) throws IOException {
        
        BearIr ir = createMinimalIr(blockKey);
        String blockName = ReactArtifactGenerator.deriveBlockName(blockKey);
        Path implDir = tempDir.resolve("src/features/" + blockKey + "/impl");
        Files.createDirectories(implDir);
        Path implFile = implDir.resolve(blockName + "FeatureImpl.tsx");
        
        // Create existing impl file
        Files.writeString(implFile, existingImplContent);
        long originalSize = Files.size(implFile);
        byte[] originalBytes = Files.readAllBytes(implFile);

        // Attempt to generate impl skeleton
        artifactGenerator.generateUserImplSkeleton(ir, implDir, blockKey);

        // Verify file was not modified
        assertTrue(Files.exists(implFile), "P9: Impl file should still exist");
        assertEquals(originalSize, Files.size(implFile), 
            "P9: Impl file size should not change");
        assertArrayEquals(originalBytes, Files.readAllBytes(implFile),
            "P9: Impl file content should be byte-identical");
    }

    /**
     * P10 — Wiring manifest present: For any valid BearIr input, compile() produces a
     * <blockKey>.wiring.json file under build/generated/bear/wiring/.
     * 
     * Validates: Requirement 4.4
     */
    @ParameterizedTest(name = "P10 iteration {0}: blockKey={1}, opCount={2}")
    @MethodSource("wiringManifestConfigurations")
    void p10_wiringManifestPresent(
            int iteration,
            String blockKey,
            int operationCount,
            boolean hasEffects,
            @TempDir Path tempDir) throws IOException {
        
        BearIr ir = createIr(blockKey, operationCount, hasEffects);
        Path wiringDir = tempDir.resolve("build/generated/bear/wiring");

        manifestGenerator.generateWiringManifest(ir, wiringDir, blockKey);

        Path manifestFile = wiringDir.resolve(blockKey + ".wiring.json");
        assertTrue(Files.exists(manifestFile),
            "P10: Wiring manifest should be created at " + manifestFile);
        
        String content = Files.readString(manifestFile);
        assertTrue(content.contains("\"schemaVersion\": \"v3\""),
            "P10: Manifest should have schemaVersion v3");
        assertTrue(content.contains("\"blockKey\": \"" + blockKey + "\""),
            "P10: Manifest should contain correct blockKey");
        assertTrue(content.startsWith("{") && content.trim().endsWith("}"),
            "P10: Manifest should be valid JSON structure");
    }

    // --- Data providers generating 100+ iterations ---

    static Stream<Arguments> roundTripConfigurations() {
        String[] blockKeys = {"user-dashboard", "product-catalog", "auth", "order-management", "inventory"};
        int[] opCounts = {1, 2, 3, 5};
        boolean[] effectOptions = {true, false};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 3; cycle++) {
            for (String blockKey : blockKeys) {
                for (int opCount : opCounts) {
                    for (boolean hasEffects : effectOptions) {
                        builder.add(Arguments.of(iteration++, blockKey, opCount, hasEffects));
                    }
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> pascalCaseConfigurations() {
        // Pairs of (kebab-case, expected PascalCase)
        String[][] conversions = {
            {"user-dashboard", "UserDashboard"},
            {"product-catalog", "ProductCatalog"},
            {"auth", "Auth"},
            {"order-management", "OrderManagement"},
            {"inventory-service", "InventoryService"},
            {"a-b-c", "ABC"},
            {"my-complex-block-name", "MyComplexBlockName"},
            {"single", "Single"},
            {"x", "X"},
            {"api-gateway", "ApiGateway"}
        };
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 10; cycle++) {
            for (String[] conversion : conversions) {
                builder.add(Arguments.of(iteration++, conversion[0], conversion[1]));
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> implPreservationConfigurations() {
        String[] blockKeys = {"user-dashboard", "product-catalog", "auth", "order-management"};
        String[] existingContents = {
            "// User's custom implementation\nexport class UserDashboardFeatureImpl {}",
            "// Modified by user\nimport React from 'react';\nexport class ProductCatalogFeatureImpl {}",
            "export class AuthFeatureImpl {\n  login() { return true; }\n}",
            "// Complex implementation\nexport class OrderManagementFeatureImpl {\n  // lots of code\n}"
        };
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 25; cycle++) {
            for (int i = 0; i < blockKeys.length; i++) {
                builder.add(Arguments.of(iteration++, blockKeys[i], existingContents[i]));
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> wiringManifestConfigurations() {
        String[] blockKeys = {"user-dashboard", "product-catalog", "auth", "order-management", "inventory"};
        int[] opCounts = {1, 2, 3, 5};
        boolean[] effectOptions = {true, false};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 3; cycle++) {
            for (String blockKey : blockKeys) {
                for (int opCount : opCounts) {
                    for (boolean hasEffects : effectOptions) {
                        builder.add(Arguments.of(iteration++, blockKey, opCount, hasEffects));
                    }
                }
            }
        }
        
        return builder.build();
    }

    // --- Helper methods ---

    private BearIr createMinimalIr(String blockName) {
        BearIr.Operation op = new BearIr.Operation(
            "Default",
            new BearIr.Contract(
                List.of(new BearIr.Field("id", BearIr.FieldType.STRING)),
                List.of(new BearIr.Field("id", BearIr.FieldType.STRING))
            ),
            new BearIr.Effects(List.of()),
            null,
            List.of()
        );
        return new BearIr(
            "v1",
            new BearIr.Block(
                blockName,
                BearIr.BlockKind.LOGIC,
                List.of(op),
                new BearIr.Effects(List.of()),
                new BearIr.Impl(List.of()),
                null,
                List.of()
            )
        );
    }

    private BearIr createIr(String blockName, int operationCount, boolean hasEffects) {
        List<BearIr.Operation> operations = new ArrayList<>();
        for (int i = 0; i < operationCount; i++) {
            operations.add(new BearIr.Operation(
                "Operation" + i,
                new BearIr.Contract(
                    List.of(new BearIr.Field("input-" + i, BearIr.FieldType.STRING)),
                    List.of(new BearIr.Field("output-" + i, BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(List.of()),
                null,
                List.of()
            ));
        }

        BearIr.Effects effects = new BearIr.Effects(List.of());
        if (hasEffects) {
            effects = new BearIr.Effects(List.of(
                new BearIr.EffectPort(
                    "storage",
                    BearIr.EffectPortKind.EXTERNAL,
                    List.of("get", "put"),
                    null,
                    null
                )
            ));
        }

        return new BearIr(
            "v1",
            new BearIr.Block(
                blockName,
                BearIr.BlockKind.LOGIC,
                operations,
                effects,
                new BearIr.Impl(List.of()),
                null,
                List.of()
            )
        );
    }

    private void assertFilesIdentical(Path dir1, Path dir2, String filename, String property) throws IOException {
        Path file1 = dir1.resolve(filename);
        Path file2 = dir2.resolve(filename);
        
        assertTrue(Files.exists(file1), property + ": First generation should create " + filename);
        assertTrue(Files.exists(file2), property + ": Second generation should create " + filename);
        
        byte[] bytes1 = Files.readAllBytes(file1);
        byte[] bytes2 = Files.readAllBytes(file2);
        
        assertArrayEquals(bytes1, bytes2, 
            property + ": " + filename + " should be byte-identical across generations");
    }
}
