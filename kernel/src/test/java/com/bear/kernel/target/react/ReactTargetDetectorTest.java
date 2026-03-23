package com.bear.kernel.target.react;

import com.bear.kernel.target.DetectedTarget;
import com.bear.kernel.target.DetectionStatus;
import com.bear.kernel.target.TargetId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReactTargetDetector covering all 11 acceptance criteria from Requirement 1.
 */
class ReactTargetDetectorTest {

    private final ReactTargetDetector detector = new ReactTargetDetector();

    // --- Acceptance Criteria 1.1: Vite+React detection ---
    @Test
    void viteReactProject_returnsSupportedWithViteReactSubProfile(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("vite-react", result.reason());
    }

    // --- Acceptance Criteria 1.2: Next.js App Router detection ---
    @Test
    void nextjsProject_returnsSupportedWithNextjsSubProfile(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("next.config.js"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("nextjs-app-router", result.reason());
    }

    @Test
    void nextjsProjectWithMjsConfig_returnsSupportedWithNextjsSubProfile(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("next.config.mjs"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("nextjs-app-router", result.reason());
    }

    @Test
    void nextjsProjectWithTsConfig_returnsSupportedWithNextjsSubProfile(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("next.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("nextjs-app-router", result.reason());
    }

    // --- Acceptance Criteria 1.3: pnpm-workspace.yaml → UNSUPPORTED ---
    @Test
    void workspaceProject_returnsUnsupported(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));
        Files.createFile(tempDir.resolve("pnpm-workspace.yaml"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("pnpm workspace detected", result.reason());
    }

    // --- Acceptance Criteria 1.4: Missing package.json → NONE ---
    @Test
    void missingPackageJson_returnsNone(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.5: Missing pnpm-lock.yaml → NONE ---
    @Test
    void missingPnpmLock_returnsNone(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.6: Missing tsconfig.json → NONE ---
    @Test
    void missingTsconfig_returnsNone(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.7: Missing react/react-dom in dependencies → NONE ---
    @Test
    void missingReactDependency_returnsNone(@TempDir Path tempDir) throws IOException {
        createPackageJsonWithoutReact(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    @Test
    void missingReactDomDependency_returnsNone(@TempDir Path tempDir) throws IOException {
        createPackageJsonWithOnlyReact(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.8: Missing "type": "module" → NONE ---
    @Test
    void missingTypeModule_returnsNone(@TempDir Path tempDir) throws IOException {
        createPackageJsonWithoutTypeModule(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.9: packageManager not starting with pnpm → NONE ---
    @Test
    void npmPackageManager_returnsNone(@TempDir Path tempDir) throws IOException {
        createPackageJsonWithNpm(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.10: Neither vite.config.ts nor next.config.* → NONE ---
    @Test
    void noBuildConfig_returnsNone(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.NONE, result.status());
    }

    // --- Acceptance Criteria 1.11: Both vite.config.ts AND next.config.* → UNSUPPORTED ---
    @Test
    void ambiguousProjectShape_returnsUnsupported(@TempDir Path tempDir) throws IOException {
        createValidReactPackageJson(tempDir);
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        Files.createFile(tempDir.resolve("tsconfig.json"));
        Files.createFile(tempDir.resolve("vite.config.ts"));
        Files.createFile(tempDir.resolve("next.config.js"));

        DetectedTarget result = detector.detect(tempDir);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertTrue(result.reason().contains("ambiguous"));
        assertTrue(result.reason().contains("Vite"));
        assertTrue(result.reason().contains("Next.js"));
    }

    // --- Fixture-based tests ---
    @Test
    void viteReactSingleBlockFixture_returnsSupportedViteReact() {
        Path fixture = Path.of("src/test/resources/fixtures/react/vite-react-single-block");
        DetectedTarget result = detector.detect(fixture);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("vite-react", result.reason());
    }

    @Test
    void nextjsSingleBlockFixture_returnsSupportedNextjs() {
        Path fixture = Path.of("src/test/resources/fixtures/react/nextjs-single-block");
        DetectedTarget result = detector.detect(fixture);

        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("nextjs-app-router", result.reason());
    }

    @Test
    void invalidWorkspaceFixture_returnsUnsupported() {
        Path fixture = Path.of("src/test/resources/fixtures/react/invalid-workspace");
        DetectedTarget result = detector.detect(fixture);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertEquals("pnpm workspace detected", result.reason());
    }

    @Test
    void invalidAmbiguousShapeFixture_returnsUnsupported() {
        Path fixture = Path.of("src/test/resources/fixtures/react/invalid-ambiguous-shape");
        DetectedTarget result = detector.detect(fixture);

        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.REACT, result.targetId());
        assertTrue(result.reason().contains("ambiguous"));
    }

    // --- Helper methods ---
    private void createValidReactPackageJson(Path dir) throws IOException {
        String content = """
            {
              "name": "test-project",
              "type": "module",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              }
            }
            """;
        Files.writeString(dir.resolve("package.json"), content);
    }

    private void createPackageJsonWithoutReact(Path dir) throws IOException {
        String content = """
            {
              "name": "test-project",
              "type": "module",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {}
            }
            """;
        Files.writeString(dir.resolve("package.json"), content);
    }

    private void createPackageJsonWithOnlyReact(Path dir) throws IOException {
        String content = """
            {
              "name": "test-project",
              "type": "module",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {
                "react": "^18.2.0"
              }
            }
            """;
        Files.writeString(dir.resolve("package.json"), content);
    }

    private void createPackageJsonWithoutTypeModule(Path dir) throws IOException {
        String content = """
            {
              "name": "test-project",
              "packageManager": "pnpm@8.0.0",
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              }
            }
            """;
        Files.writeString(dir.resolve("package.json"), content);
    }

    private void createPackageJsonWithNpm(Path dir) throws IOException {
        String content = """
            {
              "name": "test-project",
              "type": "module",
              "packageManager": "npm@9.0.0",
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              }
            }
            """;
        Files.writeString(dir.resolve("package.json"), content);
    }
}
