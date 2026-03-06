package com.bear.app;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void renderCheckAllOutputPrintsPassDetailAfterExitCode() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult(
                "alpha",
                "spec/alpha.bear.yaml",
                "services/alpha",
                BlockStatus.PASS,
                0,
                null,
                null,
                null,
                "check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=services/alpha: reason=no_selected_blocks_with_impl_allowedDeps",
                null,
                null,
                null,
                List.of()
            )
        );
        RepoAggregationResult summary = new RepoAggregationResult(0, 1, 1, 1, 0, 0, false, 0, 0, 0);

        String output = String.join("\n", AllModeRenderer.renderCheckAllOutput(results, summary));
        CliTestAsserts.assertContainsInOrder(output, List.of(
            "STATUS: PASS",
            "EXIT_CODE: 0",
            "DETAIL: check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=services/alpha: reason=no_selected_blocks_with_impl_allowedDeps",
            "SUMMARY:"
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

    @Test
    void renderPrAllOutputPreservesBlockOrderAndPlacesGovernanceThenRepoDeltaThenSummary() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult(
                "beta",
                "spec/beta.bear.yaml",
                "services/beta",
                BlockStatus.PASS,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                "NO_CHANGES",
                List.of(),
                List.of("pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: src/main/java/blocks/_shared/MegaB.java: blocks._shared.MegaB -> com.bear.generated.beta")
            ),
            new BlockExecutionResult(
                "alpha",
                "spec/alpha.bear.yaml",
                "services/alpha",
                BlockStatus.PASS,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                "NO_CHANGES",
                List.of(),
                List.of("pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: src/main/java/blocks/_shared/MegaA.java: blocks._shared.MegaA -> com.bear.generated.alpha")
            )
        );
        RepoAggregationResult summary = new RepoAggregationResult(
            0,
            2,
            2,
            2,
            0,
            0,
            false,
            0,
            0,
            0,
            List.of(
                "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: com.alpha:dep@1.0.0",
                "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: com.zeta:dep@1.0.0"
            )
        );

        String output = String.join("\n", AllModeRenderer.renderPrAllOutput(results, summary));

        CliTestAsserts.assertContainsInOrder(output, List.of(
            "BLOCK: beta",
            "BLOCK: alpha",
            "GOVERNANCE SIGNALS:",
            "MegaA",
            "MegaB",
            "REPO DELTA:",
            "com.alpha:dep@1.0.0",
            "com.zeta:dep@1.0.0",
            "SUMMARY:"
        ));
    }

    @Test
    void renderPrAllOutputPlacesGovernanceSignalsBeforeSummaryOnSuccess() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult(
                "alpha",
                "spec/alpha.bear.yaml",
                "services/alpha",
                BlockStatus.PASS,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                "NO_CHANGES",
                List.of(),
                List.of("pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: src/main/java/blocks/_shared/MegaA.java: blocks._shared.MegaA -> com.bear.generated.alpha,com.bear.generated.beta")
            ),
            new BlockExecutionResult(
                "beta",
                "spec/beta.bear.yaml",
                "services/beta",
                BlockStatus.PASS,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                "NO_CHANGES",
                List.of(),
                List.of("pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: src/main/java/blocks/_shared/MegaB.java: blocks._shared.MegaB -> com.bear.generated.alpha,com.bear.generated.gamma")
            )
        );
        RepoAggregationResult summary = new RepoAggregationResult(0, 2, 2, 2, 0, 0, false, 0, 0, 0);

        String output = String.join("\n", AllModeRenderer.renderPrAllOutput(results, summary));

        int governanceIndex = output.indexOf("GOVERNANCE SIGNALS:");
        int summaryIndex = output.indexOf("SUMMARY:");
        assertTrue(governanceIndex >= 0);
        assertTrue(summaryIndex > governanceIndex);
        assertTrue(output.contains("MegaA"));
        assertTrue(output.contains("MegaB"));
    }

    @Test
    void renderPrAllOutputOmitsGovernanceSignalsSectionOnFailure() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult(
                "alpha",
                "spec/alpha.bear.yaml",
                "services/alpha",
                BlockStatus.FAIL,
                5,
                "BOUNDARY_EXPANSION",
                "BOUNDARY_EXPANSION",
                "spec/alpha.bear.yaml",
                "detail",
                "remediation",
                null,
                "BOUNDARY_EXPANDING",
                List.of("pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: ledger"),
                List.of("pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: src/main/java/blocks/_shared/MegaA.java: blocks._shared.MegaA -> com.bear.generated.alpha,com.bear.generated.beta")
            )
        );
        RepoAggregationResult summary = new RepoAggregationResult(5, 1, 1, 0, 1, 0, false, 0, 0, 0);

        String output = String.join("\n", AllModeRenderer.renderPrAllOutput(results, summary));

        assertFalse(output.contains("GOVERNANCE SIGNALS:"));
    }

    @Test
    void renderPrAllOutputPlacesRepoDeltaBeforeSummary() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult(
                "alpha",
                "spec/alpha.bear.yaml",
                "services/alpha",
                BlockStatus.FAIL,
                5,
                "BOUNDARY_EXPANSION",
                "BOUNDARY_EXPANSION",
                "spec/alpha.bear.yaml",
                "detail",
                "remediation",
                null,
                "NO_CHANGES",
                List.of()
            )
        );
        RepoAggregationResult summary = new RepoAggregationResult(
            5,
            1,
            1,
            0,
            1,
            0,
            false,
            0,
            0,
            0,
            List.of("pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: .:_shared:com.fasterxml.jackson.core:jackson-databind@2.17.2")
        );

        String output = String.join("\n", AllModeRenderer.renderPrAllOutput(results, summary));
        CliTestAsserts.assertContainsInOrder(output, List.of(
            "REPO DELTA:",
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: .:_shared:com.fasterxml.jackson.core:jackson-databind@2.17.2",
            "SUMMARY:"
        ));
    }

    @Test
    void renderCompileAllOutputIncludesFailFastSummary() {
        List<BlockExecutionResult> results = List.of(
            new BlockExecutionResult("alpha", "spec/alpha.bear.yaml", "services/alpha", BlockStatus.PASS, 0, null, null, null, null, null, null, null, List.of()),
            new BlockExecutionResult("beta", "spec/beta.bear.yaml", "services/beta", BlockStatus.SKIP, 0, null, null, null, null, null, "FAIL_FAST_ABORT", null, List.of())
        );
        RepoAggregationResult summary = new RepoAggregationResult(0, 2, 1, 1, 0, 1, true, 0, 0, 0);

        String output = String.join("\n", AllModeRenderer.renderCompileAllOutput(results, summary));
        CliTestAsserts.assertContainsInOrder(output, List.of(
            "BLOCK: alpha",
            "STATUS: PASS",
            "BLOCK: beta",
            "REASON: FAIL_FAST_ABORT",
            "SUMMARY:",
            "FAIL_FAST_TRIGGERED: true",
            "EXIT_CODE: 0"
        ));
    }
}
