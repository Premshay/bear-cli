package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrDeltaClassifierTest {
    @Test
    void computePrDeltasClassifiesAllowedDepsAddAsBoundaryAndOpAddAsOrdinary(@TempDir Path tempDir) throws Exception {
        String base = fixtureIr();
        String head = base
            .replaceFirst("(?m)^\\s*inputs:\\s*$", "    inputs:\n      - name: note\n        type: string")
            + "  impl:\n"
            + "    allowedDeps:\n"
            + "      - maven: com.fasterxml.jackson.core:jackson-databind\n"
            + "        version: 2.17.2\n";

        List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(parseIr(tempDir, "base.yaml", base), parseIr(tempDir, "head.yaml", head));

        int depIdx = indexOf(deltas, PrCategory.ALLOWED_DEPS, PrChange.ADDED, "com.fasterxml.jackson.core:jackson-databind@2.17.2");
        int inputIdx = indexOf(deltas, PrCategory.CONTRACT, PrChange.ADDED, "input.note:string");
        assertTrue(depIdx >= 0, "deltas=" + deltas);
        assertTrue(inputIdx >= 0, "deltas=" + deltas);
        assertEquals(PrClass.BOUNDARY_EXPANDING, deltas.get(depIdx).clazz());
        assertEquals(PrClass.ORDINARY, deltas.get(inputIdx).clazz());
        assertTrue(depIdx < inputIdx);
    }

    @Test
    void computePrDeltasIdempotencyAddEmitsSingleTopLevelDelta(@TempDir Path tempDir) throws Exception {
        String fixture = fixtureIr();

        List<PrDelta> deltas = PrDeltaClassifier.computePrDeltas(
            null,
            parseIr(tempDir, "head.yaml", fixture)
        );

        List<PrDelta> idempotencyDeltas = deltas.stream()
            .filter(d -> d.category() == PrCategory.IDEMPOTENCY)
            .toList();
        assertEquals(1, idempotencyDeltas.size());
        assertEquals("idempotency", idempotencyDeltas.get(0).key());
        assertEquals(PrChange.ADDED, idempotencyDeltas.get(0).change());
    }

    private static int indexOf(List<PrDelta> deltas, PrCategory category, PrChange change, String key) {
        for (int i = 0; i < deltas.size(); i++) {
            PrDelta d = deltas.get(i);
            if (d.category() == category && d.change() == change && d.key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static BearIr parseIr(Path tempDir, String name, String ir) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, ir, StandardCharsets.UTF_8);
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIr parsed = parser.parse(file);
        validator.validate(parsed);
        return parsed;
    }

    private static String fixtureIr() throws Exception {
        return Files.readString(TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8);
    }
}
