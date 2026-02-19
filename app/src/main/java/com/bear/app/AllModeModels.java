package com.bear.app;

import java.util.List;

record CheckResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail
) {
}

record PrCheckResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail,
    List<String> deltaLines,
    boolean hasBoundary,
    boolean hasDeltas
) {
}

enum BlockStatus {
    PASS,
    FAIL,
    SKIP
}

record BlockExecutionResult(
    String name,
    String ir,
    String project,
    BlockStatus status,
    int exitCode,
    String category,
    String blockCode,
    String blockPath,
    String detail,
    String blockRemediation,
    String reason,
    String classification,
    List<String> deltaLines
) {
}

record RepoAggregationResult(
    int exitCode,
    int total,
    int checked,
    int passed,
    int failed,
    int skipped,
    boolean failFastTriggered,
    int rootReachFailed,
    int rootTestFailed,
    int rootTestSkippedDueToReach
) {
}
