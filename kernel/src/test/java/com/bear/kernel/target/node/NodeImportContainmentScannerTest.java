package com.bear.kernel.target.node;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeImportContainmentScannerTest {

    @Test
    void cleanProjectHasNoFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("user-authLogic.ts"), "// clean file\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty());
    }

    @Test
    void boundaryBypassDetected(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        // Import from sibling block — boundary bypass (../../ goes up from services/ to blocks/)
        Files.writeString(servicesDir.resolve("bad-service.ts"),
            "import { PaymentService } from '../../payment/services/payment-service';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")));
    }

    @Test
    void bareImportDetected(@TempDir Path tempDir) throws IOException {
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        // Bare package import from governed root
        Files.writeString(servicesDir.resolve("bad-service.ts"), "import _ from 'lodash';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BARE_PACKAGE_IMPORT")));
    }

    @Test
    void findingsIncludePathAndSpecifier(@TempDir Path tempDir) throws IOException {
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        Files.writeString(servicesDir.resolve("bad-service.ts"),
            "import { PaymentService } from '../../payment/services/payment-service';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        BoundaryBypassFinding finding = findings.get(0);
        assertTrue(finding.path().contains("bad-service.ts"));
        assertTrue(finding.detail().contains("../payment/services/payment-service"));
    }

    @Test
    void multipleViolationsCollected(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        Files.writeString(servicesDir.resolve("bad-service1.ts"),
            "import { A } from '../../payment/services/a';\n");
        Files.writeString(servicesDir.resolve("bad-service2.ts"),
            "import { B } from '../../payment/services/b';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertEquals(2, findings.size());
    }

    @Test
    void testFilesExcludedFromScan(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));

        // .test.ts files should be excluded
        Files.writeString(blockRoot.resolve("user-auth.test.ts"),
            "import _ from 'lodash';\n");
        // Regular .ts file with clean import
        Files.writeString(blockRoot.resolve("user-auth.ts"), "// clean\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "test files should not be scanned");
    }

    // --- Dynamic import enforcement tests (Phase C) ---

    @Test
    void dynamicImportProducesFinding(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.ts"),
            "const mod = import('./other');\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN")));
    }

    @Test
    void noDynamicImportsNoFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        // Only static imports, no dynamic import()
        Files.writeString(blockRoot.resolve("index.ts"),
            "import { x } from './utils';\nconst y = 1;\n");
        Files.writeString(blockRoot.resolve("utils.ts"), "export const x = 1;\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.stream().noneMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN")),
            "No DYNAMIC_IMPORT_FORBIDDEN findings expected for clean files");
    }

    @Test
    void multipleDynamicImportsProduceMultipleFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("index.ts"),
            """
            const a = import('./a');
            const b = import("./b");
            const c = import('./c');
            """);

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        long dynamicCount = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .count();
        assertEquals(3, dynamicCount, "Expected 3 DYNAMIC_IMPORT_FORBIDDEN findings");
    }

    @Test
    void staticBypassAndDynamicImportBothReported(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        Path blockRoot = tempDir.resolve("src/blocks/user-auth");

        // File with both a static boundary bypass and a dynamic import
        Files.writeString(blockRoot.resolve("index.ts"),
            """
            import { PaymentService } from '../payment/service';
            const mod = import('./lazy-module');
            """);

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")),
            "Expected BOUNDARY_BYPASS finding for static import");
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN")),
            "Expected DYNAMIC_IMPORT_FORBIDDEN finding for dynamic import");
    }

    @Test
    void dynamicImportFindingIncludesPathAndSpecifier(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("loader.ts"),
            "const mod = import('./my-module');\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        BoundaryBypassFinding finding = findings.stream()
            .filter(f -> f.rule().equals("DYNAMIC_IMPORT_FORBIDDEN"))
            .findFirst()
            .orElseThrow();

        // Path should be repo-relative
        assertTrue(finding.path().contains("loader.ts"), "Path should contain filename");
        assertTrue(finding.path().startsWith("src/blocks/"), "Path should be repo-relative");
        // Detail should contain the specifier
        assertTrue(finding.detail().contains("./my-module"), "Detail should contain specifier");
    }

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
