package com.bear.kernel.target.react;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReactImportBoundaryResolver.
 * Covers all specifier types (relative, bare, @/, #, URL-like) and both sub-profiles.
 */
class ReactImportBoundaryResolverTest {

    private final ReactImportBoundaryResolver resolver = new ReactImportBoundaryResolver();

    @TempDir
    Path projectRoot;

    private Path featureRoot;
    private Path sharedRoot;
    private Path generatedRoot;
    private Path importingFile;

    @BeforeEach
    void setUp() throws IOException {
        // Set up project structure
        featureRoot = projectRoot.resolve("src/features/user-dashboard");
        sharedRoot = projectRoot.resolve("src/shared");
        generatedRoot = projectRoot.resolve("build/generated/bear");

        Files.createDirectories(featureRoot.resolve("impl"));
        Files.createDirectories(featureRoot.resolve("components"));
        Files.createDirectories(sharedRoot.resolve("utils"));
        Files.createDirectories(generatedRoot.resolve("types/user-dashboard"));
        Files.createDirectories(projectRoot.resolve("src/features/product-catalog"));

        importingFile = featureRoot.resolve("impl/UserDashboardFeatureImpl.tsx");
        Files.writeString(importingFile, "// placeholder");
    }

    // --- Relative imports ---

    @Nested
    class RelativeImports {

        @Test
        void allowsImportWithinSameFeatureRoot() {
            var decision = resolver.resolve(
                importingFile,
                "../components/Button",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void allowsImportFromSharedRoot() {
            var decision = resolver.resolve(
                importingFile,
                "../../../shared/utils/helpers",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void allowsImportFromGeneratedRoot() {
            var decision = resolver.resolve(
                importingFile,
                "../../../../build/generated/bear/types/user-dashboard/UserDashboardFeaturePorts",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void failsImportEscapingFeatureRoot() {
            var decision = resolver.resolve(
                importingFile,
                "../../../outside/module",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }

        @Test
        void failsImportToSiblingFeature() {
            var decision = resolver.resolve(
                importingFile,
                "../../product-catalog/SomeComponent",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }

        @Test
        void allowsCurrentDirectoryImport() {
            var decision = resolver.resolve(
                importingFile,
                "./helper",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }
    }

    // --- Bare imports ---

    @Nested
    class BareImports {

        @Test
        void allowsReactImport() {
            var decision = resolver.resolve(
                importingFile,
                "react",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void allowsReactDomImport() {
            var decision = resolver.resolve(
                importingFile,
                "react-dom",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void failsLodashImport() {
            var decision = resolver.resolve(
                importingFile,
                "lodash",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
            assertTrue(decision.reason().contains("lodash"));
        }

        @Test
        void failsAxiosImport() {
            var decision = resolver.resolve(
                importingFile,
                "axios",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }

        @Test
        void failsSubpathBareImport() {
            var decision = resolver.resolve(
                importingFile,
                "lodash/debounce",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }

        @Test
        void failsScopedPackageImport() {
            var decision = resolver.resolve(
                importingFile,
                "@tanstack/react-query",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }
    }

    // --- @/* alias imports (Next.js) ---

    @Nested
    class NextJsAliasImports {

        @Test
        void allowsAliasWithinSameFeatureInNextJs() throws IOException {
            // Create the target file
            Files.createDirectories(projectRoot.resolve("src/features/user-dashboard/components"));

            var decision = resolver.resolve(
                importingFile,
                "@/features/user-dashboard/components/Button",
                featureRoot,
                projectRoot,
                ReactProjectShape.NEXTJS_APP_ROUTER,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void allowsAliasToSharedInNextJs() {
            var decision = resolver.resolve(
                importingFile,
                "@/shared/utils/helpers",
                featureRoot,
                projectRoot,
                ReactProjectShape.NEXTJS_APP_ROUTER,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void failsAliasToSiblingFeatureInNextJs() {
            var decision = resolver.resolve(
                importingFile,
                "@/features/product-catalog/SomeComponent",
                featureRoot,
                projectRoot,
                ReactProjectShape.NEXTJS_APP_ROUTER,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }

        @Test
        void failsAliasOutsideGovernedRootsInNextJs() {
            var decision = resolver.resolve(
                importingFile,
                "@/app/layout",
                featureRoot,
                projectRoot,
                ReactProjectShape.NEXTJS_APP_ROUTER,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }

        @Test
        void failsAliasInViteReact() {
            var decision = resolver.resolve(
                importingFile,
                "@/features/user-dashboard/components/Button",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
            assertTrue(decision.reason().contains("vite-react"));
        }
    }

    // --- # subpath imports ---

    @Nested
    class SubpathImports {

        @Test
        void failsHashImportInViteReact() {
            var decision = resolver.resolve(
                importingFile,
                "#utils/helpers",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
            assertTrue(decision.reason().contains("Subpath imports"));
        }

        @Test
        void failsHashImportInNextJs() {
            var decision = resolver.resolve(
                importingFile,
                "#components/Button",
                featureRoot,
                projectRoot,
                ReactProjectShape.NEXTJS_APP_ROUTER,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }
    }

    // --- URL-like imports ---

    @Nested
    class UrlImports {

        @Test
        void failsHttpImport() {
            var decision = resolver.resolve(
                importingFile,
                "http://example.com/module.js",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
            assertTrue(decision.reason().contains("URL imports"));
        }

        @Test
        void failsHttpsImport() {
            var decision = resolver.resolve(
                importingFile,
                "https://cdn.example.com/lib.js",
                featureRoot,
                projectRoot,
                ReactProjectShape.NEXTJS_APP_ROUTER,
                sharedRoot,
                generatedRoot
            );

            assertFalse(decision.isAllowed());
            assertEquals("BOUNDARY_BYPASS", decision.code());
        }
    }

    // --- Edge cases ---

    @Nested
    class EdgeCases {

        @Test
        void handlesNullSharedRoot() {
            var decision = resolver.resolve(
                importingFile,
                "./helper",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                null, // no shared root
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void handlesNullGeneratedRoot() {
            var decision = resolver.resolve(
                importingFile,
                "./helper",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                null // no generated root
            );

            assertTrue(decision.isAllowed());
        }

        @Test
        void allowsReactDomClientSubpath() {
            // react-dom/client is a common import pattern
            var decision = resolver.resolve(
                importingFile,
                "react-dom/client",
                featureRoot,
                projectRoot,
                ReactProjectShape.VITE_REACT,
                sharedRoot,
                generatedRoot
            );

            assertTrue(decision.isAllowed());
        }
    }
}
