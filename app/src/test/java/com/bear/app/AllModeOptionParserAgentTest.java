package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllModeOptionParserAgentTest {
    @Test
    void parseAllCheckOptionsSupportsCollectAllAndAgent(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(
            new String[] { "check", "--all", "--project", repoRoot.toString(), "--collect=all", "--agent" },
            err
        );

        assertNotNull(options);
        assertTrue(options.collectAll());
        assertTrue(options.agent());
        assertEquals("", errBytes.toString());
    }

    @Test
    void parseAllCheckOptionsRejectsUnsupportedCollectValue(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(
            new String[] { "check", "--all", "--project", repoRoot.toString(), "--collect=first" },
            err
        );

        assertNull(options);
        assertTrue(CliText.normalizeLf(errBytes.toString()).contains("unsupported value for --collect"));
    }

    @Test
    void parseAllPrCheckOptionsSupportsCollectAllAndAgent(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllPrCheckOptions options = AllModeOptionParser.parseAllPrCheckOptions(
            new String[] { "pr-check", "--all", "--project", repoRoot.toString(), "--base", "HEAD~1", "--collect=all", "--agent" },
            err
        );

        assertNotNull(options);
        assertTrue(options.collectAll());
        assertTrue(options.agent());
        assertEquals("", errBytes.toString());
    }

    @Test
    void parseAllPrCheckOptionsRejectsUnsupportedCollectValue(@TempDir Path repoRoot) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBytes);

        AllPrCheckOptions options = AllModeOptionParser.parseAllPrCheckOptions(
            new String[] { "pr-check", "--all", "--project", repoRoot.toString(), "--base", "HEAD~1", "--collect=first" },
            err
        );

        assertNull(options);
        assertTrue(CliText.normalizeLf(errBytes.toString()).contains("unsupported value for --collect"));
    }
}