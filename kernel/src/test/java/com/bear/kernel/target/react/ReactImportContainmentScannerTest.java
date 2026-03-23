package com.bear.kernel.target.react;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReactImportContainmentScanner.
 * Covers all 12 acceptance criteria from Requirement 7 + Requirement 8.
 */
class ReactImportContainmentScannerTest {

    private final ReactImportContainmentScanner scanner = new ReactImportContainmentScanner();

    @TempDir
    Path projectRoot;

    private WiringManifest userDashboardManifest;

    @BeforeEach
    void setUp() throws IOException {
        // Create basic project structure
        Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/impl"));
        Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/components"));
        Files.createDirectories(projectRoot.resolve("src/shared/utils"));
        Files.createDirectories(projectRoot.resolve("build/generated/bear/types/user-dashboard"));
        Files.createDirectories(projectRoot.resolve("build/generated/bear/wiring"));

        // Create wiring manifest
        userDashboardManifest = new WiringManifest(
            "v3",
            "user-dashboard",
            "UserDashboardFeatureWrapper",
            "UserDashboardFeatureLogic",
            "UserDashboardFeatureImpl",
            "src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx",
            "src/features/user-dashboard",
            List.of("src/features/user-dashboard", "src/shared"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    // --- Requirement 7: Import Containment Enforcement ---

    @Nested
    class Requirement7_ImportContainment {

        // 7.1: Import within same feature root passes
        @Test
        void passesImportWithinSameFeatureRoot() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { Button } from '../components/Button';"
            );
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.tsx"),
                "export const Button = () => <button>Click</button>;"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Intra-feature import should pass");
        }

        // 7.2: Import from src/shared/ passes
        @Test
        void passesImportFromShared() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { formatDate } from '../../../shared/utils/formatDate';"
            );
            Files.writeString(
                projectRoot.resolve("src/shared/utils/formatDate.ts"),
                "export function formatDate(d: Date) { return d.toISOString(); }"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Shared import should pass");
        }

        // 7.3: Import of BEAR-generated companion passes
        @Test
        void passesImportFromGeneratedBear() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import type { UserDashboardFeaturePorts } from '../../../build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts';"
            );
            Files.writeString(
                projectRoot.resolve("build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts.ts"),
                "export interface UserDashboardFeaturePorts {}"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Generated BEAR import should pass");
        }

        // 7.4: Import of react passes
        @Test
        void passesReactImport() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import React from 'react';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "React import should pass");
        }

        // 7.5: Import of react-dom passes
        @Test
        void passesReactDomImport() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { createRoot } from 'react-dom';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "React-dom import should pass");
        }

        // 7.6: Relative import escaping feature root fails
        @Test
        void failsRelativeImportEscapingFeatureRoot() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { something } from '../../../../outside/module';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("outside/module"));
        }

        // 7.7: Import reaching sibling feature root fails
        @Test
        void failsImportToSiblingFeature() throws IOException {
            Files.createDirectories(projectRoot.resolve("src/features/product-catalog"));
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { ProductCard } from '../../product-catalog/ProductCard';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("product-catalog"));
        }

        // 7.8: Bare package import other than react/react-dom fails
        @Test
        void failsBarePackageImport() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import _ from 'lodash';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("lodash"));
        }

        // 7.9: # alias import fails
        @Test
        void failsHashAliasImport() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { helper } from '#utils/helper';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("Subpath imports"));
        }

        // 7.10: URL-like specifier fails
        @Test
        void failsUrlImport() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { lib } from 'https://cdn.example.com/lib.js';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("URL imports"));
        }

        // 7.11: src/shared/ file importing from feature root fails
        @Test
        void failsSharedImportingFromFeatureRoot() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/shared/utils/helper.ts"),
                "import { UserDashboardFeatureImpl } from '../../features/user-dashboard/impl/UserDashboardFeatureImpl';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("shared code cannot import from feature roots"));
        }

        // 7.12: Finding includes repo-relative path and import specifier
        @Test
        void findingIncludesPathAndSpecifier() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import axios from 'axios';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertTrue(findings.get(0).path().contains("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"));
            assertTrue(findings.get(0).detail().contains("axios"));
            assertTrue(findings.get(0).detail().contains("line"));
        }
    }

    // --- Requirement 8: @/* Path Alias Resolution (Next.js) ---

    @Nested
    class Requirement8_NextJsAlias {

        @BeforeEach
        void setUpNextJs() throws IOException {
            // Create next.config.js to trigger Next.js sub-profile
            Files.writeString(projectRoot.resolve("next.config.js"), "module.exports = {};");
        }

        // 8.1: @/ specifier resolved by replacing @/ with ./src/
        @Test
        void resolvesAtAliasToSrc() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { Button } from '@/features/user-dashboard/components/Button';"
            );
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.tsx"),
                "export const Button = () => <button>Click</button>;"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "@/ alias within same feature should pass");
        }

        // 8.2: @/ import within same feature root passes
        @Test
        void passesAtAliasWithinSameFeature() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                """
                import { Button } from '@/features/user-dashboard/components/Button';
                import { useAuth } from '@/features/user-dashboard/hooks/useAuth';
                """
            );
            Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/hooks"));

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty());
        }

        // 8.3: @/ import to sibling feature fails
        @Test
        void failsAtAliasToSiblingFeature() throws IOException {
            Files.createDirectories(projectRoot.resolve("src/features/product-catalog"));
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { ProductCard } from '@/features/product-catalog/ProductCard';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
        }

        // 8.4: @/ import outside governed roots fails
        @Test
        void failsAtAliasOutsideGovernedRoots() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { Layout } from '@/app/layout';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
        }

        // 8.5: @/ in vite-react sub-profile fails
        @Test
        void failsAtAliasInViteReact() throws IOException {
            // Remove next.config.js to switch to Vite sub-profile
            Files.deleteIfExists(projectRoot.resolve("next.config.js"));
            Files.writeString(projectRoot.resolve("vite.config.ts"), "export default {};");

            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { Button } from '@/features/user-dashboard/components/Button';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("vite-react"));
        }

        // 8.6: @/ to shared passes in Next.js
        @Test
        void passesAtAliasToShared() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import { formatDate } from '@/shared/utils/formatDate';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty());
        }
    }

    // --- Directive detection ---

    @Nested
    class DirectiveDetection {

        @Test
        void detectsUseClientDirective() {
            String content = """
                "use client";
                import React from 'react';
                """;

            String directive = scanner.detectDirective(content);

            assertEquals("client", directive);
        }

        @Test
        void detectsUseServerDirective() {
            String content = """
                "use server";
                export async function serverAction() {}
                """;

            String directive = scanner.detectDirective(content);

            assertEquals("server", directive);
        }

        @Test
        void detectsDirectiveWithSingleQuotes() {
            String content = """
                'use client';
                import React from 'react';
                """;

            String directive = scanner.detectDirective(content);

            assertEquals("client", directive);
        }

        @Test
        void returnsNullWhenNoDirective() {
            String content = """
                import React from 'react';
                export function Component() {}
                """;

            String directive = scanner.detectDirective(content);

            assertNull(directive);
        }

        @Test
        void detectsDirectiveAfterComments() {
            String content = """
                // This is a comment
                /* Multi-line
                   comment */
                "use client";
                import React from 'react';
                """;

            String directive = scanner.detectDirective(content);

            assertEquals("client", directive);
        }
    }

    // --- Sub-profile detection ---

    @Nested
    class SubProfileDetection {

        @Test
        void detectsNextJsWithNextConfigJs() throws IOException {
            Files.writeString(projectRoot.resolve("next.config.js"), "module.exports = {};");

            ReactProjectShape subProfile = scanner.detectSubProfile(projectRoot);

            assertEquals(ReactProjectShape.NEXTJS_APP_ROUTER, subProfile);
        }

        @Test
        void detectsNextJsWithNextConfigMjs() throws IOException {
            Files.writeString(projectRoot.resolve("next.config.mjs"), "export default {};");

            ReactProjectShape subProfile = scanner.detectSubProfile(projectRoot);

            assertEquals(ReactProjectShape.NEXTJS_APP_ROUTER, subProfile);
        }

        @Test
        void detectsNextJsWithNextConfigTs() throws IOException {
            Files.writeString(projectRoot.resolve("next.config.ts"), "export default {};");

            ReactProjectShape subProfile = scanner.detectSubProfile(projectRoot);

            assertEquals(ReactProjectShape.NEXTJS_APP_ROUTER, subProfile);
        }

        @Test
        void detectsViteReactWithoutNextConfig() throws IOException {
            Files.writeString(projectRoot.resolve("vite.config.ts"), "export default {};");

            ReactProjectShape subProfile = scanner.detectSubProfile(projectRoot);

            assertEquals(ReactProjectShape.VITE_REACT, subProfile);
        }
    }

    // --- Test file exclusion ---

    @Nested
    class TestFileExclusion {

        @Test
        void excludesTestTsFiles() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import React from 'react';"
            );
            // This test file has a violation but should be excluded
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.test.ts"),
                "import axios from 'axios';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Test files should be excluded from scanning");
        }

        @Test
        void excludesSpecTsxFiles() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import React from 'react';"
            );
            // This spec file has a violation but should be excluded
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.spec.tsx"),
                "import lodash from 'lodash';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Spec files should be excluded from scanning");
        }
    }

    // --- Multiple findings ---

    @Nested
    class MultipleFindings {

        @Test
        void collectsMultipleFindingsFromSameFile() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                """
                import axios from 'axios';
                import lodash from 'lodash';
                import { something } from 'https://cdn.example.com/lib.js';
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(3, findings.size());
        }

        @Test
        void sortsFindingsByPath() throws IOException {
            Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/components"));
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.tsx"),
                "import axios from 'axios';"
            );
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "import lodash from 'lodash';"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(2, findings.size());
            // Should be sorted alphabetically by path
            assertTrue(findings.get(0).path().contains("components/Button.tsx"));
            assertTrue(findings.get(1).path().contains("impl/UserDashboardFeatureImpl.tsx"));
        }
    }
}
