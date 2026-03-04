package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertEquals("", run.stderr());
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