package com.bear.app;

import com.bear.kernel.ir.BearIr;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

record CheckResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail,
    List<AgentDiagnostics.AgentProblem> problems
) {
    CheckResult(
        int exitCode,
        List<String> stdoutLines,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        this(exitCode, stdoutLines, stderrLines, category, failureCode, failurePath, failureRemediation, detail, List.of());
    }
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
    boolean hasDeltas,
    List<String> governanceLines,
    List<AgentDiagnostics.AgentProblem> problems,
    PrGovernanceTelemetry.Snapshot governanceSnapshot
) {
    PrCheckResult(
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
        this(
            exitCode,
            stdoutLines,
            stderrLines,
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            deltaLines,
            hasBoundary,
            hasDeltas,
            List.of(),
            List.of(),
            null
        );
    }

    PrCheckResult(
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
        boolean hasDeltas,
        List<String> governanceLines
    ) {
        this(
            exitCode,
            stdoutLines,
            stderrLines,
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            deltaLines,
            hasBoundary,
            hasDeltas,
            governanceLines,
            List.of(),
            null
        );
    }

    PrCheckResult(
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
        boolean hasDeltas,
        List<String> governanceLines,
        List<AgentDiagnostics.AgentProblem> problems
    ) {
        this(
            exitCode,
            stdoutLines,
            stderrLines,
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            deltaLines,
            hasBoundary,
            hasDeltas,
            governanceLines,
            problems,
            null
        );
    }
}

record FixResult(
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

record CompileResult(
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
    List<String> deltaLines,
    List<String> governanceLines,
    List<AgentDiagnostics.AgentProblem> problems
) {
    BlockExecutionResult(
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
        this(
            name,
            ir,
            project,
            status,
            exitCode,
            category,
            blockCode,
            blockPath,
            detail,
            blockRemediation,
            reason,
            classification,
            deltaLines,
            List.of(),
            List.of()
        );
    }

    BlockExecutionResult(
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
        List<String> deltaLines,
        List<String> governanceLines
    ) {
        this(
            name,
            ir,
            project,
            status,
            exitCode,
            category,
            blockCode,
            blockPath,
            detail,
            blockRemediation,
            reason,
            classification,
            deltaLines,
            governanceLines,
            List.of()
        );
    }
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
    int rootTestSkippedDueToReach,
    List<String> repoDeltaLines
) {
    RepoAggregationResult(
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
        this(
            exitCode,
            total,
            checked,
            passed,
            failed,
            skipped,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach,
            List.of()
        );
    }

}

record CheckBlockedState(boolean blocked, String reason, String detail) {
    static CheckBlockedState notBlocked() {
        return new CheckBlockedState(false, "", "");
    }

    String summary() {
        return "reason=" + reason + "; detail=" + detail;
    }
}


record AllCheckOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean failFast,
    boolean strictOrphans,
    boolean strictHygiene,
    boolean collectAll,
    boolean agent
) {
}

record AllCompileOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean failFast,
    boolean strictOrphans
) {
}

record AllFixOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean failFast,
    boolean strictOrphans
) {
}

record AllPrCheckOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean strictOrphans,
    String baseRef,
    boolean collectAll,
    boolean agent
) {
}
enum DriftType {
    ADDED("ADDED", 0),
    REMOVED("REMOVED", 1),
    CHANGED("CHANGED", 2);

    final String label;
    final int order;

    DriftType(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

record DriftItem(String path, DriftType type) {
}

enum BoundaryType {
    CAPABILITY_ADDED("CAPABILITY_ADDED", 0),
    PURE_DEP_ADDED("PURE_DEP_ADDED", 1),
    PURE_DEP_VERSION_CHANGED("PURE_DEP_VERSION_CHANGED", 2),
    CAPABILITY_OP_ADDED("CAPABILITY_OP_ADDED", 3),
    INVARIANT_RELAXED("INVARIANT_RELAXED", 4);

    final String label;
    final int order;

    BoundaryType(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

record BoundarySignal(BoundaryType type, String key) {
}

enum PrClass {
    BOUNDARY_EXPANDING("BOUNDARY_EXPANDING", 0),
    ORDINARY("ORDINARY", 1);

    final String label;
    final int order;

    PrClass(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

enum PrCategory {
    SURFACE("SURFACE", 0),
    PORTS("PORTS", 1),
    ALLOWED_DEPS("ALLOWED_DEPS", 2),
    OPS("OPS", 3),
    IDEMPOTENCY("IDEMPOTENCY", 4),
    CONTRACT("CONTRACT", 5),
    INVARIANTS("INVARIANTS", 6);

    final String label;
    final int order;

    PrCategory(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

enum PrChange {
    CHANGED("CHANGED", 0),
    ADDED("ADDED", 1),
    REMOVED("REMOVED", 2);

    final String label;
    final int order;

    PrChange(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

record PrDelta(PrClass clazz, PrCategory category, PrChange change, String key) {
}

record PrSurface(
    TreeSet<String> externalPorts,
    Map<String, TreeSet<String>> externalOpsByPort,
    TreeSet<String> blockEdges,
    Map<String, String> allowedDeps,
    BearIr.BlockIdempotency blockIdempotency,
    TreeSet<String> blockInvariants,
    TreeSet<String> operations,
    Map<String, Map<String, BearIr.FieldType>> inputsByOperation,
    Map<String, Map<String, BearIr.FieldType>> outputsByOperation,
    Map<String, TreeSet<String>> usesByOperation,
    Map<String, BearIr.OperationIdempotency> idempotencyByOperation,
    Map<String, TreeSet<String>> invariantsByOperation
) {
}

record BoundaryManifest(
    String schemaVersion,
    String target,
    String block,
    String irHash,
    String generatorVersion,
    Map<String, TreeSet<String>> capabilities,
    Map<String, String> allowedDeps,
    TreeSet<String> invariants
) {
}



