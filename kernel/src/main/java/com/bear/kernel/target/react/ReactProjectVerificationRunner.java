package com.bear.kernel.target.react;

import com.bear.kernel.target.ProjectTestResult;
import com.bear.kernel.target.ProjectTestStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs pnpm exec tsc --noEmit -p tsconfig.json as the project verification step.
 * Follows PythonProjectVerificationRunner as the structural model.
 */
public final class ReactProjectVerificationRunner {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final String TSC_MODULE_NOT_FOUND = "Cannot find module";
    private static final String TSC_ERROR_TS2307 = "error TS2307";

    private ReactProjectVerificationRunner() {
    }

    /**
     * Runs pnpm exec tsc --noEmit -p tsconfig.json in the project root.
     *
     * @param projectRoot the project root directory
     * @return ProjectTestResult with status and output
     * @throws IOException if process creation fails
     * @throws InterruptedException if the process is interrupted
     */
    public static ProjectTestResult run(Path projectRoot) throws IOException, InterruptedException {
        if (!isPnpmAvailable()) {
            return toolMissing("pnpm not found on PATH");
        }

        List<String> command = List.of("pnpm", "exec", "tsc", "--noEmit", "-p", "tsconfig.json");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        Thread outputReader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(outputBuffer);
            } catch (IOException ignored) {
                // Best-effort capture only
            }
        }, "bear-react-verification-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startTime;

        if (!finished) {
            destroyProcess(process);
            process.waitFor(5, TimeUnit.SECONDS);
            joinOutputReader(outputReader);
            String output = outputBuffer.toString(StandardCharsets.UTF_8);
            return timeout(output, durationMs);
        }

        joinOutputReader(outputReader);
        String output = outputBuffer.toString(StandardCharsets.UTF_8);
        int exitCode = process.exitValue();

        if (exitCode == 0) {
            return passed(output, durationMs);
        }

        // Check for tsc not installed (module not found errors)
        if (isTscMissing(output)) {
            return toolMissing(output);
        }

        return failed(output, durationMs);
    }

    /**
     * Checks if pnpm is available on the system PATH.
     *
     * @return true if pnpm is available, false otherwise
     */
    static boolean isPnpmAvailable() {
        try {
            List<String> command = isWindows()
                ? List.of("where", "pnpm")
                : List.of("which", "pnpm");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static boolean isTscMissing(String output) {
        if (output == null) {
            return false;
        }
        String lower = output.toLowerCase(Locale.ROOT);

        // Module-not-found errors referencing the TypeScript compiler itself
        boolean moduleNotFoundForTypescript =
            output.contains(TSC_MODULE_NOT_FOUND)
                && (lower.contains("typescript/lib/tsc.js")
                    || lower.contains("node_modules/.bin/tsc")
                    || lower.contains("typescript'"));

        // Shell/pnpm messages indicating the tsc command is missing
        boolean tscCommandNotFound =
            lower.contains("command \"tsc\" not found")
                || lower.contains("command 'tsc' not found")
                || lower.contains("unknown command \"tsc\"")
                || lower.contains("unknown command 'tsc'")
                || lower.contains("tsc: command not found")
                || lower.contains("tsc is not recognized");

        return moduleNotFoundForTypescript || tscCommandNotFound;
    }

    private static void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.toHandle().descendants().forEach(child -> {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            });
        } catch (Exception ignored) {
            // Best effort
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static void joinOutputReader(Thread outputReader) throws InterruptedException {
        if (outputReader != null) {
            outputReader.join(2000L);
        }
    }

    private static ProjectTestResult passed(String output, long durationMs) {
        return new ProjectTestResult(
            ProjectTestStatus.PASSED,
            output,
            null,  // attemptTrail
            null,  // firstLockLine
            null,  // firstBootstrapLine
            null,  // firstSharedDepsViolationLine
            null,  // cacheMode
            false, // fallbackToUserCache
            "tsc", // phase
            null   // lastObservedTask
        );
    }

    private static ProjectTestResult failed(String output, long durationMs) {
        return new ProjectTestResult(
            ProjectTestStatus.FAILED,
            output,
            null,
            null,
            null,
            null,
            null,
            false,
            "tsc",
            null
        );
    }

    private static ProjectTestResult timeout(String output, long durationMs) {
        return new ProjectTestResult(
            ProjectTestStatus.TIMEOUT,
            output,
            null,
            null,
            null,
            null,
            null,
            false,
            "tsc",
            null
        );
    }

    private static ProjectTestResult toolMissing(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.BOOTSTRAP_IO,
            output,
            null,
            null,
            output,  // firstBootstrapLine - use output as the bootstrap message
            null,
            null,
            false,
            "tsc",
            null
        );
    }
}
