package com.bear.kernel.target.react;

import com.bear.kernel.target.react.ReactPrCheckContributor.DependencyDeltaFinding;
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
 * Tests for ReactPrCheckContributor.
 * Covers all 5 acceptance criteria from Requirement 15: Dependency Governance (pr-check).
 */
class ReactPrCheckContributorTest {

    private final ReactPrCheckContributor contributor = new ReactPrCheckContributor();

    @TempDir
    Path tempDir;

    private Path baseRoot;
    private Path headRoot;

    @BeforeEach
    void setUp() throws IOException {
        baseRoot = tempDir.resolve("base");
        headRoot = tempDir.resolve("head");
        Files.createDirectories(baseRoot);
        Files.createDirectories(headRoot);
    }

    // --- Requirement 15: Dependency Governance (pr-check) ---

    @Nested
    class Requirement15_DependencyGovernance {

        // 15.1: WHEN package.json dependencies or devDependencies differ between base and head,
        //       THE ReactPrCheckContributor SHALL classify the delta as BOUNDARY_EXPANDING.
        @Test
        void classifiesPackageJsonDiffAsBoundaryExpanding() throws IOException {
            // Base: no axios
            Files.writeString(baseRoot.resolve("package.json"), """
                {
                  "name": "test-project",
                  "dependencies": {
                    "react": "^18.2.0"
                  }
                }
                """);

            // Head: axios added
            Files.writeString(headRoot.resolve("package.json"), """
                {
                  "name": "test-project",
                  "dependencies": {
                    "react": "^18.2.0",
                    "axios": "^1.0.0"
                  }
                }
                """);

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_EXPANDING", findings.get(0).code());
            assertEquals("package.json", findings.get(0).path());
            assertEquals("dependency governance: file changed", findings.get(0).detail());
        }

        // 15.2: WHEN pnpm-lock.yaml differs between base and head,
        //       THE ReactPrCheckContributor SHALL classify the delta as BOUNDARY_EXPANDING.
        @Test
        void classifiesPnpmLockDiffAsBoundaryExpanding() throws IOException {
            // Base: original lock
            Files.writeString(baseRoot.resolve("pnpm-lock.yaml"), """
                lockfileVersion: '6.0'
                packages:
                  react@18.2.0:
                    resolution: {integrity: sha512-abc}
                """);

            // Head: updated lock
            Files.writeString(headRoot.resolve("pnpm-lock.yaml"), """
                lockfileVersion: '6.0'
                packages:
                  react@18.2.0:
                    resolution: {integrity: sha512-abc}
                  axios@1.0.0:
                    resolution: {integrity: sha512-xyz}
                """);

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_EXPANDING", findings.get(0).code());
            assertEquals("pnpm-lock.yaml", findings.get(0).path());
        }

        // 15.3: WHEN neither package.json nor pnpm-lock.yaml differ between base and head,
        //       THE ReactPrCheckContributor SHALL report no boundary-expanding changes.
        @Test
        void noFindingsWhenFilesIdentical() throws IOException {
            String packageJson = """
                {
                  "name": "test-project",
                  "dependencies": {
                    "react": "^18.2.0"
                  }
                }
                """;
            String pnpmLock = """
                lockfileVersion: '6.0'
                packages:
                  react@18.2.0:
                    resolution: {integrity: sha512-abc}
                """;

            Files.writeString(baseRoot.resolve("package.json"), packageJson);
            Files.writeString(headRoot.resolve("package.json"), packageJson);
            Files.writeString(baseRoot.resolve("pnpm-lock.yaml"), pnpmLock);
            Files.writeString(headRoot.resolve("pnpm-lock.yaml"), pnpmLock);

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertTrue(findings.isEmpty(), "No findings expected when files are identical");
        }

        // 15.4: THE ReactPrCheckContributor SHALL produce exit 5 when boundary-expanding
        //       dependency changes are detected.
        // Note: Exit code mapping is handled by app/ layer. This test verifies the finding
        // structure is correct for exit code mapping.
        @Test
        void findingHasCorrectCodeForExitMapping() throws IOException {
            Files.writeString(baseRoot.resolve("package.json"), "{}");
            Files.writeString(headRoot.resolve("package.json"), "{\"dependencies\":{}}");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(1, findings.size());
            // BOUNDARY_EXPANDING code maps to exit 5 in the app layer
            assertEquals("BOUNDARY_EXPANDING", findings.get(0).code());
        }

        // 15.5: THE ReactPrCheckContributor SHALL include the changed file path in each finding.
        @Test
        void findingIncludesChangedFilePath() throws IOException {
            Files.writeString(baseRoot.resolve("package.json"), "{}");
            Files.writeString(headRoot.resolve("package.json"), "{\"name\":\"changed\"}");
            Files.writeString(baseRoot.resolve("pnpm-lock.yaml"), "lockfileVersion: '6.0'");
            Files.writeString(headRoot.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(2, findings.size());
            assertTrue(findings.stream().anyMatch(f -> f.path().equals("package.json")));
            assertTrue(findings.stream().anyMatch(f -> f.path().equals("pnpm-lock.yaml")));
        }
    }

    // --- Additional edge cases ---

    @Nested
    class EdgeCases {

        @Test
        void detectsNewPackageJsonInHead() throws IOException {
            // Base: no package.json
            // Head: has package.json
            Files.writeString(headRoot.resolve("package.json"), "{}");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_EXPANDING", findings.get(0).code());
            assertEquals("package.json", findings.get(0).path());
        }

        @Test
        void detectsNewPnpmLockInHead() throws IOException {
            // Base: no pnpm-lock.yaml
            // Head: has pnpm-lock.yaml
            Files.writeString(headRoot.resolve("pnpm-lock.yaml"), "lockfileVersion: '6.0'");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(1, findings.size());
            assertEquals("BOUNDARY_EXPANDING", findings.get(0).code());
            assertEquals("pnpm-lock.yaml", findings.get(0).path());
        }

        @Test
        void noFindingWhenFileRemovedInHead() throws IOException {
            // Base: has package.json
            // Head: no package.json (removed)
            Files.writeString(baseRoot.resolve("package.json"), "{}");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            // File removal is not boundary-expanding
            assertTrue(findings.isEmpty());
        }

        @Test
        void noFindingWhenNeitherSnapshotHasFile() throws IOException {
            // Neither base nor head has package.json or pnpm-lock.yaml
            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertTrue(findings.isEmpty());
        }

        @Test
        void detectsBothFilesChanged() throws IOException {
            Files.writeString(baseRoot.resolve("package.json"), "{}");
            Files.writeString(headRoot.resolve("package.json"), "{\"name\":\"changed\"}");
            Files.writeString(baseRoot.resolve("pnpm-lock.yaml"), "v1");
            Files.writeString(headRoot.resolve("pnpm-lock.yaml"), "v2");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertEquals(2, findings.size());
        }

        @Test
        void handlesEmptyFiles() throws IOException {
            Files.writeString(baseRoot.resolve("package.json"), "");
            Files.writeString(headRoot.resolve("package.json"), "");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            assertTrue(findings.isEmpty(), "Identical empty files should not produce findings");
        }

        @Test
        void detectsWhitespaceOnlyDifference() throws IOException {
            Files.writeString(baseRoot.resolve("package.json"), "{}");
            Files.writeString(headRoot.resolve("package.json"), "{}\n");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(baseRoot, headRoot);

            // Byte-level comparison means whitespace differences are detected
            assertEquals(1, findings.size());
        }
    }

    // --- Fixture-based test ---

    @Nested
    class FixtureBasedTests {

        @Test
        void computesDeltaForBoundaryExpandingFixture() throws Exception {
            // Use classloader to find fixture in test resources
            Path fixtureRoot = Path.of(
                getClass().getClassLoader()
                    .getResource("fixtures/react/check-pr-check-boundary-expanding")
                    .toURI()
            );

            Path fixtureBase = fixtureRoot.resolve("base");
            Path fixtureHead = fixtureRoot.resolve("head");

            List<DependencyDeltaFinding> findings = contributor.computeDelta(fixtureBase, fixtureHead);

            assertFalse(findings.isEmpty(), "check-pr-check-boundary-expanding fixture should have findings");
            assertTrue(findings.stream().anyMatch(f -> f.code().equals("BOUNDARY_EXPANDING")));
        }
    }
}
