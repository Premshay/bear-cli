package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.NodeImportContainmentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for dynamic import enforcement.
 * Feature: phase-c-node-runtime-execution
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs.
 */
class DynamicImportEnforcementProperties {

    // -------------------------------------------------------------------------
    // Property 8: Dynamic import findings include path and specifier
    // Feature: phase-c-node-runtime-execution, Property 8: Dynamic import findings include path and specifier
    // Validates: Requirements 5.1, 5.4
    // -------------------------------------------------------------------------

    @Test
    void dynamicImportFindingsIncludePathAndSpecifier_userAuth(@TempDir Path tempDir) throws IOException {
        assertDynamicImportFindingsIncludePathAndSpecifier("user-auth", "./module", tempDir);
    }

    @Test
    void dynamicImportFindingsIncludePathAndSpecifier_payment(@TempDir Path tempDir) throws IOException {
        assertDynamicImportFindingsIncludePathAndSpecifier("payment", "./services/auth", tempDir);
    }

    @Test
    void dynamicImportFindingsIncludePathAndSpecifier_orderManager(@TempDir Path tempDir) throws IOException {
        assertDynamicImportFindingsIncludePathAndSpecifier("order-manager", "../shared/helper", tempDir);
    }

    @Test
    void dynamicImportFindingsIncludePathAndSpecifier_inventory(@TempDir Path tempDir) throws IOException {
        assertDynamicImportFindingsIncludePathAndSpecifier("inventory", "./components/button", tempDir);
    }

    @Test
    void dynamicImportFindingsIncludePathAndSpecifier_analytics(@TempDir Path tempDir) throws IOException {
        assertDynamicImportFindingsIncludePathAndSpecifier("analytics", "./utils", tempDir);
    }

    private void assertDynamicImportFindingsIncludePathAndSpecifier(String blockKey, String specifier, Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        String content = "const mod = import('" + specifier + "');\n";
        Files.writeString(blockRoot.resolve("index.ts"), content);

        List<WiringManifest> manifests = List.of(makeManifest(blockKey));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        List<BoundaryBypassFinding> dynamicFindings = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .toList();

        assertFalse(dynamicFindings.isEmpty(), "Expected at least one DYNAMIC_IMPORT_FORBIDDEN finding");

        BoundaryBypassFinding finding = dynamicFindings.get(0);
        // Path should be repo-relative and contain the file
        assertNotNull(finding.path());
        assertTrue(finding.path().contains("index.ts"), "Path should contain filename");
        assertTrue(finding.path().startsWith("src/blocks/"), "Path should be repo-relative");
        // Detail should contain the specifier
        assertNotNull(finding.detail());
        assertTrue(finding.detail().contains(specifier), "Detail should contain specifier: " + specifier);
    }

    // -------------------------------------------------------------------------
    // Property 9: No dynamic import findings for clean files
    // Feature: phase-c-node-runtime-execution, Property 9: No dynamic import findings for clean files
    // Validates: Requirement 5.2
    // -------------------------------------------------------------------------

    @Test
    void noDynamicImportFindingsForCleanFiles_emptyComment(@TempDir Path tempDir) throws IOException {
        assertNoDynamicImportFindingsForCleanFiles("user-auth", "// clean file\n", tempDir);
    }

    @Test
    void noDynamicImportFindingsForCleanFiles_constDeclaration(@TempDir Path tempDir) throws IOException {
        assertNoDynamicImportFindingsForCleanFiles("payment", "const x = 1;\n", tempDir);
    }

    @Test
    void noDynamicImportFindingsForCleanFiles_exportConst(@TempDir Path tempDir) throws IOException {
        assertNoDynamicImportFindingsForCleanFiles("order-manager", "export const y = 2;\n", tempDir);
    }

    @Test
    void noDynamicImportFindingsForCleanFiles_staticImport(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/inventory"));
        Files.writeString(blockRoot.resolve("utils.ts"), "export const x = 1;\n");
        Files.writeString(blockRoot.resolve("index.ts"), "import { x } from './utils';\nconst y = x + 1;\n");

        List<WiringManifest> manifests = List.of(makeManifest("inventory"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        long dynamicCount = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .count();

        assertEquals(0, dynamicCount, "No DYNAMIC_IMPORT_FORBIDDEN findings expected for clean files");
    }

    @Test
    void noDynamicImportFindingsForCleanFiles_namespaceImport(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/analytics"));
        Files.writeString(blockRoot.resolve("utils.ts"), "export const x = 1;\n");
        Files.writeString(blockRoot.resolve("index.ts"), "import * as utils from './utils';\nexport { utils };\n");

        List<WiringManifest> manifests = List.of(makeManifest("analytics"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        long dynamicCount = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .count();

        assertEquals(0, dynamicCount, "No DYNAMIC_IMPORT_FORBIDDEN findings expected for clean files");
    }

    @Test
    void noDynamicImportFindingsForCleanFiles_functionDeclaration(@TempDir Path tempDir) throws IOException {
        assertNoDynamicImportFindingsForCleanFiles("shipping", "// no imports at all\nfunction foo() { return 42; }\n", tempDir);
    }

    private void assertNoDynamicImportFindingsForCleanFiles(String blockKey, String source, Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("index.ts"), source);

        List<WiringManifest> manifests = List.of(makeManifest(blockKey));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        long dynamicCount = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .count();

        assertEquals(0, dynamicCount, "No DYNAMIC_IMPORT_FORBIDDEN findings expected for clean files");
    }

    // -------------------------------------------------------------------------
    // Property 10: All dynamic imports collected before reporting
    // Feature: phase-c-node-runtime-execution, Property 10: All dynamic imports collected before reporting
    // Validates: Requirement 5.6
    // -------------------------------------------------------------------------

    @Test
    void allDynamicImportsCollectedBeforeReporting_2imports(@TempDir Path tempDir) throws IOException {
        assertAllDynamicImportsCollectedBeforeReporting("user-auth", 2, tempDir);
    }

    @Test
    void allDynamicImportsCollectedBeforeReporting_3imports(@TempDir Path tempDir) throws IOException {
        assertAllDynamicImportsCollectedBeforeReporting("payment", 3, tempDir);
    }

    @Test
    void allDynamicImportsCollectedBeforeReporting_4imports(@TempDir Path tempDir) throws IOException {
        assertAllDynamicImportsCollectedBeforeReporting("order-manager", 4, tempDir);
    }

    @Test
    void allDynamicImportsCollectedBeforeReporting_5imports(@TempDir Path tempDir) throws IOException {
        assertAllDynamicImportsCollectedBeforeReporting("inventory", 5, tempDir);
    }

    private void assertAllDynamicImportsCollectedBeforeReporting(String blockKey, int count, Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));

        // Generate source with exactly `count` dynamic imports
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < count; i++) {
            source.append("const mod").append(i).append(" = import('./module").append(i).append("');\n");
        }
        Files.writeString(blockRoot.resolve("index.ts"), source.toString());

        List<WiringManifest> manifests = List.of(makeManifest(blockKey));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        long dynamicCount = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .count();

        assertEquals(count, dynamicCount,
            "Expected exactly " + count + " DYNAMIC_IMPORT_FORBIDDEN findings (no early exit)");
    }

    // -------------------------------------------------------------------------
    // Property 17: Deterministic scan output
    // Feature: phase-c-node-runtime-execution, Property 17: Deterministic scan output
    // Validates: Requirement 7.8
    // -------------------------------------------------------------------------

    @Test
    void deterministicScanOutput_userAuth(@TempDir Path tempDir) throws IOException {
        assertDeterministicScanOutput("user-auth", 2, tempDir);
    }

    @Test
    void deterministicScanOutput_payment(@TempDir Path tempDir) throws IOException {
        assertDeterministicScanOutput("payment", 3, tempDir);
    }

    @Test
    void deterministicScanOutput_orderManager(@TempDir Path tempDir) throws IOException {
        assertDeterministicScanOutput("order-manager", 1, tempDir);
    }

    private void assertDeterministicScanOutput(String blockKey, int dynamicCount, Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));

        // Generate source with dynamic imports
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < dynamicCount; i++) {
            source.append("const mod").append(i).append(" = import('./module").append(i).append("');\n");
        }
        Files.writeString(blockRoot.resolve("index.ts"), source.toString());

        List<WiringManifest> manifests = List.of(makeManifest(blockKey));

        // Run scan twice
        List<BoundaryBypassFinding> findings1 = NodeImportContainmentScanner.scan(tempDir, manifests);
        List<BoundaryBypassFinding> findings2 = NodeImportContainmentScanner.scan(tempDir, manifests);

        // Results should be identical
        assertEquals(findings1.size(), findings2.size(), "Finding counts should match");
        for (int i = 0; i < findings1.size(); i++) {
            BoundaryBypassFinding f1 = findings1.get(i);
            BoundaryBypassFinding f2 = findings2.get(i);
            assertEquals(f1.rule(), f2.rule(), "Rules should match at index " + i);
            assertEquals(f1.path(), f2.path(), "Paths should match at index " + i);
            assertEquals(f1.detail(), f2.detail(), "Details should match at index " + i);
        }
    }

    // -------------------------------------------------------------------------
    // Property 22: Both static bypass and dynamic import reported independently
    // Feature: phase-c-node-runtime-execution, Property 22: Both static bypass and dynamic import reported independently
    // Validates: Requirement 5.5
    // -------------------------------------------------------------------------

    @Test
    void bothStaticBypassAndDynamicImportReportedIndependently_userAuthPayment(@TempDir Path tempDir) throws IOException {
        assertBothStaticBypassAndDynamicImportReportedIndependently("user-auth", "payment", tempDir);
    }

    @Test
    void bothStaticBypassAndDynamicImportReportedIndependently_paymentOrderManager(@TempDir Path tempDir) throws IOException {
        assertBothStaticBypassAndDynamicImportReportedIndependently("payment", "order-manager", tempDir);
    }

    @Test
    void bothStaticBypassAndDynamicImportReportedIndependently_inventoryAnalytics(@TempDir Path tempDir) throws IOException {
        assertBothStaticBypassAndDynamicImportReportedIndependently("inventory", "analytics", tempDir);
    }

    @Test
    void bothStaticBypassAndDynamicImportReportedIndependently_shippingBilling(@TempDir Path tempDir) throws IOException {
        assertBothStaticBypassAndDynamicImportReportedIndependently("shipping", "billing", tempDir);
    }

    private void assertBothStaticBypassAndDynamicImportReportedIndependently(String blockKey, String siblingKey, Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.createDirectories(tempDir.resolve("src/blocks/" + siblingKey));

        // File with both a static boundary bypass and a dynamic import
        String content = """
            import { X } from '../%s/service';
            const mod = import('./lazy-module');
            """.formatted(siblingKey);
        Files.writeString(tempDir.resolve("src/blocks/" + blockKey + "/index.ts"), content);

        List<WiringManifest> manifests = List.of(makeManifest(blockKey));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        boolean hasStaticBypass = findings.stream()
            .anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS"));
        boolean hasDynamicImport = findings.stream()
            .anyMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"));

        assertTrue(hasStaticBypass, "Expected BOUNDARY_BYPASS finding for static import");
        assertTrue(hasDynamicImport, "Expected DYNAMIC_IMPORT_FORBIDDEN finding for dynamic import");
    }

    // -------------------------------------------------------------------------
    // Additional tests: Edge cases for dynamic import detection
    // -------------------------------------------------------------------------

    @Test
    void dynamicImportWithDoubleQuotes(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.ts"), "const mod = import(\"./module\");\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN")),
            "Dynamic import with double quotes should be detected");
    }

    @Test
    void dynamicImportWithAwait(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.ts"), "const mod = await import('./module');\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN")),
            "Dynamic import with await should be detected");
    }

    @Test
    void dynamicImportInAsyncFunction(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        String content = """
            async function loadModule() {
                const mod = await import('./lazy');
                return mod;
            }
            """;
        Files.writeString(blockRoot.resolve("index.ts"), content);

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN")),
            "Dynamic import in async function should be detected");
    }

    @Test
    void multipleFilesWithDynamicImports(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("file1.ts"), "const a = import('./a');\n");
        Files.writeString(blockRoot.resolve("file2.ts"), "const b = import('./b');\n");
        Files.writeString(blockRoot.resolve("file3.ts"), "const c = import('./c');\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        long dynamicCount = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .count();

        assertEquals(3, dynamicCount, "Expected 3 DYNAMIC_IMPORT_FORBIDDEN findings across 3 files");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "Impl.ts",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
