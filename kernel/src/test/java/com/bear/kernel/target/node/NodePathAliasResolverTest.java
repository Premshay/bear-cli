package com.bear.kernel.target.node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NodePathAliasResolver.
 * 
 * Tests the resolution of TypeScript path aliases from tsconfig.json,
 * specifically the @/* alias pattern.
 */
class NodePathAliasResolverTest {

    // -------------------------------------------------------------------------
    // Basic @/* alias resolution
    // Requirements: 6.1
    // -------------------------------------------------------------------------

    @Test
    void atSlashAliasResolvesToSrcSubtree(@TempDir Path tempDir) throws IOException {
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
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/foo/bar").normalize(), result.get());
    }

    @Test
    void atSlashAliasResolvesSimplePath(@TempDir Path tempDir) throws IOException {
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
        Optional<Path> result = resolver.resolve("@/utils", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/utils").normalize(), result.get());
    }

    @Test
    void atSlashAliasResolvesNestedPath(@TempDir Path tempDir) throws IOException {
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
        Optional<Path> result = resolver.resolve("@/blocks/user-auth/impl/UserAuthImpl", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/blocks/user-auth/impl/UserAuthImpl").normalize(), result.get());
    }

    // -------------------------------------------------------------------------
    // Missing tsconfig.json
    // Requirements: 6.6, 6.8
    // -------------------------------------------------------------------------

    @Test
    void missingTsconfigReturnsEmpty(@TempDir Path tempDir) {
        // No tsconfig.json created
        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAliasReturnsEmptyWhenTsconfigMissing(@TempDir Path tempDir) {
        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<String> alias = resolver.getAlias(tempDir);

        assertTrue(alias.isEmpty());
    }

    // -------------------------------------------------------------------------
    // No compilerOptions.paths
    // Requirements: 6.6
    // -------------------------------------------------------------------------

    @Test
    void noCompilerOptionsPathsReturnsEmpty(@TempDir Path tempDir) throws IOException {
        String tsconfig = """
            {
              "compilerOptions": {
                "target": "ES2020"
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    void emptyPathsObjectReturnsEmpty(@TempDir Path tempDir) throws IOException {
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {}
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Multi-element array → first element only
    // Requirements: 6.9
    // -------------------------------------------------------------------------

    @Test
    void multiElementArrayUsesFirstElementOnly(@TempDir Path tempDir) throws IOException {
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/*", "./alt/*", "./fallback/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isPresent());
        // Should use first element "./src/*", not "./alt/*" or "./fallback/*"
        assertEquals(tempDir.resolve("src/foo/bar").normalize(), result.get());
    }

    // -------------------------------------------------------------------------
    // Only #utils entry → Optional.empty() for @/ specifiers
    // Requirements: 6.7
    // -------------------------------------------------------------------------

    @Test
    void onlyHashUtilsEntryReturnsEmptyForAtSlash(@TempDir Path tempDir) throws IOException {
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

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    void tildeAliasIgnoredForAtSlash(@TempDir Path tempDir) throws IOException {
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "~/*": ["./src/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isEmpty());
    }

    @Test
    void dollarLibAliasIgnoredForAtSlash(@TempDir Path tempDir) throws IOException {
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "$lib/*": ["./src/lib/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Caching behavior
    // Requirements: 6.10
    // -------------------------------------------------------------------------

    @Test
    void secondCallReadsTsconfigOnlyOnce(@TempDir Path tempDir) throws IOException {
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

        // First call
        Optional<Path> result1 = resolver.resolve("@/foo", tempDir);
        assertTrue(result1.isPresent());

        // Delete tsconfig.json to verify caching
        Files.delete(tempDir.resolve("tsconfig.json"));

        // Second call should still work (cached)
        Optional<Path> result2 = resolver.resolve("@/bar", tempDir);
        assertTrue(result2.isPresent());
        assertEquals(tempDir.resolve("src/bar").normalize(), result2.get());
    }

    @Test
    void differentProjectRootInvalidatesCache(@TempDir Path tempDir1, @TempDir Path tempDir2) throws IOException {
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
        assertEquals(tempDir1.resolve("src/foo").normalize(), result1.get());

        // Second project (different root should invalidate cache)
        Optional<Path> result2 = resolver.resolve("@/foo", tempDir2);
        assertTrue(result2.isPresent());
        assertEquals(tempDir2.resolve("lib/foo").normalize(), result2.get());
    }

    // -------------------------------------------------------------------------
    // Non-@/ specifiers return empty
    // -------------------------------------------------------------------------

    @Test
    void nonAtSlashSpecifierReturnsEmpty(@TempDir Path tempDir) throws IOException {
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

        // Relative specifier
        assertTrue(resolver.resolve("./foo/bar", tempDir).isEmpty());

        // Bare specifier
        assertTrue(resolver.resolve("lodash", tempDir).isEmpty());

        // Hash alias
        assertTrue(resolver.resolve("#utils", tempDir).isEmpty());

        // Tilde alias
        assertTrue(resolver.resolve("~/foo", tempDir).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void atSlashOnlyReturnsEmpty(@TempDir Path tempDir) throws IOException {
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
        // "@/" alone (no path after) should resolve to src/
        Optional<Path> result = resolver.resolve("@/", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/").normalize(), result.get());
    }

    @Test
    void aliasWithoutTrailingStarStillWorks(@TempDir Path tempDir) throws IOException {
        // Some tsconfig setups might not have trailing *
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*": ["./src/"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/foo/bar").normalize(), result.get());
    }

    @Test
    void whitespaceInTsconfigHandled(@TempDir Path tempDir) throws IOException {
        // Tsconfig with extra whitespace
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "@/*"   :   [   "./src/*"   ]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/foo/bar").normalize(), result.get());
    }

    @Test
    void atSlashAmongOtherAliasesWorks(@TempDir Path tempDir) throws IOException {
        // Tsconfig with multiple aliases including @/*
        String tsconfig = """
            {
              "compilerOptions": {
                "paths": {
                  "#utils": ["./src/utils/*"],
                  "@/*": ["./src/*"],
                  "~/*": ["./lib/*"]
                }
              }
            }
            """;
        Files.writeString(tempDir.resolve("tsconfig.json"), tsconfig);

        NodePathAliasResolver resolver = new NodePathAliasResolver();
        Optional<Path> result = resolver.resolve("@/foo/bar", tempDir);

        assertTrue(result.isPresent());
        assertEquals(tempDir.resolve("src/foo/bar").normalize(), result.get());
    }
}
