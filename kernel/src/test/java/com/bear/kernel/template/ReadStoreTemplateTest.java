package com.bear.kernel.template;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.target.TargetId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ReadStoreTemplate.
 *
 * Feature: p3-capability-templates
 * Property 4: Emitted IR is valid v1 — Validates: Requirements 2.1, 4.2, 5.1
 * Property 6: Emission determinism — Validates: Requirements 2.5
 * Property 8: File placement invariant (IR under spec/) — Validates: Requirements 3.2
 */
class ReadStoreTemplateTest {

    private final ReadStoreTemplate template = new ReadStoreTemplate();
    private final BearIrParser parser = new BearIrParser();
    private final BearIrValidator validator = new BearIrValidator();

    /**
     * Emitted IR must parse and validate without exceptions.
     * Validates: Requirements 2.1, 4.2, 5.1
     */
    @Test
    void emittedIrParsesAndValidates(@TempDir Path projectRoot) throws IOException {
        TemplateParams params = new TemplateParams("my-block", TargetId.JVM);
        TemplatePack pack = template.emit(params, projectRoot);

        assertDoesNotThrow(() -> {
            BearIr ir = parser.parse(pack.irPath());
            validator.validate(ir);
        }, "Emitted IR must parse and validate without exceptions");
    }

    /**
     * Emitted IR must contain at least one effect port and at least one uses.allow entry
     * referencing that port.
     * Validates: Requirements 4.1, 4.2
     */
    @Test
    void emittedIrHasDataStorePort(@TempDir Path projectRoot) throws IOException {
        TemplateParams params = new TemplateParams("my-block", TargetId.JVM);
        TemplatePack pack = template.emit(params, projectRoot);

        BearIr ir = parser.parse(pack.irPath());
        List<BearIr.EffectPort> effectPorts = ir.block().effects().allow();
        assertFalse(effectPorts.isEmpty(), "Emitted IR must have at least one effect port");

        // Collect all port names declared in block effects
        java.util.Set<String> effectPortNames = new java.util.HashSet<>();
        for (BearIr.EffectPort port : effectPorts) {
            effectPortNames.add(port.port());
        }

        // At least one operation must have a uses.allow entry referencing a declared effect port
        boolean hasUsesReference = ir.block().operations().stream()
                .flatMap(op -> op.uses().allow().stream())
                .anyMatch(usePort -> effectPortNames.contains(usePort.port()));

        assertTrue(hasUsesReference,
                "At least one operation must have a uses.allow entry referencing a declared effect port");
    }

    /**
     * Property 6: Emission determinism.
     * Emitting the same template twice with the same params must produce byte-identical IR files.
     * Validates: Requirements 2.5
     *
     * Feature: p3-capability-templates, Property 6: Emission determinism
     */
    @ParameterizedTest
    @ValueSource(strings = {"my-block", "read-store-test", "foo-bar", "abc", "x1"})
    void emitIsDeterministic(String blockName, @TempDir Path tempRoot) throws IOException {
        TemplateParams params = new TemplateParams(blockName, TargetId.JVM);

        Path dir1 = tempRoot.resolve("run1");
        Path dir2 = tempRoot.resolve("run2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        TemplatePack pack1 = template.emit(params, dir1);
        TemplatePack pack2 = template.emit(params, dir2);

        byte[] bytes1 = Files.readAllBytes(pack1.irPath());
        byte[] bytes2 = Files.readAllBytes(pack2.irPath());

        assertArrayEquals(bytes1, bytes2,
                "Emitting the same template twice must produce byte-identical IR files for block: " + blockName);
    }

    /**
     * Property 8: IR path must be under projectRoot/spec/.
     * Validates: Requirements 3.2
     */
    @Test
    void emittedIrPathIsUnderSpecDir(@TempDir Path projectRoot) throws IOException {
        TemplateParams params = new TemplateParams("my-block", TargetId.JVM);
        TemplatePack pack = template.emit(params, projectRoot);

        Path specDir = projectRoot.resolve("spec");
        assertTrue(pack.irPath().startsWith(specDir),
                "TemplatePack.irPath() must be under projectRoot/spec/, but was: " + pack.irPath());
    }
}
