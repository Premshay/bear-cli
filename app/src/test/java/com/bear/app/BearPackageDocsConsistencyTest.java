package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
    void legacyAgentFilesAreRemoved() throws Exception {
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
    void bootstrapIsBoundedAndContainsDoneGates() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path bootstrap = repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md");
        String content = Files.readString(bootstrap, StandardCharsets.UTF_8);
        long lineCount;
        try (var lines = Files.lines(bootstrap)) {
            lineCount = lines.count();
        }

        assertTrue(lineCount <= 200, "BOOTSTRAP.md must stay within the 200-line budget");
        assertTrue(content.contains("bear check --all --project <repoRoot>"));
        assertTrue(content.contains("bear pr-check --all --project <repoRoot> --base <ref>"));
    }

    @Test
    void hardeningClausesArePresentAndDocPathDriftIsBlocked() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String bootstrap = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/BOOTSTRAP.md"),
                StandardCharsets.UTF_8
        );
        String contracts = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/CONTRACTS.md"),
                StandardCharsets.UTF_8
        );
        String reporting = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/REPORTING.md"),
                StandardCharsets.UTF_8
        );
        String troubleshooting = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/TROUBLESHOOTING.md"),
                StandardCharsets.UTF_8
        );
        String irReference = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/ref/IR_REFERENCE.md"),
                StandardCharsets.UTF_8
        );

        assertTrue(bootstrap.contains("If `bear.blocks.yaml` exists, treat as multi-block regardless of IR file count."));
        assertTrue(bootstrap.contains("Decomposition signals are defined in `CONTRACTS.md` (single source)."));
        assertTrue(bootstrap.contains("IR files MUST be created under `spec/`"));
        assertTrue(bootstrap.contains("Do not encode multiple externally visible operations as an action/command enum multiplexer"));
        assertTrue(bootstrap.contains("Do not use `--base HEAD` unless explicitly instructed."));
        assertTrue(bootstrap.contains("validate that exact created path (do not validate a different file)"));
        assertTrue(bootstrap.contains("TODO: replace this entire method body|Do not append logic below this placeholder return"));
        assertTrue(contracts.contains("## Decomposition Signals (Normative)"));
        assertTrue(contracts.contains("## Contract Modeling Anti-Patterns (Normative)"));
        assertTrue(contracts.contains("MUST NOT encode multiple externally visible operations as an action/command enum multiplexer"));
        assertFalse(contracts.contains("Completion is valid only with both gates evidenced green"));
        assertTrue(reporting.contains("Copy this count from the `pr-check` output of that exact completion run; do not infer."));
        assertTrue(reporting.contains("PR base used: <ref>"));
        assertTrue(reporting.contains("PR base rationale: <merge-base against target branch OR user-provided base SHA>"));
        assertTrue(reporting.contains("PR classification interpretation: <expected|unintended> - <brief rationale>"));
        assertFalse(reporting.contains("--base HEAD"));
        assertTrue(troubleshooting.contains("Schema/path mismatch or missing routed docs"));
        assertTrue(troubleshooting.contains("verify destination `.bear/agent/**` tree exactly matches source package tree."));
        assertTrue(troubleshooting.contains("## BOUNDARY_EXPANSION_DETECTED"));
        assertTrue(troubleshooting.contains("This can be expected when adding/changing blocks, contracts, effects, idempotency, invariants, or governed adapter boundaries."));
        assertTrue(troubleshooting.contains("`--base HEAD` can misclassify or hide intended delta unless explicitly instructed."));
        assertTrue(irReference.contains("generated logic signatures exclude the idempotency store port"));
        assertTrue(irReference.contains("wrapper enforcement binds idempotency via IR-declared `idempotency.store`"));
        assertTrue(irReference.contains("Strict policy format contract: see `.bear/agent/CONTRACTS.md`."));

        Path agentRoot = repoRoot.resolve("docs/bear-package/.bear/agent");
        try (var files = Files.walk(agentRoot)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(content.contains(".bear/agent/doc/"),
                        "Packaged agent docs must not reference retired doc/ paths: " + file);
            }
        }
    }

    @Test
    void irReferenceUsesV1ForIrSchemas() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String content = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/ref/IR_REFERENCE.md"),
                StandardCharsets.UTF_8
        );

        Pattern deprecatedIrVersion = Pattern.compile("(?im)^\\s*version:\\s*v0\\s*\\R\\s*block:");
        assertFalse(deprecatedIrVersion.matcher(content).find(), "IR examples must use version: v1");
        assertFalse(content.contains("valid BEAR v0"), "IR examples must not describe BEAR v0 IR as canonical");
    }

    @Test
    void primerUsesV1InvariantLanguage() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String content = Files.readString(
                repoRoot.resolve("docs/bear-package/.bear/agent/ref/BEAR_PRIMER.md"),
                StandardCharsets.UTF_8
        );
        assertFalse(content.contains("v0 supports `non_negative`"));
        assertTrue(content.contains("v1 supports `non_negative`"));
    }
}

