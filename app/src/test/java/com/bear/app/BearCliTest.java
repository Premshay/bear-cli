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

class BearCliTest {
    @Test
    void validateFixturePrintsGoldenCanonicalYaml() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path golden = repoRoot.resolve("spec/golden/withdraw.canonical.yaml");

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", fixture.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderrBytes.toString());

        String expected = normalizeLf(Files.readString(golden));
        String actual = normalizeLf(stdoutBytes.toString());
        assertEquals(expected, actual);
    }

    @Test
    void validateRequiresOneFileArg() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(64, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("usage: INVALID_ARGS:"));
    }

    @Test
    void unknownCommandIsUsageError() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "wat" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(64, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("usage: UNKNOWN_COMMAND:"));
    }

    @Test
    void validateMissingFileReturnsIoExitCode() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", "does-not-exist.yaml" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(74, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("io: IO_ERROR:"));
    }

    @Test
    void validateSchemaErrorPrintsDeterministicPrefix(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.bear.yaml");
        Files.writeString(invalid, ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  extra: true\n");

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", invalid.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(2, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("schema at block: UNKNOWN_KEY:"));
    }

    @Test
    void compileRequiresExpectedArgs() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "compile" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(64, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("usage: INVALID_ARGS:"));
    }

    @Test
    void compileMissingIrReturnsIoExitCode(@TempDir Path tempDir) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "compile", "missing.yaml", "--project", tempDir.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(74, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("io: IO_ERROR:"));
    }

    @Test
    void compileFixtureGeneratesArtifactsAndPreservesExistingImpl(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        Path impl = tempDir.resolve("src/main/java/com/bear/generated/withdraw/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "package com.bear.generated.withdraw;\npublic final class WithdrawImpl {}\n");

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "compile", fixture.toString(), "--project", tempDir.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderrBytes.toString());

        Path generatedRoot = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw");
        assertTrue(Files.exists(generatedRoot.resolve("Withdraw.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawLogic.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawRequest.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawResult.java")));
        assertTrue(Files.exists(generatedRoot.resolve("LedgerPort.java")));
        assertTrue(Files.exists(generatedRoot.resolve("IdempotencyPort.java")));

        assertEquals("package com.bear.generated.withdraw;\npublic final class WithdrawImpl {}\n", Files.readString(impl));
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }
}
