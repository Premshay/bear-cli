package com.bear.app;

import java.util.List;

/**
 * Result of a {@code bear scaffold} invocation.
 * Mirrors the pattern of {@link CompileResult}, {@link CheckResult}, etc.
 */
record ScaffoldResult(
        int exitCode,
        List<String> stdoutLines,
        List<String> stderrLines,
        String failureCode,
        String failurePath,
        String failureRemediation
) {}
