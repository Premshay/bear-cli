package com.bear.kernel.target.node;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;

public class NodeImportBoundaryResolver {

    private final NodePathAliasResolver aliasResolver;

    /**
     * Creates a resolver with no alias resolution support.
     * Use this constructor for backward compatibility with existing tests.
     */
    public NodeImportBoundaryResolver() {
        this.aliasResolver = null;
    }

    /**
     * Creates a resolver with alias resolution support.
     * The alias resolver is shared across all files in a single scan invocation.
     */
    public NodeImportBoundaryResolver(NodePathAliasResolver aliasResolver) {
        this.aliasResolver = aliasResolver;
    }

    /**
     * Resolves an import specifier and determines if it violates boundaries.
     */
    public BoundaryDecision resolve(Path importingFile, String specifier, Set<Path> governedRoots, Path projectRoot) {
        // 0. @/* alias resolution (Phase C) - before bare specifier check
        if (specifier.startsWith("@/")) {
            return resolveAtSlashAlias(importingFile, specifier, governedRoots, projectRoot);
        }

        // 1. Check for bare specifier (e.g., "lodash")
        if (isBareSpecifier(specifier)) {
            return BoundaryDecision.fail("BARE_PACKAGE_IMPORT");
        }

        // 2. Check for alias specifier (e.g., "#utils")
        if (isAliasSpecifier(specifier)) {
            return BoundaryDecision.fail("ALIAS_IMPORT");
        }

        // 3. Check for URL-like specifier
        if (isUrlSpecifier(specifier)) {
            return BoundaryDecision.fail("URL_IMPORT");
        }

        // 4. Check for absolute path specifier (e.g., "/absolute/path")
        if (specifier.startsWith("/")) {
            return BoundaryDecision.fail("ABSOLUTE_PATH_IMPORT");
        }

        // 5. Resolve relative specifier lexically
        Path resolved = resolveRelative(importingFile, specifier);

        // 5. Check if resolved path is within BEAR-generated directory
        Path generatedDir = projectRoot.resolve("build/generated/bear");
        if (resolved.startsWith(generatedDir)) {
            return BoundaryDecision.allowed();
        }

        // 6. Check if resolved path is within same governed root
        Path importingRoot = findGovernedRoot(importingFile, governedRoots);
        if (importingRoot != null && resolved.startsWith(importingRoot)) {
            return BoundaryDecision.allowed();
        }

        // 7. Check _shared boundary rules
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        if (importingFile.startsWith(sharedRoot)) {
            // _shared must not import block roots — only _shared-internal or generated is allowed
            if (!resolved.startsWith(sharedRoot) && !resolved.startsWith(generatedDir)) {
                return BoundaryDecision.fail("SHARED_IMPORTS_BLOCK");
            }
            return BoundaryDecision.allowed();
        }
        // Block importing _shared is allowed
        if (resolved.startsWith(sharedRoot)) {
            return BoundaryDecision.allowed();
        }

        // 8. All other cases are boundary bypass
        return BoundaryDecision.fail("BOUNDARY_BYPASS");
    }

    private boolean isBareSpecifier(String specifier) {
        // Bare specifier: no leading . or / and not a URL
        return !specifier.startsWith(".") && !specifier.startsWith("/") && !specifier.startsWith("http") && !specifier.startsWith("#");
    }

    private boolean isAliasSpecifier(String specifier) {
        return specifier.startsWith("#") && !specifier.startsWith("#/");
    }

    private boolean isUrlSpecifier(String specifier) {
        return specifier.startsWith("http://") || specifier.startsWith("https://");
    }

    private Path resolveRelative(Path importingFile, String specifier) {
        Path parentDir = importingFile.getParent();
        if (parentDir == null) {
            parentDir = Path.of(".");
        }
        Path base = parentDir.resolve(specifier).normalize();

        // If the base path already points to a regular file, use it directly.
        if (Files.exists(base) && Files.isRegularFile(base)) {
            return base;
        }

        // Probe common TypeScript/JavaScript resolution variants, e.g. "./foo" ->
        // "./foo.ts", "./foo.tsx", "./foo/index.ts", etc.
        String[] extensions = new String[] {
            ".ts", ".tsx", ".mts", ".cts",
            ".js", ".jsx", ".mjs", ".cjs"
        };

        // Try same path with known extensions: "./foo" -> "./foo.ts", "./foo.js", ...
        for (String ext : extensions) {
            Path candidate = base.resolveSibling(base.getFileName().toString() + ext);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        // If "base" is a directory, try "index" files inside it: "./foo/index.ts", etc.
        if (Files.exists(base) && Files.isDirectory(base)) {
            for (String ext : extensions) {
                Path candidate = base.resolve("index" + ext);
                if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }

        // Fallback to the normalized base path if no concrete file variant is found.
        return base;
    }

    private Path findGovernedRoot(Path file, Set<Path> governedRoots) {
        for (Path root : governedRoots) {
            if (file.startsWith(root)) {
                return root;
            }
        }
        return null;
    }

    /**
     * Resolves @/* alias specifiers using the configured alias resolver.
     * 
     * @/ specifier with alias configured: resolve to path, apply same boundary rules as relative imports
     *   - same block → allowed
     *   - _shared importing _shared → allowed
     *   - _shared importing block → fail("SHARED_IMPORTS_BLOCK")
     *   - block importing _shared → allowed
     *   - generated dir → allowed
     *   - otherwise → fail("BOUNDARY_BYPASS")
     * @/ specifier with no alias configured → fail("BOUNDARY_BYPASS")
     */
    private BoundaryDecision resolveAtSlashAlias(Path importingFile, String specifier, Set<Path> governedRoots, Path projectRoot) {
        // If no alias resolver configured, treat as boundary bypass
        if (aliasResolver == null) {
            return BoundaryDecision.fail("BOUNDARY_BYPASS");
        }

        Optional<Path> resolved = aliasResolver.resolve(specifier, projectRoot);
        if (resolved.isEmpty()) {
            // No alias configured or tsconfig missing → fail
            return BoundaryDecision.fail("BOUNDARY_BYPASS");
        }

        Path target = resolved.get();

        // Apply same boundary rules as relative imports:

        // 1. Check if resolved path is within BEAR-generated directory
        Path generatedDir = projectRoot.resolve("build/generated/bear");
        if (target.startsWith(generatedDir)) {
            return BoundaryDecision.allowed();
        }

        // 2. Check if resolved path is within same governed root as importing file
        Path importingRoot = findGovernedRoot(importingFile, governedRoots);
        if (importingRoot != null && target.startsWith(importingRoot)) {
            return BoundaryDecision.allowed();
        }

        // 3. Enforce _shared import block rule for alias imports:
        //    files under src/blocks/_shared cannot import other blocks via alias
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        if (importingFile.startsWith(sharedRoot) && !target.startsWith(sharedRoot)) {
            return BoundaryDecision.fail("SHARED_IMPORTS_BLOCK");
        }

        // 4. Allow imports into _shared from other locations
        if (target.startsWith(sharedRoot)) {
            return BoundaryDecision.allowed();
        }

        // 5. All other cases are boundary bypass
        return BoundaryDecision.fail("BOUNDARY_BYPASS");
    }
}
