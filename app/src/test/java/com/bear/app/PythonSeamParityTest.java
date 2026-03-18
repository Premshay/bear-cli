package com.bear.app;

import com.bear.kernel.target.python.PythonTarget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that explicit-target Python check/pr-check results match registry-routed results.
 * Mirrors TargetSeamParityTest for the Python target.
 */
class PythonSeamParityTest {

    @Test
    void explicitPythonCheckMatchesRegistryRoutedCheck(@TempDir Path tempDir) throws Exception {
        Path irFile = writePythonIr(tempDir, "UserAuth", "user-auth");
        TestTargetPins.pinPython(tempDir);

        CompileResult compileResult = BearCli.executeCompile(irFile, tempDir, null, null);
        assertEquals(0, compileResult.exitCode(), "compile should succeed: " + compileResult.stderrLines());

        writePythonImplStub(tempDir, "user-auth", "user_auth");

        CheckResult explicit = CheckCommandService.executeCheck(
            irFile, tempDir, true, false, null, null,
            null, true, null, false,
            new PythonTarget()
        );
        CheckResult routed = CheckCommandService.executeCheck(
            irFile, tempDir, true, false, null, null,
            null, true, null, false
        );

        assertEquals(explicit, routed);
    }

    @Test
    void explicitPythonPrCheckMatchesRegistryRoutedPrCheck(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        TestTargetPins.pinPython(repo);
        Path irFile = repo.resolve("bear-ir/user-auth.bear.yaml");
        Files.createDirectories(irFile.getParent());
        Files.writeString(irFile, pythonIrContent("UserAuth"), StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        PrCheckResult explicit = PrCheckCommandService.executePrCheck(
            repo,
            "bear-ir/user-auth.bear.yaml",
            "HEAD",
            true,
            ".",
            null,
            false,
            new PythonTarget()
        );
        PrCheckResult routed = PrCheckCommandService.executePrCheck(
            repo,
            "bear-ir/user-auth.bear.yaml",
            "HEAD",
            null,
            false
        );

        assertEquals(explicit.exitCode(), routed.exitCode());
        assertEquals(explicit.stdoutLines(), routed.stdoutLines());
        assertEquals(explicit.stderrLines(), routed.stderrLines());
        assertEquals(explicit.failureCode(), routed.failureCode());
        assertEquals(explicit.failurePath(), routed.failurePath());
        assertEquals(explicit.failureRemediation(), routed.failureRemediation());
        assertEquals(explicit.detail(), routed.detail());
        assertEquals(explicit.deltaLines(), routed.deltaLines());
        assertEquals(explicit.hasBoundary(), routed.hasBoundary());
        assertEquals(explicit.hasDeltas(), routed.hasDeltas());
        assertEquals(explicit.governanceLines(), routed.governanceLines());
    }

    // ---- IR helpers ----

    private static Path writePythonIr(Path projectRoot, String blockName, String blockKey) throws Exception {
        Path irFile = projectRoot.resolve(blockKey + ".bear.yaml");
        Files.writeString(irFile, pythonIrContent(blockName), StandardCharsets.UTF_8);
        return irFile;
    }

    private static String pythonIrContent(String blockName) {
        // Echo-safe: output mirrors input name:type, no idempotency/invariants, effects.allow: []
        return "version: v1\n"
            + "block:\n"
            + "  name: " + blockName + "\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: process\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: input\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: input\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n";
    }

    private static void writePythonImplStub(Path projectRoot, String blockKey, String modulePrefix) throws Exception {
        Path implDir = projectRoot.resolve("src/blocks/" + blockKey + "/impl");
        Files.createDirectories(implDir);
        Path implFile = implDir.resolve(modulePrefix + "_impl.py");
        if (!Files.exists(implFile)) {
            Files.writeString(implFile, "# stub impl\n", StandardCharsets.UTF_8);
        }
    }

    // ---- Git helpers (copied from TargetSeamParityTest) ----

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
}
