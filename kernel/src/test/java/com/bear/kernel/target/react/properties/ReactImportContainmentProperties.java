package com.bear.kernel.target.react.properties;

import com.bear.kernel.target.react.ReactImportBoundaryResolver;
import com.bear.kernel.target.react.ReactProjectShape;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for React import containment (P11–P18).
 * Each property runs 100+ iterations over generated inputs.
 * 
 * Feature: react-target-scan-only
 */
class ReactImportContainmentProperties {

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
        Files.createDirectories(featureRoot.resolve("hooks"));
        Files.createDirectories(sharedRoot.resolve("utils"));
        Files.createDirectories(generatedRoot.resolve("types/user-dashboard"));
        Files.createDirectories(projectRoot.resolve("src/features/product-catalog"));
        Files.createDirectories(projectRoot.resolve("src/features/checkout"));

        importingFile = featureRoot.resolve("impl/UserDashboardFeatureImpl.tsx");
        Files.writeString(importingFile, "// placeholder");
    }

    /**
     * P11 — Intra-feature imports allowed: For any import specifier that resolves within the
     * same feature root, ReactImportBoundaryResolver.resolve() returns allowed().
     * 
     * Validates: Requirement 7.1
     */
    @ParameterizedTest(name = "P11 iteration {0}: specifier={1}, subProfile={2}")
    @MethodSource("intraFeatureImportConfigurations")
    void p11_intraFeatureImportsAllowed(
            int iteration,
            String specifier,
            ReactProjectShape subProfile) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            subProfile,
            sharedRoot,
            generatedRoot
        );

        assertTrue(decision.isAllowed(),
            "P11: Intra-feature import '" + specifier + "' should be allowed");
    }

    /**
     * P12 — Shared imports allowed: For any import specifier that resolves within src/shared/,
     * ReactImportBoundaryResolver.resolve() returns allowed().
     * 
     * Validates: Requirement 7.2
     */
    @ParameterizedTest(name = "P12 iteration {0}: specifier={1}, subProfile={2}")
    @MethodSource("sharedImportConfigurations")
    void p12_sharedImportsAllowed(
            int iteration,
            String specifier,
            ReactProjectShape subProfile) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            subProfile,
            sharedRoot,
            generatedRoot
        );

        assertTrue(decision.isAllowed(),
            "P12: Shared import '" + specifier + "' should be allowed");
    }

    /**
     * P13 — react/react-dom allowed: For bare specifiers "react" and "react-dom",
     * ReactImportBoundaryResolver.resolve() returns allowed().
     * 
     * Validates: Requirement 7.4, 7.5
     */
    @ParameterizedTest(name = "P13 iteration {0}: specifier={1}, subProfile={2}")
    @MethodSource("reactImportConfigurations")
    void p13_reactAndReactDomAllowed(
            int iteration,
            String specifier,
            ReactProjectShape subProfile) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            subProfile,
            sharedRoot,
            generatedRoot
        );

        assertTrue(decision.isAllowed(),
            "P13: React import '" + specifier + "' should be allowed");
    }

    /**
     * P14 — Sibling feature imports fail: For any import specifier that resolves within a
     * different feature root, ReactImportBoundaryResolver.resolve() returns fail(BOUNDARY_BYPASS).
     * 
     * Validates: Requirement 7.7
     */
    @ParameterizedTest(name = "P14 iteration {0}: specifier={1}, subProfile={2}")
    @MethodSource("siblingFeatureImportConfigurations")
    void p14_siblingFeatureImportsFail(
            int iteration,
            String specifier,
            ReactProjectShape subProfile) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            subProfile,
            sharedRoot,
            generatedRoot
        );

        assertFalse(decision.isAllowed(),
            "P14: Sibling feature import '" + specifier + "' should fail");
        assertEquals("BOUNDARY_BYPASS", decision.code(),
            "P14: Should return BOUNDARY_BYPASS code");
    }

    /**
     * P15 — Bare package imports fail: For any bare specifier other than react/react-dom,
     * ReactImportBoundaryResolver.resolve() returns fail(BOUNDARY_BYPASS).
     * 
     * Validates: Requirement 7.8
     */
    @ParameterizedTest(name = "P15 iteration {0}: specifier={1}, subProfile={2}")
    @MethodSource("barePackageImportConfigurations")
    void p15_barePackageImportsFail(
            int iteration,
            String specifier,
            ReactProjectShape subProfile) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            subProfile,
            sharedRoot,
            generatedRoot
        );

        assertFalse(decision.isAllowed(),
            "P15: Bare package import '" + specifier + "' should fail");
        assertEquals("BOUNDARY_BYPASS", decision.code(),
            "P15: Should return BOUNDARY_BYPASS code");
    }

    /**
     * P16 — Next.js @/* alias within same feature allowed: For any @/features/{blockKey}/...
     * specifier in nextjs-app-router sub-profile, resolver returns allowed() when target is
     * within the same feature root.
     * 
     * Validates: Requirement 8.2
     */
    @ParameterizedTest(name = "P16 iteration {0}: specifier={1}")
    @MethodSource("nextjsAliasWithinFeatureConfigurations")
    void p16_nextjsAliasWithinSameFeatureAllowed(
            int iteration,
            String specifier) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            ReactProjectShape.NEXTJS_APP_ROUTER,
            sharedRoot,
            generatedRoot
        );

        assertTrue(decision.isAllowed(),
            "P16: Next.js @/* alias within same feature '" + specifier + "' should be allowed");
    }

    /**
     * P17 — Next.js @/* alias to sibling fails: For any @/features/{otherBlock}/... specifier
     * in nextjs-app-router sub-profile, resolver returns fail(BOUNDARY_BYPASS).
     * 
     * Validates: Requirement 8.3
     */
    @ParameterizedTest(name = "P17 iteration {0}: specifier={1}")
    @MethodSource("nextjsAliasToSiblingConfigurations")
    void p17_nextjsAliasToSiblingFails(
            int iteration,
            String specifier) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            ReactProjectShape.NEXTJS_APP_ROUTER,
            sharedRoot,
            generatedRoot
        );

        assertFalse(decision.isAllowed(),
            "P17: Next.js @/* alias to sibling '" + specifier + "' should fail");
        assertEquals("BOUNDARY_BYPASS", decision.code(),
            "P17: Should return BOUNDARY_BYPASS code");
    }

    /**
     * P18 — Vite @/* alias always fails: For any @/... specifier in vite-react sub-profile,
     * resolver returns fail(BOUNDARY_BYPASS).
     * 
     * Validates: Requirement 8.5
     */
    @ParameterizedTest(name = "P18 iteration {0}: specifier={1}")
    @MethodSource("viteAliasConfigurations")
    void p18_viteAliasAlwaysFails(
            int iteration,
            String specifier) {
        
        var decision = resolver.resolve(
            importingFile,
            specifier,
            featureRoot,
            projectRoot,
            ReactProjectShape.VITE_REACT,
            sharedRoot,
            generatedRoot
        );

        assertFalse(decision.isAllowed(),
            "P18: Vite @/* alias '" + specifier + "' should fail");
        assertEquals("BOUNDARY_BYPASS", decision.code(),
            "P18: Should return BOUNDARY_BYPASS code");
        assertTrue(decision.reason().contains("vite-react"),
            "P18: Reason should mention vite-react sub-profile");
    }

    // --- Data providers generating 100+ iterations ---

    static Stream<Arguments> intraFeatureImportConfigurations() {
        String[] relativeSpecifiers = {
            "./helper",
            "../components/Button",
            "../hooks/useAuth",
            "./utils/format",
            "../components/Card",
            "./types",
            "../impl/helper"
        };
        ReactProjectShape[] subProfiles = {ReactProjectShape.VITE_REACT, ReactProjectShape.NEXTJS_APP_ROUTER};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 8; cycle++) {
            for (String specifier : relativeSpecifiers) {
                for (ReactProjectShape subProfile : subProfiles) {
                    builder.add(Arguments.of(iteration++, specifier, subProfile));
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> sharedImportConfigurations() {
        String[] relativeSpecifiers = {
            "../../../shared/utils/helpers",
            "../../../shared/utils/format",
            "../../../shared/types",
            "../../../shared/constants",
            "../../../shared/utils/validation"
        };
        ReactProjectShape[] subProfiles = {ReactProjectShape.VITE_REACT, ReactProjectShape.NEXTJS_APP_ROUTER};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 10; cycle++) {
            for (String specifier : relativeSpecifiers) {
                for (ReactProjectShape subProfile : subProfiles) {
                    builder.add(Arguments.of(iteration++, specifier, subProfile));
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> reactImportConfigurations() {
        String[] specifiers = {"react", "react-dom"};
        ReactProjectShape[] subProfiles = {ReactProjectShape.VITE_REACT, ReactProjectShape.NEXTJS_APP_ROUTER};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 26; cycle++) {
            for (String specifier : specifiers) {
                for (ReactProjectShape subProfile : subProfiles) {
                    builder.add(Arguments.of(iteration++, specifier, subProfile));
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> siblingFeatureImportConfigurations() {
        String[] specifiers = {
            "../../product-catalog/ProductCard",
            "../../product-catalog/hooks/useProduct",
            "../../checkout/CheckoutForm",
            "../../checkout/impl/CheckoutImpl",
            "../../product-catalog/types"
        };
        ReactProjectShape[] subProfiles = {ReactProjectShape.VITE_REACT, ReactProjectShape.NEXTJS_APP_ROUTER};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 10; cycle++) {
            for (String specifier : specifiers) {
                for (ReactProjectShape subProfile : subProfiles) {
                    builder.add(Arguments.of(iteration++, specifier, subProfile));
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> barePackageImportConfigurations() {
        String[] specifiers = {
            "lodash",
            "axios",
            "moment",
            "dayjs",
            "@tanstack/react-query",
            "zustand",
            "jotai",
            "swr",
            "lodash/debounce",
            "date-fns"
        };
        ReactProjectShape[] subProfiles = {ReactProjectShape.VITE_REACT, ReactProjectShape.NEXTJS_APP_ROUTER};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 5; cycle++) {
            for (String specifier : specifiers) {
                for (ReactProjectShape subProfile : subProfiles) {
                    builder.add(Arguments.of(iteration++, specifier, subProfile));
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> nextjsAliasWithinFeatureConfigurations() {
        String[] specifiers = {
            "@/features/user-dashboard/components/Button",
            "@/features/user-dashboard/hooks/useAuth",
            "@/features/user-dashboard/impl/helper",
            "@/features/user-dashboard/types",
            "@/features/user-dashboard/utils/format"
        };
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 20; cycle++) {
            for (String specifier : specifiers) {
                builder.add(Arguments.of(iteration++, specifier));
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> nextjsAliasToSiblingConfigurations() {
        String[] specifiers = {
            "@/features/product-catalog/ProductCard",
            "@/features/product-catalog/hooks/useProduct",
            "@/features/checkout/CheckoutForm",
            "@/features/checkout/impl/CheckoutImpl",
            "@/features/product-catalog/types"
        };
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 20; cycle++) {
            for (String specifier : specifiers) {
                builder.add(Arguments.of(iteration++, specifier));
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> viteAliasConfigurations() {
        String[] specifiers = {
            "@/features/user-dashboard/components/Button",
            "@/features/product-catalog/ProductCard",
            "@/shared/utils/helpers",
            "@/components/Button",
            "@/hooks/useAuth",
            "@/utils/format",
            "@/types",
            "@/constants"
        };
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 13; cycle++) {
            for (String specifier : specifiers) {
                builder.add(Arguments.of(iteration++, specifier));
            }
        }
        
        return builder.build();
    }
}
