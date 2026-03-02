package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearPackageDocsConsistencyTest {
    @Test
    void packageAgentFileSetIsExact() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path agentRoot = repoRoot.resolve("docs/bear-package/.bear/agent");

        Set<String> actual = Files.walk(agentRoot)
            .filter(Files::isRegularFile)
            .map(path -> agentRoot.relativize(path).toString().replace('\\', '/'))
            .collect(Collectors.toSet());

        Set<String> expected = Set.of(
            "BOOTSTRAP.md",
            "CONTRACTS.md",
            "TROUBLESHOOTING.md",
            "REPORTING.md",
            "ref/IR_REFERENCE.md",
            "ref/BEAR_PRIMER.md",
            "ref/BLOCK_INDEX_QUICKREF.md"
        );

        assertEquals(expected, actual, "Agent package file set must match the canonical hard-cut layout");
    }

    @Test
    void legacyAgentFilesAreRemoved() {
        Path repoRoot = TestRepoPaths.repoRoot();
        String[] legacyPaths = {
            "docs/bear-package/.bear/agent/BEAR_AGENT.md",
            "docs/bear-package/.bear/agent/WORKFLOW.md",
            "docs/bear-package/.bear/agent/doc/IR_QUICKREF.md",
            "docs/bear-package/.bear/agent/doc/IR_EXAMPLES.md"
        };

        for (String legacyPath : legacyPaths) {
            assertFalse(Files.exists(repoRoot.resolve(legacyPath)), "Legacy file must be removed: " + legacyPath);
        }
    }

    @Test
    void requiredSectionAnchorsExist() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"));
        String troubleshooting = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/TROUBLESHOOTING.md"));
        String reporting = Files.readString(repoRoot.resolve("docs/bear-package/.bear/agent/REPORTING.md"));

        assertMatchesHeading(bootstrap, "(?m)^##\\s+AGENT_PACKAGE_PARITY_PRECONDITION\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+GREENFIELD_HARD_STOP\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+INDEX_REQUIRED_PREFLIGHT\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+GREENFIELD_PR_CHECK_POLICY\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+DECOMPOSITION_DEFAULT\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+DECOMPOSITION_SPLIT_TRIGGERS\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+POLICY_SCOPE_MISMATCH\\s*$");
        assertMatchesHeading(bootstrap, "(?m)^##\\s+GREENFIELD_ARTIFACT_SOURCE_RULE\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+IO_LOCK\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+PR_CHECK_EXIT_ENVELOPE_ANOMALY\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+GREENFIELD_BASELINE_PR\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+POLICY_SCOPE_MISMATCH\\s*$");
        assertMatchesHeading(troubleshooting, "(?m)^##\\s+PROCESS_VIOLATION\\s*$");
        assertMatchesHeading(reporting, "(?m)^##\\s+GREENFIELD_BASELINE_WAITING_SEMANTICS\\s*$");
        assertMatchesHeading(reporting, "(?m)^##\\s+Blocker\\s+And\\s+Anomaly\\s+Reporting\\s*$");
    }

    private static void assertMatchesHeading(String content, String headingRegex) {
        assertTrue(Pattern.compile(headingRegex).matcher(content).find(), "Expected heading anchor missing: " + headingRegex);
    }
}
