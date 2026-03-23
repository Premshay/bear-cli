package com.bear.kernel.target.react.properties;

import com.bear.kernel.target.react.ReactTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ReactTarget governed roots computation.
 * 
 * Tests verify that:
 * - ownedGeneratedPrefixes returns correct paths for any block key
 * - prepareCheckWorkspace correctly handles src/shared/ presence
 * 
 * Uses plain JUnit 5 with 100+ iterations over generated inputs.
 */
class ReactGovernedRootsProperties {

    private ReactTarget target;
    private Random random;

    @BeforeEach
    void setUp() {
        target = new ReactTarget();
        random = new Random(42); // Fixed seed for reproducibility
    }

    /**
     * Property: For any valid kebab-case block key, ownedGeneratedPrefixes returns
     * exactly two paths: the types directory and the wiring manifest path.
     * 
     * Validates: Requirement 6.1, 6.2 (governed roots include feature and shared directories)
     */
    @Test
    void ownedGeneratedPrefixes_alwaysReturnsTwoPaths_forAnyBlockKey() {
        for (int i = 0; i < 100; i++) {
            String blockKey = generateRandomKebabCaseBlockKey();
            
            Set<String> prefixes = target.ownedGeneratedPrefixes(blockKey);
            
            assertEquals(2, prefixes.size(), 
                "ownedGeneratedPrefixes should return exactly 2 paths for block key: " + blockKey);
            
            // Verify types directory path
            String expectedTypesPath = "build/generated/bear/types/" + blockKey.toLowerCase() + "/";
            assertTrue(prefixes.contains(expectedTypesPath),
                "Should contain types directory path for block key: " + blockKey);
            
            // Verify wiring manifest path
            String expectedWiringPath = "build/generated/bear/wiring/" + blockKey.toLowerCase() + ".wiring.json";
            assertTrue(prefixes.contains(expectedWiringPath),
                "Should contain wiring manifest path for block key: " + blockKey);
        }
    }

    /**
     * Property: For any PascalCase block name, ownedGeneratedPrefixes converts it
     * to kebab-case for the paths.
     * 
     * Validates: Requirement 4.10 (block keys converted from kebab-case to PascalCase)
     */
    @Test
    void ownedGeneratedPrefixes_convertsToKebabCase_forPascalCaseInput() {
        // Note: The toKebabCase implementation uses regex ([a-z])([A-Z]) which only
        // inserts hyphens between lowercase-uppercase transitions. Consecutive uppercase
        // letters like "API" are treated as a single unit.
        String[][] testCases = {
            {"UserDashboard", "user-dashboard"},
            {"ProductCatalog", "product-catalog"},
            {"SimpleBlock", "simple-block"},
            {"Dashboard", "dashboard"},
            {"user-dashboard", "user-dashboard"} // Already kebab-case
        };
        
        for (String[] testCase : testCases) {
            String input = testCase[0];
            String expectedKebab = testCase[1];
            
            Set<String> prefixes = target.ownedGeneratedPrefixes(input);
            
            assertTrue(prefixes.contains("build/generated/bear/types/" + expectedKebab + "/"),
                "Should convert " + input + " to " + expectedKebab + " for types path");
            assertTrue(prefixes.contains("build/generated/bear/wiring/" + expectedKebab + ".wiring.json"),
                "Should convert " + input + " to " + expectedKebab + " for wiring path");
        }
    }

    /**
     * Property: prepareCheckWorkspace creates src/shared/ in tempRoot if and only if
     * it exists in projectRoot.
     * 
     * Validates: Requirement 6.2, 6.3 (shared root included when exists, excluded when absent)
     */
    @Test
    void prepareCheckWorkspace_createsSharedDir_iffPresentInProjectRoot(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 100; i++) {
            Path projectRoot = tempDir.resolve("project-" + i);
            Path tempRoot = tempDir.resolve("temp-" + i);
            Files.createDirectories(projectRoot);
            Files.createDirectories(tempRoot);
            
            boolean hasShared = random.nextBoolean();
            if (hasShared) {
                Files.createDirectories(projectRoot.resolve("src/shared"));
            }
            
            target.prepareCheckWorkspace(projectRoot, tempRoot);
            
            Path tempShared = tempRoot.resolve("src/shared");
            if (hasShared) {
                assertTrue(Files.isDirectory(tempShared),
                    "src/shared should be created in tempRoot when present in projectRoot");
            } else {
                assertFalse(Files.exists(tempShared),
                    "src/shared should NOT be created in tempRoot when absent in projectRoot");
            }
        }
    }

    /**
     * Property: prepareCheckWorkspace is idempotent - calling it multiple times
     * produces the same result.
     * 
     * Validates: Requirement 6.2 (governed roots computation is deterministic)
     */
    @Test
    void prepareCheckWorkspace_isIdempotent(@TempDir Path tempDir) throws IOException {
        for (int i = 0; i < 50; i++) {
            Path projectRoot = tempDir.resolve("project-idem-" + i);
            Path tempRoot = tempDir.resolve("temp-idem-" + i);
            Files.createDirectories(projectRoot);
            Files.createDirectories(tempRoot);
            
            if (random.nextBoolean()) {
                Files.createDirectories(projectRoot.resolve("src/shared"));
            }
            
            // Call multiple times
            target.prepareCheckWorkspace(projectRoot, tempRoot);
            target.prepareCheckWorkspace(projectRoot, tempRoot);
            target.prepareCheckWorkspace(projectRoot, tempRoot);
            
            // Verify state is consistent
            Path tempShared = tempRoot.resolve("src/shared");
            boolean projectHasShared = Files.isDirectory(projectRoot.resolve("src/shared"));
            assertEquals(projectHasShared, Files.isDirectory(tempShared),
                "prepareCheckWorkspace should be idempotent");
        }
    }

    /**
     * Property: ownedGeneratedPrefixes returns immutable set.
     * 
     * Validates: Defensive programming - returned set should not be modifiable
     */
    @Test
    void ownedGeneratedPrefixes_returnsImmutableSet() {
        for (int i = 0; i < 50; i++) {
            String blockKey = generateRandomKebabCaseBlockKey();
            Set<String> prefixes = target.ownedGeneratedPrefixes(blockKey);
            
            assertThrows(UnsupportedOperationException.class, () -> {
                prefixes.add("malicious/path/");
            }, "ownedGeneratedPrefixes should return immutable set");
        }
    }

    // --- Helper methods ---

    private String generateRandomKebabCaseBlockKey() {
        String[] words = {"user", "product", "order", "payment", "auth", "dashboard", "catalog", "cart", "checkout", "profile"};
        int wordCount = random.nextInt(3) + 1; // 1-3 words
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) sb.append("-");
            sb.append(words[random.nextInt(words.length)]);
        }
        return sb.toString();
    }
}
