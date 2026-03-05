package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentNextActionCommandReliabilityTest {
    @Test
    void nextActionCommandsRoundTripViaRealParserAcrossSingleAndAllModes() {
        Path repoRoot = Path.of(".").toAbsolutePath().normalize();
        Path blocksPath = repoRoot.resolve("bear.blocks.yaml");

        AgentCommandContext checkSingle = AgentCommandContext.forCheckSingle(
            Path.of("spec/account-service.bear.yaml"),
            repoRoot,
            null,
            true,
            true,
            true
        );
        AgentCommandContext prCheckSingle = AgentCommandContext.forPrCheckSingle(
            "spec/account-service.bear.yaml",
            repoRoot,
            "origin/main",
            null,
            true,
            true
        );
        AgentCommandContext checkAll = AgentCommandContext.forCheckAll(
            new AllCheckOptions(repoRoot, blocksPath, Set.of(), false, false, true, true, true)
        );
        AgentCommandContext prCheckAll = AgentCommandContext.forPrCheckAll(
            new AllPrCheckOptions(repoRoot, blocksPath, Set.of(), false, "origin/main", true, true)
        );

        assertRoundTrip(checkSingle, CliCodes.EXIT_IO);
        assertRoundTrip(prCheckSingle, CliCodes.EXIT_BOUNDARY_EXPANSION);
        assertRoundTrip(checkAll, CliCodes.EXIT_IO);
        assertRoundTrip(prCheckAll, CliCodes.EXIT_BOUNDARY_EXPANSION);
    }

    private static void assertRoundTrip(AgentCommandContext expected, int exitCode) {
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            expected,
            exitCode,
            List.of(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.INFRA,
                CliCodes.IO_ERROR,
                null,
                "PROJECT_TEST_BOOTSTRAP",
                AgentDiagnostics.AgentSeverity.ERROR,
                null,
                "project.tests",
                null,
                "PROJECT_TEST_BOOTSTRAP",
                "bootstrap IO",
                java.util.Map.of()
            )),
            true
        );
        assertNotNull(payload.nextAction());
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(AgentDiagnostics.toJson(payload));
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
    }
}
