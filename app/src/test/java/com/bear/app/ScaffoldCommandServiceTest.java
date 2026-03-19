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

class ScaffoldCommandServiceTest {

    // -------------------------------------------------------------------------
    // Helper: run the service and capture stdout/stderr
    // -------------------------------------------------------------------------

    private record Result(int exitCode, String stdout, String stderr) {}

    private static Result run(String... args) {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            int code = ScaffoldCommandService.execute(args, out, err);
            return new Result(code,
                    outBuf.toString(StandardCharsets.UTF_8),
                    errBuf.toString(StandardCharsets.UTF_8));
        }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void unknownTemplateExitsUsage(@TempDir Path tempDir) {
        Result result = run("scaffold", "--template", "unknown-xyz", "--block", "my-block",
                "--project", tempDir.toString());

        assertEquals(64, result.exitCode());
        assertTrue(result.stderr().contains(CliCodes.USAGE_UNKNOWN_TEMPLATE),
                "stderr should contain USAGE_UNKNOWN_TEMPLATE but was: " + result.stderr());
    }

    @Test
    void existingBlockExitsUsage(@TempDir Path tempDir) throws Exception {
        // Write a bear.blocks.yaml with my-block already present
        Path indexPath = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(indexPath, ""
                + "version: v1\n"
                + "blocks:\n"
                + "  - name: my-block\n"
                + "    ir: spec/my-block.ir.yaml\n"
                + "    projectRoot: .\n",
                StandardCharsets.UTF_8);

        Result result = run("scaffold", "--template", "read-store", "--block", "my-block",
                "--project", tempDir.toString());

        assertEquals(64, result.exitCode());
        assertTrue(result.stderr().contains(CliCodes.BLOCK_ALREADY_EXISTS),
                "stderr should contain BLOCK_ALREADY_EXISTS but was: " + result.stderr());
    }

    @Test
    void listPrintsSortedTemplateIds() {
        Result result = run("scaffold", "--list");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("read-store"),
                "stdout should contain 'read-store' but was: " + result.stdout());
    }

    @Test
    void existingIrFileExitsUsage(@TempDir Path tempDir) throws Exception {
        // Pre-create the IR file that scaffold would emit
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Files.writeString(specDir.resolve("my-block.ir.yaml"), "existing content", StandardCharsets.UTF_8);

        // Pin target so scaffold gets past target resolution
        TestTargetPins.pinJvm(tempDir);

        Result result = run("scaffold", "--template", "read-store", "--block", "my-block",
                "--project", tempDir.toString());

        assertEquals(64, result.exitCode());
        assertTrue(result.stderr().contains(CliCodes.BLOCK_ALREADY_EXISTS),
                "stderr should contain BLOCK_ALREADY_EXISTS but was: " + result.stderr());
    }

    @Test
    void missingTemplateFlagExitsUsage(@TempDir Path tempDir) {
        Result result = run("scaffold", "--block", "my-block",
                "--project", tempDir.toString());

        assertEquals(64, result.exitCode());
    }

    @Test
    void missingBlockFlagExitsUsage(@TempDir Path tempDir) {
        Result result = run("scaffold", "--template", "read-store",
                "--project", tempDir.toString());

        assertEquals(64, result.exitCode());
    }
}
