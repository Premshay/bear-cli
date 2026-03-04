package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearCliAgentModeTest {
    @Test
    void checkAgentModeWritesJsonOnlyToStdoutOnDeterministicFailure(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "check",
            "missing-file.bear.yaml",
            "--project",
            tempDir.toString(),
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().startsWith("{\"schemaVersion\":\"bear.nextAction.v1\""));
        assertTrue(run.stdout().contains("bear check --agent"), run.stdout());
        assertEquals("", run.stderr());
    }

    @Test
    void checkAgentModePreservesCollectAllInRerunCommand(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "check",
            "missing-file.bear.yaml",
            "--project",
            tempDir.toString(),
            "--collect=all",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().contains("bear check --collect=all --agent"), run.stdout());
        assertEquals("", run.stderr());
    }

    @Test
    void checkAgentModeEmitsProjectTestLockReasonKey(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode());
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check",
                fixture.toString(),
                "--project",
                tempDir.toString(),
                "--collect=all",
                "--agent"
            });

            assertEquals(CliCodes.EXIT_IO, run.exitCode());
            assertTrue(run.stdout().contains("\"reasonKey\":\"PROJECT_TEST_LOCK\""), run.stdout());
            assertTrue(run.stdout().contains("\"title\":\"Clear blocked check marker and retry\""));
            assertTrue(run.stdout().contains("bear unblock"));
            assertTrue(run.stdout().contains("bear check --collect=all --agent"));
            assertEquals("", run.stderr());
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void prCheckAgentModeWritesJsonOnlyToStdoutOutsideGitRepo(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        Path ir = tempDir.resolve("withdraw.bear.yaml");
        Files.copy(fixture, ir);

        CliRunResult run = runCli(new String[] {
            "pr-check",
            "withdraw.bear.yaml",
            "--project",
            tempDir.toString(),
            "--base",
            "HEAD",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().startsWith("{\"schemaVersion\":\"bear.nextAction.v1\""));
        assertTrue(run.stdout().contains("\"reasonKey\":\"NOT_A_GIT_REPO\""));
        assertEquals("", run.stderr());
    }

    @Test
    void prCheckAgentModeEmitsMergeBaseFailedReasonKey(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(
            ir,
            Files.readString(TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "base ir");

        CliRunResult run = runCli(new String[] {
            "pr-check",
            "spec/withdraw.bear.yaml",
            "--project",
            repo.toString(),
            "--base",
            "origin/does-not-exist",
            "--collect=all",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().contains("\"reasonKey\":\"MERGE_BASE_FAILED\""), run.stdout());
        assertTrue(run.stdout().contains("\"title\":\"Capture base-resolution diagnostics and escalate\""));
        assertTrue(run.stdout().contains("bear pr-check --collect=all --agent"));
        assertEquals("", run.stderr());
    }

    @Test
    void checkRejectsUnsupportedCollectValue() {
        CliRunResult run = runCli(new String[] {
            "check",
            "missing-file.bear.yaml",
            "--project",
            ".",
            "--collect=foo"
        });

        assertEquals(CliCodes.EXIT_USAGE, run.exitCode());
        assertTrue(run.stderr().contains("unsupported value for --collect"));
    }

    private static void writeWorkingWithdrawImpl(Path projectRoot) throws Exception {
        Path impl = projectRoot.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        String source = ""
            + "package blocks.withdraw.impl;\n"
            + "\n"
            + "import com.bear.generated.withdraw.BearValue;\n"
            + "import com.bear.generated.withdraw.LedgerPort;\n"
            + "import com.bear.generated.withdraw.WithdrawLogic;\n"
            + "import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawRequest;\n"
            + "import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawResult;\n"
            + "\n"
            + "public final class WithdrawImpl implements WithdrawLogic {\n"
            + "  public Withdraw_ExecuteWithdrawResult executeExecuteWithdraw(Withdraw_ExecuteWithdrawRequest request, LedgerPort ledgerPort) {\n"
            + "    ledgerPort.getBalance(BearValue.empty());\n"
            + "    return new Withdraw_ExecuteWithdrawResult(java.math.BigDecimal.ZERO);\n"
            + "  }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    private static void writeProjectWrapper(Path projectRoot, String windowsContent, String unixContent) throws Exception {
        Path wrapper = projectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        String content = isWindows() ? windowsContent : unixContent;
        Files.writeString(wrapper, content, StandardCharsets.UTF_8);
        if (!isWindows()) {
            try {
                Files.setPosixFilePermissions(wrapper, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                ));
            } catch (UnsupportedOperationException ignored) {
                // Ignore on filesystems without POSIX permissions.
            }
            wrapper.toFile().setExecutable(true, false);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

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

    private static void restoreSystemProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static CliRunResult runCli(String[] args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            args,
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );
        return new CliRunResult(
            exitCode,
            stdoutBytes.toString(StandardCharsets.UTF_8),
            stderrBytes.toString(StandardCharsets.UTF_8)
        );
    }

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}