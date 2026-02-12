package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.target.JvmTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmTargetTest {
    @Test
    void compileIsDeterministicAndCreatesExpectedFiles(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        target.compile(normalized, tempDir);
        Map<String, String> first = readTree(tempDir.resolve("build/generated/bear"));
        target.compile(normalized, tempDir);
        Map<String, String> second = readTree(tempDir.resolve("build/generated/bear"));

        assertEquals(first, second);
        assertTrue(first.containsKey("src/main/java/com/bear/generated/withdraw/Withdraw.java"));
        assertTrue(first.containsKey("src/main/java/com/bear/generated/withdraw/WithdrawLogic.java"));
        assertTrue(first.containsKey("src/main/java/com/bear/generated/withdraw/WithdrawRequest.java"));
        assertTrue(first.containsKey("src/main/java/com/bear/generated/withdraw/WithdrawResult.java"));
        assertTrue(first.containsKey("src/main/java/com/bear/generated/withdraw/LedgerPort.java"));
        assertTrue(first.containsKey("src/main/java/com/bear/generated/withdraw/IdempotencyPort.java"));
        assertTrue(first.containsKey("src/test/java/com/bear/generated/withdraw/WithdrawIdempotencyTest.java"));
        assertTrue(first.containsKey("src/test/java/com/bear/generated/withdraw/WithdrawInvariantNonNegativeTest.java"));
    }

    @Test
    void compileCreatesImplOnlyWhenMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        Path impl = tempDir.resolve("src/main/java/com/bear/generated/withdraw/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "package com.bear.generated.withdraw;\nclass KeepMe {}\n");

        target.compile(normalized, tempDir);
        assertEquals("package com.bear.generated.withdraw;\nclass KeepMe {}\n", Files.readString(impl));
    }

    private static Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .forEach(path -> {
                    String rel = root.relativize(path).toString().replace('\\', '/');
                    try {
                        files.put(rel, Files.readString(path));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
        return files;
    }
}
