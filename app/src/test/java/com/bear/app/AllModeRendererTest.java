package com.bear.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AllModeRendererTest {
    @Test
    void renderCheckAllOutputIncludesBlockSectionsAndSummary() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult("alpha", "spec/alpha.bear.yaml", "services/alpha", BlockStatus.PASS, 0, null, null, null, null, null, null, null, List.of()),
            new BlockExecutionResult("beta", "spec/beta.bear.yaml", "services/beta", BlockStatus.SKIP, 0, null, null, null, null, null, "DISABLED", null, List.of())
        );
        RepoAggregationResult summary = new RepoAggregationResult(0, 2, 1, 1, 0, 1, false, 0, 0, 0);

        String output = String.join("\n", AllModeRenderer.renderCheckAllOutput(results, summary));

        CliTestAsserts.assertContainsInOrder(output, List.of(
            "BLOCK: alpha",
            "STATUS: PASS",
            "BLOCK: beta",
            "REASON: DISABLED",
            "SUMMARY:",
            "2 blocks total",
            "EXIT_CODE: 0"
        ));
    }

    @Test
    void renderPrAllOutputCountsBoundaryExpandingClassifications() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult("alpha", "spec/alpha.bear.yaml", "services/alpha", BlockStatus.FAIL, 5, null, null, null, null, null, null, "BOUNDARY_EXPANDING", List.of("pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: ledger")),
            new BlockExecutionResult("beta", "spec/beta.bear.yaml", "services/beta", BlockStatus.PASS, 0, null, null, null, null, null, null, "ORDINARY", List.of("pr-delta: ORDINARY: OPS: ADDED: ledger.debit"))
        );
        RepoAggregationResult summary = new RepoAggregationResult(5, 2, 2, 1, 1, 0, false, 0, 0, 0);

        String output = String.join("\n", AllModeRenderer.renderPrAllOutput(results, summary));

        assertTrue(output.contains("BOUNDARY_EXPANDING: 1"));
        assertTrue(output.contains("DELTA:"));
        assertTrue(output.contains("pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: ledger"));
    }
}
