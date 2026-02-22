package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestParsersTest {
    @Test
    void parseManifestParsesCanonicalMinifiedPayload(@TempDir Path tempDir) throws Exception {
        Path manifest = tempDir.resolve("x.surface.json");
        Files.writeString(
            manifest,
            "{\"schemaVersion\":\"v0\",\"target\":\"jvm\",\"block\":\"withdraw\",\"irHash\":\"abc\",\"generatorVersion\":\"jvm-v0\",\"capabilities\":[{\"name\":\"ledger\",\"ops\":[\"debit\"]}],\"allowedDeps\":[{\"ga\":\"com.fasterxml.jackson.core:jackson-databind\",\"version\":\"2.17.2\"}],\"invariants\":[{\"kind\":\"non_negative\",\"field\":\"result.balance\"}]}"
        );

        BoundaryManifest parsed = ManifestParsers.parseManifest(manifest);

        assertEquals("v0", parsed.schemaVersion());
        assertEquals("withdraw", parsed.block());
        assertEquals("jvm-v0", parsed.generatorVersion());
        assertEquals("2.17.2", parsed.allowedDeps().get("com.fasterxml.jackson.core:jackson-databind"));
        assertEquals(true, parsed.capabilities().get("ledger").contains("debit"));
    }

    @Test
    void parseManifestRejectsMalformedJson(@TempDir Path tempDir) throws Exception {
        Path manifest = tempDir.resolve("x.surface.json");
        Files.writeString(manifest, "{");

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseManifest(manifest));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    @Test
    void parseWiringManifestRejectsNonV2Schema(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v1\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"ledgerPort\"],\"logicRequiredPorts\":[\"ledgerPort\"],\"wrapperOwnedSemanticPorts\":[],\"wrapperOwnedSemanticChecks\":[]}"
        );

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseWiringManifest(wiring));
        assertEquals("UNSUPPORTED_WIRING_SCHEMA_VERSION", ex.reasonCode());
    }

    @Test
    void parseWiringManifestParsesSemanticFieldsWhenPresent(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v2\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\",\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\"],\"requiredEffectPorts\":[\"idempotencyPort\",\"ledgerPort\"],\"constructorPortParams\":[\"idempotencyPort\",\"ledgerPort\"],\"logicRequiredPorts\":[\"ledgerPort\"],\"wrapperOwnedSemanticPorts\":[\"idempotencyPort\"],\"wrapperOwnedSemanticChecks\":[\"IDEMPOTENCY\",\"INVARIANTS\"]}"
        );

        WiringManifest parsed = ManifestParsers.parseWiringManifest(wiring);
        assertEquals(List.of("ledgerPort"), parsed.logicRequiredPorts());
        assertEquals("src/main/java/blocks/withdraw", parsed.blockRootSourceDir());
        assertEquals(List.of("src/main/java/blocks/withdraw"), parsed.governedSourceRoots());
        assertEquals(List.of("idempotencyPort"), parsed.wrapperOwnedSemanticPorts());
        assertEquals(List.of("IDEMPOTENCY", "INVARIANTS"), parsed.wrapperOwnedSemanticChecks());
    }

    @Test
    void parseWiringManifestV2AllowsExplicitEmptySemanticArrays(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v2\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\",\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/_shared\"],\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"ledgerPort\"],\"logicRequiredPorts\":[],\"wrapperOwnedSemanticPorts\":[],\"wrapperOwnedSemanticChecks\":[]}"
        );

        WiringManifest parsed = ManifestParsers.parseWiringManifest(wiring);
        assertEquals(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared"), parsed.governedSourceRoots());
        assertEquals(List.of(), parsed.logicRequiredPorts());
        assertEquals(List.of(), parsed.wrapperOwnedSemanticPorts());
        assertEquals(List.of(), parsed.wrapperOwnedSemanticChecks());
    }

    @Test
    void parseWiringManifestV2RequiresBlockRootSourceDir(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v2\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\"],\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"ledgerPort\"],\"logicRequiredPorts\":[],\"wrapperOwnedSemanticPorts\":[],\"wrapperOwnedSemanticChecks\":[]}"
        );

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseWiringManifest(wiring));
        assertEquals("MISSING_KEY_blockRootSourceDir", ex.reasonCode());
    }

    @Test
    void parseWiringManifestV2RequiresGovernedRoots(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v2\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\",\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"ledgerPort\"],\"logicRequiredPorts\":[],\"wrapperOwnedSemanticPorts\":[],\"wrapperOwnedSemanticChecks\":[]}"
        );

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseWiringManifest(wiring));
        assertEquals("MISSING_KEY_governedSourceRoots", ex.reasonCode());
    }

    @Test
    void parseWiringManifestV2RejectsInvalidGovernedRootsOrder(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v2\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\",\"governedSourceRoots\":[\"src/main/java/blocks/_shared\",\"src/main/java/blocks/withdraw\"],\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"ledgerPort\"],\"logicRequiredPorts\":[],\"wrapperOwnedSemanticPorts\":[],\"wrapperOwnedSemanticChecks\":[]}"
        );

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseWiringManifest(wiring));
        assertEquals("INVALID_GOVERNED_SOURCE_ROOTS", ex.reasonCode());
    }

    @Test
    void parseWiringManifestV2RejectsInvalidGovernedRootPath(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v2\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\",\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/../x\"],\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"ledgerPort\"],\"logicRequiredPorts\":[],\"wrapperOwnedSemanticPorts\":[],\"wrapperOwnedSemanticChecks\":[]}"
        );

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseWiringManifest(wiring));
        assertEquals("INVALID_ROOT_PATH_governedSourceRoots", ex.reasonCode());
    }
}
