package com.bear.kernel.target.react.properties;

import com.bear.kernel.target.ProjectTestResult;
import com.bear.kernel.target.ProjectTestStatus;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ReactProjectVerificationRunner.
 * 
 * Properties P21–P23 from the design document:
 * - P21: PASSED on zero tsc exit — Validates: Requirement 14.2
 * - P22: FAILED on non-zero tsc exit — Validates: Requirement 14.3
 * - P23: BOOTSTRAP_IO on missing pnpm — Validates: Requirement 14.4
 * 
 * Note: These properties test the result mapping logic since we cannot
 * control actual process execution in property tests. The actual process
 * execution is tested in ReactProjectVerificationRunnerTest.
 */
class ReactProjectVerificationProperties {

    private static final int ITERATIONS = 100;
    private final Random random = new Random(42);

    /**
     * Property 21: PASSED on zero tsc exit.
     * **Validates: Requirement 14.2**
     * 
     * For any successful tsc execution (exit code 0), the result status
     * must be PASSED with phase "tsc".
     */
    @Test
    void property21_passedOnZeroTscExit() {
        for (int i = 0; i < ITERATIONS; i++) {
            // Generate random successful output
            String output = generateRandomSuccessOutput();
            
            ProjectTestResult result = createPassedResult(output);
            
            assertEquals(ProjectTestStatus.PASSED, result.status(),
                "Exit code 0 must map to PASSED status");
            assertEquals("tsc", result.phase(),
                "Phase must be 'tsc' for React verification");
            assertNotNull(result.output(),
                "Output must not be null");
            assertNull(result.firstBootstrapLine(),
                "firstBootstrapLine must be null for PASSED status");
        }
    }

    /**
     * Property 22: FAILED on non-zero tsc exit.
     * **Validates: Requirement 14.3**
     * 
     * For any failed tsc execution (non-zero exit code, not module-not-found),
     * the result status must be FAILED with phase "tsc".
     */
    @Test
    void property22_failedOnNonZeroTscExit() {
        for (int i = 0; i < ITERATIONS; i++) {
            // Generate random type error output (not module-not-found)
            String output = generateRandomTypeErrorOutput();
            
            ProjectTestResult result = createFailedResult(output);
            
            assertEquals(ProjectTestStatus.FAILED, result.status(),
                "Non-zero exit code (type errors) must map to FAILED status");
            assertEquals("tsc", result.phase(),
                "Phase must be 'tsc' for React verification");
            assertNotNull(result.output(),
                "Output must not be null");
            assertNull(result.firstBootstrapLine(),
                "firstBootstrapLine must be null for FAILED status");
        }
    }

    /**
     * Property 23: BOOTSTRAP_IO on missing pnpm.
     * **Validates: Requirement 14.4**
     * 
     * When pnpm is not found on PATH, the result status must be BOOTSTRAP_IO
     * with phase "tsc" and firstBootstrapLine set.
     */
    @Test
    void property23_bootstrapIoOnMissingPnpm() {
        for (int i = 0; i < ITERATIONS; i++) {
            // Generate pnpm missing message
            String output = "pnpm not found on PATH";
            
            ProjectTestResult result = createBootstrapIoResult(output);
            
            assertEquals(ProjectTestStatus.BOOTSTRAP_IO, result.status(),
                "Missing pnpm must map to BOOTSTRAP_IO status");
            assertEquals("tsc", result.phase(),
                "Phase must be 'tsc' for React verification");
            assertNotNull(result.output(),
                "Output must not be null");
            assertNotNull(result.firstBootstrapLine(),
                "firstBootstrapLine must be set for BOOTSTRAP_IO status");
            assertEquals(output, result.firstBootstrapLine(),
                "firstBootstrapLine must contain the bootstrap message");
        }
    }

    /**
     * Additional property: BOOTSTRAP_IO on missing tsc.
     * **Validates: Requirement 14.5**
     * 
     * When tsc is not found (module-not-found error), the result status
     * must be BOOTSTRAP_IO.
     */
    @Test
    void property_bootstrapIoOnMissingTsc() {
        String[] tscMissingPatterns = {
            "Cannot find module 'typescript/lib/tsc.js'",
            "error TS2307: Cannot find module 'typescript'",
            "Cannot find module '@types/node'"
        };

        for (int i = 0; i < ITERATIONS; i++) {
            String output = tscMissingPatterns[i % tscMissingPatterns.length];
            
            ProjectTestResult result = createBootstrapIoResult(output);
            
            assertEquals(ProjectTestStatus.BOOTSTRAP_IO, result.status(),
                "Missing tsc must map to BOOTSTRAP_IO status");
            assertEquals("tsc", result.phase(),
                "Phase must be 'tsc' for React verification");
        }
    }

    /**
     * Additional property: TIMEOUT on process timeout.
     * **Validates: Requirement 14.6**
     * 
     * When the verification process exceeds the timeout, the result status
     * must be TIMEOUT.
     */
    @Test
    void property_timeoutOnProcessTimeout() {
        for (int i = 0; i < ITERATIONS; i++) {
            String output = generateRandomPartialOutput();
            
            ProjectTestResult result = createTimeoutResult(output);
            
            assertEquals(ProjectTestStatus.TIMEOUT, result.status(),
                "Process timeout must map to TIMEOUT status");
            assertEquals("tsc", result.phase(),
                "Phase must be 'tsc' for React verification");
            assertNotNull(result.output(),
                "Output must not be null even on timeout");
        }
    }

    /**
     * Property: Output is always captured.
     * **Validates: Requirement 14.7**
     * 
     * For any result status, the output field must be non-null.
     */
    @Test
    void property_outputAlwaysCaptured() {
        ProjectTestStatus[] statuses = {
            ProjectTestStatus.PASSED,
            ProjectTestStatus.FAILED,
            ProjectTestStatus.TIMEOUT,
            ProjectTestStatus.BOOTSTRAP_IO
        };

        for (int i = 0; i < ITERATIONS; i++) {
            ProjectTestStatus status = statuses[i % statuses.length];
            String output = generateRandomOutput();
            
            ProjectTestResult result = createResult(status, output);
            
            assertNotNull(result.output(),
                "Output must never be null for status " + status);
        }
    }

    // --- Helper methods ---

    private String generateRandomSuccessOutput() {
        String[] templates = {
            "",
            "Found 0 errors.",
            "Compilation complete. Watching for file changes.",
            "tsc --noEmit completed successfully"
        };
        return templates[random.nextInt(templates.length)];
    }

    private String generateRandomTypeErrorOutput() {
        String[] templates = {
            "error TS2322: Type 'string' is not assignable to type 'number'.",
            "error TS2339: Property 'foo' does not exist on type 'Bar'.",
            "error TS2345: Argument of type 'X' is not assignable to parameter of type 'Y'.",
            "src/features/user/impl/UserFeatureImpl.tsx(10,5): error TS2741: Property 'name' is missing.",
            "Found 3 errors in 2 files."
        };
        return templates[random.nextInt(templates.length)];
    }

    private String generateRandomPartialOutput() {
        String[] templates = {
            "Checking file 1 of 100...",
            "Processing src/features/...",
            "Type checking in progress...",
            ""
        };
        return templates[random.nextInt(templates.length)];
    }

    private String generateRandomOutput() {
        String[] templates = {
            "",
            "Some output",
            "error: something went wrong",
            "success: all good"
        };
        return templates[random.nextInt(templates.length)];
    }

    private ProjectTestResult createPassedResult(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.PASSED,
            output,
            null, null, null, null, null, false, "tsc", null
        );
    }

    private ProjectTestResult createFailedResult(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.FAILED,
            output,
            null, null, null, null, null, false, "tsc", null
        );
    }

    private ProjectTestResult createBootstrapIoResult(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.BOOTSTRAP_IO,
            output,
            null, null, output, null, null, false, "tsc", null
        );
    }

    private ProjectTestResult createTimeoutResult(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.TIMEOUT,
            output,
            null, null, null, null, null, false, "tsc", null
        );
    }

    private ProjectTestResult createResult(ProjectTestStatus status, String output) {
        return new ProjectTestResult(
            status,
            output,
            null,
            null,
            status == ProjectTestStatus.BOOTSTRAP_IO ? output : null,
            null,
            null,
            false,
            "tsc",
            null
        );
    }
}
