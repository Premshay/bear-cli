package com.bear.app;

import java.util.List;

final class AllModeAggregation {
    private AllModeAggregation() {
    }

    static RepoAggregationResult aggregateCheckResults(
        List<BlockExecutionResult> results,
        boolean failFastTriggered,
        int rootReachFailed,
        int rootTestFailed,
        int rootTestSkippedDueToReach
    ) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = CliCodes.EXIT_OK;
        int rank = severityRankCheck(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankCheck(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach
        );
    }

    static RepoAggregationResult aggregatePrResults(List<BlockExecutionResult> results) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = CliCodes.EXIT_OK;
        int rank = severityRankPr(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankPr(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            false,
            0,
            0,
            0
        );
    }

    static RepoAggregationResult aggregateFixResults(List<BlockExecutionResult> results, boolean failFastTriggered) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = CliCodes.EXIT_OK;
        int rank = severityRankFix(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankFix(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            failFastTriggered,
            0,
            0,
            0
        );
    }

    static RepoAggregationResult aggregateCompileResults(List<BlockExecutionResult> results, boolean failFastTriggered) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = CliCodes.EXIT_OK;
        int rank = severityRankFix(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankFix(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            failFastTriggered,
            0,
            0,
            0
        );
    }

    static int severityRankCheck(int code) {
        return switch (code) {
            case 70 -> 1;
            case 74 -> 2;
            case 64 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            case 7 -> 6;
            case 6 -> 7;
            case 4 -> 8;
            case 0 -> 9;
            default -> 1;
        };
    }

    static int severityRankPr(int code) {
        return switch (code) {
            case 70 -> 1;
            case 74 -> 2;
            case 64 -> 3;
            case 2 -> 4;
            case 7 -> 5;
            case 5 -> 6;
            case 0 -> 7;
            default -> 1;
        };
    }

    static int severityRankFix(int code) {
        return switch (code) {
            case 70 -> 1;
            case 74 -> 2;
            case 64 -> 3;
            case 2 -> 4;
            case 0 -> 5;
            default -> 1;
        };
    }
}
