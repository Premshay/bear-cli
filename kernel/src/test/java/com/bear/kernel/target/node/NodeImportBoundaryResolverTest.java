package com.bear.kernel.target.node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NodeImportBoundaryResolverTest {

    private final NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver();

    @Test
    void sameBlockImportPasses(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(importingFile, "./utils", Set.of(blockRoot), tempDir);

        assertTrue(decision.pass());
    }

    @Test
    void sharedImportPasses(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        // from services/ -> up 2 levels to blocks/, then into _shared/
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../_shared/shared-utils", Set.of(blockRoot, sharedRoot), tempDir);

        assertTrue(decision.pass());
    }

    @Test
    void generatedImportPasses(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Files.createDirectories(tempDir.resolve("build/generated/bear/types/user-auth"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        // from services/ -> up 4 levels to tempDir/, then into build/generated/bear/...
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../../../build/generated/bear/types/user-auth/generated",
            Set.of(blockRoot), tempDir);

        assertTrue(decision.pass());
    }

    @Test
    void siblingBlockImportFails(@TempDir Path tempDir) throws IOException {
        Path block1Root = createBlockStructure(tempDir, "user-auth");
        Path block2Root = createBlockStructure(tempDir, "payment");
        Path importingFile = block1Root.resolve("services/user-service.ts");

        // from services/ -> up 2 levels to blocks/, then into payment/
        BoundaryDecision decision = resolver.resolve(
            importingFile, "../../payment/services/payment-service",
            Set.of(block1Root, block2Root), tempDir);

        assertFalse(decision.pass());
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void barePackageImportFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(importingFile, "lodash", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("BARE_PACKAGE_IMPORT", decision.failureReason());
    }

    @Test
    void aliasImportFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(importingFile, "#utils", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("ALIAS_IMPORT", decision.failureReason());
    }

    @Test
    void urlImportFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        BoundaryDecision decision = resolver.resolve(
            importingFile, "https://example.com/utils", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("URL_IMPORT", decision.failureReason());
    }

    @Test
    void sharedImportsBlockFails(@TempDir Path tempDir) throws IOException {
        Path blockRoot = createBlockStructure(tempDir, "user-auth");
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Path sharedFile = Files.createFile(sharedRoot.resolve("shared-utils.ts"));

        BoundaryDecision decision = resolver.resolve(
            sharedFile, "../user-auth/services/user-service",
            Set.of(blockRoot, sharedRoot), tempDir);

        assertFalse(decision.pass());
        assertEquals("SHARED_IMPORTS_BLOCK", decision.failureReason());
    }

    private Path createBlockStructure(Path tempDir, String blockKey) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.createDirectories(blockRoot.resolve("services"));
        return blockRoot;
    }

    // -------------------------------------------------------------------------
    // Phase C: @/* alias resolution tests
    // Requirements: 6.2–6.5
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasSameBlockPasses(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with @/* → ["./src/*"]
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path blockRoot = createBlockStructure(tempDir, "my-block");
        Files.createFile(blockRoot.resolve("foo.ts"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/blocks/my-block/foo with alias, same block → allowed()
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/blocks/my-block/foo", Set.of(blockRoot), tempDir);

        assertTrue(decision.pass(), "Same block @/ alias import should pass");
    }

    @Test
    void atSlashAliasSiblingBlockFails(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with @/* → ["./src/*"]
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path myBlockRoot = createBlockStructure(tempDir, "my-block");
        Path otherBlockRoot = createBlockStructure(tempDir, "other-block");
        Files.createFile(otherBlockRoot.resolve("foo.ts"));
        Path importingFile = myBlockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/blocks/other-block/foo with alias → fail("BOUNDARY_BYPASS")
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/blocks/other-block/foo", Set.of(myBlockRoot, otherBlockRoot), tempDir);

        assertFalse(decision.pass(), "Sibling block @/ alias import should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void atSlashAliasSharedPasses(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with @/* → ["./src/*"]
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path blockRoot = createBlockStructure(tempDir, "my-block");
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Files.createFile(sharedRoot.resolve("utils.ts"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/blocks/_shared/utils with alias → allowed()
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/blocks/_shared/utils", Set.of(blockRoot, sharedRoot), tempDir);

        assertTrue(decision.pass(), "_shared @/ alias import should pass");
    }

    @Test
    void atSlashAliasOutsidePathFails(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with @/* → ["./src/*"]
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path blockRoot = createBlockStructure(tempDir, "my-block");
        // Create an outside path (not in blocks)
        Files.createDirectories(tempDir.resolve("src/outside"));
        Files.createFile(tempDir.resolve("src/outside/path.ts"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/outside/path with alias → fail("BOUNDARY_BYPASS")
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/outside/path", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass(), "Outside path @/ alias import should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void atSlashAliasNoAliasConfiguredFails(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with NO @/* alias (only other aliases)
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "#utils": ["./src/utils/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path blockRoot = createBlockStructure(tempDir, "my-block");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/foo with no @/* alias configured → fail("BOUNDARY_BYPASS")
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/foo", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass(), "@/ import with no @/* alias should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void atSlashAliasMissingTsconfigFails(@TempDir Path tempDir) throws IOException {
        // No tsconfig.json created
        Path blockRoot = createBlockStructure(tempDir, "my-block");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/foo with missing tsconfig → fail("BOUNDARY_BYPASS")
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/foo", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass(), "@/ import with missing tsconfig should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void atSlashAliasNoResolverFails(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with @/* → ["./src/*"]
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path blockRoot = createBlockStructure(tempDir, "my-block");
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        // Use resolver without alias resolver (backward compatibility)
        // @/foo with no alias resolver → fail("BOUNDARY_BYPASS")
        BoundaryDecision decision = resolver.resolve(
            importingFile, "@/foo", Set.of(blockRoot), tempDir);

        assertFalse(decision.pass(), "@/ import with no alias resolver should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void atSlashAliasToGeneratedDirPasses(@TempDir Path tempDir) throws IOException {
        // Setup: tsconfig.json with @/* → ["./*"] (maps to project root)
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        Path blockRoot = createBlockStructure(tempDir, "my-block");
        Files.createDirectories(tempDir.resolve("build/generated/bear/types/my-block"));
        Path importingFile = blockRoot.resolve("services/user-service.ts");

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolverWithAlias = new NodeImportBoundaryResolver(aliasResolver);

        // @/build/generated/bear/types/my-block/generated with alias → allowed()
        BoundaryDecision decision = resolverWithAlias.resolve(
            importingFile, "@/build/generated/bear/types/my-block/generated", Set.of(blockRoot), tempDir);

        assertTrue(decision.pass(), "Generated dir @/ alias import should pass");
    }
}
