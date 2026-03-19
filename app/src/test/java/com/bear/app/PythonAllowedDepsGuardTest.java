package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the impl.allowedDeps unsupported guard with Python target.
 * Mirrors NodeAllowedDepsGuardTest for the Python target.
 */
class PythonAllowedDepsGuardTest {

    /**
     * Block with impl.allowedDeps under Python target → check fails, exit 64, CODE=UNSUPPORTED_TARGET.
     */
    @Test
    void checkWithAllowedDepsAndPythonTarget_failsWithExit64(@TempDir Path tempDir) throws Exception {
        Path irFile = writePythonIrWithAllowedDeps(tempDir, "PaymentService");
        PythonTarget pythonTarget = new PythonTarget();
        compilePythonProject(pythonTarget, irFile, tempDir, "payment-service");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, true, null, false, pythonTarget
        );
        assertEquals(64, check.exitCode(), "check with allowedDeps + Python should exit 64");
        assertEquals("UNSUPPORTED_TARGET", check.failureCode());
    }

    /**
     * Error output includes IR file path.
     */
    @Test
    void checkWithAllowedDepsAndPythonTarget_errorIncludesIrPath(@TempDir Path tempDir) throws Exception {
        Path irFile = writePythonIrWithAllowedDeps(tempDir, "PaymentService");
        PythonTarget pythonTarget = new PythonTarget();
        compilePythonProject(pythonTarget, irFile, tempDir, "payment-service");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, true, null, false, pythonTarget
        );
        assertEquals(64, check.exitCode());
        assertNotNull(check.failurePath());
        assertTrue(check.failurePath().contains("payment-service.bear.yaml"),
            "error should include IR file path, got: " + check.failurePath());
    }

    /**
     * Error output includes remediation message.
     */
    @Test
    void checkWithAllowedDepsAndPythonTarget_errorIncludesRemediation(@TempDir Path tempDir) throws Exception {
        Path irFile = writePythonIrWithAllowedDeps(tempDir, "PaymentService");
        PythonTarget pythonTarget = new PythonTarget();
        compilePythonProject(pythonTarget, irFile, tempDir, "payment-service");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, true, null, false, pythonTarget
        );
        assertEquals(64, check.exitCode());
        assertNotNull(check.failureRemediation());
        assertTrue(check.failureRemediation().contains("Remove impl.allowedDeps"),
            "remediation should mention removing allowedDeps, got: " + check.failureRemediation());
    }

    /**
     * Block without impl.allowedDeps under Python target → passes the guard.
     */
    @Test
    void checkWithoutAllowedDepsAndPythonTarget_doesNotTriggerGuard(@TempDir Path tempDir) throws Exception {
        Path irFile = writePythonIrWithoutAllowedDeps(tempDir, "UserAuth");
        PythonTarget pythonTarget = new PythonTarget();
        compilePythonProject(pythonTarget, irFile, tempDir, "user-auth");

        CheckResult check = CheckCommandService.executeCheck(
            irFile, tempDir, false, false, null, null,
            null, false, null, false, pythonTarget
        );
        assertNotEquals(64, check.exitCode(),
            "check without allowedDeps should not trigger UNSUPPORTED_TARGET guard");
    }

    /**
     * pr-check uses generateWiringOnly which does not trigger the allowedDeps guard.
     */
    @Test
    void prCheckUnaffectedByAllowedDeps(@TempDir Path tempDir) throws Exception {
        PythonTarget pythonTarget = new PythonTarget();
        BearIr ir = new BearIr("1", new BearIr.Block(
            "PaymentService", BearIr.BlockKind.LOGIC,
            java.util.List.of(new BearIr.Operation(
                "charge",
                new BearIr.Contract(
                    java.util.List.of(new BearIr.Field("amount", BearIr.FieldType.STRING)),
                    java.util.List.of(new BearIr.Field("amount", BearIr.FieldType.STRING))
                ),
                new BearIr.Effects(java.util.List.of()), null, java.util.List.of()
            )),
            new BearIr.Effects(java.util.List.of()),
            new BearIr.Impl(java.util.List.of(new BearIr.AllowedDep("com.example:lib", "1.0"))),
            null, java.util.List.of()
        ));

        Path outputRoot = tempDir.resolve("output");
        assertDoesNotThrow(() -> pythonTarget.generateWiringOnly(ir, tempDir, outputRoot, "payment-service"));
        assertTrue(Files.exists(outputRoot.resolve("wiring/payment-service.wiring.json")),
            "generateWiringOnly should produce wiring manifest even with allowedDeps");
    }

    // ---- IR helpers ----

    private Path writePythonIrWithAllowedDeps(Path tempDir, String blockName) throws Exception {
        String blockKey = toKebabCase(blockName);
        Path irFile = tempDir.resolve(blockKey + ".bear.yaml");
        Files.writeString(irFile,
            "version: v1\n"
            + "block:\n"
            + "  name: " + blockName + "\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: charge\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: amount\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: amount\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  impl:\n"
            + "    allowedDeps:\n"
            + "      - maven: com.example:lib\n"
            + "        version: \"1.0\"\n",
            StandardCharsets.UTF_8);
        return irFile;
    }

    private Path writePythonIrWithoutAllowedDeps(Path tempDir, String blockName) throws Exception {
        String blockKey = toKebabCase(blockName);
        Path irFile = tempDir.resolve(blockKey + ".bear.yaml");
        Files.writeString(irFile,
            "version: v1\n"
            + "block:\n"
            + "  name: " + blockName + "\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: login\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: token\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: token\n"
            + "            type: string\n"
            + "      uses:\n"
            + "        allow: []\n"
            + "  effects:\n"
            + "    allow: []\n",
            StandardCharsets.UTF_8);
        return irFile;
    }

    private void compilePythonProject(PythonTarget target, Path irFile, Path projectRoot, String blockKey) throws Exception {
        BearIr ir = new BearIrParser().parse(irFile);
        target.compile(ir, projectRoot, blockKey);
    }

    private static String toKebabCase(String input) {
        return input.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
