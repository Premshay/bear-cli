package com.bear.kernel.target.node.properties;

import com.bear.kernel.target.node.BoundaryDecision;
import com.bear.kernel.target.node.NodeImportBoundaryResolver;
import com.bear.kernel.target.node.NodePathAliasResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-style tests for NodePathAliasResolver.
 * Feature: phase-c-node-runtime-execution
 * Note: plain JUnit 5 (no jqwik in build); uses representative fixed inputs.
 */
class PathAliasResolutionProperties {

    // -------------------------------------------------------------------------
    // Property 11: `@/*` alias resolves to `src/` subtree
    // Feature: phase-c-node-runtime-execution, Property 11: `@/*` alias resolves to `src/` subtree
    // Validates: Requirement 6.1
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasResolvesToSrcSubtree_fooBar(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("foo/bar", tempDir);
    }

    @Test
    void atSlashAliasResolvesToSrcSubtree_utils(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("utils", tempDir);
    }

    @Test
    void atSlashAliasResolvesToSrcSubtree_blocksUserAuthImpl(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("blocks/user-auth/impl", tempDir);
    }

    @Test
    void atSlashAliasResolvesToSrcSubtree_componentsButton(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("components/Button", tempDir);
    }

    @Test
    void atSlashAliasResolvesToSrcSubtree_libHelpers(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("lib/helpers", tempDir);
    }

    @Test
    void atSlashAliasResolvesToSrcSubtree_deeplyNestedPath(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("a/b/c/d/e/f", tempDir);
    }

    @Test
    void atSlashAliasResolvesToSrcSubtree_singleSegment(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasResolvesToSrcSubtree("index", tempDir);
    }

    private void assertAtSlashAliasResolvesToSrcSubtree(String pathSuffix, Path tempDir) throws IOException {
        // Create tsconfig.json with @/* → ["./src/*"]
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

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        String specifier = "@/" + pathSuffix;
        Optional<Path> result = resolver.resolve(specifier, tempDir);

        assertTrue(result.isPresent(),
            "Specifier " + specifier + " should resolve when @/* alias is configured");
        assertEquals(tempDir.resolve("src/" + pathSuffix).normalize(), result.get(),
            "Specifier " + specifier + " should resolve to src/" + pathSuffix);
    }

    // -------------------------------------------------------------------------
    // Property 16: Non-`@/*` alias entries ignored
    // Feature: phase-c-node-runtime-execution, Property 16: Non-`@/*` alias entries ignored
    // Validates: Requirement 6.7
    // -------------------------------------------------------------------------

    @Test
    void nonAtStarAliasEntriesIgnored_hashUtils(@TempDir Path tempDir) throws IOException {
        assertNonAtStarAliasEntriesIgnored("#utils", "./src/utils/*", tempDir);
    }

    @Test
    void nonAtStarAliasEntriesIgnored_tildeStar(@TempDir Path tempDir) throws IOException {
        assertNonAtStarAliasEntriesIgnored("~/*", "./src/*", tempDir);
    }

    @Test
    void nonAtStarAliasEntriesIgnored_dollarLibStar(@TempDir Path tempDir) throws IOException {
        assertNonAtStarAliasEntriesIgnored("$lib/*", "./src/lib/*", tempDir);
    }

    @Test
    void nonAtStarAliasEntriesIgnored_atComponents(@TempDir Path tempDir) throws IOException {
        // Note: "@components/*" is different from "@/*"
        assertNonAtStarAliasEntriesIgnored("@components/*", "./src/components/*", tempDir);
    }

    @Test
    void nonAtStarAliasEntriesIgnored_hashShared(@TempDir Path tempDir) throws IOException {
        assertNonAtStarAliasEntriesIgnored("#shared", "./src/shared/*", tempDir);
    }

    @Test
    void nonAtStarAliasEntriesIgnored_baseUrl(@TempDir Path tempDir) throws IOException {
        assertNonAtStarAliasEntriesIgnored("baseUrl/*", "./src/*", tempDir);
    }

    @Test
    void nonAtStarAliasEntriesIgnored_srcStar(@TempDir Path tempDir) throws IOException {
        assertNonAtStarAliasEntriesIgnored("src/*", "./src/*", tempDir);
    }

    private void assertNonAtStarAliasEntriesIgnored(String aliasKey, String aliasValue, Path tempDir) throws IOException {
        // Create tsconfig.json with only the non-@/* alias
        String tsconfig = String.format("""
            {
              "compilerOptions": {
                "paths": {
                  "%s": ["%s"]
                }
              }
            }
            """, aliasKey, aliasValue);
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();

        // @/ specifiers should return empty when only non-@/* aliases are configured
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);
        assertTrue(result.isEmpty(),
            "@/foo/bar should return empty when only " + aliasKey + " alias is configured (not @/*)");
    }

    // -------------------------------------------------------------------------
    // Property 20: `NodePathAliasResolver` caches tsconfig read
    // Feature: phase-c-node-runtime-execution, Property 20: `NodePathAliasResolver` caches tsconfig read
    // Validates: Requirement 6.10
    // -------------------------------------------------------------------------

    @Test
    void nodePathAliasResolverCachesTsconfigRead_twoCallsSameProject(@TempDir Path tempDir) throws IOException {
        assertNodePathAliasResolverCachesTsconfigRead(tempDir, 2);
    }

    @Test
    void nodePathAliasResolverCachesTsconfigRead_fiveCallsSameProject(@TempDir Path tempDir) throws IOException {
        assertNodePathAliasResolverCachesTsconfigRead(tempDir, 5);
    }

    @Test
    void nodePathAliasResolverCachesTsconfigRead_tenCallsSameProject(@TempDir Path tempDir) throws IOException {
        assertNodePathAliasResolverCachesTsconfigRead(tempDir, 10);
    }

    private void assertNodePathAliasResolverCachesTsconfigRead(Path tempDir, int callCount) throws IOException {
        // Create tsconfig.json
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

        NodePathAliasResolver resolver = new NodePathAliasResolver();

        // First call reads the file
        Optional<Path> result1 = resolver.resolve("@/first", tempDir);
        assertTrue(result1.isPresent());

        // Delete tsconfig.json to verify caching
        Files.delete(tempDir.resolve("tsconfig.json"));

        // Subsequent calls should still work (cached)
        for (int i = 2; i <= callCount; i++) {
            Optional<Path> result = resolver.resolve("@/call" + i, tempDir);
            assertTrue(result.isPresent(),
                "Call " + i + " should still resolve using cached tsconfig");
            assertEquals(tempDir.resolve("src/call" + i).normalize(), result.get(),
                "Call " + i + " should resolve correctly using cached alias");
        }
    }

    @Test
    void cacheInvalidatedForDifferentProjectRoot(@TempDir Path tempDir1, @TempDir Path tempDir2) throws IOException {
        // Setup first project with @/* → ./src/*
        String tsconfig1 = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir1.resolve("tsconfig.json"), tsconfig1);

        // Setup second project with @/* → ./lib/*
        String tsconfig2 = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./lib/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir2.resolve("tsconfig.json"), tsconfig2);

        NodePathAliasResolver resolver = new NodePathAliasResolver();

        // First project
        Optional<Path> result1 = resolver.resolve("@/foo", tempDir1);
        assertTrue(result1.isPresent());
        assertEquals(tempDir1.resolve("src/foo").normalize(), result1.get(),
            "First project should resolve to src/");

        // Second project (different root should invalidate cache)
        Optional<Path> result2 = resolver.resolve("@/foo", tempDir2);
        assertTrue(result2.isPresent());
        assertEquals(tempDir2.resolve("lib/foo").normalize(), result2.get(),
            "Second project should resolve to lib/ (cache invalidated for different root)");
    }

    // -------------------------------------------------------------------------
    // Additional property tests for edge cases
    // -------------------------------------------------------------------------

    @Test
    void missingTsconfigReturnsEmpty_userAuth(@TempDir Path tempDir) {
        assertMissingTsconfigReturnsEmpty("user-auth", tempDir);
    }

    @Test
    void missingTsconfigReturnsEmpty_payment(@TempDir Path tempDir) {
        assertMissingTsconfigReturnsEmpty("payment", tempDir);
    }

    @Test
    void missingTsconfigReturnsEmpty_orderManager(@TempDir Path tempDir) {
        assertMissingTsconfigReturnsEmpty("order-manager", tempDir);
    }

    private void assertMissingTsconfigReturnsEmpty(String blockKey, Path tempDir) {
        // No tsconfig.json created
        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/blocks/" + blockKey + "/impl", tempDir);

        assertTrue(result.isEmpty(),
            "@/ specifier should return empty when tsconfig.json is missing");
    }

    @Test
    void multiElementArrayUsesFirstElement_srcAlt(@TempDir Path tempDir) throws IOException {
        assertMultiElementArrayUsesFirstElement("./src/*", "./alt/*", tempDir);
    }

    @Test
    void multiElementArrayUsesFirstElement_libFallback(@TempDir Path tempDir) throws IOException {
        assertMultiElementArrayUsesFirstElement("./lib/*", "./fallback/*", tempDir);
    }

    @Test
    void multiElementArrayUsesFirstElement_appBackup(@TempDir Path tempDir) throws IOException {
        assertMultiElementArrayUsesFirstElement("./app/*", "./backup/*", tempDir);
    }

    private void assertMultiElementArrayUsesFirstElement(String firstElement, String secondElement, Path tempDir) throws IOException {
        String tsconfig = String.format("""
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["%s", "%s"]
                }
              }
            }
            """, firstElement, secondElement);
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isPresent());
        // Extract the directory from the first element (remove ./* suffix)
        String expectedDir = firstElement.replace("./", "").replace("/*", "").replace("*", "");
        assertEquals(tempDir.resolve(expectedDir + "/foo/bar").normalize(), result.get(),
            "Should use first element " + firstElement + ", not " + secondElement);
    }

    @Test
    void noPathsEntryReturnsEmpty_userAuth(@TempDir Path tempDir) throws IOException {
        assertNoPathsEntryReturnsEmpty("user-auth", tempDir);
    }

    @Test
    void noPathsEntryReturnsEmpty_payment(@TempDir Path tempDir) throws IOException {
        assertNoPathsEntryReturnsEmpty("payment", tempDir);
    }

    private void assertNoPathsEntryReturnsEmpty(String blockKey, Path tempDir) throws IOException {
        String tsconfig = """
            {
              "compilerOptions": {
                "target": "ES2020",
                "module": "ESNext"
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/blocks/" + blockKey + "/impl", tempDir);

        assertTrue(result.isEmpty(),
            "@/ specifier should return empty when compilerOptions.paths is missing");
    }

    // -------------------------------------------------------------------------
    // Property 12: `@/*` alias to same block passes
    // Feature: phase-c-node-runtime-execution, Property 12: `@/*` alias to same block passes
    // Validates: Requirement 6.2
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasToSameBlockPasses_userAuth(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSameBlockPasses("user-auth", "utils", tempDir);
    }

    @Test
    void atSlashAliasToSameBlockPasses_payment(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSameBlockPasses("payment", "services/payment-service", tempDir);
    }

    @Test
    void atSlashAliasToSameBlockPasses_orderManager(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSameBlockPasses("order-manager", "impl/OrderManagerImpl", tempDir);
    }

    @Test
    void atSlashAliasToSameBlockPasses_deepPath(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSameBlockPasses("my-block", "a/b/c/d/e", tempDir);
    }

    @Test
    void atSlashAliasToSameBlockPasses_index(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSameBlockPasses("my-block", "index", tempDir);
    }

    private void assertAtSlashAliasToSameBlockPasses(String blockKey, String targetPath, Path tempDir) throws IOException {
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

        // Create block structure
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.createDirectories(blockRoot.resolve("services"));
        Path importingFile = Files.createFile(blockRoot.resolve("index.ts"));

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver(aliasResolver);

        // @/blocks/<blockKey>/<targetPath> with alias, same block → allowed()
        String specifier = "@/blocks/" + blockKey + "/" + targetPath;
        BoundaryDecision decision = resolver.resolve(importingFile, specifier, Set.of(blockRoot), tempDir);

        assertTrue(decision.pass(),
            "Same block @/ alias import " + specifier + " should pass");
    }

    // -------------------------------------------------------------------------
    // Property 13: `@/*` alias to sibling block fails
    // Feature: phase-c-node-runtime-execution, Property 13: `@/*` alias to sibling block fails
    // Validates: Requirement 6.3
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasToSiblingBlockFails_userAuthToPayment(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSiblingBlockFails("user-auth", "payment", "utils", tempDir);
    }

    @Test
    void atSlashAliasToSiblingBlockFails_paymentToOrderManager(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSiblingBlockFails("payment", "order-manager", "services/order-service", tempDir);
    }

    @Test
    void atSlashAliasToSiblingBlockFails_blockAToBlockB(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSiblingBlockFails("block-a", "block-b", "impl/BlockBImpl", tempDir);
    }

    @Test
    void atSlashAliasToSiblingBlockFails_deepPath(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSiblingBlockFails("my-block", "other-block", "a/b/c/d", tempDir);
    }

    private void assertAtSlashAliasToSiblingBlockFails(String importingBlock, String targetBlock, String targetPath, Path tempDir) throws IOException {
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

        // Create both block structures
        Path importingBlockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + importingBlock));
        Path targetBlockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + targetBlock));
        Path importingFile = Files.createFile(importingBlockRoot.resolve("index.ts"));

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver(aliasResolver);

        // @/blocks/<targetBlock>/<targetPath> with alias → fail("BOUNDARY_BYPASS")
        String specifier = "@/blocks/" + targetBlock + "/" + targetPath;
        BoundaryDecision decision = resolver.resolve(importingFile, specifier, Set.of(importingBlockRoot, targetBlockRoot), tempDir);

        assertFalse(decision.pass(),
            "Sibling block @/ alias import " + specifier + " should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason(),
            "Sibling block @/ alias import should fail with BOUNDARY_BYPASS");
    }

    // -------------------------------------------------------------------------
    // Property 14: `@/*` alias to `_shared` passes
    // Feature: phase-c-node-runtime-execution, Property 14: `@/*` alias to `_shared` passes
    // Validates: Requirement 6.4
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasToSharedPasses_utils(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSharedPasses("user-auth", "utils", tempDir);
    }

    @Test
    void atSlashAliasToSharedPasses_helpers(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSharedPasses("payment", "helpers/format", tempDir);
    }

    @Test
    void atSlashAliasToSharedPasses_types(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSharedPasses("order-manager", "types/common", tempDir);
    }

    @Test
    void atSlashAliasToSharedPasses_deepPath(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSharedPasses("my-block", "a/b/c/d/e", tempDir);
    }

    @Test
    void atSlashAliasToSharedPasses_index(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToSharedPasses("my-block", "index", tempDir);
    }

    private void assertAtSlashAliasToSharedPasses(String blockKey, String sharedPath, Path tempDir) throws IOException {
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

        // Create block and _shared structures
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Path importingFile = Files.createFile(blockRoot.resolve("index.ts"));

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver(aliasResolver);

        // @/blocks/_shared/<sharedPath> with alias → allowed()
        String specifier = "@/blocks/_shared/" + sharedPath;
        BoundaryDecision decision = resolver.resolve(importingFile, specifier, Set.of(blockRoot, sharedRoot), tempDir);

        assertTrue(decision.pass(),
            "_shared @/ alias import " + specifier + " should pass");
    }

    // -------------------------------------------------------------------------
    // Property 15: `@/*` alias to ungoverned path fails
    // Feature: phase-c-node-runtime-execution, Property 15: `@/*` alias to ungoverned path fails
    // Validates: Requirement 6.5
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasToUngovernedPathFails_outside(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToUngovernedPathFails("my-block", "outside/path", tempDir);
    }

    @Test
    void atSlashAliasToUngovernedPathFails_lib(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToUngovernedPathFails("user-auth", "lib/utils", tempDir);
    }

    @Test
    void atSlashAliasToUngovernedPathFails_components(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToUngovernedPathFails("payment", "components/Button", tempDir);
    }

    @Test
    void atSlashAliasToUngovernedPathFails_utils(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToUngovernedPathFails("order-manager", "utils/format", tempDir);
    }

    @Test
    void atSlashAliasToUngovernedPathFails_deepPath(@TempDir Path tempDir) throws IOException {
        assertAtSlashAliasToUngovernedPathFails("my-block", "a/b/c/d/e", tempDir);
    }

    private void assertAtSlashAliasToUngovernedPathFails(String blockKey, String ungovernedPath, Path tempDir) throws IOException {
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

        // Create block structure (but NOT the ungoverned path)
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Path importingFile = Files.createFile(blockRoot.resolve("index.ts"));

        NodePathAliasResolver aliasResolver = new NodePathAliasResolver();
        NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver(aliasResolver);

        // @/<ungovernedPath> with alias → fail("BOUNDARY_BYPASS")
        // This path is outside governed roots and outside build/generated/bear/
        String specifier = "@/" + ungovernedPath;
        BoundaryDecision decision = resolver.resolve(importingFile, specifier, Set.of(blockRoot), tempDir);

        assertFalse(decision.pass(),
            "Ungoverned path @/ alias import " + specifier + " should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason(),
            "Ungoverned path @/ alias import should fail with BOUNDARY_BYPASS");
    }
}
