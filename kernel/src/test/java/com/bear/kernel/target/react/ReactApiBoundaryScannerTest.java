package com.bear.kernel.target.react;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReactApiBoundaryScanner.
 * Covers all 6 acceptance criteria from Requirement 12: API Boundary Signaling.
 */
class ReactApiBoundaryScannerTest {

    private final ReactApiBoundaryScanner scanner = new ReactApiBoundaryScanner();

    @TempDir
    Path projectRoot;

    private WiringManifest userDashboardManifest;

    @BeforeEach
    void setUp() throws IOException {
        // Create basic project structure
        Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/impl"));
        Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/components"));
        Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/services"));
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
            List.of("src/features/user-dashboard"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    // --- Requirement 12: API Boundary Signaling ---

    @Nested
    class Requirement12_ApiBoundarySignaling {

        // 12.1: Governed .tsx file with fetch( reports BOUNDARY_BYPASS
        @Test
        void reportsFetchCallInTsxFile() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/UserList.tsx"),
                """
                import React, { useEffect, useState } from 'react';
                
                export function UserList() {
                    const [users, setUsers] = useState([]);
                    
                    useEffect(() => {
                        const data = await fetch('/api/users');
                        setUsers(await data.json());
                    }, []);
                    
                    return <ul>{users.map(u => <li key={u.id}>{u.name}</li>)}</ul>;
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("fetch("));
            assertTrue(findings.get(0).path().contains("UserList.tsx"));
        }

        // 12.2: Governed .tsx file with new XMLHttpRequest( reports BOUNDARY_BYPASS
        @Test
        void reportsXmlHttpRequestInTsxFile() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/DataLoader.tsx"),
                """
                import React from 'react';
                
                export function DataLoader() {
                    const loadData = () => {
                        const xhr = new XMLHttpRequest();
                        xhr.open('GET', '/api/data');
                        xhr.send();
                    };
                    
                    return <button onClick={loadData}>Load</button>;
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
            assertTrue(findings.get(0).detail().contains("new XMLHttpRequest("));
            assertTrue(findings.get(0).path().contains("DataLoader.tsx"));
        }

        // 12.3: Governed .ts file (non-component) with fetch/XHR is NOT reported
        @Test
        void doesNotReportFetchInTsServiceFile() throws IOException {
            // .ts files are service/logic files, not components — excluded from API boundary scanning
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/services/UserService.ts"),
                """
                export class UserService {
                    async getUsers() {
                        const response = await fetch('/api/users');
                        return response.json();
                    }
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), ".ts service files should not be scanned for API boundary violations");
        }

        // 12.3 (continued): Governed .ts file with XMLHttpRequest is NOT reported
        @Test
        void doesNotReportXhrInTsApiFile() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/services/ApiClient.ts"),
                """
                export class ApiClient {
                    request(url: string) {
                        const xhr = new XMLHttpRequest();
                        xhr.open('GET', url);
                        xhr.send();
                    }
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), ".ts API files should not be scanned for API boundary violations");
        }

        // 12.4: Governed .tsx file without fetch/XHR reports no findings
        @Test
        void noFindingsForCleanTsxFile() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.tsx"),
                """
                import React from 'react';
                
                export function Button({ onClick, children }) {
                    return <button onClick={onClick}>{children}</button>;
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Clean .tsx file should have no findings");
        }

        // 12.5: Exit code 7 is handled by caller (ReactTarget.scanBoundaryBypass)
        // This test verifies the finding structure is correct for exit code mapping
        @Test
        void findingHasCorrectCodeForExitMapping() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "const data = await fetch('/api/users');"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            // BOUNDARY_BYPASS code maps to exit 7 in the app layer
            assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
        }

        // 12.6: Finding includes repo-relative file path and detected call pattern
        @Test
        void findingIncludesPathAndCallPattern() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/DataFetcher.tsx"),
                """
                export function DataFetcher() {
                    fetch('/api/data');
                    return null;
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(1, findings.size());
            // Path should be repo-relative
            assertEquals("src/features/user-dashboard/components/DataFetcher.tsx", findings.get(0).path());
            // Detail should contain the detected call pattern
            assertEquals("fetch(", findings.get(0).detail());
        }
    }

    // --- Test file exclusion ---

    @Nested
    class TestFileExclusion {

        // 5.3: Exclude test files (*.test.tsx, *.spec.tsx) from scanning
        @Test
        void excludesTestTsxFiles() throws IOException {
            // Clean component file
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.tsx"),
                "export const Button = () => <button>Click</button>;"
            );
            // Test file with fetch — should be excluded
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.test.tsx"),
                """
                import { render } from '@testing-library/react';
                test('fetches data', async () => {
                    const data = await fetch('/api/test');
                });
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Test files should be excluded from API boundary scanning");
        }

        @Test
        void excludesSpecTsxFiles() throws IOException {
            // Clean component file
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Card.tsx"),
                "export const Card = () => <div>Card</div>;"
            );
            // Spec file with XMLHttpRequest — should be excluded
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Card.spec.tsx"),
                """
                describe('Card', () => {
                    it('loads data', () => {
                        const xhr = new XMLHttpRequest();
                    });
                });
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty(), "Spec files should be excluded from API boundary scanning");
        }
    }

    // --- Multiple findings ---

    @Nested
    class MultipleFindings {

        @Test
        void reportsBothFetchAndXhrInSameFile() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/LegacyComponent.tsx"),
                """
                export function LegacyComponent() {
                    // Modern approach
                    fetch('/api/modern');
                    
                    // Legacy approach
                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', '/api/legacy');
                    
                    return null;
                }
                """
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(2, findings.size());
            assertTrue(findings.stream().anyMatch(f -> f.detail().contains("fetch(")));
            assertTrue(findings.stream().anyMatch(f -> f.detail().contains("new XMLHttpRequest(")));
        }

        @Test
        void collectsFindingsFromMultipleFiles() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/UserList.tsx"),
                "const users = await fetch('/api/users');"
            );
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/ProductList.tsx"),
                "const products = await fetch('/api/products');"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(2, findings.size());
        }

        @Test
        void sortsFindingsByPath() throws IOException {
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx"),
                "fetch('/api/impl');"
            );
            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/Button.tsx"),
                "fetch('/api/button');"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertEquals(2, findings.size());
            // Should be sorted alphabetically by path
            assertTrue(findings.get(0).path().contains("components/Button.tsx"));
            assertTrue(findings.get(1).path().contains("impl/UserDashboardFeatureImpl.tsx"));
        }
    }

    // --- Edge cases ---

    @Nested
    class EdgeCases {

        @Test
        void handlesEmptyFeatureRoot() throws IOException {
            // Feature root exists but has no files
            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(userDashboardManifest));

            assertTrue(findings.isEmpty());
        }

        @Test
        void handlesNonExistentFeatureRoot() throws IOException {
            WiringManifest nonExistentManifest = new WiringManifest(
                "v3",
                "non-existent-feature",
                "NonExistentFeatureWrapper",
                "NonExistentFeatureLogic",
                "NonExistentFeatureImpl",
                "src/features/non-existent-feature/impl/NonExistentFeatureImpl.tsx",
                "src/features/non-existent-feature",
                List.of("src/features/non-existent-feature"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            );

            List<BoundaryBypassFinding> findings = scanner.scan(projectRoot, List.of(nonExistentManifest));

            assertTrue(findings.isEmpty());
        }

        @Test
        void handlesMultipleBlocks() throws IOException {
            // Create second feature
            Files.createDirectories(projectRoot.resolve("src/features/product-catalog/components"));
            
            WiringManifest productManifest = new WiringManifest(
                "v3",
                "product-catalog",
                "ProductCatalogFeatureWrapper",
                "ProductCatalogFeatureLogic",
                "ProductCatalogFeatureImpl",
                "src/features/product-catalog/impl/ProductCatalogFeatureImpl.tsx",
                "src/features/product-catalog",
                List.of("src/features/product-catalog"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            );

            Files.writeString(
                projectRoot.resolve("src/features/user-dashboard/components/UserList.tsx"),
                "fetch('/api/users');"
            );
            Files.writeString(
                projectRoot.resolve("src/features/product-catalog/components/ProductList.tsx"),
                "fetch('/api/products');"
            );

            List<BoundaryBypassFinding> findings = scanner.scan(
                projectRoot, 
                List.of(userDashboardManifest, productManifest)
            );

            assertEquals(2, findings.size());
        }
    }

    // --- Fixture-based test ---

    @Nested
    class FixtureBasedTests {

        @Test
        void scansBoundaryBypassFetchFixture() throws IOException, URISyntaxException {
            // Use classloader to find fixture in test resources
            Path fixtureRoot = Path.of(
                getClass().getClassLoader()
                    .getResource("fixtures/react/boundary-bypass-fetch")
                    .toURI()
            );

            WiringManifest manifest = new WiringManifest(
                "v3",
                "user-dashboard",
                "UserDashboardFeatureWrapper",
                "UserDashboardFeatureLogic",
                "UserDashboardFeatureImpl",
                "src/features/user-dashboard/impl/UserDashboardFeatureImpl.tsx",
                "src/features/user-dashboard",
                List.of("src/features/user-dashboard"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            );

            List<BoundaryBypassFinding> findings = scanner.scan(fixtureRoot, List.of(manifest));

            assertFalse(findings.isEmpty(), "boundary-bypass-fetch fixture should have findings");
            assertTrue(findings.stream().anyMatch(f -> f.detail().contains("fetch(")));
        }
    }
}
