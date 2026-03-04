package com.bear.app;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDiagnosticsTest {
    @Test
    void payloadIsDeterministicAcrossInputOrder() {
        List<AgentDiagnostics.AgentProblem> problems = List.of(
            problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, "DIRECT_IMPL_USAGE", null, "src/main/java/blocks/a/impl/A.java"),
            problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "PROJECT_TEST_LOCK", "build.gradle"),
            problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.UNDECLARED_REACH, CliCodes.UNDECLARED_REACH, null, "src/main/java/blocks/a/impl/B.java")
        );

        ArrayList<AgentDiagnostics.AgentProblem> shuffled = new ArrayList<>(problems);
        Collections.shuffle(shuffled);

        AgentDiagnostics.AgentPayload first = AgentDiagnostics.payload("check", "single", "all", CliCodes.EXIT_IO, problems);
        AgentDiagnostics.AgentPayload second = AgentDiagnostics.payload("check", "single", "all", CliCodes.EXIT_IO, shuffled);

        assertEquals(first.problems(), second.problems());
        assertEquals(first.clusters(), second.clusters());
        assertEquals(AgentDiagnostics.toJson(first), AgentDiagnostics.toJson(second));
        assertNotNull(first.nextAction());
        assertTrue(first.clusters().stream().anyMatch(cluster -> cluster.clusterId().equals(first.nextAction().primaryClusterId())));
    }

    @Test
    void payloadTruncationPreservesBreadthAndPrimaryCluster() {
        ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            problems.add(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "DIRECT_IMPL_USAGE",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/a/impl/A.java",
                null,
                "DIRECT_IMPL_USAGE_" + i,
                "DIRECT_IMPL_USAGE_" + i,
                java.util.Map.of("templateVariant", "direct_" + i)
            ));
            problems.add(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "IMPL_CONTAINMENT_BYPASS",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/b/impl/B.java",
                null,
                "IMPL_CONTAINMENT_BYPASS_" + i,
                "IMPL_CONTAINMENT_BYPASS_" + i,
                java.util.Map.of("templateVariant", "containment_" + i)
            ));
            problems.add(AgentDiagnostics.problem(
                AgentDiagnostics.AgentCategory.GOVERNANCE,
                CliCodes.BOUNDARY_BYPASS,
                "BLOCK_PORT_IMPL_INVALID",
                null,
                AgentDiagnostics.AgentSeverity.ERROR,
                "alpha",
                "src/main/java/blocks/c/impl/C.java",
                null,
                "BLOCK_PORT_IMPL_INVALID_" + i,
                "BLOCK_PORT_IMPL_INVALID_" + i,
                java.util.Map.of("templateVariant", "port_" + i)
            ));
        }

        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload("check", "all", "all", CliCodes.EXIT_BOUNDARY_BYPASS, problems);

        assertTrue(payload.truncated());
        assertEquals(200, payload.problems().size());
        assertEquals(10, payload.suppressedViolations());
        assertEquals(3, payload.clusters().size());
        assertTrue(payload.problems().stream().anyMatch(problem -> "DIRECT_IMPL_USAGE".equals(problem.ruleId())));
        assertTrue(payload.problems().stream().anyMatch(problem -> "IMPL_CONTAINMENT_BYPASS".equals(problem.ruleId())));
        assertTrue(payload.problems().stream().anyMatch(problem -> "BLOCK_PORT_IMPL_INVALID".equals(problem.ruleId())));

        AgentDiagnostics.AgentCluster primaryCluster = payload.clusters().stream()
            .filter(cluster -> cluster.clusterId().equals(payload.nextAction().primaryClusterId()))
            .findFirst()
            .orElseThrow();
        assertEquals(70, primaryCluster.count());
        assertEquals(1, primaryCluster.files().size());
        assertFalse(primaryCluster.filesTruncated());
    }


    @Test
    void jsonSchemaOmitsResultCategoryAndIncludesVersionAnchor() {
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            "check",
            "single",
            "first",
            CliCodes.EXIT_IO,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, null, "PROJECT_TEST_BOOTSTRAP", "project.tests"))
        );

        String json = AgentDiagnostics.toJson(payload);
        assertTrue(json.contains("\"schemaVersion\":\"bear.nextAction.v1\""));
        assertFalse(json.contains("resultCategory"));
    }

    @Test
    void governanceRegistryRuleIdsProduceTemplateBackedNextAction() {
        for (String ruleId : GovernanceRuleRegistry.PUBLIC_RULE_IDS) {
            AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
                "check",
                "single",
                "first",
                CliCodes.EXIT_BOUNDARY_BYPASS,
                List.of(problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, ruleId, null, "src/main/java/blocks/x/impl/X.java"))
            );
            assertNotNull(payload.nextAction(), "Missing nextAction for ruleId: " + ruleId);
            assertFalse(payload.nextAction().commands().isEmpty(), "Missing commands for ruleId: " + ruleId);
        }
    }

    @Test
    void unknownTemplateKeyFallsBackToSafeInfraTemplate() {
        AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
            "check",
            "single",
            "first",
            CliCodes.EXIT_INTERNAL,
            List.of(problem(AgentDiagnostics.AgentCategory.INFRA, "SOME_NEW_FAILURE", null, "SOME_NEW_REASON", "internal"))
        );

        assertNotNull(payload.nextAction());
        assertEquals("INFRA", payload.nextAction().kind());
        assertTrue(payload.nextAction().title().toLowerCase().contains("capture"));
    }

    private static AgentDiagnostics.AgentProblem problem(
        AgentDiagnostics.AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        String file
    ) {
        return AgentDiagnostics.problem(
            category,
            failureCode,
            ruleId,
            reasonKey,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            file,
            null,
            ruleId != null ? ruleId : reasonKey,
            failureCode,
            java.util.Map.of()
        );
    }
}