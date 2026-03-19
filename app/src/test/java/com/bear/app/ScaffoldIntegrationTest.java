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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the {@code bear scaffold} command.
 *
 * <p>Uses the same fixture patterns as {@link AllModeContractTest} and
 * {@link MultiRootCompositionTest}: {@code @TempDir}, in-process CLI invocation,
 * and a real JVM project fixture with {@code gradlew} scripts.
 */
class ScaffoldIntegrationTest {

    // ── CLI helper ────────────────────────────────────────────────────────────

    private static CliRunResult runCli(String[] args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(args, new PrintStream(stdoutBytes, false, StandardCharsets.UTF_8), new PrintStream(stderrBytes, false, StandardCharsets.UTF_8));
        return new CliRunResult(
            exitCode,
            stdoutBytes.toString(StandardCharsets.UTF_8),
            stderrBytes.toString(StandardCharsets.UTF_8)
        );
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }

    // ── Fixture helpers ───────────────────────────────────────────────────────

    /**
     * Writes {@code gradlew} / {@code gradlew.bat} and a minimal {@code build.gradle}
     * so that {@code JvmTargetDetector} recognises the directory as a JVM project.
     */
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
     * Sets up a minimal JVM project root: project wrapper + JVM target pin.
     */
    private static void setupProjectRoot(Path projectRoot) throws Exception {
        Files.createDirectories(projectRoot);
        writeProjectWrapper(projectRoot);
        TestTargetPins.pinJvm(projectRoot);
    }

    // ── Git helpers (mirrored from MultiRootCompositionTest) ─────────────────

    private static void initGitRepo(Path repoRoot) throws Exception {
        Files.createDirectories(repoRoot);
        git(repoRoot, "init");
        git(repoRoot, "config", "user.email", "bear@example.com");
        git(repoRoot, "config", "user.name", "Bear Test");
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
     * Writes a working impl for the read-store my-block scaffold.
     * The placeholder stub triggers IMPL_PLACEHOLDER; this replaces it with real logic.
     *
     * Port variable name derivation:
     *   IR port name: "myBlockStore"
     *   toPascalCase → "MyBlockStore" → interfaceName = "MyBlockStorePort"
     *   toCamelCase("MyBlockStorePort") → variableName = "myBlockStorePort"
     */
    private static void writeWorkingMyBlockImpl(Path projectRoot) throws Exception {
        // Package segment for "MyBlock" (PascalCase from IR) → canonicalTokens → ["my","block"] → "my.block"
        Path impl = projectRoot.resolve("src/main/java/blocks/my/block/impl/MyBlockImpl.java");
        Files.createDirectories(impl.getParent());
        String source = "package blocks.my.block.impl;\n"
            + "\n"
            + "import com.bear.generated.my.block.MyBlockLogic;\n"
            + "import com.bear.generated.my.block.MyBlockStorePort;\n"
            + "import com.bear.generated.my.block.MyBlock_GetMyBlockRequest;\n"
            + "import com.bear.generated.my.block.MyBlock_GetMyBlockResult;\n"
            + "\n"
            + "public final class MyBlockImpl implements MyBlockLogic {\n"
            + "    @Override\n"
            + "    public MyBlock_GetMyBlockResult executeGetMyBlock(\n"
            + "        MyBlock_GetMyBlockRequest request,\n"
            + "        MyBlockStorePort myBlockStorePort\n"
            + "    ) {\n"
            + "        myBlockStorePort.get(request.id());\n"
            + "        return new MyBlock_GetMyBlockResult(\"\");\n"
            + "    }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    /**
     * Writes a working impl for the withdraw fixture block.
     * Mirrors the pattern from AllModeContractTest.writeWorkingWithdrawImpl.
     */
    private static void writeWorkingWithdrawImpl(Path projectRoot) throws Exception {
        Path impl = projectRoot.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        String source = "package blocks.withdraw.impl;\n"
            + "\n"
            + "import com.bear.generated.withdraw.BearValue;\n"
            + "import com.bear.generated.withdraw.LedgerPort;\n"
            + "import com.bear.generated.withdraw.WithdrawLogic;\n"
            + "import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawRequest;\n"
            + "import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawResult;\n"
            + "import java.math.BigDecimal;\n"
            + "\n"
            + "public final class WithdrawImpl implements WithdrawLogic {\n"
            + "    @Override\n"
            + "    public Withdraw_ExecuteWithdrawResult executeExecuteWithdraw(\n"
            + "        Withdraw_ExecuteWithdrawRequest request,\n"
            + "        LedgerPort ledgerPort\n"
            + "    ) {\n"
            + "        ledgerPort.getBalance(BearValue.empty());\n"
            + "        return new Withdraw_ExecuteWithdrawResult(BigDecimal.ZERO);\n"
            + "    }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Property 11: Scaffold command happy path.
     * Validates: Requirements 6.1
     */
    @Test
    void scaffoldHappyPathExitsZero(@TempDir Path tempDir) throws Exception {
        setupProjectRoot(tempDir);

        CliRunResult result = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", tempDir.toString()
        });

        assertEquals(0, result.exitCode(),
            "scaffold should exit 0.\nstdout: " + result.stdout() + "\nstderr: " + result.stderr());
        assertTrue(normalizeLf(result.stdout()).contains("scaffold: OK"),
            "stdout should contain 'scaffold: OK'.\nstdout: " + result.stdout());
    }

    /**
     * Property 9: bear.blocks.yaml entry correctness.
     * Validates: Requirements 3.5
     */
    @Test
    void scaffoldWritesIrAndBlocksYaml(@TempDir Path tempDir) throws Exception {
        setupProjectRoot(tempDir);

        CliRunResult result = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", tempDir.toString()
        });
        assertEquals(0, result.exitCode(),
            "scaffold should exit 0.\nstdout: " + result.stdout() + "\nstderr: " + result.stderr());

        // IR file must exist
        Path irFile = tempDir.resolve("spec/my-block.ir.yaml");
        assertTrue(Files.exists(irFile), "spec/my-block.ir.yaml should exist after scaffold");

        // bear.blocks.yaml must exist and contain the block name
        Path blocksYaml = tempDir.resolve("bear.blocks.yaml");
        assertTrue(Files.exists(blocksYaml), "bear.blocks.yaml should exist after scaffold");
        String blocksContent = Files.readString(blocksYaml, StandardCharsets.UTF_8);
        assertTrue(blocksContent.contains("name: my-block"),
            "bear.blocks.yaml should contain 'name: my-block'.\nContent: " + blocksContent);

        // Parse with BlockIndexParser and assert entry fields
        BlockIndexParser parser = new BlockIndexParser();
        BlockIndex index = parser.parse(tempDir, blocksYaml);
        assertEquals(1, index.blocks().size(), "should have exactly one block entry");
        BlockIndexEntry entry = index.blocks().get(0);
        assertEquals("my-block", entry.name(), "block name should be 'my-block'");
        assertEquals("spec/my-block.ir.yaml", entry.ir(), "ir path should be 'spec/my-block.ir.yaml'");
        assertEquals(".", entry.projectRoot(), "projectRoot should be '.'");
    }

    /**
     * Property 8: File placement invariant.
     * Validates: Requirements 3.1, 3.2, 7.4
     */
    @Test
    void scaffoldFilePlacementInvariant(@TempDir Path tempDir) throws Exception {
        setupProjectRoot(tempDir);

        CliRunResult result = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", tempDir.toString()
        });
        assertEquals(0, result.exitCode(),
            "scaffold should exit 0.\nstdout: " + result.stdout() + "\nstderr: " + result.stderr());

        // IR must be under spec/
        Path irFile = tempDir.resolve("spec/my-block.ir.yaml");
        assertTrue(Files.exists(irFile), "IR file should be under spec/");
        assertTrue(irFile.startsWith(tempDir.resolve("spec")), "IR file must be under spec/");

        // Generated artifacts must be under build/generated/bear/
        Path generatedDir = tempDir.resolve("build/generated/bear");
        assertTrue(Files.exists(generatedDir),
            "build/generated/bear/ should exist after scaffold");

        // Impl stub must be under src/main/java/blocks/my/block/impl/
        // (sanitizePackageSegment("MyBlock") → "my.block" → path "my/block")
        Path implDir = tempDir.resolve("src/main/java/blocks/my/block/impl");
        assertTrue(Files.exists(implDir),
            "src/main/java/blocks/my/block/impl/ should exist after scaffold");

        // Walk all files under tempDir and assert none are outside the three trees
        // (excluding project wrapper files and target pin)
        List<Path> allowedRoots = List.of(
            tempDir.resolve("spec"),
            tempDir.resolve("build/generated/bear"),
            tempDir.resolve("src/main/java/blocks"),
            tempDir.resolve("gradlew"),
            tempDir.resolve("gradlew.bat"),
            tempDir.resolve("build.gradle"),
            tempDir.resolve(".bear"),
            tempDir.resolve("bear.blocks.yaml")
        );
        try (var fileStream = Files.walk(tempDir)) {
            fileStream
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    boolean allowed = allowedRoots.stream().anyMatch(root ->
                        file.startsWith(root) || file.equals(root));
                    assertTrue(allowed,
                        "Unexpected file outside allowed trees: " + tempDir.relativize(file));
                });
        }
    }

    /**
     * Property 10: bear check passes on freshly scaffolded project.
     * Validates: Requirements 3.3, 5.3
     */
    @Test
    void scaffoldThenCheckExitsZero(@TempDir Path tempDir) throws Exception {
        setupProjectRoot(tempDir);

        CliRunResult scaffold = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", tempDir.toString()
        });
        assertEquals(0, scaffold.exitCode(),
            "scaffold should exit 0.\nstdout: " + scaffold.stdout() + "\nstderr: " + scaffold.stderr());

        // Write a working impl to replace the placeholder stub (placeholder triggers IMPL_PLACEHOLDER)
        writeWorkingMyBlockImpl(tempDir);

        // Run check with the absolute IR path
        Path irFile = tempDir.resolve("spec/my-block.ir.yaml");
        CliRunResult check = runCli(new String[] {
            "check", irFile.toString(), "--project", tempDir.toString()
        });
        assertEquals(0, check.exitCode(),
            "check should exit 0 on freshly scaffolded project.\nstdout: " + check.stdout()
                + "\nstderr: " + check.stderr());
    }

    /**
     * Property 5: Compile-equivalence round-trip.
     * Validates: Requirements 2.2, 3.4, 5.2
     */
    @Test
    void scaffoldThenCompileIsEquivalent(@TempDir Path tempDir) throws Exception {
        Path dirA = tempDir.resolve("dir-a");
        Path dirB = tempDir.resolve("dir-b");

        // Set up dir A and scaffold
        setupProjectRoot(dirA);
        CliRunResult scaffold = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", dirA.toString()
        });
        assertEquals(0, scaffold.exitCode(),
            "scaffold to dir A should exit 0.\nstdout: " + scaffold.stdout() + "\nstderr: " + scaffold.stderr());

        // Set up dir B and compile the same IR
        setupProjectRoot(dirB);
        Path irA = dirA.resolve("spec/my-block.ir.yaml");
        Path irB = dirB.resolve("spec/my-block.ir.yaml");
        Files.createDirectories(irB.getParent());
        Files.copy(irA, irB);

        CliRunResult compile = runCli(new String[] {
            "compile", irB.toString(), "--project", dirB.toString()
        });
        assertEquals(0, compile.exitCode(),
            "compile on dir B should exit 0.\nstdout: " + compile.stdout() + "\nstderr: " + compile.stderr());

        // Compare generated artifacts under build/generated/bear/ between A and B
        Path genA = dirA.resolve("build/generated/bear");
        Path genB = dirB.resolve("build/generated/bear");
        assertTrue(Files.exists(genA), "build/generated/bear should exist in dir A");
        assertTrue(Files.exists(genB), "build/generated/bear should exist in dir B");

        List<Path> filesA = new ArrayList<>();
        try (var walkStream = Files.walk(genA)) {
            walkStream.filter(Files::isRegularFile).sorted().forEach(filesA::add);
        }
        assertFalse(filesA.isEmpty(), "dir A should have generated files");

        for (Path fileA : filesA) {
            Path relative = genA.relativize(fileA);
            Path fileB = genB.resolve(relative);
            assertTrue(Files.exists(fileB),
                "File missing in dir B: " + relative);
            byte[] bytesA = Files.readAllBytes(fileA);
            byte[] bytesB = Files.readAllBytes(fileB);
            assertTrue(Arrays.equals(bytesA, bytesB),
                "File content differs between scaffold and compile for: " + relative);
        }
    }

    /**
     * Property 7: Impl stub preservation.
     * Validates: Requirements 2.6
     */
    @Test
    void scaffoldPreservesExistingImplStub(@TempDir Path tempDir) throws Exception {
        setupProjectRoot(tempDir);

        // Write a custom impl stub before scaffolding
        // (impl path uses sanitized package segment: "MyBlock" → "my.block" → "my/block")
        Path stubPath = tempDir.resolve("src/main/java/blocks/my/block/impl/MyBlockImpl.java");
        Files.createDirectories(stubPath.getParent());
        String customContent = "// custom impl stub — must not be overwritten\npublic class MyBlockImpl {}\n";
        Files.writeString(stubPath, customContent, StandardCharsets.UTF_8);

        CliRunResult result = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", tempDir.toString()
        });
        assertEquals(0, result.exitCode(),
            "scaffold should exit 0.\nstdout: " + result.stdout() + "\nstderr: " + result.stderr());

        // Stub content must be unchanged
        String afterContent = Files.readString(stubPath, StandardCharsets.UTF_8);
        assertEquals(customContent, afterContent,
            "Existing impl stub must not be overwritten by scaffold");
    }

    /**
     * Scaffold then pr-check is boundary-expanding.
     * Validates: Requirements 5.4
     */
    @Test
    void scaffoldThenPrCheckIsBoundaryExpanding(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        setupProjectRoot(repoRoot);
        initGitRepo(repoRoot);

        // Commit empty state (project wrapper + target pin, no IR)
        gitCommitAll(repoRoot, "base state without IR");

        // Scaffold
        CliRunResult scaffold = runCli(new String[] {
            "scaffold", "--template", "read-store", "--block", "my-block", "--project", repoRoot.toString()
        });
        assertEquals(0, scaffold.exitCode(),
            "scaffold should exit 0.\nstdout: " + scaffold.stdout() + "\nstderr: " + scaffold.stderr());

        // Commit scaffolded state
        gitCommitAll(repoRoot, "scaffold my-block");

        // Run pr-check with repo-relative IR path
        CliRunResult prCheck = runCli(new String[] {
            "pr-check", "spec/my-block.ir.yaml",
            "--project", repoRoot.toString(),
            "--base", "HEAD~1"
        });
        assertEquals(5, prCheck.exitCode(),
            "pr-check should exit 5 (boundary expansion) for newly scaffolded block.\n"
                + "stdout: " + prCheck.stdout() + "\nstderr: " + prCheck.stderr());
    }

    /**
     * Existing commands are unaffected by scaffold addition.
     * Validates: Requirements 7.3
     */
    @Test
    void existingCommandsUnaffectedByScaffoldAddition(@TempDir Path tempDir) throws Exception {
        // Use the withdraw fixture IR
        Path fixtureIr = TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml");
        TestTargetPins.pinJvm(tempDir);
        writeProjectWrapper(tempDir);

        // validate should exit 0
        CliRunResult validate = runCli(new String[] { "validate", fixtureIr.toString() });
        assertEquals(0, validate.exitCode(),
            "validate should exit 0.\nstdout: " + validate.stdout() + "\nstderr: " + validate.stderr());

        // compile should exit 0
        CliRunResult compile = runCli(new String[] {
            "compile", fixtureIr.toString(), "--project", tempDir.toString()
        });
        assertEquals(0, compile.exitCode(),
            "compile should exit 0.\nstdout: " + compile.stdout() + "\nstderr: " + compile.stderr());

        // Write a working withdraw impl to replace the placeholder stub
        writeWorkingWithdrawImpl(tempDir);

        // check should exit 0 after compile
        CliRunResult check = runCli(new String[] {
            "check", fixtureIr.toString(), "--project", tempDir.toString()
        });
        assertEquals(0, check.exitCode(),
            "check should exit 0 after compile.\nstdout: " + check.stdout() + "\nstderr: " + check.stderr());
    }

    // ── Records ───────────────────────────────────────────────────────────────

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}
