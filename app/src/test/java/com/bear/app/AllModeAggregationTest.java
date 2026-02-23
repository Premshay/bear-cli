package com.bear.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllModeAggregationTest {
    @Test
    void aggregateCheckResultsUsesConfiguredSeverityOrdering() {
        List<BlockExecutionResult> results = List.of(
            block("a", BlockStatus.FAIL, 4),
            block("b", BlockStatus.FAIL, 74),
            block("c", BlockStatus.PASS, 0),
            block("d", BlockStatus.SKIP, 0)
        );

        RepoAggregationResult summary = AllModeAggregation.aggregateCheckResults(results, false, 0, 0, 0);

        assertEquals(74, summary.exitCode());
        assertEquals(1, summary.passed());
        assertEquals(2, summary.failed());
        assertEquals(1, summary.skipped());
        assertEquals(3, summary.checked());
    }

    @Test
    void aggregatePrResultsPrefersBoundaryExpansionOverOk() {
        RepoAggregationResult summary = AllModeAggregation.aggregatePrResults(List.of(
            block("a", BlockStatus.PASS, 0),
            block("b", BlockStatus.FAIL, 5)
        ));

        assertEquals(5, summary.exitCode());
        assertEquals(1, summary.failed());
    }

    @Test
    void aggregateCheckResultsPrefersStructuralBypassOverReachAndTests() {
        RepoAggregationResult summary = AllModeAggregation.aggregateCheckResults(List.of(
            block("a", BlockStatus.FAIL, 4),
            block("b", BlockStatus.FAIL, 6),
            block("c", BlockStatus.FAIL, 7)
        ), false, 0, 0, 0);

        assertEquals(7, summary.exitCode());
    }

    @Test
    void aggregatePrResultsPrefersStructuralBypassOverBoundaryExpansion() {
        RepoAggregationResult summary = AllModeAggregation.aggregatePrResults(List.of(
            block("a", BlockStatus.FAIL, 5),
            block("b", BlockStatus.FAIL, 7)
        ));

        assertEquals(7, summary.exitCode());
    }

    @Test
    void aggregateFixResultsTracksFailFastFlag() {
        RepoAggregationResult summary = AllModeAggregation.aggregateFixResults(List.of(
            block("a", BlockStatus.FAIL, 2),
            block("b", BlockStatus.SKIP, 0)
        ), true);

        assertEquals(2, summary.exitCode());
        assertEquals(1, summary.failed());
        assertEquals(1, summary.skipped());
        assertEquals(true, summary.failFastTriggered());
    }

    @Test
    void aggregateCompileResultsTracksFailuresAndFailFastFlag() {
        RepoAggregationResult summary = AllModeAggregation.aggregateCompileResults(List.of(
            block("a", BlockStatus.FAIL, 2),
            block("b", BlockStatus.SKIP, 0),
            block("c", BlockStatus.PASS, 0)
        ), true);

        assertEquals(2, summary.exitCode());
        assertEquals(1, summary.failed());
        assertEquals(1, summary.skipped());
        assertEquals(1, summary.passed());
        assertEquals(true, summary.failFastTriggered());
    }

    private static BlockExecutionResult block(String name, BlockStatus status, int code) {
        return new BlockExecutionResult(
            name,
            "spec/" + name + ".bear.yaml",
            "services/" + name,
            status,
            code,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of()
        );
    }
}
