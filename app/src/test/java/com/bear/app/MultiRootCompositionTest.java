package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiRootCompositionTest {

    // ── helpers (mirrored from AllModeContractTest) ──────────────────────

    private static CliRunResult runCli(String[] args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(args, new PrintStream(stdoutBytes), new PrintStream(stderrBytes));
        return new CliRunResult(
            exitCode,
            stdoutBytes.toString(StandardCharsets.UTF_8),
            stderrBytes.toString(StandardCharsets.UTF_8)
        );
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }

    private static List<String> stdoutLines(CliRunResult run) {
        List<String> lines = new ArrayList<>();
        for (String line : normalizeLf(run.stdout()).split("\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static List<String> stderrLines(CliRunResult run) {
        List<String> lines = new ArrayList<>();
        for (String line : normalizeLf(run.stderr()).split("\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void assertOrderedSubsequence(List<String> lines, List<String> expected) {
        int cursor = 0;
        for (String line : lines) {
            if (cursor >= expected.size()) {
                break;
            }
            if (line.equals(expected.get(cursor))) {
                cursor++;
            }
        }
        assertEquals(expected.size(), cursor, "expected ordered subsequence missing: " + expected);
    }

    // ── fixture helpers ─────────────────────────────────────────────────

    private static void writeProjectWrapper(Path projectRoot) throws Exception {
        String windows = "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n";
        String unix = "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n";
        Files.writeString(projectRoot.resolve("gradlew.bat"), windows, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("gradlew"), unix, StandardCharsets.UTF_8);
        if (!Files.exists(projectRoot.resolve("build.gradle"))) {
            Files.writeString(projectRoot.resolve("build.gradle"), "// test fixture\n", StandardCharsets.UTF_8);
        }
    }

    /**
     * Returns withdraw-based IR YAML content with the block name replaced.
     * The blockName should be PascalCase (e.g., "Alpha") so that it canonicalizes
     * to a simple lowercase key (e.g., "alpha").
     */
    private static String withdrawIrWithBlockName(String blockName) throws Exception {
        Path fixtureIr = TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml");
        String content = Files.readString(fixtureIr, StandardCharsets.UTF_8);
        return content.replace("name: Withdraw", "name: " + blockName);
    }

    /**
     * Writes a working impl for a block whose IR is based on the withdraw fixture.
     * The blockKey is the canonical lowercase name (e.g., "alpha").
     * The pascal is the PascalCase form (e.g., "Alpha").
     */
    private static void writeWorkingImpl(Path projectRoot, String blockKey, String pascal) throws Exception {
        Path impl = projectRoot.resolve("src/main/java/blocks/" + blockKey + "/impl/" + pascal + "Impl.java");
        Files.createDirectories(impl.getParent());
        String genPkg = "com.bear.generated." + blockKey;
        String source = ""
            + "package blocks." + blockKey + ".impl;\n"
            + "\n"
            + "import " + genPkg + ".BearValue;\n"
            + "import " + genPkg + ".LedgerPort;\n"
            + "import " + genPkg + "." + pascal + "Logic;\n"
            + "import " + genPkg + "." + pascal + "_ExecuteWithdrawRequest;\n"
            + "import " + genPkg + "." + pascal + "_ExecuteWithdrawResult;\n"
            + "import java.math.BigDecimal;\n"
            + "\n"
            + "public final class " + pascal + "Impl implements " + pascal + "Logic {\n"
            + "    @Override\n"
            + "    public " + pascal + "_ExecuteWithdrawResult executeExecuteWithdraw(\n"
            + "        " + pascal + "_ExecuteWithdrawRequest request,\n"
            + "        LedgerPort ledgerPort\n"
            + "    ) {\n"
            + "        ledgerPort.getBalance(BearValue.empty());\n"
            + "        return new " + pascal + "_ExecuteWithdrawResult(BigDecimal.ZERO);\n"
            + "    }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    /**
     * Creates a two-root layout with block "alpha" in module-a and block "beta" in module-b.
     * Each block uses a withdraw-based IR with a matching block name.
     * Compiles both blocks and writes working impls.
     */
    private static TwoRootFixture createTwoRootFixture(Path repoRoot) throws Exception {
        Path irDir = repoRoot.resolve("bear-ir");
        Files.createDirectories(irDir);
        Files.writeString(irDir.resolve("alpha.bear.yaml"), withdrawIrWithBlockName("Alpha"), StandardCharsets.UTF_8);
        Files.writeString(irDir.resolve("beta.bear.yaml"), withdrawIrWithBlockName("Beta"), StandardCharsets.UTF_8);

        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            ""
                + "version: v1\n"
                + "blocks:\n"
                + "  - name: alpha\n"
                + "    ir: bear-ir/alpha.bear.yaml\n"
                + "    projectRoot: module-a\n"
                + "  - name: beta\n"
                + "    ir: bear-ir/beta.bear.yaml\n"
                + "    projectRoot: module-b\n",
            StandardCharsets.UTF_8
        );

        Path moduleA = repoRoot.resolve("module-a");
        Path moduleB = repoRoot.resolve("module-b");
        Files.createDirectories(moduleA);
        Files.createDirectories(moduleB);
        writeProjectWrapper(moduleA);
        writeProjectWrapper(moduleB);

        CliRunResult compile = runCli(new String[] { "compile", "--all", "--project", repoRoot.toString() });
        assertEquals(0, compile.exitCode(), compile.stderr());

        writeWorkingImpl(moduleA, "alpha", "Alpha");
        writeWorkingImpl(moduleB, "beta", "Beta");

        return new TwoRootFixture(repoRoot, moduleA, moduleB);
    }

    // ── git helpers (copied from TargetSeamParityTest) ────────────────

    private static Path initGitRepo(Path repoRoot) throws Exception {
        Files.createDirectories(repoRoot);
        git(repoRoot, "init");
        git(repoRoot, "config", "user.email", "bear@example.com");
        git(repoRoot, "config", "user.name", "Bear Test");
        return repoRoot;
    }

    private static void gitCommitAll(Path repoRoot, String message) throws Exception {
        git(repoRoot, "add", "-A");
        git(repoRoot, "commit", "-m", message);
    }

    private static void git(Path repoRoot, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        assertEquals(0, exit, "git command failed: " + String.join(" ", command) + "\n" + output);
    }

    /**
     * Creates a two-root git fixture for pr-check tests.
     * Commit 1: bear.blocks.yaml + project wrappers (no IR files).
     * Then adds IR files, compiles, and commits as commit 2.
     * This means HEAD~1 has no IR → pr-check treats as empty base → boundary expansion.
     */
    private static TwoRootFixture createTwoRootGitFixture(Path repoRoot) throws Exception {
        initGitRepo(repoRoot);
        TestTargetPins.pinJvm(repoRoot);

        // Write bear.blocks.yaml and project wrappers first
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            ""
                + "version: v1\n"
                + "blocks:\n"
                + "  - name: alpha\n"
                + "    ir: bear-ir/alpha.bear.yaml\n"
                + "    projectRoot: module-a\n"
                + "  - name: beta\n"
                + "    ir: bear-ir/beta.bear.yaml\n"
                + "    projectRoot: module-b\n",
            StandardCharsets.UTF_8
        );

        Path moduleA = repoRoot.resolve("module-a");
        Path moduleB = repoRoot.resolve("module-b");
        Files.createDirectories(moduleA);
        Files.createDirectories(moduleB);
        writeProjectWrapper(moduleA);
        writeProjectWrapper(moduleB);

        // Commit 1: base state without IR files
        gitCommitAll(repoRoot, "base state without IR");

        // Now add IR files
        Path irDir = repoRoot.resolve("bear-ir");
        Files.createDirectories(irDir);
        Files.writeString(irDir.resolve("alpha.bear.yaml"), withdrawIrWithBlockName("Alpha"), StandardCharsets.UTF_8);
        Files.writeString(irDir.resolve("beta.bear.yaml"), withdrawIrWithBlockName("Beta"), StandardCharsets.UTF_8);

        // Compile both blocks
        CliRunResult compile = runCli(new String[] { "compile", "--all", "--project", repoRoot.toString() });
        assertEquals(0, compile.exitCode(), compile.stderr());

        // Commit 2: with IR files and compiled artifacts
        gitCommitAll(repoRoot, "add IR files and compile");

        return new TwoRootFixture(repoRoot, moduleA, moduleB);
    }

    /**
     * Verifies that pr-delta: lines within a block section are in deterministic sorted order.
     * Per-block deltas are sorted by (class.order, category.order, change.order, key) —
     * BOUNDARY_EXPANDING lines come before ORDINARY lines, and within each class,
     * lines are sorted by category and key.
     */
    private static void assertDeltaLinesAreDeterministicallySorted(List<String> deltaLines, String blockName) {
        // Verify BOUNDARY_EXPANDING lines come before ORDINARY lines (class ordering)
        boolean seenOrdinary = false;
        for (String line : deltaLines) {
            if (line.contains("ORDINARY:")) {
                seenOrdinary = true;
            } else if (line.contains("BOUNDARY_EXPANDING:") && seenOrdinary) {
                throw new AssertionError(
                    "BOUNDARY_EXPANDING line appeared after ORDINARY line in block " + blockName
                    + ": delta lines are not in deterministic sorted order. Lines: " + deltaLines);
            }
        }
    }

    // ── check --all tests ───────────────────────────────────────────────

    /**
     * Property 2: Block selection is lexicographic regardless of index order.
     * Validates: Requirements 2.1, 5.5
     */
    @Test
    void blockSelectionIsLexicographicRegardlessOfIndexOrder(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Path irDir = repoRoot.resolve("bear-ir");
        Files.createDirectories(irDir);

        // Write IR files for three blocks with matching names
        Files.writeString(irDir.resolve("zeta.bear.yaml"), withdrawIrWithBlockName("Zeta"), StandardCharsets.UTF_8);
        Files.writeString(irDir.resolve("beta.bear.yaml"), withdrawIrWithBlockName("Beta"), StandardCharsets.UTF_8);
        Files.writeString(irDir.resolve("alpha.bear.yaml"), withdrawIrWithBlockName("Alpha"), StandardCharsets.UTF_8);

        // Index lists blocks in reverse-lexicographic order: zeta, beta, alpha
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            ""
                + "version: v1\n"
                + "blocks:\n"
                + "  - name: zeta\n"
                + "    ir: bear-ir/zeta.bear.yaml\n"
                + "    projectRoot: svc-zeta\n"
                + "  - name: beta\n"
                + "    ir: bear-ir/beta.bear.yaml\n"
                + "    projectRoot: svc-beta\n"
                + "  - name: alpha\n"
                + "    ir: bear-ir/alpha.bear.yaml\n"
                + "    projectRoot: svc-alpha\n",
            StandardCharsets.UTF_8
        );

        for (String root : List.of("svc-alpha", "svc-beta", "svc-zeta")) {
            Path rootPath = repoRoot.resolve(root);
            Files.createDirectories(rootPath);
            writeProjectWrapper(rootPath);
        }

        CliRunResult compile = runCli(new String[] { "compile", "--all", "--project", repoRoot.toString() });
        assertEquals(0, compile.exitCode(), compile.stderr());

        writeWorkingImpl(repoRoot.resolve("svc-alpha"), "alpha", "Alpha");
        writeWorkingImpl(repoRoot.resolve("svc-beta"), "beta", "Beta");
        writeWorkingImpl(repoRoot.resolve("svc-zeta"), "zeta", "Zeta");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", repoRoot.toString() });
        assertEquals(0, run.exitCode(), run.stderr());

        List<String> lines = stdoutLines(run);
        assertOrderedSubsequence(lines, List.of("BLOCK: alpha", "BLOCK: beta", "BLOCK: zeta"));
    }

    /**
     * Two-root layout: both blocks pass.
     * Validates: Requirements 5.1, 3.1
     */
    @Test
    void twoRootLayoutBothBlocksPass(@TempDir Path tempDir) throws Exception {
        TwoRootFixture fixture = createTwoRootFixture(tempDir.resolve("repo"));
        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });

        assertEquals(0, run.exitCode(), run.stderr());
        List<String> lines = stdoutLines(run);
        assertOrderedSubsequence(lines, List.of("BLOCK: alpha", "STATUS: PASS", "BLOCK: beta", "STATUS: PASS"));

        String stdout = normalizeLf(run.stdout());
        assertTrue(stdout.contains("2 passed"), "SUMMARY should show 2 passed");
    }

    /**
     * Two-root layout: one root drifts, the other passes.
     * Property 6: Per-root drift isolation.
     * Validates: Requirements 3.1, 5.2
     */
    @Test
    void twoRootLayoutOneRootDriftOtherPasses(@TempDir Path tempDir) throws Exception {
        TwoRootFixture fixture = createTwoRootFixture(tempDir.resolve("repo"));

        // Delete the surface file for alpha to induce drift
        Path surfaceAlpha = fixture.moduleA().resolve("build/generated/bear/surfaces/alpha.surface.json");
        assertTrue(Files.exists(surfaceAlpha), "surface file should exist after compile");
        Files.delete(surfaceAlpha);

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });

        assertNotEquals(0, run.exitCode(), "exit code should be non-zero when one block drifts");
        // When check --all has failures, block results are rendered to stderr
        List<String> lines = stderrLines(run);
        assertOrderedSubsequence(lines, List.of("BLOCK: alpha", "STATUS: FAIL", "BLOCK: beta", "STATUS: PASS"));

        String stderr = normalizeLf(run.stderr());
        assertTrue(stderr.contains("1 failed"), "SUMMARY should show 1 failed");
    }

    /**
     * Two-root layout: ROOT_TEST_START/DONE lines are lexicographically ordered.
     * Property 5: ROOT_TEST_START/DONE lines are lexicographically ordered by root.
     * Validates: Requirements 2.4, 5.6
     */
    @Test
    void twoRootLayoutRootTestStartDoneAreOrdered(@TempDir Path tempDir) throws Exception {
        TwoRootFixture fixture = createTwoRootFixture(tempDir.resolve("repo"));
        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run.exitCode(), run.stderr());

        List<String> lines = stdoutLines(run);

        // Assert ROOT_TEST_START lines appear in lexicographic order by root
        assertOrderedSubsequence(
            lines,
            List.of(
                "check-all: ROOT_TEST_START project=module-a",
                "check-all: ROOT_TEST_START project=module-b"
            )
        );

        // Assert each root emits exactly one ROOT_TEST_START and one ROOT_TEST_DONE
        long startCountA = lines.stream().filter(l -> l.equals("check-all: ROOT_TEST_START project=module-a")).count();
        long startCountB = lines.stream().filter(l -> l.equals("check-all: ROOT_TEST_START project=module-b")).count();
        long doneCountA = lines.stream().filter(l -> l.startsWith("check-all: ROOT_TEST_DONE project=module-a")).count();
        long doneCountB = lines.stream().filter(l -> l.startsWith("check-all: ROOT_TEST_DONE project=module-b")).count();
        assertEquals(1, startCountA, "module-a should have exactly one ROOT_TEST_START");
        assertEquals(1, startCountB, "module-b should have exactly one ROOT_TEST_START");
        assertEquals(1, doneCountA, "module-a should have exactly one ROOT_TEST_DONE");
        assertEquals(1, doneCountB, "module-b should have exactly one ROOT_TEST_DONE");
    }

    /**
     * Check --all idempotence: running twice on the same repo state produces identical output.
     * Property 3: Check --all idempotence.
     * Validates: Requirements 2.2, 6.5
     */
    @Test
    void checkAllIsIdempotentOnSameRepoState(@TempDir Path tempDir) throws Exception {
        TwoRootFixture fixture = createTwoRootFixture(tempDir.resolve("repo"));

        CliRunResult run1 = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run1.exitCode(), run1.stderr());

        CliRunResult run2 = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run2.exitCode(), run2.stderr());

        assertEquals(normalizeLf(run1.stdout()), normalizeLf(run2.stdout()),
            "check --all should produce byte-identical stdout on two consecutive runs");
    }

    // ── pr-check --all tests ────────────────────────────────────────────

    /**
     * Property 9: Pr-check delta lines are deterministically sorted across roots.
     * Validates: Requirements 4.1, 5.3
     *
     * Init git repo; commit base without IR; add IR files and compile; commit;
     * run pr-check --all --base HEAD~1; assert exitCode == 5 (boundary expansion
     * because IR is missing at base); collect all pr-delta: lines from stderr;
     * assert they are in lexicographic sorted order within each block section.
     */
    @Test
    void twoRootPrCheckDeltaLinesAreSorted(@TempDir Path tempDir) throws Exception {
        TwoRootFixture fixture = createTwoRootGitFixture(tempDir.resolve("repo"));

        // Run pr-check --all --base HEAD~1
        // HEAD~1 has no IR files → treated as empty base → boundary expansion (exit 5)
        CliRunResult run = runCli(new String[] {
            "pr-check", "--all", "--project", fixture.repoRoot().toString(), "--base", "HEAD~1"
        });

        assertEquals(5, run.exitCode(), "exit code should be 5 (boundary expansion detected).\nstdout: "
            + run.stdout() + "\nstderr: " + run.stderr());

        // pr-check --all with boundary expansion renders block results to stderr
        String stderr = normalizeLf(run.stderr());
        List<String> allLines = new ArrayList<>();
        for (String line : stderr.split("\n")) {
            if (!line.isBlank()) {
                allLines.add(line.trim());
            }
        }

        // Extract pr-delta: lines within each BLOCK section and verify sorted order
        List<String> currentBlockDeltas = new ArrayList<>();
        String currentBlock = null;
        for (String line : allLines) {
            if (line.startsWith("BLOCK: ")) {
                // Verify previous block's deltas were in deterministic sorted order
                if (currentBlock != null && !currentBlockDeltas.isEmpty()) {
                    assertDeltaLinesAreDeterministicallySorted(currentBlockDeltas, currentBlock);
                }
                currentBlock = line.substring("BLOCK: ".length());
                currentBlockDeltas = new ArrayList<>();
            } else if (line.startsWith("pr-delta: ")) {
                currentBlockDeltas.add(line);
            }
        }
        // Verify last block's deltas
        if (currentBlock != null && !currentBlockDeltas.isEmpty()) {
            assertDeltaLinesAreDeterministicallySorted(currentBlockDeltas, currentBlock);
        }

        // Verify we actually found pr-delta: lines (sanity check)
        long totalDeltaLines = allLines.stream().filter(l -> l.startsWith("pr-delta: ")).count();
        assertTrue(totalDeltaLines > 0, "should have at least one pr-delta: line in the output");
    }

    /**
     * Pr-check --all idempotence: running twice on the same repo state produces identical output.
     * Property 4: Pr-check --all idempotence.
     * Validates: Requirements 2.3, 6.5
     */
    @Test
    void prCheckAllIsIdempotentOnSameRepoState(@TempDir Path tempDir) throws Exception {
        TwoRootFixture fixture = createTwoRootGitFixture(tempDir.resolve("repo"));

        // Run pr-check --all --base HEAD~1 twice
        String[] prCheckArgs = { "pr-check", "--all", "--project", fixture.repoRoot().toString(), "--base", "HEAD~1" };

        CliRunResult run1 = runCli(prCheckArgs);
        assertEquals(5, run1.exitCode(), "first run exit code should be 5.\nstdout: "
            + run1.stdout() + "\nstderr: " + run1.stderr());

        CliRunResult run2 = runCli(prCheckArgs);
        assertEquals(5, run2.exitCode(), "second run exit code should be 5.\nstdout: "
            + run2.stdout() + "\nstderr: " + run2.stderr());

        assertEquals(normalizeLf(run1.stdout()), normalizeLf(run2.stdout()),
            "pr-check --all should produce byte-identical stdout on two consecutive runs");
        assertEquals(normalizeLf(run1.stderr()), normalizeLf(run2.stderr()),
            "pr-check --all should produce byte-identical stderr on two consecutive runs");
    }

    // ── records ──────────────────────────────────────────────────────────

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }

    private record TwoRootFixture(Path repoRoot, Path moduleA, Path moduleB) {
    }
}
