package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTestRunnerTest {
    @Test
    void gradleLockDetectionMatchesKnownSignatures() {
        assertTrue(ProjectTestRunner.isGradleWrapperLockOutput("java.io.FileNotFoundException: gradle-8.12.1-bin.zip.lck (Access is denied)"));
        assertTrue(ProjectTestRunner.isGradleWrapperLockOutput("PROJECT_TEST_GRADLE_LOCK_SIMULATED"));
    }

    @Test
    void gradleBootstrapDetectionMatchesKnownSignatures() {
        assertTrue(ProjectTestRunner.isGradleWrapperBootstrapIoOutput("java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip"));
        assertTrue(ProjectTestRunner.isGradleWrapperBootstrapIoOutput("PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED"));
    }

    @Test
    void shortFailureHelpersAndDetailFormattingAreDeterministic() {
        String firstLine = ProjectTestRunner.firstRelevantProjectTestFailureLine("line1\nFAILURE: Build failed\nline3");
        assertEquals("FAILURE: Build failed", firstLine);
        assertEquals(
            "root-level project tests failed; line: FAILURE: Build failed; tail: tail info",
            ProjectTestRunner.projectTestDetail("root-level project tests failed", "FAILURE: Build failed", "tail info")
        );
        assertNull(ProjectTestRunner.firstGradleLockLine("no lock signature"));
    }

    @Test
    void timeoutSecondsUsesPropertyWithSafeFallbacks() {
        String key = "bear.check.testTimeoutSeconds";
        String previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(300, ProjectTestRunner.testTimeoutSeconds());

            System.setProperty(key, "15");
            assertEquals(15, ProjectTestRunner.testTimeoutSeconds());

            System.setProperty(key, "0");
            assertEquals(300, ProjectTestRunner.testTimeoutSeconds());

            System.setProperty(key, "abc");
            assertEquals(300, ProjectTestRunner.testTimeoutSeconds());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void runProjectTestsRetriesIsolatedAfterSelfHealAndPasses(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);
        Path partFile = projectRoot.resolve(
            ".bear-gradle-user-home/wrapper/dists/gradle-8.12.1-bin/test/gradle-8.12.1-bin.zip.part"
        );
        Files.createDirectories(partFile.getParent());
        Files.writeString(partFile, "partial");

        String partPath = partFile.toString();
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\nif exist \"" + partPath + "\" (\r\n"
                + "  echo java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\nif [ -f \"" + partPath.replace("\\", "\\\\") + "\" ]; then\n"
                + "  echo \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            ProjectTestResult result = ProjectTestRunner.runProjectTests(projectRoot);
            assertEquals(ProjectTestStatus.PASSED, result.status());
            assertEquals("isolated,isolated-retry", result.attemptTrail());
            assertFalse(Files.exists(partFile));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void runProjectTestsFallsBackToUserCacheAfterIsolatedRetry(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);
        String isolated = projectRoot.resolve(".bear-gradle-user-home").toString();

        writeProjectWrapper(
            projectRoot,
            "@echo off\r\nif /I \"%GRADLE_USER_HOME%\"==\"" + isolated + "\" (\r\n"
                + "  echo java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\nif [ \"$GRADLE_USER_HOME\" = \"" + isolated.replace("\\", "\\\\") + "\" ]; then\n"
                + "  echo \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            ProjectTestResult result = ProjectTestRunner.runProjectTests(projectRoot);
            assertEquals(ProjectTestStatus.PASSED, result.status());
            assertEquals("isolated,isolated-retry,user-cache", result.attemptTrail());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void runProjectTestsWithExternalGradleHomeDoesNotFallback(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, tempDir.resolve("external-gradle-home").toString());
            writeProjectWrapper(
                projectRoot,
                "@echo off\r\n"
                    + "echo java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\n"
                    + "exit /b 1\r\n",
                "#!/usr/bin/env sh\n"
                    + "echo \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\n"
                    + "exit 1\n"
            );

            ProjectTestResult result = ProjectTestRunner.runProjectTests(projectRoot);
            assertEquals(ProjectTestStatus.BOOTSTRAP_IO, result.status());
            assertEquals("external-env,external-env-retry", result.attemptTrail());
            assertTrue(result.firstBootstrapLine().contains("gradle-8.12.1-bin.zip"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static void writeProjectWrapper(Path projectRoot, String windowsContent, String unixContent) throws Exception {
        Path wrapper = projectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        Files.writeString(wrapper, isWindows() ? windowsContent : unixContent);
        if (!isWindows()) {
            try {
                Files.setPosixFilePermissions(
                    wrapper,
                    Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                    )
                );
            } catch (UnsupportedOperationException ignored) {
                // Filesystem does not support POSIX perms.
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
