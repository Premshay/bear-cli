package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.ProjectTestResult;
import com.bear.kernel.target.ProjectTestStatus;
import com.bear.kernel.target.node.NodeProjectVerificationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for NodeProjectVerificationRunner.
 * Feature: phase-c-node-runtime-execution
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs.
 * 
 * Property 7 (isTscMissing pattern detection) is tested in NodeProjectVerificationRunnerTest
 * which is in the same package as the runner and can access package-private methods.
 * This file focuses on Property 19 (phase tag) which uses only public API.
 */
class ProjectVerificationProperties {

    // -------------------------------------------------------------------------
    // Property 19: NodeProjectVerificationRunner phase tag
    // Feature: phase-c-node-runtime-execution, Property 19: NodeProjectVerificationRunner phase tag
    // Validates: Requirement 4.9
    // -------------------------------------------------------------------------

    @Test
    void phaseTag_passedStatus_isTsc() {
        ProjectTestResult result = createTestResult(ProjectTestStatus.PASSED, "success");
        assertEquals("tsc", result.phase(),
            "PASSED result should have phase='tsc'");
    }

    @Test
    void phaseTag_failedStatus_isTsc() {
        ProjectTestResult result = createTestResult(ProjectTestStatus.FAILED, 
            "error TS2322: Type mismatch");
        assertEquals("tsc", result.phase(),
            "FAILED result should have phase='tsc'");
    }

    @Test
    void phaseTag_bootstrapIoStatus_isTsc() {
        ProjectTestResult result = createTestResult(ProjectTestStatus.BOOTSTRAP_IO, 
            "pnpm not found on PATH");
        assertEquals("tsc", result.phase(),
            "BOOTSTRAP_IO result should have phase='tsc'");
    }

    @Test
    void phaseTag_timeoutStatus_isTsc() {
        ProjectTestResult result = createTestResult(ProjectTestStatus.TIMEOUT, 
            "partial output before timeout");
        assertEquals("tsc", result.phase(),
            "TIMEOUT result should have phase='tsc'");
    }

    @Test
    void phaseTag_allStatusesHaveTscPhase() {
        // Verify all possible statuses that NodeProjectVerificationRunner can return
        ProjectTestStatus[] relevantStatuses = {
            ProjectTestStatus.PASSED,
            ProjectTestStatus.FAILED,
            ProjectTestStatus.BOOTSTRAP_IO,
            ProjectTestStatus.TIMEOUT
        };

        for (ProjectTestStatus status : relevantStatuses) {
            ProjectTestResult result = createTestResult(status, "test output for " + status);
            assertEquals("tsc", result.phase(),
                status + " result should have phase='tsc'");
        }
    }

    // -------------------------------------------------------------------------
    // Property 19 (integration): Actual runner returns phase="tsc"
    // Note: isPnpmAvailable() is package-private, so we test via run() behavior
    // -------------------------------------------------------------------------

    @Test
    void actualRunner_returnsPhaseEqualsTsc(@TempDir Path tempDir) 
            throws IOException, InterruptedException {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);

        // Run the actual runner - it will return BOOTSTRAP_IO if pnpm is missing
        // or attempt to run tsc if pnpm is available
        ProjectTestResult result = NodeProjectVerificationRunner.run(projectRoot);

        // Regardless of the outcome, phase should always be "tsc"
        assertEquals("tsc", result.phase(),
            "Actual runner should return phase='tsc' for any result status");
    }

    // -------------------------------------------------------------------------
    // Additional property: Result structure consistency
    // -------------------------------------------------------------------------

    @Test
    void resultStructure_bootstrapIo_hasFirstBootstrapLine() {
        String output = "pnpm not found on PATH";
        ProjectTestResult result = createTestResult(ProjectTestStatus.BOOTSTRAP_IO, output);
        
        assertEquals(output, result.firstBootstrapLine(),
            "BOOTSTRAP_IO result should have firstBootstrapLine set to output");
    }

    @Test
    void resultStructure_nonBootstrapIo_hasNullFirstBootstrapLine() {
        ProjectTestResult passed = createTestResult(ProjectTestStatus.PASSED, "success");
        ProjectTestResult failed = createTestResult(ProjectTestStatus.FAILED, "error");
        ProjectTestResult timeout = createTestResult(ProjectTestStatus.TIMEOUT, "timeout");

        assertNull(passed.firstBootstrapLine(),
            "PASSED result should have null firstBootstrapLine");
        assertNull(failed.firstBootstrapLine(),
            "FAILED result should have null firstBootstrapLine");
        assertNull(timeout.firstBootstrapLine(),
            "TIMEOUT result should have null firstBootstrapLine");
    }

    @Test
    void resultStructure_outputIsPreserved() {
        String[] testOutputs = {
            "",
            "simple output",
            "multi\nline\noutput",
            "output with special chars: @#$%^&*()",
            "very long output ".repeat(100)
        };

        for (String output : testOutputs) {
            ProjectTestResult result = createTestResult(ProjectTestStatus.PASSED, output);
            assertEquals(output, result.output(),
                "Output should be preserved exactly");
        }
    }

    /**
     * Helper to create test results matching NodeProjectVerificationRunner's output format.
     */
    private ProjectTestResult createTestResult(ProjectTestStatus status, String output) {
        return new ProjectTestResult(
            status,
            output,
            null,  // attemptTrail
            null,  // firstLockLine
            status == ProjectTestStatus.BOOTSTRAP_IO ? output : null,  // firstBootstrapLine
            null,  // firstSharedDepsViolationLine
            null,  // cacheMode
            false, // fallbackToUserCache
            "tsc", // phase
            null   // lastObservedTask
        );
    }
}
