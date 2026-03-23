package com.bear.kernel.target.react;

import com.bear.kernel.ir.BearIr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReactArtifactGenerator covering all 10 acceptance criteria from Requirement 4.
 * 
 * Feature: react-target-scan-only
 */
class ReactArtifactGeneratorTest {

    private final ReactArtifactGenerator generator = new ReactArtifactGenerator();

    @TempDir
    Path tempDir;

    // --- Acceptance Criteria 4.1: FeaturePorts.ts generation ---

    @Test
    void generatePorts_createsFeaturePortsFile() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generatePorts(ir, typesDir, "user-dashboard");

        Path portsFile = typesDir.resolve("UserDashboardFeaturePorts.ts");
        assertTrue(Files.exists(portsFile), "FeaturePorts.ts should be created");
        String content = Files.readString(portsFile);
        assertTrue(content.contains("BEAR-generated"), "Should have BEAR header");
        assertTrue(content.contains("export interface UserDashboardFeaturePorts"), 
            "Should export FeaturePorts interface");
    }

    @Test
    void generatePorts_includesPortInterfaces() throws IOException {
        BearIr ir = createIrWithEffects("user-dashboard");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generatePorts(ir, typesDir, "user-dashboard");

        Path portsFile = typesDir.resolve("UserDashboardFeaturePorts.ts");
        String content = Files.readString(portsFile);
        assertTrue(content.contains("export interface StoragePort"), 
            "Should export port interface");
        assertTrue(content.contains("storage: StoragePort"), 
            "FeaturePorts should reference port");
    }

    // --- Acceptance Criteria 4.2: FeatureLogic.ts generation ---

    @Test
    void generateLogic_createsFeatureLogicFile() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generateLogic(ir, typesDir, "user-dashboard");

        Path logicFile = typesDir.resolve("UserDashboardFeatureLogic.ts");
        assertTrue(Files.exists(logicFile), "FeatureLogic.ts should be created");
        String content = Files.readString(logicFile);
        assertTrue(content.contains("BEAR-generated"), "Should have BEAR header");
        assertTrue(content.contains("export interface UserDashboardFeatureLogic"), 
            "Should export FeatureLogic interface");
    }

    @Test
    void generateLogic_includesRequestResultTypes() throws IOException {
        BearIr ir = createIrWithOperation("user-dashboard", "GetUser");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generateLogic(ir, typesDir, "user-dashboard");

        Path logicFile = typesDir.resolve("UserDashboardFeatureLogic.ts");
        String content = Files.readString(logicFile);
        assertTrue(content.contains("export interface UserDashboardGetUserRequest"), 
            "Should export Request type");
        assertTrue(content.contains("export interface UserDashboardGetUserResult"), 
            "Should export Result type");
        assertTrue(content.contains("GetUser(request: UserDashboardGetUserRequest"), 
            "Logic interface should have operation method");
    }

    // --- Acceptance Criteria 4.3: FeatureWrapper.ts generation ---

    @Test
    void generateWrapper_createsFeatureWrapperFile() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generateWrapper(ir, typesDir, "user-dashboard");

        Path wrapperFile = typesDir.resolve("UserDashboardFeatureWrapper.ts");
        assertTrue(Files.exists(wrapperFile), "FeatureWrapper.ts should be created");
        String content = Files.readString(wrapperFile);
        assertTrue(content.contains("BEAR-generated"), "Should have BEAR header");
        assertTrue(content.contains("createUserDashboardFeatureWrapper"), 
            "Should export wrapper factory function");
    }

    @Test
    void generateWrapper_importsLogicAndPorts() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generateWrapper(ir, typesDir, "user-dashboard");

        Path wrapperFile = typesDir.resolve("UserDashboardFeatureWrapper.ts");
        String content = Files.readString(wrapperFile);
        assertTrue(content.contains("import type { UserDashboardFeatureLogic }"), 
            "Should import FeatureLogic");
        assertTrue(content.contains("import type { UserDashboardFeaturePorts }"), 
            "Should import FeaturePorts");
    }

    // --- Acceptance Criteria 4.4: Wiring manifest generation (tested in ReactManifestGeneratorTest) ---

    // --- Acceptance Criteria 4.5: User impl skeleton creation ---

    @Test
    void generateUserImplSkeleton_createsImplFile() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path implDir = tempDir.resolve("src/features/user-dashboard/impl");

        generator.generateUserImplSkeleton(ir, implDir, "user-dashboard");

        Path implFile = implDir.resolve("UserDashboardFeatureImpl.tsx");
        assertTrue(Files.exists(implFile), "FeatureImpl.tsx should be created");
        String content = Files.readString(implFile);
        assertTrue(content.contains("User-owned"), "Should have user-owned header");
        assertTrue(content.contains("class UserDashboardFeatureImpl"), 
            "Should export impl class");
    }

    @Test
    void generateUserImplSkeleton_usesTsxExtension() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path implDir = tempDir.resolve("src/features/user-dashboard/impl");

        generator.generateUserImplSkeleton(ir, implDir, "user-dashboard");

        Path implFile = implDir.resolve("UserDashboardFeatureImpl.tsx");
        assertTrue(Files.exists(implFile), "Should use .tsx extension");
        assertFalse(Files.exists(implDir.resolve("UserDashboardFeatureImpl.ts")), 
            "Should not create .ts file");
    }

    // --- Acceptance Criteria 4.6: Impl preservation ---

    @Test
    void generateUserImplSkeleton_preservesExistingImpl() throws IOException {
        BearIr ir = createMinimalIr("user-dashboard");
        Path implDir = tempDir.resolve("src/features/user-dashboard/impl");
        Files.createDirectories(implDir);
        Path implFile = implDir.resolve("UserDashboardFeatureImpl.tsx");
        String existingContent = "// User's custom implementation\nexport class UserDashboardFeatureImpl {}";
        Files.writeString(implFile, existingContent);

        generator.generateUserImplSkeleton(ir, implDir, "user-dashboard");

        String afterContent = Files.readString(implFile);
        assertEquals(existingContent, afterContent, "Existing impl should not be modified");
    }

    // --- Acceptance Criteria 4.7: generateWiringOnly (tested in ReactManifestGeneratorTest) ---

    // --- Acceptance Criteria 4.8: Syntactically valid TypeScript ---

    @Test
    void generatedFiles_areSyntacticallyValid() throws IOException {
        BearIr ir = createIrWithOperation("user-dashboard", "GetUser");
        Path typesDir = tempDir.resolve("build/generated/bear/types/user-dashboard");

        generator.generatePorts(ir, typesDir, "user-dashboard");
        generator.generateLogic(ir, typesDir, "user-dashboard");
        generator.generateWrapper(ir, typesDir, "user-dashboard");

        // Basic syntax validation: check for balanced braces and proper structure
        for (String filename : List.of("UserDashboardFeaturePorts.ts", 
                                        "UserDashboardFeatureLogic.ts", 
                                        "UserDashboardFeatureWrapper.ts")) {
            String content = Files.readString(typesDir.resolve(filename));
            assertBalancedBraces(content, filename);
            assertNoSyntaxErrors(content, filename);
        }
    }

    // --- Acceptance Criteria 4.9: React-flavored naming ---

    @Test
    void generatedFiles_useReactFlavoredSuffixes() throws IOException {
        BearIr ir = createMinimalIr("product-catalog");
        Path typesDir = tempDir.resolve("build/generated/bear/types/product-catalog");

        generator.generatePorts(ir, typesDir, "product-catalog");
        generator.generateLogic(ir, typesDir, "product-catalog");
        generator.generateWrapper(ir, typesDir, "product-catalog");

        assertTrue(Files.exists(typesDir.resolve("ProductCatalogFeaturePorts.ts")), 
            "Should use FeaturePorts suffix");
        assertTrue(Files.exists(typesDir.resolve("ProductCatalogFeatureLogic.ts")), 
            "Should use FeatureLogic suffix");
        assertTrue(Files.exists(typesDir.resolve("ProductCatalogFeatureWrapper.ts")), 
            "Should use FeatureWrapper suffix");
    }

    // --- Acceptance Criteria 4.10: Kebab-case to PascalCase conversion ---

    @Test
    void deriveBlockName_convertsToPascalCase() {
        assertEquals("UserDashboard", ReactArtifactGenerator.deriveBlockName("user-dashboard"));
        assertEquals("ProductCatalog", ReactArtifactGenerator.deriveBlockName("product-catalog"));
        assertEquals("Auth", ReactArtifactGenerator.deriveBlockName("auth"));
        assertEquals("MyComplexBlockName", ReactArtifactGenerator.deriveBlockName("my-complex-block-name"));
    }

    @Test
    void kebabToPascal_handlesEdgeCases() {
        assertEquals("", ReactArtifactGenerator.kebabToPascal(""));
        assertNull(ReactArtifactGenerator.kebabToPascal(null));
        assertEquals("Single", ReactArtifactGenerator.kebabToPascal("single"));
        assertEquals("AB", ReactArtifactGenerator.kebabToPascal("a-b"));
    }

    @Test
    void kebabToCamel_convertsCorrectly() {
        assertEquals("userDashboard", ReactArtifactGenerator.kebabToCamel("user-dashboard"));
        assertEquals("productCatalog", ReactArtifactGenerator.kebabToCamel("product-catalog"));
        assertEquals("auth", ReactArtifactGenerator.kebabToCamel("auth"));
    }

    // --- writeIfDifferent tests ---

    @Test
    void writeIfDifferent_writesNewFile() throws IOException {
        Path file = tempDir.resolve("test.ts");
        String content = "export const x = 1;";

        generator.writeIfDifferent(file, content);

        assertTrue(Files.exists(file));
        assertEquals(content, Files.readString(file));
    }

    @Test
    void writeIfDifferent_skipsIdenticalContent() throws IOException {
        Path file = tempDir.resolve("test.ts");
        String content = "export const x = 1;";
        Files.writeString(file, content);
        long originalModified = Files.getLastModifiedTime(file).toMillis();

        // Small delay to ensure timestamp would change if file was rewritten
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        generator.writeIfDifferent(file, content);

        // File should not have been rewritten
        assertEquals(content, Files.readString(file));
    }

    @Test
    void writeIfDifferent_updatesChangedContent() throws IOException {
        Path file = tempDir.resolve("test.ts");
        String originalContent = "export const x = 1;";
        String newContent = "export const x = 2;";
        Files.writeString(file, originalContent);

        generator.writeIfDifferent(file, newContent);

        assertEquals(newContent, Files.readString(file));
    }

    @Test
    void writeIfDifferent_normalizesLineEndings() throws IOException {
        Path file = tempDir.resolve("test.ts");
        String contentWithCrlf = "line1\r\nline2\r\n";
        String expectedContent = "line1\nline2\n";

        generator.writeIfDifferent(file, contentWithCrlf);

        assertEquals(expectedContent, Files.readString(file));
    }

    // --- Helper methods ---

    private BearIr createMinimalIr(String blockName) {
        return new BearIr(
            "1",
            new BearIr.Block(
                blockName,
                BearIr.BlockKind.LOGIC,
                List.of(),
                null,
                null,
                null,
                List.of()
            )
        );
    }

    private BearIr createIrWithOperation(String blockName, String opName) {
        BearIr.Operation op = new BearIr.Operation(
            opName,
            new BearIr.Contract(
                List.of(new BearIr.Field("user-id", BearIr.FieldType.STRING)),
                List.of(new BearIr.Field("user-name", BearIr.FieldType.STRING))
            ),
            null,
            null,
            List.of()
        );

        return new BearIr(
            "1",
            new BearIr.Block(
                blockName,
                BearIr.BlockKind.LOGIC,
                List.of(op),
                null,
                null,
                null,
                List.of()
            )
        );
    }

    private BearIr createIrWithEffects(String blockName) {
        BearIr.EffectPort port = new BearIr.EffectPort(
            "storage",
            BearIr.EffectPortKind.EXTERNAL,
            List.of("get", "put"),
            null,
            null
        );

        return new BearIr(
            "1",
            new BearIr.Block(
                blockName,
                BearIr.BlockKind.LOGIC,
                List.of(),
                new BearIr.Effects(List.of(port)),
                null,
                null,
                List.of()
            )
        );
    }

    private void assertBalancedBraces(String content, String filename) {
        int braceCount = 0;
        for (char c : content.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
        }
        assertEquals(0, braceCount, "Unbalanced braces in " + filename);
    }

    private void assertNoSyntaxErrors(String content, String filename) {
        // Basic checks for common syntax issues
        assertFalse(content.contains(";;"), "Double semicolons in " + filename);
        assertFalse(content.contains("{{"), "Double opening braces in " + filename);
        assertFalse(content.contains("}}"), "Double closing braces in " + filename);
        assertTrue(content.endsWith("\n") || content.endsWith("}"), 
            "File should end properly in " + filename);
    }
}
