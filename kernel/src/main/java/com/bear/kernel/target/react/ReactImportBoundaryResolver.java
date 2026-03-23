package com.bear.kernel.target.react;

import java.nio.file.Path;
import java.util.Set;

/**
 * Resolves import specifiers against boundary rules for React projects.
 * Implements the boundary resolution logic from design.md:
 * 
 * - Relative imports: resolved against file parent, checked against governed roots
 * - Bare imports: only react/react-dom allowed
 * - @/* alias: resolved for nextjs-app-router sub-profile only
 * - # alias: always fails (subpath imports not supported)
 * - URL-like: always fails (http:// or https://)
 */
public class ReactImportBoundaryResolver {

    private static final Set<String> ALLOWED_BARE_PACKAGES = Set.of("react", "react-dom");

    /**
     * Resolves an import specifier and determines if it's allowed.
     *
     * @param importingFile the file containing the import
     * @param specifier the import specifier string
     * @param featureRoot the feature root directory (e.g., src/features/user-dashboard)
     * @param projectRoot the project root directory
     * @param subProfile the detected sub-profile (VITE_REACT or NEXTJS_APP_ROUTER)
     * @param sharedRoot the shared root directory (src/shared), may be null
     * @param generatedRoot the generated root directory (build/generated/bear)
     * @return BoundaryDecision indicating allowed or fail with reason
     */
    public BoundaryDecision resolve(
            Path importingFile,
            String specifier,
            Path featureRoot,
            Path projectRoot,
            ReactProjectShape subProfile,
            Path sharedRoot,
            Path generatedRoot
    ) {
        // Handle @/* alias
        if (specifier.startsWith("@/")) {
            return resolveAtAlias(specifier, featureRoot, projectRoot, subProfile, sharedRoot, generatedRoot);
        }

        // Handle # subpath imports — always fail
        if (specifier.startsWith("#")) {
            return BoundaryDecision.fail("BOUNDARY_BYPASS", 
                "Subpath imports (#) are not supported in governed React code");
        }

        // Handle URL-like specifiers — always fail
        if (specifier.startsWith("http://") || specifier.startsWith("https://")) {
            return BoundaryDecision.fail("BOUNDARY_BYPASS",
                "URL imports are not supported in governed React code");
        }

        // Handle relative imports
        if (specifier.startsWith("./") || specifier.startsWith("../")) {
            return resolveRelative(importingFile, specifier, featureRoot, projectRoot, sharedRoot, generatedRoot);
        }

        // Handle bare imports
        return resolveBare(specifier);
    }

    private BoundaryDecision resolveAtAlias(
            String specifier,
            Path featureRoot,
            Path projectRoot,
            ReactProjectShape subProfile,
            Path sharedRoot,
            Path generatedRoot
    ) {
        // @/* alias only supported in Next.js App Router sub-profile
        if (subProfile != ReactProjectShape.NEXTJS_APP_ROUTER) {
            return BoundaryDecision.fail("BOUNDARY_BYPASS",
                "@/* path aliases are not supported in vite-react sub-profile");
        }

        // Resolve @/ to ./src/ relative to project root
        String pathAfterAlias = specifier.substring(2); // Remove "@/"
        Path resolved = projectRoot.resolve("src").resolve(pathAfterAlias).normalize();

        return checkResolvedPath(resolved, featureRoot, projectRoot, sharedRoot, generatedRoot);
    }

    private BoundaryDecision resolveRelative(
            Path importingFile,
            String specifier,
            Path featureRoot,
            Path projectRoot,
            Path sharedRoot,
            Path generatedRoot
    ) {
        // Resolve relative to the importing file's parent directory
        Path resolved = importingFile.getParent().resolve(specifier).normalize();

        return checkResolvedPath(resolved, featureRoot, projectRoot, sharedRoot, generatedRoot);
    }

    private BoundaryDecision checkResolvedPath(
            Path resolved,
            Path featureRoot,
            Path projectRoot,
            Path sharedRoot,
            Path generatedRoot
    ) {
        // Normalize all paths for comparison
        Path normalizedResolved = resolved.normalize();
        Path normalizedFeatureRoot = featureRoot.normalize();

        // Check if within same feature root
        if (isWithinDirectory(normalizedResolved, normalizedFeatureRoot)) {
            return BoundaryDecision.allowed();
        }

        // Check if within src/shared/
        if (sharedRoot != null) {
            Path normalizedSharedRoot = sharedRoot.normalize();
            if (isWithinDirectory(normalizedResolved, normalizedSharedRoot)) {
                return BoundaryDecision.allowed();
            }
        }

        // Check if within build/generated/bear/
        if (generatedRoot != null) {
            Path normalizedGeneratedRoot = generatedRoot.normalize();
            if (isWithinDirectory(normalizedResolved, normalizedGeneratedRoot)) {
                return BoundaryDecision.allowed();
            }
        }

        return BoundaryDecision.fail("BOUNDARY_BYPASS",
            "Import escapes feature boundary: resolved to " + resolved);
    }

    private BoundaryDecision resolveBare(String specifier) {
        // Extract package name (handle scoped packages like @types/react)
        String packageName = extractPackageName(specifier);

        if (ALLOWED_BARE_PACKAGES.contains(packageName)) {
            return BoundaryDecision.allowed();
        }

        return BoundaryDecision.fail("BOUNDARY_BYPASS",
            "Bare package import '" + specifier + "' is not allowed in governed React code");
    }

    /**
     * Extracts the package name from a bare specifier.
     * Handles scoped packages: @scope/package -> @scope/package
     * Handles subpath imports: lodash/fp -> lodash
     */
    private String extractPackageName(String specifier) {
        if (specifier.startsWith("@")) {
            // Scoped package: @scope/package or @scope/package/subpath
            int firstSlash = specifier.indexOf('/');
            if (firstSlash == -1) {
                return specifier;
            }
            int secondSlash = specifier.indexOf('/', firstSlash + 1);
            if (secondSlash == -1) {
                return specifier;
            }
            return specifier.substring(0, secondSlash);
        } else {
            // Regular package: package or package/subpath
            int slash = specifier.indexOf('/');
            if (slash == -1) {
                return specifier;
            }
            return specifier.substring(0, slash);
        }
    }

    /**
     * Checks if a path is within a directory (or is the directory itself).
     */
    private boolean isWithinDirectory(Path path, Path directory) {
        Path normalizedPath = path.normalize();
        Path normalizedDir = directory.normalize();
        return normalizedPath.startsWith(normalizedDir);
    }

    /**
     * Result of boundary resolution.
     */
    public record BoundaryDecision(boolean isAllowed, String code, String reason) {
        public static BoundaryDecision allowed() {
            return new BoundaryDecision(true, null, null);
        }

        public static BoundaryDecision fail(String code, String reason) {
            return new BoundaryDecision(false, code, reason);
        }
    }
}
