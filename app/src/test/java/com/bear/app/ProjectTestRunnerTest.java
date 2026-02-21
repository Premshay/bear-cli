package com.bear.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
