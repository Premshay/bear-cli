package com.bear.kernel.target.react.properties;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetId;
import com.bear.kernel.target.node.NodeTargetDetector;
import com.bear.kernel.target.react.ReactTargetDetector;
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
 * Property-based tests for React target detection (P1–P6).
 * Each property runs 100+ iterations over generated inputs.
 * 
 * Feature: react-target-scan-only
 */
class ReactDetectionProperties {

    private final ReactTargetDetector reactDetector = new ReactTargetDetector();
    private final NodeTargetDetector nodeDetector = new NodeTargetDetector();

    /**
     * P1 — Detection completeness (Vite+React): For any project root with valid Vite+React structure
     * (package.json with react+react-dom deps, pnpm lock, tsconfig, vite.config.ts, no next.config.*),
     * ReactTargetDetector.detect() returns SUPPORTED with targetId=REACT.
     * 
     * Validates: Requirement 1.1
     */
    @ParameterizedTest(name = "P1 iteration {0}: react={1}, reactDom={2}, pnpmVersion={3}")
    @MethodSource("viteReactConfigurations")
    void p1_viteReactDetectionCompleteness(
            int iteration,
            String reactVersion,
            String reactDomVersion,
            String pnpmVersion,
            @TempDir Path tempDir) throws IOException {
        
        createViteReactProject(tempDir, reactVersion, reactDomVersion, pnpmVersion);

        DetectedTarget result = reactDetector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status(),
            "P1: Valid Vite+React project should be SUPPORTED");
        assertEquals(TargetId.REACT, result.targetId(),
            "P1: Target should be REACT");
        assertEquals("vite-react", result.reason(),
            "P1: Sub-profile should be vite-react");
    }

    /**
     * P2 — Detection exclusivity (Next.js): For any project root with valid Next.js App Router structure
     * (package.json with react+react-dom deps, pnpm lock, tsconfig, next.config.js/mjs/ts, no vite.config.ts),
     * ReactTargetDetector.detect() returns SUPPORTED with targetId=REACT.
     * 
     * Validates: Requirement 1.2
     */
    @ParameterizedTest(name = "P2 iteration {0}: nextConfigExt={1}, reactVersion={2}")
    @MethodSource("nextjsConfigurations")
    void p2_nextjsDetectionExclusivity(
            int iteration,
            String nextConfigExtension,
            String reactVersion,
            String pnpmVersion,
            @TempDir Path tempDir) throws IOException {
        
        createNextjsProject(tempDir, nextConfigExtension, reactVersion, pnpmVersion);

        DetectedTarget result = reactDetector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status(),
            "P2: Valid Next.js project should be SUPPORTED");
        assertEquals(TargetId.REACT, result.targetId(),
            "P2: Target should be REACT");
        assertEquals("nextjs-app-router", result.reason(),
            "P2: Sub-profile should be nextjs-app-router");
    }

    /**
     * P3 — Detection NONE on missing react dep: For any project root missing react or react-dom
     * in package.json dependencies, ReactTargetDetector.detect() returns NONE.
     * 
     * Validates: Requirement 1.7
     */
    @ParameterizedTest(name = "P3 iteration {0}: hasReact={1}, hasReactDom={2}")
    @MethodSource("missingReactDepConfigurations")
    void p3_detectionNoneOnMissingReactDep(
            int iteration,
            boolean hasReact,
            boolean hasReactDom,
            @TempDir Path tempDir) throws IOException {
        
        createProjectWithOptionalReactDeps(tempDir, hasReact, hasReactDom);

        DetectedTarget result = reactDetector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status(),
            "P3: Project missing react or react-dom should return NONE");
    }

    /**
     * P4 — Detection UNSUPPORTED on workspace: For any project root with pnpm-workspace.yaml,
     * ReactTargetDetector.detect() returns UNSUPPORTED.
     * 
     * Validates: Requirement 1.3
     */
    @ParameterizedTest(name = "P4 iteration {0}: buildTool={1}")
    @MethodSource("workspaceConfigurations")
    void p4_detectionUnsupportedOnWorkspace(
            int iteration,
            String buildTool,
            @TempDir Path tempDir) throws IOException {
        
        createWorkspaceProject(tempDir, buildTool);

        DetectedTarget result = reactDetector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status(),
            "P4: Workspace project should return UNSUPPORTED");
        assertEquals(TargetId.REACT, result.targetId(),
            "P4: Target should be REACT");
        assertTrue(result.reason().contains("workspace"),
            "P4: Reason should mention workspace");
    }

    /**
     * P5 — Detection UNSUPPORTED on ambiguous shape: For any project root with both vite.config.ts
     * and next.config.*, ReactTargetDetector.detect() returns UNSUPPORTED.
     * 
     * Validates: Requirement 1.11
     */
    @ParameterizedTest(name = "P5 iteration {0}: nextConfigExt={1}")
    @MethodSource("ambiguousShapeConfigurations")
    void p5_detectionUnsupportedOnAmbiguousShape(
            int iteration,
            String nextConfigExtension,
            @TempDir Path tempDir) throws IOException {
        
        createAmbiguousProject(tempDir, nextConfigExtension);

        DetectedTarget result = reactDetector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status(),
            "P5: Ambiguous project should return UNSUPPORTED");
        assertEquals(TargetId.REACT, result.targetId(),
            "P5: Target should be REACT");
        assertTrue(result.reason().contains("ambiguous"),
            "P5: Reason should mention ambiguous");
    }

    /**
     * P6 — Node exclusion: For any project root where ReactTargetDetector returns SUPPORTED,
     * NodeTargetDetector.detect() returns NONE.
     * 
     * Validates: Requirement 3.1, 3.2
     */
    @ParameterizedTest(name = "P6 iteration {0}: buildTool={1}")
    @MethodSource("nodeExclusionConfigurations")
    void p6_nodeExclusionWhenReactSupported(
            int iteration,
            String buildTool,
            @TempDir Path tempDir) throws IOException {
        
        if ("vite".equals(buildTool)) {
            createViteReactProject(tempDir, "^18.2.0", "^18.2.0", "pnpm@8.0.0");
        } else {
            createNextjsProject(tempDir, ".js", "^18.2.0", "pnpm@8.0.0");
        }

        DetectedTarget reactResult = reactDetector.detect(tempDir);
        DetectedTarget nodeResult = nodeDetector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, reactResult.status(),
            "P6: React detector should return SUPPORTED");
        assertEquals(DetectionStatus.NONE, nodeResult.status(),
            "P6: Node detector should return NONE when React is SUPPORTED");
    }

    // --- Data providers generating 100+ iterations ---

    static Stream<Arguments> viteReactConfigurations() {
        String[] reactVersions = {"^18.2.0", "^18.0.0", "^17.0.0", "18.2.0", "~18.2.0"};
        String[] pnpmVersions = {"pnpm@8.0.0", "pnpm@9.0.0", "pnpm@7.33.0", "pnpm@8.15.0"};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 5; cycle++) {
            for (String reactVersion : reactVersions) {
                for (String pnpmVersion : pnpmVersions) {
                    builder.add(Arguments.of(
                        iteration++,
                        reactVersion,
                        reactVersion, // react-dom same version
                        pnpmVersion
                    ));
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> nextjsConfigurations() {
        String[] nextConfigExtensions = {".js", ".mjs", ".ts"};
        String[] reactVersions = {"^18.2.0", "^18.0.0", "18.2.0", "~18.2.0"};
        String[] pnpmVersions = {"pnpm@8.0.0", "pnpm@9.0.0", "pnpm@7.33.0"};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 3; cycle++) {
            for (String ext : nextConfigExtensions) {
                for (String reactVersion : reactVersions) {
                    for (String pnpmVersion : pnpmVersions) {
                        builder.add(Arguments.of(
                            iteration++,
                            ext,
                            reactVersion,
                            pnpmVersion
                        ));
                    }
                }
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> missingReactDepConfigurations() {
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations with various combinations
        for (int cycle = 0; cycle < 50; cycle++) {
            // Missing both
            builder.add(Arguments.of(iteration++, false, false));
            // Missing react-dom only
            builder.add(Arguments.of(iteration++, true, false));
            // Missing react only
            builder.add(Arguments.of(iteration++, false, true));
        }
        
        return builder.build();
    }

    static Stream<Arguments> workspaceConfigurations() {
        String[] buildTools = {"vite", "next"};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 50; cycle++) {
            for (String buildTool : buildTools) {
                builder.add(Arguments.of(iteration++, buildTool));
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> ambiguousShapeConfigurations() {
        String[] nextConfigExtensions = {".js", ".mjs", ".ts"};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 34; cycle++) {
            for (String ext : nextConfigExtensions) {
                builder.add(Arguments.of(iteration++, ext));
            }
        }
        
        return builder.build();
    }

    static Stream<Arguments> nodeExclusionConfigurations() {
        String[] buildTools = {"vite", "next"};
        
        Stream.Builder<Arguments> builder = Stream.builder();
        int iteration = 0;
        
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 50; cycle++) {
            for (String buildTool : buildTools) {
                builder.add(Arguments.of(iteration++, buildTool));
            }
        }
        
        return builder.build();
    }

    // --- Helper methods ---

    private void createViteReactProject(Path dir, String reactVersion, String reactDomVersion, String pnpmVersion) throws IOException {
        String packageJson = """
            {
              "name": "test-vite-react",
              "type": "module",
              "packageManager": "%s",
              "dependencies": {
                "react": "%s",
                "react-dom": "%s"
              }
            }
            """.formatted(pnpmVersion, reactVersion, reactDomVersion);
        Files.writeString(dir.resolve("package.json"), packageJson);
        Files.createFile(dir.resolve("pnpm-lock.yaml"));
        Files.createFile(dir.resolve("tsconfig.json"));
        Files.createFile(dir.resolve("vite.config.ts"));
    }

    private void createNextjsProject(Path dir, String nextConfigExtension, String reactVersion, String pnpmVersion) throws IOException {
        String packageJson = """
            {
              "name": "test-nextjs",
              "type": "module",
              "packageManager": "%s",
              "dependencies": {
                "react": "%s",
                "react-dom": "%s",
                "next": "^14.0.0"
              }
            }
            """.formatted(pnpmVersion, reactVersion, reactVersion);
        Files.writeString(dir.resolve("package.json"), packageJson);
        Files.createFile(dir.resolve("pnpm-lock.yaml"));
        Files.createFile(dir.resolve("tsconfig.json"));
        Files.createFile(dir.resolve("next.config" + nextConfigExtension));
    }

    private void createProjectWithOptionalReactDeps(Path dir, boolean hasReact, boolean hasReactDom) throws IOException {
        StringBuilder deps = new StringBuilder();
        if (hasReact) {
            deps.append("\"react\": \"^18.2.0\"");
        }
        if (hasReact && hasReactDom) {
            deps.append(",");
        }
        if (hasReactDom) {
            deps.append("\"react-dom\": \"^18.2.0\"");
        }
        
        String packageJson = """
            {
              "name": "test-project",
              "type": "module",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {%s}
            }
            """.formatted(deps.toString());
        Files.writeString(dir.resolve("package.json"), packageJson);
        Files.createFile(dir.resolve("pnpm-lock.yaml"));
        Files.createFile(dir.resolve("tsconfig.json"));
        Files.createFile(dir.resolve("vite.config.ts"));
    }

    private void createWorkspaceProject(Path dir, String buildTool) throws IOException {
        String packageJson = """
            {
              "name": "test-workspace",
              "type": "module",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              }
            }
            """;
        Files.writeString(dir.resolve("package.json"), packageJson);
        Files.createFile(dir.resolve("pnpm-lock.yaml"));
        Files.createFile(dir.resolve("tsconfig.json"));
        Files.createFile(dir.resolve("pnpm-workspace.yaml"));
        
        if ("vite".equals(buildTool)) {
            Files.createFile(dir.resolve("vite.config.ts"));
        } else {
            Files.createFile(dir.resolve("next.config.js"));
        }
    }

    private void createAmbiguousProject(Path dir, String nextConfigExtension) throws IOException {
        String packageJson = """
            {
              "name": "test-ambiguous",
              "type": "module",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              }
            }
            """;
        Files.writeString(dir.resolve("package.json"), packageJson);
        Files.createFile(dir.resolve("pnpm-lock.yaml"));
        Files.createFile(dir.resolve("tsconfig.json"));
        Files.createFile(dir.resolve("vite.config.ts"));
        Files.createFile(dir.resolve("next.config" + nextConfigExtension));
    }
}
